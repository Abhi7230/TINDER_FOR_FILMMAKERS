package com.tff.app.ui.screens.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.AppClock
import com.tff.app.core.Geo
import com.tff.app.domain.Gate
import com.tff.app.domain.MatchEngine
import com.tff.app.ui.screens.discover.DeckLogic
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.Application
import com.tff.app.model.Profile
import com.tff.app.model.RoleWithProject
import com.tff.app.model.SwipeDir
import com.tff.app.ui.screens.discover.DeckSignals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoleDetailUi(
    val loading: Boolean = true,
    val error: String? = null,
    /** Loaded, but the role isn't visible (closed, draft, blocked or deleted). */
    val missing: Boolean = false,
    val item: RoleWithProject? = null,
    val me: Profile? = null,
    val isGuest: Boolean = false,
    val distanceKm: Double? = null,
    val application: Application? = null,
    val saved: Boolean = false,
    val busy: Boolean = false,
    /** Signed-in viewer, not the owner: this listing isn't one of their roles (gate G1). */
    val notYourRole: Boolean = false,
    /** Signed-in viewer: the shoot is beyond their travel radius (×1.5, the deck's own fallback) (gate G2). */
    val outOfRange: Boolean = false,
    /** The viewer's area name, for "858 km from Jayanagar". */
    val myAreaName: String? = null,
) {
    val isOwner: Boolean get() = me != null && item != null && item.project.ownerId == me.id
}

/** S06 Role detail. Guests can read everything (deep links `calltime://r/{id}` work signed out). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoleDetailViewModel(private val repo: Repo, private val roleId: String) : ViewModel() {
    private val _ui = MutableStateFlow(RoleDetailUi(isGuest = repo.session.value == null))
    val ui: StateFlow<RoleDetailUi> = _ui.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            repo.session.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repo.myApplications() }.collect { apps ->
                _ui.update { it.copy(application = apps.firstOrNull { a -> a.roleId == roleId }) }
            }
        }
        viewModelScope.launch {
            repo.session.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repo.saved() }.collect { saved ->
                _ui.update { it.copy(saved = saved.any { r -> r.role.id == roleId }) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val guest = repo.session.value == null
                val item = repo.roleWithProject(roleId)
                val me = if (guest) null else repo.me().first()
                val cities = if (me != null) repo.cities() else emptyList()
                val area = me?.let { m -> cities.asSequence().flatMap { it.areas.asSequence() }.firstOrNull { it.id == m.areaId } }
                val dist = if (me != null && item != null && !item.role.remoteOk) {
                    area?.let { Geo.haversineKm(it.lat, it.lng, item.areaLat, item.areaLng) }
                } else null
                // A shared link can open a role outside the viewer's gates: say so instead of a silent "858 KM".
                var notYours = false
                var far = false
                if (me != null && item != null && item.project.ownerId != me.id) {
                    val viewer = DeckLogic.viewer(me, null, cities)
                    if (viewer != null) {
                        val failed = runCatching { MatchEngine.failedGates(viewer, item, AppClock.today(), MatchEngine.FALLBACK_RADIUS, includeAllRemote = false) }
                            .getOrDefault(emptySet())
                        notYours = Gate.G1_ROLE in failed
                        far = Gate.G2_PLACE in failed && dist != null
                    }
                }
                _ui.update {
                    it.copy(
                        loading = false, missing = item == null, item = item, me = me, isGuest = guest, distanceKm = dist,
                        notYourRole = notYours, outOfRange = far, myAreaName = area?.name,
                    )
                }
            } catch (e: RepoException) {
                _ui.update { it.copy(loading = false, error = e.userMessage) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = "") }
            }
        }
    }

    /** Pass or Save from the sticky bar. The Gigs deck drops the card too. */
    suspend fun swipe(dir: SwipeDir): Result<Unit> {
        _ui.update { it.copy(busy = true) }
        val r = viewModelScope.async { runCatching { repo.swipeRole(roleId, dir) }.getOrElse { Result.failure(it) } }.await()
        _ui.update { it.copy(busy = false) }
        if (r.isSuccess) DeckSignals.hide(roleId)
        return r
    }

    fun applied() { DeckSignals.hide(roleId) }

    suspend fun applyNow(form: ApplyForm): ApplyOutcome = viewModelScope.async { ApplyActions.submit(repo, roleId, form) }.await()
}
