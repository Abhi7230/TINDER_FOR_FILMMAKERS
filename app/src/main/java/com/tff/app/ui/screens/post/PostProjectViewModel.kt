package com.tff.app.ui.screens.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.AppClock
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.domain.ListingRules
import com.tff.app.domain.RuleResult
import com.tff.app.model.Area
import com.tff.app.model.City
import com.tff.app.model.CompType
import com.tff.app.model.Meals
import com.tff.app.model.OwnerSummary
import com.tff.app.model.Profile
import com.tff.app.model.Project
import com.tff.app.model.ProjectDraft
import com.tff.app.model.ProjectRole
import com.tff.app.model.ProjectStatus
import com.tff.app.model.Reputation
import com.tff.app.model.RoleCode
import com.tff.app.model.RoleStatus
import com.tff.app.model.RoleWithProject
import com.tff.app.model.Stage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WizardPhase { LOADING, GUEST, ERROR, READY }

/** After Publish: the live roles, for the share sheet (F17). [updated] = an already-live listing was edited. */
data class PublishedInfo(val projectId: String, val items: List<RoleWithProject>, val updated: Boolean)

sealed interface WizardEvent {
    data object DraftSaved : WizardEvent
    data object ListingUpdated : WizardEvent
    data class FixFirst(val count: Int) : WizardEvent
    data class Failed(val message: String?) : WizardEvent
    data object Exit : WizardEvent
}

data class WizardUi(
    val phase: WizardPhase = WizardPhase.LOADING,
    val error: String? = null,
    val draft: ProjectDraft? = null,
    val postOnly: Boolean = false,
    val step: WizardStep = WizardStep.BASICS,
    /** Role editor: a role id, [PostProjectViewModel.NEW_ROLE] while picking a new role, or null. */
    val editingRoleId: String? = null,
    /** True while the editor is choosing a new code for [editingRoleId] ([Change role]). */
    val changingCode: Boolean = false,
    val pay: PayDefaults = PayDefaults(),
    val rules: List<RuleResult> = emptyList(),
    val attempted: Set<WizardStep> = emptySet(),
    val owner: Profile? = null,
    val ownerSummary: OwnerSummary? = null,
    val cities: List<City> = emptyList(),
    val savedId: String? = null,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val saving: Boolean = false,
    val publishing: Boolean = false,
    val dirty: Boolean = false,
    val dismissed: Set<String> = emptySet(),
    val focusField: String? = null,
    val focusSeq: Int = 0,
    val returnToReview: Boolean = false,
    val published: PublishedInfo? = null,
    val today: java.time.LocalDate = AppClock.today(),
) {
    val city: City? get() = draft?.let { d -> cities.firstOrNull { it.id == d.project.cityId } }
    val area: Area? get() = draft?.let { d -> city?.areas?.firstOrNull { it.id == d.project.areaId } }
    val currency: String get() = city?.currency ?: "INR"
    val isLive: Boolean get() = status == ProjectStatus.OPEN || status == ProjectStatus.CREWED
    /** Dates of a live listing move only through Reschedule (F12), so booked people get the change. */
    val datesLocked: Boolean get() = isLive && (draft?.project?.shootDates?.isNotEmpty() == true)
    val activeRoles: List<ProjectRole> get() = draft?.let { WizardLogic.activeRoles(it) }.orEmpty()
    val editingRole: ProjectRole? get() = editingRoleId?.let { id -> draft?.roles?.firstOrNull { it.id == id } }
    fun showErrors(step: WizardStep): Boolean = step in attempted
    fun issues(field: String, roleId: String? = null): List<RuleResult> = rules.filter { it.field == field && it.roleId == roleId }
    fun roleIssues(roleId: String): List<RuleResult> = rules.filter { it.roleId == roleId }
}

/**
 * S11 Post a project wizard: required fields first, defaults pre-filled, live ListingRules, Save draft at any time,
 * Publish → share sheet. Also edits an existing DRAFT (it opens on Review so blockers show with their fix) and a live
 * listing ("Update listing", dates locked to Reschedule).
 */
class PostProjectViewModel(private val repo: Repo, private val projectId: String?) : ViewModel() {
    companion object {
        const val NEW_ROLE = "__new_role"
        const val NUDGE_R21 = "R21"
        const val NUDGE_R22 = "R22"
    }

