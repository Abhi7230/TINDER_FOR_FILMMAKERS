package com.tff.app.data.demo

import android.content.Context
import android.util.Log
import com.tff.app.core.AppClock
import com.tff.app.data.DemoControls
import com.tff.app.data.Repo
import com.tff.app.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Offline demo mode (PRD §11). A thin Android wrapper: all logic lives in the JVM-tested DemoEngine /
 * DemoQueries / DemoSimulator. State is a MutableStateFlow<DemoState> (so every screen updates live),
 * written behind to filesDir/demo_state.json on every change, and the demo clock offset is mirrored
 * into AppClock.offsetMillis.
 */
class DemoRepo(context: Context) : Repo, DemoControls {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = DeltaStateStore(File(appContext.filesDir, STATE_FILE))
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Engine, read models and simulator, ready once the JSON is loaded (or the world is seeded) off the main thread. */
    private class Core(val engine: DemoEngine, val queries: DemoQueries, val simulator: DemoSimulator)
    private val ready = CompletableDeferred<Core>()
    private suspend fun core(): Core = ready.await()

    override val mode: AppMode = AppMode.DEMO
    override val features: Features = Features()
    // A tiny snapshot in SharedPreferences answers "who is signed in?" instantly at launch (Splash).
    private val _session = MutableStateFlow(readSessionSnapshot())
    override val session: StateFlow<Session?> get() = _session
    override val demo: DemoControls get() = this
    private val _demoDate = MutableStateFlow(LocalDate.now())
    private val _speed = MutableStateFlow(runCatching { SimSpeed.valueOf(prefs.getString(KEY_SPEED, null) ?: "") }.getOrDefault(SimSpeed.NORMAL))
    override val demoDate: StateFlow<LocalDate> get() = _demoDate
    override val speed: StateFlow<SimSpeed> get() = _speed

    init {
        AppClock.offsetMillis = prefs.getLong(KEY_OFFSET, 0L)
        _demoDate.value = AppClock.today()
        scope.launch(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val loaded = runCatching { store.load() }.onFailure { Log.w(TAG, "Demo state unreadable, reseeding", it) }.getOrNull()
            File(appContext.filesDir, LEGACY_STATE_FILE).delete()
            // a materialised-seed cache tried during FIXD is slower to decode than rebuilding: never keep one around
            appContext.filesDir.listFiles { f -> f.name.startsWith("demo_seed_v") }?.forEach { it.delete() }
            val tLoad = System.nanoTime()
            val initial = loaded ?: DemoSeed.build(LocalDate.now(), Random(42), ZoneId.systemDefault(), System.currentTimeMillis()).also { store.prime(it) }
            val tSeed = System.nanoTime()
            val engine = DemoEngine(initial, SystemDemoClock) { Log.w(TAG, it) }
            val simulator = DemoSimulator(engine, scope)
            simulator.attach()
            val c = Core(engine, DemoQueries(engine), simulator)
            // Time rules first (a saved world may be days old), so the state the first screen reads is final and the
            // warm-up below isn't thrown away by a tick a moment later.
            runCatching { engine.tick() }.onFailure { Log.w(TAG, "tick failed", it) }
            val tTick = System.nanoTime()
            warmUp(c)
            syncFrom(c, engine.state.value)
            val tWarm = System.nanoTime()
            ready.complete(c)
            Log.i(TAG, "demo ready in ${(System.nanoTime() - t0) / 1_000_000} ms (${if (loaded != null) "loaded" else "seeded"}): " +
                "load ${(tLoad - t0) / 1_000_000} [${store.lastLoadTimings}] · seed ${(tSeed - tLoad) / 1_000_000} [${DemoSeed.lastTimings}] · " +
                "tick ${(tTick - tSeed) / 1_000_000} · warm-up ${(tWarm - tTick) / 1_000_000}")
            // Mirror state into the session / clock / controls flows.
            launch(Dispatchers.Default) { engine.state.collect { syncFrom(c, it) } }
            // Write-behind: every change lands in the JSON file (coalesced while changes keep coming). A freshly loaded
            // world is already on disk, so its first emission isn't written again; a fresh seed is, once startup is over.
            launch {
                val onDisk = if (loaded != null) initial else null
                var first = true
                engine.state.collectLatest { st ->
                    if (st === onDisk) return@collectLatest
                    delay(if (first) FIRST_SAVE_DELAY_MS else WRITE_DELAY_MS)
                    first = false
                    runCatching { store.save(st) }.onFailure { Log.w(TAG, "Couldn't save demo state", it) }
                }
            }
            // Time rules (expiry, auto-wrap, T-48h, overdue nudges...) keep running in real time too.
            launch(Dispatchers.Default) {
                simulator.scan()
                delay(TICK_MS)
                while (isActive) {
                    val t = System.nanoTime()
                    runCatching { engine.tick(); simulator.scan() }.onFailure { Log.w(TAG, "tick failed", it) }
                    val ms = (System.nanoTime() - t) / 1_000_000; if (ms >= SLOW_MS) Log.i(TAG, "tick took $ms ms")
                    delay(TICK_MS)
                }
            }
        }
    }

