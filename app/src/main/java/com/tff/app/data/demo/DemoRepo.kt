package com.tff.app.data.demo

import android.content.Context
import android.util.Log
import com.tff.app.core.AppClock
import com.tff.app.data.DemoControls
import com.tff.app.data.Repo
import com.tff.app.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val store = FileStateStore(File(appContext.filesDir, STATE_FILE))
    private val engine: DemoEngine
    private val queries: DemoQueries
    private val simulator: DemoSimulator

    override val mode: AppMode = AppMode.DEMO
    override val features: Features = Features()
    private val _session: MutableStateFlow<Session?>
    override val session: StateFlow<Session?> get() = _session
    override val demo: DemoControls get() = this
    private val _demoDate: MutableStateFlow<LocalDate>
    private val _speed: MutableStateFlow<SimSpeed>
    override val demoDate: StateFlow<LocalDate> get() = _demoDate
    override val speed: StateFlow<SimSpeed> get() = _speed

    init {
        val loaded = runCatching { store.load() }.onFailure { Log.w(TAG, "Demo state unreadable, reseeding", it) }.getOrNull()
        val initial = loaded ?: DemoSeed.build(LocalDate.now(), Random(42), ZoneId.systemDefault(), System.currentTimeMillis())
        engine = DemoEngine(initial, SystemDemoClock) { Log.w(TAG, it) }
        queries = DemoQueries(engine)
        simulator = DemoSimulator(engine, scope)
        simulator.attach()
        AppClock.offsetMillis = initial.offsetMillis
        _session = MutableStateFlow(initial.session)
        _demoDate = MutableStateFlow(engine.today())
        _speed = MutableStateFlow(initial.speed)

        // Mirror state into the session / clock / controls flows.
        scope.launch { engine.state.collect { syncFrom(it) } }
        // Write-behind: every change lands in the JSON file (coalesced while changes keep coming).
        scope.launch(Dispatchers.IO) {
            engine.state.collectLatest { s ->
                delay(WRITE_DELAY_MS)
                runCatching { store.save(s) }.onFailure { Log.w(TAG, "Couldn't save demo state", it) }
            }
        }
        // Time rules (expiry, auto-wrap, T-48h, overdue nudges...) keep running in real time too.
        scope.launch {
            while (isActive) {
                runCatching { engine.tick(); simulator.scan() }.onFailure { Log.w(TAG, "tick failed", it) }
                delay(TICK_MS)
            }
        }
    }

    private fun syncFrom(s: DemoState) {
        AppClock.offsetMillis = s.offsetMillis
        if (_session.value != s.session) _session.value = s.session
        if (_speed.value != s.speed) _speed.value = s.speed
        val d = engine.today()
        if (_demoDate.value != d) _demoDate.value = d
    }

    private fun uid(): String? = engine.state.value.session?.userId

    /** A Flow over the live state for the signed-in viewer. Never throws into the UI; falls back to [fallback]. */
    private fun <T> view(fallback: T, f: (DemoState, String?) -> T): Flow<T> = engine.state
        .map { s -> runCatching { f(s, s.session?.userId) }.onFailure { Log.w(TAG, "view failed", it) }.getOrDefault(fallback) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.Default) {
        block().also { syncFrom(engine.state.value) }
    }

    // ── Auth & profile ──
    override suspend fun sendOtp(email: String): Result<Unit> = io { engine.sendOtp(email) }
    override suspend fun verifyOtp(email: String, code: String): Result<Session> = io { engine.verifyOtp(email, code) }
    override suspend fun startDemo(persona: DemoPersona?): Session = io { engine.startDemo(persona) }
    override suspend fun signOut() = io { engine.signOut() }
    override fun me(): Flow<Profile?> = view(null) { s, u -> queries.me(s, u) }
    override suspend fun myPrivateMinRate(): Int? = queries.myPrivateMinRate(engine.state.value, uid())
    override suspend fun saveProfile(p: Profile, privateMinRate: Int?): Result<Profile> = io { engine.saveProfile(uid(), p, privateMinRate) }
    override suspend fun profile(id: String): ProfileCard? = io { queries.profileCard(engine.state.value, uid(), id) }
    override suspend fun cities(): List<City> = queries.cities()
    override fun density(cityId: String): Flow<Density> =
        view(Density(cityId, DemoCities.city(cityId)?.name ?: "", false, false, 0, 0)) { s, _ -> queries.density(s, cityId) }

    // ── Gigs ──
    override suspend fun discoverCandidates(cityId: String?): List<RoleWithProject> = io { queries.discoverCandidates(engine.state.value, uid(), cityId) }
    override suspend fun roleWithProject(roleId: String): RoleWithProject? = io { queries.roleWithProject(engine.state.value, uid(), roleId) }
    override suspend fun swipeRole(roleId: String, dir: SwipeDir): Result<Unit> = io { engine.swipeRole(uid(), roleId, dir) }
    override suspend fun undoSwipe(roleId: String): Result<Unit> = io { engine.undoSwipe(uid(), roleId) }
    override suspend fun apply(roleId: String, note: String?, workLinkId: String?, answer: String?, kit: Boolean): Result<Application> =
        io { engine.apply(uid(), roleId, note, workLinkId, answer, kit) }
    override fun likesLeftToday(): Flow<Int> = view(DemoEngine.DAILY_LIKES) { s, u -> queries.likesLeftToday(s, u) }
    override fun saved(): Flow<List<RoleWithProject>> = view(emptyList()) { s, u -> queries.saved(s, u) }
    override fun myApplications(): Flow<List<Application>> = view(emptyList()) { s, u -> queries.myApplications(s, u) }

    // ── Projects ──
    override fun myProjects(): Flow<List<ProjectSummary>> = view(emptyList()) { s, u -> queries.myProjects(s, u) }
    override fun joinedProjects(): Flow<List<ProjectSummary>> = view(emptyList()) { s, u -> queries.joinedProjects(s, u) }
    override fun project(id: String): Flow<ProjectFull?> = view(null) { s, u -> queries.projectFull(s, u, id) }
    override suspend fun draft(projectId: String): ProjectDraft? = queries.draft(engine.state.value, uid(), projectId)
    override suspend fun saveDraft(d: ProjectDraft): Result<String> = io { engine.saveDraft(uid(), d) }
    override suspend fun publish(projectId: String): Result<Unit> = io { engine.publish(uid(), projectId) }
    override suspend fun reschedule(projectId: String, newDates: List<LocalDate>): Result<Unit> = io { engine.reschedule(uid(), projectId, newDates) }
    override suspend fun cancelProject(projectId: String, reason: String): Result<Unit> = io { engine.cancelProject(uid(), projectId, reason) }
    override suspend fun closeRole(roleId: String): Result<Unit> = io { engine.closeRole(uid(), roleId) }

    // ── Crew up ──
    override fun applicants(roleId: String): Flow<List<ApplicantView>> = view(emptyList()) { s, u -> queries.applicants(s, u, roleId) }
    override suspend fun decide(applicationId: String, d: Decision, reason: String?): Result<Match?> = io { engine.decide(uid(), applicationId, d, reason) }
    override suspend fun scout(roleId: String): List<ApplicantView> = io { queries.scout(engine.state.value, uid(), roleId) }
    override suspend fun askCheck(roleId: String, profileId: String): String? = io { engine.askCheck(uid(), roleId, profileId) }
    override suspend fun sendAsk(roleId: String, profileId: String, note: String?): Result<Unit> = io { engine.sendAsk(uid(), roleId, profileId, note) }
    override fun asksLeftToday(projectId: String): Flow<Int> = view(DemoEngine.DAILY_ASKS) { s, _ -> queries.asksLeftToday(s, projectId) }
    override suspend fun respondAsk(applicationId: String, accept: Boolean, reason: AskDeclineReason?): Result<Match?> =
        io { engine.respondAsk(uid(), applicationId, accept, reason) }
    override suspend fun offerSlotToBackup(roleId: String): Result<Unit> = io { engine.offerSlotToBackup(uid(), roleId) }

    // ── Inbox / chat / booking ──
    override fun inbox(): Flow<Inbox> = view(Inbox()) { s, u -> queries.inbox(s, u) }
    override fun matchView(matchId: String): Flow<MatchView?> = view(null) { s, u -> queries.matchView(s, u, matchId) }
    override fun messages(matchId: String): Flow<List<Message>> = view(emptyList()) { s, u -> queries.messages(s, u, matchId) }
    override suspend fun sendMessage(matchId: String, body: String): Result<Unit> = io { engine.sendMessage(uid(), matchId, body) }
    override suspend fun markRead(matchId: String) = io { engine.markRead(uid(), matchId) }
    override suspend fun sendAuditionRequest(matchId: String, req: AuditionRequest): Result<Unit> = io { engine.sendAuditionRequest(uid(), matchId, req) }
    override suspend fun respondAudition(messageId: String, response: AuditionResponse, tapeUrl: String?): Result<Unit> =
        io { engine.respondAudition(uid(), messageId, response, tapeUrl) }
    override fun bookingCards(matchId: String): Flow<List<BookingCard>> = view(emptyList()) { s, u -> queries.bookingCards(s, u, matchId) }
    override suspend fun draftBookingTerms(matchId: String): BookingTerms = io { engine.draftBookingTerms(uid(), matchId) }
    override suspend fun sendBookingCard(matchId: String, terms: BookingTerms): Result<Unit> = io { engine.sendBookingCard(uid(), matchId, terms) }
    override suspend fun respondBookingCard(cardId: String, accept: Boolean): Result<Unit> = io { engine.respondBookingCard(uid(), cardId, accept) }
    override suspend fun withdraw(matchId: String, reason: WithdrawReason, note: String?): Result<String?> = io { engine.withdraw(uid(), matchId, reason, note) }
    override suspend fun release(matchId: String, reason: ReleaseReason): Result<Unit> = io { engine.release(uid(), matchId, reason) }
    override suspend fun shareContacts(matchId: String): Result<Unit> = io { engine.shareContacts(uid(), matchId) }

    // ── Shoot & wrap ──
    override fun shootDays(projectId: String): Flow<List<ShootDayFull>> = view(emptyList()) { s, u -> queries.shootDays(s, u, projectId) }
    override suspend fun publishShootDay(d: ShootDayDraft): Result<Unit> = io { engine.publishShootDay(uid(), d) }
    override suspend fun ackShootDay(shootDayId: String): Result<Unit> = io { engine.ackShootDay(uid(), shootDayId) }
    override suspend fun nudge(shootDayId: String, profileId: String): Result<Unit> = io { engine.nudge(uid(), shootDayId, profileId) }
    override suspend fun setAttendance(shootDayId: String, status: Attendance, profileId: String?): Result<Unit> =
        io { engine.setAttendance(uid(), shootDayId, status, profileId) }
    override suspend fun markWrapped(projectId: String): Result<Unit> = io { engine.markWrapped(uid(), projectId) }
    override fun wrapItems(matchId: String): Flow<List<WrapItem>> = view(emptyList()) { s, u -> queries.wrapItems(s, u, matchId) }
    override fun projectWrap(projectId: String): Flow<List<WrapGroup>> = view(emptyList()) { s, u -> queries.projectWrap(s, u, projectId) }
    override suspend fun markWrapItem(itemId: String, link: String?, note: String?): Result<Unit> = io { engine.markWrapItem(uid(), itemId, link, note) }
    override suspend fun confirmWrapItem(itemId: String, received: Boolean): Result<Unit> = io { engine.confirmWrapItem(uid(), itemId, received) }
    override fun feedbackState(matchId: String): Flow<FeedbackState> =
        view(FeedbackState(false, false, false, false)) { s, u -> queries.feedbackState(s, u, matchId) }
    override suspend fun submitFeedback(matchId: String, answers: FeedbackAnswers): Result<Unit> = io { engine.submitFeedback(uid(), matchId, answers) }
    override suspend fun reputation(profileId: String): Reputation = io { queries.reputation(engine.state.value, profileId) }
    override fun keepList(): Flow<List<ProfileCard>> = view(emptyList()) { s, u -> queries.keepList(s, u) }

    // ── Safety & account ──
    override suspend fun report(kind: ReportTarget, targetId: String, reason: ReportReason, details: String?): Result<Unit> =
        io { engine.report(uid(), kind, targetId, reason, details) }
    override fun myReports(): Flow<List<Report>> = view(emptyList()) { s, u -> queries.myReports(s, u) }
    override suspend fun block(profileId: String): Result<Unit> = io { engine.block(uid(), profileId) }
    override suspend fun unblock(profileId: String): Result<Unit> = io { engine.unblock(uid(), profileId) }
    override fun blocked(): Flow<List<ProfileCard>> = view(emptyList()) { s, u -> queries.blocked(s, u) }
    override fun notifications(): Flow<List<AppNotification>> = view(emptyList()) { s, u -> queries.notifications(s, u) }
    override suspend fun markNotificationRead(id: String) = io { engine.markNotificationRead(uid(), id) }
    override suspend fun exportMyData(): String = io { queries.exportMyData(engine.state.value, uid()) }
    override suspend fun deleteAccount(): Result<Unit> = io { engine.deleteAccount(uid()) }
    override suspend fun log(event: String, props: Map<String, String>) {
        Log.d(TAG, "event $event $props")
        io { engine.log(uid(), event, props) }
    }

    // ── Demo controls (S22) ──
    override fun setSpeed(s: SimSpeed) { engine.setSpeed(s); syncFrom(engine.state.value) }
    override suspend fun reset() = io {
        simulator.cancelAll()
        engine.reset()
        engine.tick()
    }
    override suspend fun advanceDays(days: Int) = io { engine.advanceDays(days.toDouble()); simulator.scan() }
    override suspend fun jumpPastNextShoot() = io { engine.jumpPastNextShoot(); simulator.scan() }
    override suspend fun jumpPastWrap() = io { engine.jumpPastWrap(); simulator.scan() }
    override suspend fun injectFlaggedExample() = io { engine.injectFlaggedExample().onFailure { Log.w(TAG, "inject: ${it.message}") }; Unit }
    override suspend fun simulateWithdrawal() = io { engine.simulateWithdrawal().onFailure { Log.w(TAG, "withdrawal: ${it.message}") }; Unit }
    override suspend fun switchPersona(p: DemoPersona?) { startDemo(p) }

    companion object {
        private const val TAG = "CalltimeDemo"
        const val STATE_FILE = "demo_state.json"
        private const val WRITE_DELAY_MS = 250L
        private const val TICK_MS = 30_000L
    }
}
