package com.tff.app.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.AppClock
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.domain.DeckCard
import com.tff.app.domain.Viewer
import com.tff.app.model.AppMode
import com.tff.app.model.City
import com.tff.app.model.DeckFilters
import com.tff.app.model.Density
import com.tff.app.model.Profile
import com.tff.app.model.ProjectStatus
import com.tff.app.model.RoleStatus
import com.tff.app.model.RoleWithProject
import com.tff.app.model.Session
import com.tff.app.model.SwipeDir
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S05 Gigs deck state. [cards] is the deck after filters and local swipes, in frozen order. */
data class DiscoverUi(
    val loading: Boolean = true,
    val error: String? = null,
    val isGuest: Boolean = false,
    /** Signed in but onboarding isn't finished: no profile to gate on yet. */
    val needsProfile: Boolean = false,
    val me: Profile? = null,
    val city: City? = null,
    val cities: List<City> = emptyList(),
    val density: Density? = null,
    val cards: List<DeckCard> = emptyList(),
    val unfilteredCount: Int = 0,
    val filteredCount: Int = 0,
    val filters: DeckFilters = DeckFilters(),
    val likesLeft: Int = 25,
    val ownsOpenRoles: Boolean = false,
    val empty: DeckEmpty? = null,
    val blockers: DeckBlockers = DeckBlockers(),
    val languageOptions: List<String> = emptyList(),
    /** Bumped whenever the deck is rebuilt from the Repo, so the screen resets its swipe state. */
    val deckVersion: Int = 0,
    val busy: Boolean = false,
    /** Roles that "Widen radius" would add (0 = don't offer it). */
    val widenGain: Int = 0,
)

