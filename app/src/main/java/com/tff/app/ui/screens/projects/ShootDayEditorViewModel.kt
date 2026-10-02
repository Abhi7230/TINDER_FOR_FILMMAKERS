package com.tff.app.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.AppClock
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.MatchState
import com.tff.app.model.ProjectFull
import com.tff.app.model.ShootDayDraft
import com.tff.app.model.ShootDayFull
import com.tff.app.model.TeamEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** The S16E form. [perPerson] holds only the people whose call differs from the general call. */
data class DayForm(
    val day: LocalDate? = null,
    val generalCall: LocalTime = LocalTime.of(7, 0),
    val wrapBy: LocalTime = LocalTime.of(17, 0),
    val address: String = "",
    val parking: String = "",
    val hospital: String = "",
    val meals: String = "",
    val notes: String = "",
    /** How people get home after a late wrap. Stored as the first line of the sheet's notes ("Getting home: …"). */
    val gettingHome: String = "",
    val perPerson: Map<String, LocalTime> = emptyMap(),
) {
    fun callFor(profileId: String): LocalTime = perPerson[profileId] ?: generalCall
    /** Overnight shoots wrap the next morning (19:00 → 05:00). */
    val overnight: Boolean get() = wrapBy <= generalCall
    /** Wrap at 22:00 or later (or past midnight): the sheet has to say how people get home. */
    val lateWrap: Boolean get() = overnight || !wrapBy.isBefore(LATE_WRAP)
    /** The notes as stored: the "Getting home" line first, then the owner's notes. */
    val storedNotes: String get() = DayNotes.join(gettingHome, notes)

    companion object {
        val LATE_WRAP: LocalTime = LocalTime.of(22, 0)
        const val NOTES_MAX = 500
        const val HOME_MAX = 120

        /** Room left for the owner's notes once the "Getting home" line is in the same 500 characters. */
        fun notesBudget(gettingHome: String): Int =
            if (gettingHome.isBlank()) NOTES_MAX else NOTES_MAX - DayNotes.PREFIX.length - gettingHome.trim().length - 1
    }
}

/** The "Getting home" line lives inside ShootDay.notes (no model change): "Getting home: Cab paid after 22:00\n…". */
object DayNotes {
    const val PREFIX = "Getting home: "

    fun join(gettingHome: String, notes: String): String {
        val home = gettingHome.trim().replace('\n', ' ')
        val rest = notes.trim()
        return when {
            home.isEmpty() -> rest
            rest.isEmpty() -> PREFIX + home
            else -> PREFIX + home + "\n" + rest
        }
    }

    /** Stored notes → (getting home, the rest). */
    fun split(stored: String?): Pair<String, String> {
        val text = stored.orEmpty()
        if (!text.startsWith(PREFIX)) return "" to text
        val nl = text.indexOf('\n')
        return if (nl < 0) text.removePrefix(PREFIX).trim() to "" else text.substring(PREFIX.length, nl).trim() to text.substring(nl + 1).trim()
    }
}

enum class EditorPhase { LOADING, ERROR, NOT_OWNER, POST_ONLY, NO_DATES, READY }

data class EditorUi(
    val phase: EditorPhase = EditorPhase.LOADING,
    val error: String? = null,
    val full: ProjectFull? = null,
    val days: List<ShootDayFull> = emptyList(),
    val form: DayForm = DayForm(),
    /** The day sheet being edited, once it exists (after Publish it's the one just sent). */
    val dayId: String? = null,
    val publishing: Boolean = false,
    val attempted: Boolean = false,
    val dirty: Boolean = false,
    val today: LocalDate = AppClock.today(),
) {
    /** Booked on-set people: the ones who get this day sheet and a call time. */
    val crew: List<TeamEntry> get() = full?.team.orEmpty().filter { it.match.state == MatchState.BOOKED && !it.match.isPostRole }
    val current: ShootDayFull? get() = dayId?.let { id -> days.firstOrNull { it.day.id == id } } ?: form.day?.let { d -> days.firstOrNull { it.day.day == d } }
    val dates: List<LocalDate> get() = full?.project?.shootDates.orEmpty().sorted()
    val addressError: Boolean get() = form.address.isBlank()
    val dayError: Boolean get() = form.day == null
    val gettingHomeError: Boolean get() = form.lateWrap && form.gettingHome.isBlank()
    val notesTooLong: Boolean get() = form.storedNotes.length > DayForm.NOTES_MAX
}

