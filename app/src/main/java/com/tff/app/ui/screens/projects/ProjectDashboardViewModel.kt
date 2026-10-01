package com.tff.app.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.AppClock
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.domain.ListingRules
import com.tff.app.model.MatchState
import com.tff.app.model.MatchView
import com.tff.app.model.ProjectFull
import com.tff.app.model.ProjectStatus
import com.tff.app.model.RoleStatus
import com.tff.app.model.RoleWithProject
import com.tff.app.model.ShootDayFull
import com.tff.app.model.WrapGroup
import com.tff.app.model.WrapItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DashTab { ROLES, TEAM, DAYS, WRAP, SETTINGS }

/** Everything the S13 dashboard shows, for the owner or a collaborator. */
data class DashData(
    val full: ProjectFull,
    val days: List<ShootDayFull> = emptyList(),
    val wrap: List<WrapGroup> = emptyList(),
    val myView: MatchView? = null,
    val myItems: List<WrapItem> = emptyList(),
    val today: LocalDate = AppClock.today(),
) {
    val project get() = full.project
    val postOnly: Boolean get() = full.project.shootDates.isEmpty() && ListingRules.isPostOnly(full.roles.filter { it.status != RoleStatus.CLOSED })
    val firstShootPassed: Boolean get() = full.project.shootStart?.let { !today.isBefore(it) } == true
    /** Wrap shows after the first shoot day, or as soon as a post role is booked (S13 tab 4). */
    val wrapVisible: Boolean get() = firstShootPassed || full.project.status == ProjectStatus.WRAPPED ||
        full.team.any { it.match.isPostRole } || wrap.any { g -> g.items.isNotEmpty() && g.match.state == MatchState.WRAPPED }
    val bookedOnSet get() = full.team.filter { it.match.state == MatchState.BOOKED && !it.match.isPostRole }
    val canEdit: Boolean get() = full.isOwner && full.project.status in setOf(ProjectStatus.DRAFT, ProjectStatus.OPEN)
    val lastShootPassed: Boolean get() = full.project.shootEnd?.let { today.isAfter(it) } == true

    /** Role + project for the share text (F17). Distance isn't needed for the text. */
    fun item(roleId: String): RoleWithProject? {
        val r = full.roles.firstOrNull { it.id == roleId } ?: return null
        return RoleWithProject(
            role = r, project = full.project, owner = full.owner, cityName = full.cityName, areaName = full.areaName,
            areaLat = 0.0, areaLng = 0.0, paidRoles = full.paidRoles, totalRoles = full.totalRoles,
        )
    }
}

sealed interface DashUi {
    data object Loading : DashUi
    data object Missing : DashUi
    data class Error(val message: String?) : DashUi
    data class Ready(val data: DashData) : DashUi
}

sealed interface DashEvent {
    data class Toast(val res: Int, val arg: String? = null) : DashEvent
    data class Message(val text: String) : DashEvent
    data object Closed : DashEvent
}

/** S13 Project dashboard. Owner: roles, team & backups, shoot days, wrap, settings. Collaborator: my booking, days, wrap, team. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDashboardViewModel(private val repo: Repo, private val projectId: String) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = _busy
    private val _events = Channel<DashEvent>(Channel.BUFFERED)
    val events: Flow<DashEvent> get() = _events.receiveAsFlow()
    val features get() = repo.features

    val ui: StateFlow<DashUi> = retry.flatMapLatest {
        repo.project(projectId).flatMapLatest { full ->
            if (full == null) flowOf<DashUi>(DashUi.Missing)
            else {
                val member = full.isOwner || full.myMatch?.state in setOf(MatchState.BOOKED, MatchState.WRAPPED)
                val days = if (member && repo.features.shootDays) repo.shootDays(projectId) else flowOf(emptyList())
                val wrap = if (full.isOwner && repo.features.wrap) repo.projectWrap(projectId) else flowOf(emptyList())
                val mid = full.myMatch?.id?.takeIf { !full.isOwner }
                val view = if (mid != null) repo.matchView(mid) else flowOf(null)
                val items = if (mid != null && repo.features.wrap) repo.wrapItems(mid) else flowOf(emptyList())
                combine(days, wrap, view, items) { d, w, v, it ->
                    DashUi.Ready(DashData(full, d, w, v, it, AppClock.today())) as DashUi
                }
            }
        }.onStart { emit(DashUi.Loading) }
            .catch { e -> emit(DashUi.Error((e as? RepoException)?.userMessage)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashUi.Loading)

    fun retry() { retry.value++ }

    private fun run(okRes: Int, okArg: String? = null, after: (() -> Unit)? = null, block: suspend () -> Result<*>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block()
                .onSuccess { _events.send(DashEvent.Toast(okRes, okArg)); after?.invoke() }
                .onFailure { e -> _events.send(DashEvent.Message((e as? RepoException)?.userMessage ?: e.message ?: "")) }
            _busy.value = false
        }
    }

    fun closeRole(roleId: String) = run(com.tff.app.R.string.ws5_toast_role_closed) { repo.closeRole(roleId) }

    fun offerSlot(roleId: String, backupName: String) =
        run(com.tff.app.R.string.ws5_toast_slot_offered, backupName) { repo.offerSlotToBackup(roleId) }

    fun reschedule(dates: List<LocalDate>) = run(com.tff.app.R.string.ws5_toast_rescheduled) { repo.reschedule(projectId, dates) }

    fun cancelProject(reason: String) = run(com.tff.app.R.string.ws5_toast_cancelled) { repo.cancelProject(projectId, reason) }

    /** A draft has no audience, so "delete" is a silent cancel that the Projects list hides (no Repo delete in the contract). */
    fun deleteDraft() = run(com.tff.app.R.string.ws5_toast_draft_deleted, after = { _events.trySend(DashEvent.Closed) }) {
        repo.cancelProject(projectId, DRAFT_DELETED)
    }

    fun markWrapped() = run(com.tff.app.R.string.ws5_toast_wrapped) { repo.markWrapped(projectId) }

    fun log(event: String, props: Map<String, String> = emptyMap()) { viewModelScope.launch { repo.log(event, props) } }

    companion object {
        const val DRAFT_DELETED = "Draft deleted by the owner"
    }
}
