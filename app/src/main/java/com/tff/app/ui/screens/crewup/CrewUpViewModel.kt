package com.tff.app.ui.screens.crewup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.ApplicantView
import com.tff.app.model.Decision
import com.tff.app.model.Match
import com.tff.app.model.RoleWithProject
import com.tff.app.ui.screens.discover.Ws4Prefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One chip in the role selector: the role and how many people are waiting for an answer. */
data class RoleTab(val item: RoleWithProject, val pending: Int)

data class CrewUpUi(
    val loading: Boolean = true,
    val error: String? = null,
    val isGuest: Boolean = false,
    val roles: List<RoleTab> = emptyList(),
    val selectedId: String? = null,
    val tab: CrewTab = CrewTab.APPLICANTS,
    val applicants: List<ApplicantView> = emptyList(),
    val scout: List<ApplicantView> = emptyList(),
    val scoutLoading: Boolean = false,
    val scoutError: Boolean = false,
    val asksLeft: Int = 10,
    /** Decisions made on the selected role in this visit (for the "everyone has an answer" copy). */
    val answered: Int = 0,
) {
    val selected: RoleTab? get() = roles.firstOrNull { it.item.role.id == selectedId }
}

/**
 * S09 Crew up: the viewer's open roles, ranked Applicants (repo.applicants, ApplicantRanker on the
 * Repo side) and Scout candidates (repo.scout), decisions, asks and the daily ask counter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewUpViewModel(private val repo: Repo, private val initialRoleId: String?) : ViewModel() {
    private val _ui = MutableStateFlow(CrewUpUi(isGuest = repo.session.value == null))
    val ui: StateFlow<CrewUpUi> = _ui.asStateFlow()

    private val applicantOrder = mutableMapOf<String, List<String>>()
    private val scoutOrder = mutableMapOf<String, List<String>>()
    private var mainJob: Job? = null
    private var scoutJob: Job? = null
    private var asksJob: Job? = null
    private var asksProject: String? = null
    private var userPickedTab = false
    private var items: Map<String, RoleWithProject> = emptyMap()
    private var latest: Map<String, List<ApplicantView>> = emptyMap()

    init { start() }

    private fun start() {
        mainJob?.cancel()
        mainJob = viewModelScope.launch {
            repo.session.map { it?.userId }.distinctUntilChanged().flatMapLatest { uid ->
                if (uid == null) flowOf(null)
                else repo.myProjects().map { CrewUpLogic.openRoleIds(it) }.distinctUntilChanged()
            }.flatMapLatest { ids ->
                when {
                    ids == null -> flowOf(null)
                    ids.isEmpty() -> flowOf(emptyMap())
                    else -> {
                        // Role cards first (titles, pay, currency), then one live applicants list per role.
                        val fetched = ids.mapNotNull { id -> repo.roleWithProject(id) }
                        items = fetched.associateBy { it.role.id }
                        if (fetched.isEmpty()) flowOf(emptyMap())
                        else combine(fetched.map { item -> repo.applicants(item.role.id).map { item.role.id to it } }) { it.toMap() }
                    }
                }
            }.catch { e ->
                _ui.update { it.copy(loading = false, error = (e as? RepoException)?.userMessage ?: "") }
            }.collect { byRole -> onApplicants(byRole) }
        }
    }

    private fun onApplicants(byRole: Map<String, List<ApplicantView>>?) {
        if (byRole == null) {
            _ui.update { it.copy(loading = false, isGuest = true, roles = emptyList(), selectedId = null) }
            return
        }
        latest = byRole
        val tabs = items.values.filter { it.role.id in byRole }.sortedBy { it.project.id + "/" + it.role.id }
            .map { RoleTab(it, byRole[it.role.id].orEmpty().size) }
        val prev = _ui.value
        val selected = prev.selectedId?.takeIf { id -> tabs.any { it.item.role.id == id } }
            ?: initialRoleId?.takeIf { id -> tabs.any { it.item.role.id == id } }
            ?: tabs.firstOrNull()?.item?.role?.id
        val changed = selected != prev.selectedId
        val tab = if (changed && !userPickedTab) defaultTabFor(tabs, selected) else prev.tab
        _ui.update {
            it.copy(
                loading = false, isGuest = false, error = null, roles = tabs, selectedId = selected,
                applicants = applicantsFor(selected), tab = tab, answered = if (changed) 0 else it.answered,
                scout = if (changed) emptyList() else it.scout,
            )
        }
        if (changed) onRoleChanged()
        else if (tab == CrewTab.SCOUT) {
            // Someone applied (or withdrew) meanwhile: they move between Scout and Applicants.
            val ids = latest[selected].orEmpty().map { it.card.profile.id }.toSet()
            if (ids != lastApplicantIds) refreshScout()
        }
        lastApplicantIds = latest[selected].orEmpty().map { it.card.profile.id }.toSet()
    }
    private var lastApplicantIds: Set<String> = emptySet()

    private fun defaultTabFor(tabs: List<RoleTab>, roleId: String?): CrewTab =
        tabs.firstOrNull { it.item.role.id == roleId }?.let { CrewUpLogic.defaultTab(it.item.role, it.pending) } ?: CrewTab.APPLICANTS

    private fun applicantsFor(roleId: String?): List<ApplicantView> {
        roleId ?: return emptyList()
        val l = CrewUpLogic.stableOrder(applicantOrder[roleId].orEmpty(), latest[roleId].orEmpty())
        applicantOrder[roleId] = l.map(CrewUpLogic::key)
        return l
    }

    private fun onRoleChanged() {
        val sel = _ui.value.selected ?: return
        if (asksProject != sel.item.project.id) {
            asksProject = sel.item.project.id
            asksJob?.cancel()
            asksJob = viewModelScope.launch {
                repo.asksLeftToday(sel.item.project.id).collect { n -> _ui.update { it.copy(asksLeft = n) } }
            }
        }
        // Scarce crew roles also load Scout in the background, so the Applicants deck can say how many more fit.
        if (_ui.value.tab == CrewTab.SCOUT || CrewUpLogic.isScarce(sel.item.role)) refreshScout()
    }

    fun selectRole(roleId: String) {
        val s = _ui.value
        if (roleId == s.selectedId || s.roles.none { it.item.role.id == roleId }) return
        userPickedTab = false
        _ui.update {
            it.copy(selectedId = roleId, answered = 0, scout = emptyList(), applicants = applicantsFor(roleId), tab = defaultTabFor(it.roles, roleId))
        }
        onRoleChanged()
    }

    fun selectTab(tab: CrewTab) {
        userPickedTab = true
        _ui.update { it.copy(tab = tab) }
        if (tab == CrewTab.SCOUT) refreshScout()
    }

    /** Scout candidates (crew roles only), minus the people skipped on this device. */
    fun refreshScout() {
        val sel = _ui.value.selected ?: return
        if (!CrewUpLogic.hasScout(sel.item.role)) return
        val roleId = sel.item.role.id
        scoutJob?.cancel()
        scoutJob = viewModelScope.launch {
            _ui.update { it.copy(scoutLoading = it.scout.isEmpty(), scoutError = false) }
            runCatching { repo.scout(roleId) }
                .onSuccess { fresh ->
                    val skips = Ws4Prefs.scoutSkips(roleId)
                    val l = CrewUpLogic.stableOrder(scoutOrder[roleId].orEmpty(), fresh.filter { it.card.profile.id !in skips })
                    scoutOrder[roleId] = l.map(CrewUpLogic::key)
                    if (_ui.value.selectedId == roleId) _ui.update { it.copy(scout = l, scoutLoading = false) }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _ui.update { it.copy(scoutLoading = false, scoutError = true) }
                }
        }
    }

    /** Let's talk / Backup / Not this time on an applicant. TALK returns the new match. */
    suspend fun decide(v: ApplicantView, d: Decision): Result<Match?> {
        val app = v.application ?: return Result.failure(RepoException("This person hasn't applied."))
        val r = viewModelScope.async { runCatching { repo.decide(app.id, d, null) }.getOrElse { Result.failure(it) } }.await()
        if (r.isSuccess) _ui.update { it.copy(answered = it.answered + 1) }
        return r
    }

    /** Scout "Skip": hide this person for this role (kept on this device). */
    fun skip(v: ApplicantView) {
        val roleId = _ui.value.selectedId ?: return
        Ws4Prefs.addScoutSkip(roleId, v.card.profile.id)
        _ui.update { s -> s.copy(scout = s.scout.filterNot { it.card.profile.id == v.card.profile.id }) }
    }

    fun unskip(v: ApplicantView) {
        val roleId = _ui.value.selectedId ?: return
        Ws4Prefs.removeScoutSkip(roleId, v.card.profile.id)
        refreshScout()
    }

    /** Null if an Ask may be sent, else the reason ("Kabir only takes paid work right now."). */
    suspend fun askCheck(v: ApplicantView): String? {
        val roleId = _ui.value.selectedId ?: return null
        return viewModelScope.async { runCatching { repo.askCheck(roleId, v.card.profile.id) }.getOrNull() }.await()
    }

    suspend fun sendAsk(v: ApplicantView, note: String?): Result<Unit> {
        val roleId = _ui.value.selectedId ?: return Result.failure(RepoException("Pick a role first."))
        val r = viewModelScope.async {
            runCatching { repo.sendAsk(roleId, v.card.profile.id, note?.trim()?.ifBlank { null }) }.getOrElse { Result.failure(it) }
        }.await()
        // The deck flies the card out first, then calls [dropFromScout].
        return r
    }

    /** Take someone out of the Scout deck after an ask went to them (the card has already left). */
    fun dropFromScout(v: ApplicantView) {
        _ui.update { s -> s.copy(scout = s.scout.filterNot { it.card.profile.id == v.card.profile.id }) }
    }

    fun retry() {
        _ui.update { it.copy(loading = true, error = null) }
        start()
    }
}