sealed interface EditorEvent {
    data class Published(val updated: Boolean, val count: Int) : EditorEvent
    data class Nudged(val name: String) : EditorEvent
    data class Failed(val message: String?) : EditorEvent
    data object DayCancelled : EditorEvent
}

/** S16E Shoot day editor (owner): date from the booking dates, calls, address, logistics; Publish / Update; acks and Nudge. */
class ShootDayEditorViewModel(private val repo: Repo, private val projectId: String, rawShootDayId: String?) : ViewModel() {
    companion object {
        private const val DATE_PREFIX = "date:"
        /** A new sheet for one specific shoot date: passed through nav.toShootDayEditor's shootDayId as "date:2026-10-11". */
        fun forDate(d: LocalDate): String = DATE_PREFIX + d
    }

    /** The existing sheet to edit (null = a new one). */
    private val shootDayId: String? = rawShootDayId?.takeUnless { it.startsWith(DATE_PREFIX) }
    /** The date a new sheet was asked for, if any. */
    private val requestedDate: LocalDate? = rawShootDayId?.takeIf { it.startsWith(DATE_PREFIX) }
        ?.let { runCatching { LocalDate.parse(it.removePrefix(DATE_PREFIX)) }.getOrNull() }

    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> get() = _ui
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> get() = _events.receiveAsFlow()
    private var prefilled = false
    private var started = false