    /**
     * Fill the index caches (owner summaries, reputations, booked dates) and load the code paths the first screens use,
     * on the exact state they will read, so Discover / Inbox open with content instead of skeletons. Errors are ignored.
     */
    private fun warmUp(c: Core) {
        val s = c.engine.state.value
        val uid = s.session?.userId
        runCatching {
            val ix = c.engine.index(s)
            val me = c.queries.me(s, uid)
            val cityId = me?.cityId ?: DemoCities.all.firstOrNull { it.isLive }?.id
            val candidates = c.queries.discoverCandidates(s, uid, if (me == null) cityId else null)
            cityId?.let { c.queries.density(s, it) }
            if (me != null) {
                ix.area(me.areaId)?.let { a ->
                    com.tff.app.domain.MatchEngine.buildDeck(
                        com.tff.app.domain.Viewer(me, c.queries.myPrivateMinRate(s, uid), a.lat, a.lng), candidates, ix.today, ix.now)
                }
                c.queries.inbox(s, uid)
                c.queries.myProjects(s, uid)
                c.queries.notifications(s, uid)
            } else com.tff.app.domain.MatchEngine.guestDeck(candidates, ix.today, ix.now)
        }.onFailure { Log.w(TAG, "warm-up failed", it) }
    }

    /** Suspends until the demo world is loaded and warmed up (Splash holds its mark until then). */
    suspend fun awaitReady() { ready.await() }

    /** True once the demo world is loaded (no wait needed). */
    val isReady: Boolean get() = ready.isCompleted

    private fun readSessionSnapshot(): Session? = prefs.getString(KEY_SESSION, null)?.let { raw ->
        runCatching { DemoJson.json.decodeFromString(Session.serializer(), raw) }.getOrNull()
    }

    private fun syncFrom(c: Core, s: DemoState) {
        AppClock.offsetMillis = s.offsetMillis
        if (_session.value != s.session) _session.value = s.session
        if (_speed.value != s.speed) _speed.value = s.speed
        val d = c.engine.today()
        if (_demoDate.value != d) _demoDate.value = d
        val sessJson = s.session?.let { DemoJson.json.encodeToString(Session.serializer(), it) }
        if (prefs.getString(KEY_SESSION, null) != sessJson || prefs.getLong(KEY_OFFSET, 0L) != s.offsetMillis || prefs.getString(KEY_SPEED, null) != s.speed.name) {
            prefs.edit().putString(KEY_SESSION, sessJson).putLong(KEY_OFFSET, s.offsetMillis).putString(KEY_SPEED, s.speed.name).apply()
        }
    }

    private fun uid(c: Core): String? = c.engine.state.value.session?.userId