/** The 5 s Undo chip after a Pass or Save. */
data class UndoOffer(val roleId: String, val dir: SwipeDir, val at: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow(DiscoverUi(isGuest = repo.session.value == null))
    val ui: StateFlow<DiscoverUi> = _ui.asStateFlow()

    private val _undo = MutableStateFlow<UndoOffer?>(null)
    val undo: StateFlow<UndoOffer?> = _undo.asStateFlow()

    /** The card whose Apply sheet is open (right swipe or Interested button). */
    private val _applyFor = MutableStateFlow<DeckCard?>(null)
    val applyFor: StateFlow<DeckCard?> = _applyFor.asStateFlow()

    private var raw: List<DeckCard> = emptyList()
    private var candidates: List<RoleWithProject> = emptyList()
    private var viewer: Viewer? = null
    private val localGone = mutableSetOf<String>()
    private var sessionSwipes = 0
    private var hasHistory = false
    private var buildJob: Job? = null
    private var currentCityId: String? = null
    /** Wall-clock time of the last successful deck build (0 = none yet). */
    private var builtAtMs = 0L

    init {
        // Who is looking: a session change (persona switch, sign-in, sign-out) rebuilds everything.
        viewModelScope.launch {
            repo.session.map { it?.userId to (it?.hasProfile == true) }.distinctUntilChanged().collectLatest { (uid, _) ->
                localGone.clear(); sessionSwipes = 0
                _undo.value = null
                // Someone else is looking now: never flash the previous person's deck.
                raw = emptyList(); candidates = emptyList(); viewer = null; currentCityId = null
                _ui.update { it.copy(loading = true, cards = emptyList(), empty = null, density = null, me = null, filters = DeckFilters()) }
                if (uid == null) {
                    _ui.update { it.copy(isGuest = true, needsProfile = false, me = null, ownsOpenRoles = false) }
                    rebuild(cityId = Ws4Prefs.guestCityId)
                } else {
                    _ui.update { it.copy(isGuest = false) }
                    // Profile changes that MatchEngine reads (radius, remote, roles...) rebuild the deck.
                    repo.me().map { DeckLogic.gatingKey(it) }.distinctUntilChanged().collectLatest { key ->
                        if (key == null) {
                            _ui.update { it.copy(loading = false, needsProfile = true, me = null, cards = emptyList(), empty = null) }
                        } else {
                            _ui.update { it.copy(needsProfile = false) }
                            rebuild(cityId = null)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.session.flatMapLatest { s -> if (s == null) flowOf(DAILY) else repo.likesLeftToday() }
                .distinctUntilChanged().collect { n -> _ui.update { it.copy(likesLeft = n) }; recompute() }
        }
        viewModelScope.launch {
            repo.session.flatMapLatest { s -> if (s == null) flowOf(false) else repo.myProjects().map { ps ->
                ps.any { p -> p.project.status == ProjectStatus.OPEN && p.roles.any { it.status == RoleStatus.OPEN } }
            } }.distinctUntilChanged().collect { owns -> _ui.update { it.copy(ownsOpenRoles = owns) } }
        }
        viewModelScope.launch {
            repo.session.flatMapLatest { s -> if (s == null) flowOf(false) else combineHistory() }
                .distinctUntilChanged().collect { h -> hasHistory = h; recompute() }
        }
        // Swipes made on Role detail drop out of the frozen deck.
        viewModelScope.launch { DeckSignals.gone.collect { recompute() } }
    }

    private fun combineHistory() = kotlinx.coroutines.flow.combine(repo.myApplications(), repo.saved()) { a, s -> a.isNotEmpty() || s.isNotEmpty() }

    private fun densityFor(cityId: String) {
        if (cityId == currentCityId && _ui.value.density != null) return
        currentCityId = cityId
        densityJob?.cancel()
        densityJob = viewModelScope.launch { repo.density(cityId).collect { d -> _ui.update { it.copy(density = d) } } }
    }
    private var densityJob: Job? = null

    /** Fetch candidates and build the deck. [cityId] is only passed for guests. */
    private fun rebuild(cityId: String?) {
        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            // First build (or a retry after an error) shows the skeleton; later rebuilds keep the screen.
            _ui.update { it.copy(loading = it.loading || it.deckVersion == 0 || it.error != null, error = null) }
            try {
                val session: Session? = repo.session.value
                val cities = _ui.value.cities.ifEmpty { repo.cities() }
                val me = if (session != null) repo.me().first() else null
                val city = cities.firstOrNull { it.id == (me?.cityId ?: cityId) } ?: cities.firstOrNull { it.id == Ws4Prefs.DEFAULT_GUEST_CITY }
                city?.let { densityFor(it.id) }
                val minRate = if (me != null) repo.myPrivateMinRate() else null
                val cands = repo.discoverCandidates(if (me == null) city?.id ?: Ws4Prefs.DEFAULT_GUEST_CITY else null)
                val v = me?.let { DeckLogic.viewer(it, minRate, cities) }
                val deck = DeckLogic.build(v, cands, AppClock.today(), AppClock.nowMillis())
                candidates = cands
                viewer = v
                raw = deck
                builtAtMs = System.currentTimeMillis()
                localGone.clear()
                DeckSignals.clear()
                _undo.value = null
                _ui.update {
                    it.copy(
                        loading = false, error = null, me = me, city = city, cities = cities,
                        languageOptions = DeckLogic.languageOptions(cands, me),
                        deckVersion = it.deckVersion + 1,
                    )
                }
                recompute()
            } catch (e: RepoException) {
                _ui.update { it.copy(loading = false, error = e.userMessage) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = "") }
            }
        }
    }

    /** Re-derive the visible deck and empty state from the frozen deck, filters and local swipes. */
    private fun recompute() {
        val s = _ui.value
        if (s.loading && raw.isEmpty()) return
        val today = AppClock.today()
        val filtered = DeckLogic.filter(raw, s.filters, today)
        val gone = localGone + DeckSignals.gone.value
        val visible = filtered.filter { it.item.role.id !in gone }
        val empty = DeckLogic.emptyKind(s.isGuest, s.likesLeft, raw.size, filtered.size, visible.size, hasHistory || sessionSwipes > 0)
        val emptyish = empty == DeckEmpty.NOTHING_IN_RANGE || empty == DeckEmpty.EXHAUSTED
        val blockers = if (emptyish) DeckLogic.blockers(viewer, candidates, today) else DeckBlockers()
        val gain = if (emptyish) {
            val deckIds = raw.map { it.item.role.id }.toSet() + gone
            DeckLogic.widenGain(viewer, candidates, deckIds, today, viewer?.profile?.travelRadiusKm?.let(DeckLogic::widenedRadius))
        } else 0
        _ui.update { it.copy(cards = visible, unfilteredCount = raw.size, filteredCount = filtered.size, empty = empty, blockers = blockers, widenGain = gain) }
    }

    fun retry() = rebuild(if (repo.session.value == null) Ws4Prefs.guestCityId else null)

    /**
     * LIVE only: the deck is a snapshot of the server, and other people post roles while the app is open. When
     * Discover comes back on screen ([fromPoll] = false) the deck is re-fetched if it's empty or older than
     * [STALE_MS]; while it stays on screen with an empty deck the screen polls ([fromPoll] = true) so new roles
     * land without a restart. A deck with cards is never rebuilt under the person's thumb. Demo keeps its frozen deck.
     */
    fun refreshIfStale(fromPoll: Boolean) {
        if (repo.mode != AppMode.LIVE || builtAtMs == 0L) return
        val s = _ui.value
        if (s.loading || s.needsProfile || s.error != null || s.busy || buildJob?.isActive == true || _applyFor.value != null) return
        val age = System.currentTimeMillis() - builtAtMs
        val due = if (s.cards.isEmpty()) age >= MIN_REFRESH_MS else !fromPoll && age >= STALE_MS
        if (due) retry()
    }

    // ───────────────────────── Filters (S08) ─────────────────────────

    fun setFilters(f: DeckFilters) { _ui.update { it.copy(filters = f) }; recompute() }

    /** How many cards a filter set would show (the sheet's "Show N roles" button). */
    fun previewCount(f: DeckFilters): Int {
        val gone = localGone + DeckSignals.gone.value
        return DeckLogic.filter(raw, f, AppClock.today()).count { it.item.role.id !in gone }
    }

    // ───────────────────────── Guest city ─────────────────────────

    fun pickGuestCity(cityId: String) {
        Ws4Prefs.guestCityId = cityId
        if (repo.session.value == null) {
            _ui.update { it.copy(city = it.cities.firstOrNull { c -> c.id == cityId } ?: it.city, density = null) }
            rebuild(cityId)
        }
    }

    // ───────────────────────── Swipes ─────────────────────────

    /** Pass (LEFT) or Save (UP) from the deck. Interested goes through [startApply]. */
    fun swiped(card: DeckCard, dir: SwipeDir) {
        val id = card.item.role.id
        localGone += id
        sessionSwipes++
        recompute()
        _undo.value = UndoOffer(id, dir, AppClock.nowMillis())
        viewModelScope.launch {
            repo.swipeRole(id, dir).onFailure { e ->
                localGone -= id
                _undo.value = null
                recompute()
                _events.tryEmit((e as? RepoException)?.userMessage ?: "")
            }
        }
    }

    /** Undo the last Pass or Save in the Repo first; the screen then brings the card back. */
    suspend fun undo(offer: UndoOffer): Boolean {
        val r = viewModelScope.async { repo.undoSwipe(offer.roleId) }.await()
        _undo.value = null
        return r.fold(
            onSuccess = { localGone -= offer.roleId; DeckSignals.unhide(offer.roleId); sessionSwipes = (sessionSwipes - 1).coerceAtLeast(0); recompute(); true },
            onFailure = { _events.tryEmit((it as? RepoException)?.userMessage ?: ""); false },
        )
    }

    fun expireUndo(offer: UndoOffer) { _undo.update { if (it == offer) null else it } }

    /** Right swipe: the card has flown out; open the Apply sheet for it. */
    fun startApply(card: DeckCard) {
        _undo.value = null
        _applyFor.value = card
    }

    /** The sheet was cancelled: the card comes back (the screen reverses the fling). */
    fun cancelApply() { _applyFor.value = null }

    /** Sent, filled meanwhile, or capped: the card stays gone. */
    fun applyFinished(roleId: String, keepGone: Boolean) {
        _applyFor.value = null
        if (keepGone) { localGone += roleId; sessionSwipes++ }
        recompute()
    }

    // ───────────────────────── Empty-state fixes ─────────────────────────

    /** "Widen radius": saves the profile radius × 1.5. Returns the new radius, or null on failure. */
    suspend fun widenRadius(): Int? = editProfile { p ->
        val r = DeckLogic.widenedRadius(p.travelRadiusKm) ?: return@editProfile null
        p.copy(travelRadiusKm = r)
    }?.travelRadiusKm

    /** "Include remote roles": turns Remote OK on in the profile. */
    suspend fun includeRemote(): Boolean = editProfile { p -> if (p.remoteOk) null else p.copy(remoteOk = true) } != null

    private suspend fun editProfile(change: (Profile) -> Profile?): Profile? {
        if (_ui.value.busy) return null
        _ui.update { it.copy(busy = true) }
        return try {
            viewModelScope.async {
                val me = repo.me().first() ?: return@async null
                val next = change(me) ?: return@async null
                repo.saveProfile(next, repo.myPrivateMinRate()).fold(
                    onSuccess = { it },
                    onFailure = { e -> _events.tryEmit((e as? RepoException)?.userMessage ?: ""); null },
                )
            }.await()
        } finally {
            _ui.update { it.copy(busy = false) }
        }
    }

    /** One-shot messages for the screen's toast (blank = generic error copy). */
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: kotlinx.coroutines.flow.SharedFlow<String> = _events

    companion object {
        private const val DAILY = 25
        /** LIVE: a deck older than this is re-fetched when Discover comes back on screen. */
        const val STALE_MS = 10 * 60_000L
        /** LIVE: an empty deck is re-fetched at most this often (on resume and by the screen's poll). */
        const val MIN_REFRESH_MS = 20_000L
        /** LIVE: how often the screen asks for a refresh while it stays on screen. */
        const val POLL_MS = 60_000L
    }
}