    private val _ui = MutableStateFlow(WizardUi())
    val ui: StateFlow<WizardUi> get() = _ui
    private val _events = Channel<WizardEvent>(Channel.BUFFERED)
    val events: Flow<WizardEvent> get() = _events.receiveAsFlow()
    private val openedAt = System.currentTimeMillis()
    private var loggedOpen = false

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(phase = WizardPhase.LOADING, error = null) }
            if (repo.session.value == null) { _ui.update { it.copy(phase = WizardPhase.GUEST) }; return@launch }
            try {
                val me = repo.me().first() ?: return@launch fail("Finish your profile first, then post a project.")
                val cities = repo.cities()
                val rep = runCatching { repo.reputation(me.id) }.getOrDefault(Reputation(me.id))
                val owner = OwnerSummary(me.id, me.displayName, me.level, me.idVerified, rep, me.lastActiveAt)
                val existing = projectId?.let { id ->
                    repo.draft(id) ?: return@launch fail("This project isn't yours to edit, or it no longer exists.")
                }
                if (existing != null && existing.project.status !in setOf(ProjectStatus.DRAFT, ProjectStatus.OPEN)) {
                    return@launch fail(
                        if (existing.project.status == ProjectStatus.CREWED) "This project is fully crewed. Change terms on each booking card instead."
                        else "This project can't be edited any more.",
                    )
                }
                val today = AppClock.today()
                val draft = existing ?: WizardLogic.newDraft(WizardLogic.newId(), me, cities.firstOrNull { it.id == me.cityId }, AppClock.nowMillis())
                val postOnly = existing != null && existing.project.stage == Stage.POST &&
                    (existing.project.shootDates.isEmpty() || ListingRules.isPostOnly(existing.roles))
                _ui.update {
                    it.copy(
                        phase = WizardPhase.READY, draft = draft, owner = me, ownerSummary = owner, cities = cities,
                        postOnly = postOnly, pay = if (existing != null) WizardLogic.payFrom(WizardLogic.activeRoles(existing)) else PayDefaults(),
                        savedId = existing?.project?.id, status = existing?.project?.status ?: ProjectStatus.DRAFT,
                        step = if (existing != null) WizardStep.REVIEW else WizardStep.BASICS,
                        attempted = if (existing != null) WizardStep.entries.toSet() else emptySet(),
                        today = today,
                    ).recomputed()
                }
                if (!loggedOpen) {
                    loggedOpen = true
                    repo.log("wizard_opened", mapOf("existing" to (existing != null).toString()))
                }
            } catch (e: Exception) {
                fail((e as? RepoException)?.userMessage)
            }
        }
    }

    private fun fail(message: String?) {
        _ui.update { it.copy(phase = WizardPhase.ERROR, error = message) }
    }

    private fun WizardUi.recomputed(): WizardUi {
        val d = draft ?: return this
        val owner = owner ?: return this
        val city = city ?: return copy(rules = emptyList())
        return copy(rules = WizardLogic.check(d, postOnly, owner, city, today))
    }

    private fun edit(f: (WizardUi) -> WizardUi) = _ui.update { f(it).copy(dirty = true).recomputed() }

    // ───────────── Project fields ─────────────

    fun updateProject(f: (Project) -> Project) = edit { s -> s.copy(draft = s.draft?.let { d -> d.copy(project = f(d.project)) }) }

    fun setCity(cityId: String) = edit { s ->
        val city = s.cities.firstOrNull { it.id == cityId } ?: return@edit s
        val d = s.draft ?: return@edit s
        val area = city.areas.firstOrNull { it.id == d.project.areaId } ?: city.areas.firstOrNull()
        s.copy(draft = d.copy(
            project = d.project.copy(cityId = city.id, areaId = area?.id ?: d.project.areaId),
            roles = d.roles.map { it.copy(currency = city.currency) },
        ))
    }

    fun setPostOnly(on: Boolean) = edit { s ->
        val d = s.draft ?: return@edit s
        val stage = if (on) Stage.POST else if (d.project.stage == Stage.POST) Stage.PRE else d.project.stage
        val roles = if (on) d.roles.map { r -> if (WizardLogic.isPostDept(r.roleCode)) r.copy(remoteOk = true) else r } else d.roles
        s.copy(postOnly = on, draft = d.copy(project = d.project.copy(stage = stage), roles = roles))
    }

    fun setStage(stage: Stage) = edit { s ->
        val d = s.draft ?: return@edit s
        s.copy(draft = d.copy(project = d.project.copy(stage = stage)), postOnly = if (stage == Stage.POST) s.postOnly || d.project.stage != Stage.POST else false)
    }

    // ───────────── Roles ─────────────

    fun setPay(p: PayDefaults) = edit { it.copy(pay = p) }

    fun applyPayToAll() = edit { s ->
        val d = s.draft ?: return@edit s
        s.copy(draft = d.copy(roles = d.roles.map { r -> if (r.status == RoleStatus.OPEN && r.filledCount == 0) WizardLogic.withPay(r, s.pay) else r }))
    }

    /** [Add role]: opens the picker, or adds [preset] straight away (R22 nudge "Add an editor"). */
    fun startAddRole(preset: RoleCode? = null) {
        if (preset != null) addRole(preset) else _ui.update { it.copy(editingRoleId = NEW_ROLE, changingCode = false) }
    }

    fun pickCode(code: RoleCode) {
        val s = _ui.value
        if (s.editingRoleId == NEW_ROLE) addRole(code)
        else if (s.changingCode && s.editingRoleId != null) {
            edit { st -> st.copy(changingCode = false, draft = st.draft?.let { d ->
                d.copy(roles = d.roles.map { if (it.id == st.editingRoleId) WizardLogic.changeCode(it, code, st.postOnly) else it })
            }) }
        }
    }

    private fun addRole(code: RoleCode) = edit { s ->
        val d = s.draft ?: return@edit s
        val r = WizardLogic.newRole(WizardLogic.newId(), code, d.project, s.pay, s.currency, s.postOnly)
        s.copy(draft = d.copy(roles = d.roles + r), editingRoleId = r.id, changingCode = false, step = WizardStep.ROLES)
    }

    fun startChangeCode() = _ui.update { it.copy(changingCode = true) }
    fun cancelChangeCode() = _ui.update { it.copy(changingCode = false) }

    fun updateRole(id: String, f: (ProjectRole) -> ProjectRole) = edit { s ->
        s.copy(draft = s.draft?.let { d -> d.copy(roles = d.roles.map { if (it.id == id) f(it) else it }) })
    }

    fun duplicateRole(id: String) = edit { s ->
        val d = s.draft ?: return@edit s
        val i = d.roles.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return@edit s
        val copy = WizardLogic.duplicate(d.roles[i], WizardLogic.newId())
        s.copy(draft = d.copy(roles = d.roles.toMutableList().apply { add(i + 1, copy) }), editingRoleId = copy.id, changingCode = true)
    }

    fun removeRole(id: String) = edit { s ->
        val d = s.draft ?: return@edit s
        s.copy(draft = d.copy(roles = d.roles.filterNot { it.id == id }), editingRoleId = if (s.editingRoleId == id) null else s.editingRoleId)
    }

    fun openRole(id: String) = _ui.update { it.copy(editingRoleId = id, changingCode = false) }
    fun closeEditor() = _ui.update { it.copy(editingRoleId = null, changingCode = false, attempted = it.attempted + WizardStep.ROLES) }

    // ───────────── Steps ─────────────

    fun goTo(step: WizardStep, fromReview: Boolean = false) =
        _ui.update { it.copy(step = step, editingRoleId = null, changingCode = false, returnToReview = fromReview) }

    fun next() {
        val s = _ui.value
        if (s.returnToReview) { goTo(WizardStep.REVIEW); return }
        val blockers = WizardLogic.stepBlockers(s.step, s.rules)
        _ui.update { it.copy(attempted = it.attempted + s.step) }
        if (blockers.isNotEmpty()) {
            focus(blockers.first().field)
            _events.trySend(WizardEvent.FixFirst(blockers.size))
            return
        }
        val nextStep = WizardStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        _ui.update { it.copy(step = nextStep, attempted = if (nextStep == WizardStep.REVIEW) WizardStep.entries.toSet() else it.attempted) }
    }

    /** System back / top-bar back: editor → step → previous step → leave. */
    fun back(): Boolean {
        val s = _ui.value
        when {
            s.changingCode -> _ui.update { it.copy(changingCode = false) }
            s.editingRoleId != null -> closeEditor()
            s.returnToReview -> goTo(WizardStep.REVIEW)
            s.step.ordinal > 0 -> _ui.update { it.copy(step = WizardStep.entries[s.step.ordinal - 1]) }
            else -> return false
        }
        return true
    }

    /** A red Review item: go to its step (and its role editor) and bring the field into view. */
    fun jump(r: RuleResult) {
        val step = WizardLogic.stepOf(r)
        _ui.update {
            it.copy(
                step = step, returnToReview = true, attempted = it.attempted + step,
                editingRoleId = if (WizardLogic.opensRoleEditor(r)) r.roleId else null, changingCode = false,
            )
        }
        focus(r.field)
    }

    fun focus(field: String?) = _ui.update { it.copy(focusField = field, focusSeq = it.focusSeq + 1) }
    fun clearFocus() = _ui.update { it.copy(focusField = null) }

    /** One-tap fixes for the unambiguous rules ("If you can't pay people, feed people"; "cap at 10-12"). */
    fun quickFix(r: RuleResult) {
        when {
            r.rule == "R02" && r.field == Fields.MEALS -> updateProject { it.copy(meals = Meals.TWO_PLUS_MEALS) }
            r.rule == "R06" -> updateProject { it.copy(hoursCap = 12) }
        }
    }

    fun hasQuickFix(r: RuleResult): Boolean = (r.rule == "R02" && r.field == Fields.MEALS) || r.rule == "R06"

    fun dismiss(key: String) = _ui.update { it.copy(dismissed = it.dismissed + key) }

    // ───────────── Save & publish ─────────────

    private suspend fun persist(): Result<String> {
        val s = _ui.value
        val d = s.draft ?: return Result.failure(IllegalStateException())
        val toSave = WizardLogic.forSave(d, s.postOnly)
        return repo.saveDraft(toSave).onSuccess { pid ->
            // The backend may assign its own role ids; keep the local list in step with what was stored.
            val stored = runCatching { repo.draft(pid) }.getOrNull()
            _ui.update { st ->
                val cur = st.draft ?: return@update st
                val ids = cur.roles.map { it.id }.toSet()
                val roles = if (stored != null && stored.roles.map { it.id }.toSet() != ids) stored.roles else cur.roles
                st.copy(savedId = pid, dirty = false, draft = cur.copy(project = cur.project.copy(id = pid), roles = roles)).recomputed()
            }
        }
    }

    fun saveDraft() {
        val s = _ui.value
        if (s.saving || s.publishing || s.draft == null) return
        if (s.isLive) { publish(); return }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true) }
            persist()
                .onSuccess { _events.send(WizardEvent.DraftSaved) }
                .onFailure { _events.send(WizardEvent.Failed((it as? RepoException)?.userMessage)) }
            _ui.update { it.copy(saving = false) }
        }
    }

    /** "Save draft" from the leave dialog: save, then close the wizard. */
    fun saveAndExit() {
        val s = _ui.value
        if (s.saving || s.publishing || s.draft == null) return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true) }
            persist()
                .onSuccess { _events.send(WizardEvent.DraftSaved); _events.send(WizardEvent.Exit) }
                .onFailure { _events.send(WizardEvent.Failed((it as? RepoException)?.userMessage)) }
            _ui.update { it.copy(saving = false) }
        }
    }

    fun publish() {
        val s = _ui.value
        if (s.saving || s.publishing || s.draft == null) return
        val blockers = WizardLogic.blockers(s.rules)
        if (blockers.isNotEmpty()) {
            viewModelScope.launch { repo.log("publish_blocked", mapOf("rule" to blockers.first().rule, "count" to blockers.size.toString())) }
            _ui.update { it.copy(step = WizardStep.REVIEW, editingRoleId = null, returnToReview = false, attempted = WizardStep.entries.toSet()) }
            _events.trySend(WizardEvent.FixFirst(blockers.size))
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(publishing = true) }
            val wasLive = s.isLive
            val pid = persist().getOrElse {
                _events.send(WizardEvent.Failed((it as? RepoException)?.userMessage))
                _ui.update { st -> st.copy(publishing = false) }
                return@launch
            }
            repo.publish(pid)
                .onSuccess {
                    val seconds = (System.currentTimeMillis() - openedAt) / 1000
                    repo.log("project_published", mapOf(
                        "seconds" to seconds.toString(), "roles" to s.activeRoles.size.toString(),
                        "post_only" to s.postOnly.toString(), "updated" to wasLive.toString(),
                    ))
                    if (wasLive) {
                        _ui.update { st -> st.copy(publishing = false, status = ProjectStatus.OPEN) }
                        _events.send(WizardEvent.ListingUpdated)
                        _events.send(WizardEvent.Exit)
                    } else {
                        val stored = runCatching { repo.draft(pid) }.getOrNull()
                        val ids = (stored?.let { WizardLogic.activeRoles(it) } ?: s.activeRoles).map { it.id }
                        val items = ids.mapNotNull { runCatching { repo.roleWithProject(it) }.getOrNull() }
                        _ui.update { st -> st.copy(publishing = false, status = ProjectStatus.OPEN, published = PublishedInfo(pid, items, updated = false)) }
                    }
                }
                .onFailure {
                    val e = it as? RepoException
                    repo.log("publish_blocked", mapOf("rule" to (e?.code ?: "unknown")))
                    _ui.update { st -> st.copy(publishing = false) }
                    _events.send(WizardEvent.Failed(e?.userMessage))
                }
        }
    }

    /** Cards for the live preview, built from the draft exactly as publish will store it. */
    fun previewDraft(s: WizardUi): ProjectDraft? = s.draft?.let { WizardLogic.previewDraft(it, s.postOnly, s.city) }
}