    /** A Flow over the live state for the signed-in viewer. Never throws into the UI; falls back to [fallback]. */
    private fun <T> view(fallback: T, tag: String = "view", f: Core.(DemoState, String?) -> T): Flow<T> = flow {
        val c = core()
        emitAll(c.engine.state.map { s ->
            val t = System.nanoTime()
            runCatching { c.f(s, s.session?.userId) }.onFailure { Log.w(TAG, "view failed", it) }.getOrDefault(fallback)
                .also { val ms = (System.nanoTime() - t) / 1_000_000; if (ms >= SLOW_MS) Log.i(TAG, "$tag took $ms ms") }
        })
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** Runs [block] off the main thread against the loaded engine, then mirrors the new state into the flows. */
    private suspend fun <T> io(tag: String = "io", block: Core.(String?) -> T): T {
        val c = core()
        return withContext(Dispatchers.Default) {
            val t = System.nanoTime()
            c.block(uid(c)).also {
                syncFrom(c, c.engine.state.value)
                val ms = (System.nanoTime() - t) / 1_000_000; if (ms >= SLOW_MS) Log.i(TAG, "$tag took $ms ms")
            }
        }
    }

    // ── Auth & profile ──
    override suspend fun sendOtp(email: String): Result<Unit> = io { engine.sendOtp(email) }
    override suspend fun verifyOtp(email: String, code: String): Result<Session> = io { engine.verifyOtp(email, code) }
    override suspend fun startDemo(persona: DemoPersona?): Session = io { engine.startDemo(persona) }
    override suspend fun signOut() = io { engine.signOut() }
    override fun me(): Flow<Profile?> = view(null, "me") { s, u -> queries.me(s, u) }
    override suspend fun myPrivateMinRate(): Int? = io { u -> queries.myPrivateMinRate(engine.state.value, u) }
    override suspend fun saveProfile(p: Profile, privateMinRate: Int?): Result<Profile> = io { u -> engine.saveProfile(u, p, privateMinRate) }
    override suspend fun profile(id: String): ProfileCard? = io { u -> queries.profileCard(engine.state.value, u, id) }
    override suspend fun cities(): List<City> = DemoCities.all.sortedWith(compareBy<City>({ !it.isLive }, { it.isEarly }))
    override fun density(cityId: String): Flow<Density> =
        view(Density(cityId, DemoCities.city(cityId)?.name ?: "", false, false, 0, 0), "density") { s, _ -> queries.density(s, cityId) }

    // ── Gigs ──
    override suspend fun discoverCandidates(cityId: String?): List<RoleWithProject> = io("discoverCandidates") { u -> queries.discoverCandidates(engine.state.value, u, cityId) }
    override suspend fun roleWithProject(roleId: String): RoleWithProject? = io { u -> queries.roleWithProject(engine.state.value, u, roleId) }
    override suspend fun swipeRole(roleId: String, dir: SwipeDir): Result<Unit> = io { u -> engine.swipeRole(u, roleId, dir) }
    override suspend fun undoSwipe(roleId: String): Result<Unit> = io { u -> engine.undoSwipe(u, roleId) }
    override suspend fun apply(roleId: String, note: String?, workLinkId: String?, answer: String?, kit: Boolean): Result<Application> =
        io { u -> engine.apply(u, roleId, note, workLinkId, answer, kit) }
    override fun likesLeftToday(): Flow<Int> = view(DemoEngine.DAILY_LIKES) { s, u -> queries.likesLeftToday(s, u) }
    override fun saved(): Flow<List<RoleWithProject>> = view(emptyList()) { s, u -> queries.saved(s, u) }
    override fun myApplications(): Flow<List<Application>> = view(emptyList()) { s, u -> queries.myApplications(s, u) }

    // ── Projects ──
    override fun myProjects(): Flow<List<ProjectSummary>> = view(emptyList(), "myProjects") { s, u -> queries.myProjects(s, u) }
    override fun joinedProjects(): Flow<List<ProjectSummary>> = view(emptyList()) { s, u -> queries.joinedProjects(s, u) }
    override fun project(id: String): Flow<ProjectFull?> = view(null) { s, u -> queries.projectFull(s, u, id) }
    override suspend fun draft(projectId: String): ProjectDraft? = io { u -> queries.draft(engine.state.value, u, projectId) }
    override suspend fun saveDraft(d: ProjectDraft): Result<String> = io { u -> engine.saveDraft(u, d) }
    override suspend fun publish(projectId: String): Result<Unit> = io { u -> engine.publish(u, projectId) }
    override suspend fun reschedule(projectId: String, newDates: List<LocalDate>): Result<Unit> = io { u -> engine.reschedule(u, projectId, newDates) }
    override suspend fun cancelProject(projectId: String, reason: String): Result<Unit> = io { u -> engine.cancelProject(u, projectId, reason) }
    override suspend fun closeRole(roleId: String): Result<Unit> = io { u -> engine.closeRole(u, roleId) }

    // ── Crew up ──
    override fun applicants(roleId: String): Flow<List<ApplicantView>> = view(emptyList()) { s, u -> queries.applicants(s, u, roleId) }
    override suspend fun decide(applicationId: String, d: Decision, reason: String?): Result<Match?> = io { u -> engine.decide(u, applicationId, d, reason) }
    override suspend fun scout(roleId: String): List<ApplicantView> = io { u -> queries.scout(engine.state.value, u, roleId) }
    override suspend fun askCheck(roleId: String, profileId: String): String? = io { u -> engine.askCheck(u, roleId, profileId) }
    override suspend fun sendAsk(roleId: String, profileId: String, note: String?): Result<Unit> = io { u -> engine.sendAsk(u, roleId, profileId, note) }
    override fun asksLeftToday(projectId: String): Flow<Int> = view(DemoEngine.DAILY_ASKS) { s, _ -> queries.asksLeftToday(s, projectId) }
    override suspend fun respondAsk(applicationId: String, accept: Boolean, reason: AskDeclineReason?): Result<Match?> =
        io { u -> engine.respondAsk(u, applicationId, accept, reason) }
    override suspend fun offerSlotToBackup(roleId: String): Result<Unit> = io { u -> engine.offerSlotToBackup(u, roleId) }

    // ── Inbox / chat / booking ──
    override fun inbox(): Flow<Inbox> = view(Inbox(), "inbox") { s, u -> queries.inbox(s, u) }
    override fun matchView(matchId: String): Flow<MatchView?> = view(null) { s, u -> queries.matchView(s, u, matchId) }
    override fun messages(matchId: String): Flow<List<Message>> = view(emptyList()) { s, u -> queries.messages(s, u, matchId) }
    override suspend fun sendMessage(matchId: String, body: String): Result<Unit> = io { u -> engine.sendMessage(u, matchId, body) }
    override suspend fun markRead(matchId: String) = io { u -> engine.markRead(u, matchId) }
    override suspend fun sendAuditionRequest(matchId: String, req: AuditionRequest): Result<Unit> = io { u -> engine.sendAuditionRequest(u, matchId, req) }
    override suspend fun respondAudition(messageId: String, response: AuditionResponse, tapeUrl: String?): Result<Unit> =
        io { u -> engine.respondAudition(u, messageId, response, tapeUrl) }
    override fun bookingCards(matchId: String): Flow<List<BookingCard>> = view(emptyList()) { s, u -> queries.bookingCards(s, u, matchId) }
    override suspend fun draftBookingTerms(matchId: String): BookingTerms = io { u -> engine.draftBookingTerms(u, matchId) }
    override suspend fun sendBookingCard(matchId: String, terms: BookingTerms): Result<Unit> = io { u -> engine.sendBookingCard(u, matchId, terms) }
    override suspend fun respondBookingCard(cardId: String, accept: Boolean): Result<Unit> = io { u -> engine.respondBookingCard(u, cardId, accept) }
    override suspend fun withdraw(matchId: String, reason: WithdrawReason, note: String?): Result<String?> = io { u -> engine.withdraw(u, matchId, reason, note) }
    override suspend fun release(matchId: String, reason: ReleaseReason): Result<Unit> = io { u -> engine.release(u, matchId, reason) }
    override suspend fun shareContacts(matchId: String): Result<Unit> = io { u -> engine.shareContacts(u, matchId) }

    // ── Shoot & wrap ──
    override fun shootDays(projectId: String): Flow<List<ShootDayFull>> = view(emptyList()) { s, u -> queries.shootDays(s, u, projectId) }
    override suspend fun publishShootDay(d: ShootDayDraft): Result<Unit> = io { u -> engine.publishShootDay(u, d) }
    override suspend fun ackShootDay(shootDayId: String): Result<Unit> = io { u -> engine.ackShootDay(u, shootDayId) }
    override suspend fun nudge(shootDayId: String, profileId: String): Result<Unit> = io { u -> engine.nudge(u, shootDayId, profileId) }
    override suspend fun setAttendance(shootDayId: String, status: Attendance, profileId: String?): Result<Unit> =
        io { u -> engine.setAttendance(u, shootDayId, status, profileId) }
    override suspend fun markWrapped(projectId: String): Result<Unit> = io { u -> engine.markWrapped(u, projectId) }
    override fun wrapItems(matchId: String): Flow<List<WrapItem>> = view(emptyList()) { s, u -> queries.wrapItems(s, u, matchId) }
    override fun projectWrap(projectId: String): Flow<List<WrapGroup>> = view(emptyList()) { s, u -> queries.projectWrap(s, u, projectId) }
    override suspend fun markWrapItem(itemId: String, link: String?, note: String?): Result<Unit> = io { u -> engine.markWrapItem(u, itemId, link, note) }
    override suspend fun confirmWrapItem(itemId: String, received: Boolean): Result<Unit> = io { u -> engine.confirmWrapItem(u, itemId, received) }
    override fun feedbackState(matchId: String): Flow<FeedbackState> =
        view(FeedbackState(false, false, false, false)) { s, u -> queries.feedbackState(s, u, matchId) }
    override suspend fun submitFeedback(matchId: String, answers: FeedbackAnswers): Result<Unit> = io { u -> engine.submitFeedback(u, matchId, answers) }
    override suspend fun reputation(profileId: String): Reputation = io { queries.reputation(engine.state.value, profileId) }
    override fun keepList(): Flow<List<ProfileCard>> = view(emptyList()) { s, u -> queries.keepList(s, u) }

    // ── Safety & account ──
    override suspend fun report(kind: ReportTarget, targetId: String, reason: ReportReason, details: String?): Result<Unit> =
        io { u -> engine.report(u, kind, targetId, reason, details) }
    /** S21 report rows: report id → (who or what it's about, monogram initials or null). Demo only. */
    suspend fun reportTargets(reports: List<Report>): Map<String, Pair<String, String?>> =
        io { u -> reports.mapNotNull { r -> queries.reportTarget(engine.state.value, u, r)?.let { r.id to it } }.toMap() }

    override fun myReports(): Flow<List<Report>> = view(emptyList()) { s, u -> queries.myReports(s, u) }
    override suspend fun block(profileId: String): Result<Unit> = io { u -> engine.block(u, profileId) }
    override suspend fun unblock(profileId: String): Result<Unit> = io { u -> engine.unblock(u, profileId) }
    override fun blocked(): Flow<List<ProfileCard>> = view(emptyList()) { s, u -> queries.blocked(s, u) }
    override fun notifications(): Flow<List<AppNotification>> = view(emptyList(), "notifications") { s, u -> queries.notifications(s, u) }
    override suspend fun markNotificationRead(id: String) = io { u -> engine.markNotificationRead(u, id) }
    override suspend fun exportMyData(): String = io { u -> queries.exportMyData(engine.state.value, u) }
    override suspend fun deleteAccount(): Result<Unit> = io { u -> engine.deleteAccount(u) }
    override suspend fun log(event: String, props: Map<String, String>) {
        Log.d(TAG, "event $event $props")
        io { u -> engine.log(u, event, props) }
    }

    // ── Demo controls (S22) ──
    override fun setSpeed(s: SimSpeed) {
        _speed.value = s
        scope.launch { io { engine.setSpeed(s) } }
    }
    override suspend fun reset() = io {
        val t0 = System.nanoTime()
        simulator.cancelAll()
        engine.reset { store.cachedBase() }
        // the collections right after a reset are exactly the seed's, so they're the base for future deltas
        store.prime(engine.state.value)
        engine.tick()
        Log.i(TAG, "demo reset in ${(System.nanoTime() - t0) / 1_000_000} ms")
        Unit
    }
    override suspend fun advanceDays(days: Int) = io { engine.advanceDays(days.toDouble()); simulator.scan() }
    override suspend fun jumpPastNextShoot() = io { engine.jumpPastNextShoot(); simulator.scan() }
    override suspend fun jumpPastWrap() = io { engine.jumpPastWrap(); simulator.scan() }
    override suspend fun injectFlaggedExample() = io { engine.injectFlaggedExample().onFailure { Log.w(TAG, "inject: ${it.message}") }; Unit }
    override suspend fun simulateWithdrawal() = io { engine.simulateWithdrawal().onFailure { Log.w(TAG, "withdrawal: ${it.message}") }; Unit }
    override suspend fun switchPersona(p: DemoPersona?) { startDemo(p) }

    companion object {
        private const val TAG = "CalltimeDemo"
        const val STATE_FILE = "demo_state_v2.json"
        private const val LEGACY_STATE_FILE = "demo_state.json"
        private const val PREFS = "calltime_demo"
        private const val KEY_SESSION = "session"
        private const val KEY_OFFSET = "offset_millis"
        private const val KEY_SPEED = "speed"
        private const val WRITE_DELAY_MS = 250L
        private const val FIRST_SAVE_DELAY_MS = 2_000L
        private const val TICK_MS = 30_000L
        private const val SLOW_MS = 40L
    }
}