    init { start() }

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            combine(repo.project(projectId), repo.shootDays(projectId)) { full, days -> full to days }
                .catch { e -> _ui.update { it.copy(phase = EditorPhase.ERROR, error = (e as? RepoException)?.userMessage) }; started = false }
                .collect { (full, days) ->
                    _ui.update { s ->
                        val phase = when {
                            full == null || !full.isOwner -> EditorPhase.NOT_OWNER
                            full.project.shootDates.isEmpty() && full.roles.none { !(it.remoteOk && com.tff.app.model.Taxonomy.dept(it.roleCode) in com.tff.app.model.Taxonomy.POST_DEPTS) } -> EditorPhase.POST_ONLY
                            full.project.shootDates.isEmpty() -> EditorPhase.NO_DATES
                            else -> EditorPhase.READY
                        }
                        var next = s.copy(phase = phase, full = full, days = days, today = AppClock.today())
                        if (!prefilled && full != null && phase == EditorPhase.READY) {
                            prefilled = true
                            next = next.copy(form = prefill(full, days), dayId = shootDayId ?: next.dayId)
                        }
                        next
                    }
                }
        }
    }

    fun retry() { started = false; prefilled = false; _ui.value = EditorUi(); start() }

    fun featuresReschedule(): Boolean = repo.features.reschedule

    private fun prefill(full: ProjectFull, days: List<ShootDayFull>): DayForm {
        val p = full.project
        val existing = shootDayId?.let { id -> days.firstOrNull { it.day.id == id } }
        if (existing != null) {
            val d = existing.day
            val (home, notes) = DayNotes.split(d.notes)
            return DayForm(
                day = d.day, generalCall = d.generalCall, wrapBy = d.wrapBy, address = d.address,
                parking = d.parkingNote.orEmpty(), hospital = d.hospitalNote.orEmpty(), meals = d.mealsPlan.orEmpty(), notes = notes, gettingHome = home,
                perPerson = existing.calls.filter { it.call.callTime != d.generalCall }.associate { it.call.profileId to it.call.callTime },
            )
        }
        val today = AppClock.today()
        val taken = days.map { it.day.day }.toSet()
        val day = requestedDate?.takeIf { it in p.shootDates && it !in taken }
            ?: p.shootDates.sorted().firstOrNull { it !in taken && !it.isBefore(today) } ?: p.shootDates.sorted().firstOrNull { !it.isBefore(today) }
        val last = days.maxByOrNull { it.day.publishedAt ?: 0L }?.day
        val call = last?.generalCall ?: LocalTime.of(7, 0)
        val wrap = last?.wrapBy ?: call.plusHours((p.hoursCap ?: 10).toLong())
        return DayForm(
            day = day, generalCall = call, wrapBy = wrap,
            address = p.exactAddress ?: last?.address.orEmpty(),
            parking = last?.parkingNote.orEmpty(), hospital = last?.hospitalNote.orEmpty(), meals = last?.mealsPlan.orEmpty(),
            gettingHome = DayNotes.split(last?.notes).first,
        )
    }

    fun edit(f: (DayForm) -> DayForm) = _ui.update { it.copy(form = f(it.form), dirty = true) }

    /** Picking a date that already has a sheet switches to editing that sheet. */
    fun pickDay(d: LocalDate) {
        val s = _ui.value
        val sheet = s.days.firstOrNull { it.day.day == d }
        if (sheet != null && sheet.day.id != s.dayId) {
            val day = sheet.day
            val (home, notes) = DayNotes.split(day.notes)
            _ui.update {
                it.copy(dayId = day.id, form = DayForm(
                    day = day.day, generalCall = day.generalCall, wrapBy = day.wrapBy, address = day.address,
                    parking = day.parkingNote.orEmpty(), hospital = day.hospitalNote.orEmpty(), meals = day.mealsPlan.orEmpty(), notes = notes, gettingHome = home,
                    perPerson = sheet.calls.filter { c -> c.call.callTime != day.generalCall }.associate { c -> c.call.profileId to c.call.callTime },
                ), dirty = false)
            }
        } else if (sheet == null) {
            _ui.update { it.copy(dayId = null, form = it.form.copy(day = d), dirty = true) }
        }
    }

    fun setCall(profileId: String, t: LocalTime) = edit { f ->
        f.copy(perPerson = if (t == f.generalCall) f.perPerson - profileId else f.perPerson + (profileId to t))
    }

    fun setGeneralCall(t: LocalTime) = edit { f ->
        // People who were on the general call move with it; staggered calls stay as set.
        f.copy(generalCall = t, perPerson = f.perPerson.filterValues { it != t })
    }

    fun canPublish(): Boolean = _ui.value.let { !it.dayError && !it.addressError && !it.gettingHomeError && !it.notesTooLong }

    fun publish() {
        val s = _ui.value
        _ui.update { it.copy(attempted = true) }
        if (!canPublish() || s.publishing) return
        val f = s.form
        val day = f.day ?: return
        val updating = s.current != null
        viewModelScope.launch {
            _ui.update { it.copy(publishing = true) }
            repo.publishShootDay(
                ShootDayDraft(
                    projectId = projectId, shootDayId = s.current?.day?.id, day = day, generalCall = f.generalCall, wrapBy = f.wrapBy,
                    address = f.address.trim(), parkingNote = f.parking.trim().ifBlank { null }, hospitalNote = f.hospital.trim().ifBlank { null },
                    mealsPlan = f.meals.trim().ifBlank { null }, notes = f.storedNotes.ifBlank { null },
                    perPersonCall = s.crew.associate { it.match.collaboratorId to f.callFor(it.match.collaboratorId) },
                ),
            ).onSuccess {
                repo.log("day_sheet_published", mapOf("updated" to updating.toString(), "people" to s.crew.size.toString()))
                _ui.update { st -> st.copy(publishing = false, dirty = false, dayId = st.days.firstOrNull { it.day.day == day }?.day?.id ?: st.dayId) }
                _events.send(EditorEvent.Published(updating, s.crew.size))
            }.onFailure { e ->
                _ui.update { it.copy(publishing = false) }
                _events.send(EditorEvent.Failed((e as? RepoException)?.userMessage))
            }
        }
    }

    fun nudge(profileId: String, name: String) {
        val id = _ui.value.current?.day?.id ?: return
        viewModelScope.launch {
            repo.nudge(id, profileId)
                .onSuccess { _events.send(EditorEvent.Nudged(name)) }
                .onFailure { e -> _events.send(EditorEvent.Failed((e as? RepoException)?.userMessage)) }
        }
    }

    /** F12: cancelling a shoot day = the project's dates without it, broadcast to everyone booked and waiting. */
    fun cancelDay() {
        val s = _ui.value
        val day = s.form.day ?: return
        val left = s.dates - day
        if (left.isEmpty()) return
        viewModelScope.launch {
            repo.reschedule(projectId, left)
                .onSuccess { _events.send(EditorEvent.DayCancelled) }
                .onFailure { e -> _events.send(EditorEvent.Failed((e as? RepoException)?.userMessage)) }
        }
    }
}
