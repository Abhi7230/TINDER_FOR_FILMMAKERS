package com.tff.app.ui.screens.shootday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.model.Attendance
import com.tff.app.model.Features
import com.tff.app.model.MatchView
import com.tff.app.model.ProjectFull
import com.tff.app.model.ShootDayFull
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.userMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [day] is null when the sheet isn't visible to the viewer (not booked, cancelled, or unknown id). */
data class DayData(
    val project: ProjectFull?,
    val day: ShootDayFull?,
    val myId: String?,
    /** The viewer's own booking on this project (collaborators), for "Can't make it" → withdraw. */
    val myMatch: MatchView?,
)

/** S16V shoot day view for the team: call time, address, logistics, ack, calendar and trusted-contact share. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShootDayViewModel(private val repo: Repo, private val projectId: String, private val dayId: String) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val features: Features = repo.features

    val state: StateFlow<Load<DayData>> = retry.flatMapLatest {
        combine(repo.project(projectId), repo.shootDays(projectId), repo.session) { p, days, s -> Triple(p, days.firstOrNull { it.day.id == dayId }, s?.userId) }
            .flatMapLatest { (p, d, me) ->
                val mid = p?.myMatch?.id
                if (mid == null || p.isOwner) flowOf(DayData(p, d, me, null))
                else repo.matchView(mid).map { DayData(p, d, me, it) }
            }
            .map<DayData, Load<DayData>> { Load.Ready(it) }
            .onStart { emit(Load.Loading) }
            .catch { emit(Load.Failed(it.userMessage())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    val busy = MutableStateFlow(false)

    fun retry() { retry.value++ }

    fun ack(onDone: (Result<Unit>) -> Unit) = call(onDone) { repo.ackShootDay(dayId) }
    fun stillComing(onDone: (Result<Unit>) -> Unit) = call(onDone) { repo.setAttendance(dayId, Attendance.CONFIRMED) }
    fun nudge(profileId: String, onDone: (Result<Unit>) -> Unit) = call(onDone, showBusy = false) { repo.nudge(dayId, profileId) }

    private fun call(onDone: (Result<Unit>) -> Unit, showBusy: Boolean = true, block: suspend () -> Result<Unit>) {
        if (showBusy && busy.value) return
        if (showBusy) busy.value = true
        viewModelScope.launch {
            val r = runCatching { block() }.getOrElse { Result.failure(it) }
            if (showBusy) busy.value = false
            onDone(r)
        }
    }
}
