package com.tff.app.data

import com.tff.app.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * THE single data contract (PRD §10.2, frozen). Every screen codes against this; DemoRepo (offline,
 * seeded + simulated) and SupabaseRepo (LIVE) implement it. Ranking/gating is NOT done here: the Repo
 * returns pre-gate candidates and the ViewModels run domain/MatchEngine + ApplicantRanker on device.
 *
 * Rules every implementation follows:
 *  - Flows are hot-ish: they re-emit whenever the underlying state changes (a swipe, a new message...).
 *  - Profiles of other people are only returned where a relationship exists (PRD §9.3); otherwise null.
 *  - ProfileCards are sanitised: contentWontDo emptied; HEADSHOT links only where allowed.
 *  - A private min day rate never leaves its owner (myPrivateMinRate is the only accessor).
 *  - Errors come back as Result.failure(RepoException(userMessage)) with a message fit to show the user.
 */
interface Repo {
    val mode: AppMode
    val features: Features
    val session: StateFlow<Session?>                      // null = guest
    /** Demo controls (Settings → Demo). Null in LIVE mode. */
    val demo: DemoControls?

    // ── Auth & profile ──
    suspend fun sendOtp(email: String): Result<Unit>
    suspend fun verifyOtp(email: String, code: String): Result<Session>
    /** Demo only. persona == null → fresh "Build my own profile" user with no profile (goes to onboarding). */
    suspend fun startDemo(persona: DemoPersona?): Session
    suspend fun signOut()
    fun me(): Flow<Profile?>
    suspend fun myPrivateMinRate(): Int?
    suspend fun saveProfile(p: Profile, privateMinRate: Int?): Result<Profile>
    /** Null when no relationship exists (PRD §9.3) — the UI shows "Profile not available". */
    suspend fun profile(id: String): ProfileCard?
    suspend fun cities(): List<City>                      // with areas; live cities first
    fun density(cityId: String): Flow<Density>

    // ── Gigs ──
    /** Pre-gate OPEN roles for the viewer's city plus remote roles; excludes own/swiped/blocked. Guests: cityId required. */
    suspend fun discoverCandidates(cityId: String? = null): List<RoleWithProject>
    suspend fun roleWithProject(roleId: String): RoleWithProject?
    suspend fun swipeRole(roleId: String, dir: SwipeDir): Result<Unit>
    /** Undo the last swipe (5 s Undo chip). Only LEFT/SAVE can be undone; RIGHT creates an application. */
    suspend fun undoSwipe(roleId: String): Result<Unit>
    suspend fun apply(roleId: String, note: String?, workLinkId: String?, answer: String?, kit: Boolean): Result<Application>
    fun likesLeftToday(): Flow<Int>
    fun saved(): Flow<List<RoleWithProject>>
    fun myApplications(): Flow<List<Application>>

    // ── Projects (owner) ──
    fun myProjects(): Flow<List<ProjectSummary>>
    fun joinedProjects(): Flow<List<ProjectSummary>>
    fun project(id: String): Flow<ProjectFull?>
    suspend fun draft(projectId: String): ProjectDraft?
    suspend fun saveDraft(d: ProjectDraft): Result<String>             // returns projectId
    suspend fun publish(projectId: String): Result<Unit>               // ListingRules must pass
    suspend fun reschedule(projectId: String, newDates: List<LocalDate>): Result<Unit>
    suspend fun cancelProject(projectId: String, reason: String): Result<Unit>
    suspend fun closeRole(roleId: String): Result<Unit>

    // ── Crew up ──
    fun applicants(roleId: String): Flow<List<ApplicantView>>          // ranked by ApplicantRanker
    suspend fun decide(applicationId: String, d: Decision, reason: String? = null): Result<Match?>
    suspend fun scout(roleId: String): List<ApplicantView>             // empty for cast roles
    /** Null if an Ask is allowed, else the reason to show ("Kabir only takes paid work right now"). */
    suspend fun askCheck(roleId: String, profileId: String): String?
    suspend fun sendAsk(roleId: String, profileId: String, note: String?): Result<Unit>
    fun asksLeftToday(projectId: String): Flow<Int>
    suspend fun respondAsk(applicationId: String, accept: Boolean, reason: AskDeclineReason?): Result<Match?>
    /** Withdrawal → backup: sends backup #1 an Ask with the same terms. */
    suspend fun offerSlotToBackup(roleId: String): Result<Unit>

    // ── Inbox / chat / booking ──
    fun inbox(): Flow<Inbox>
    fun matchView(matchId: String): Flow<MatchView?>
    fun messages(matchId: String): Flow<List<Message>>
    suspend fun sendMessage(matchId: String, body: String): Result<Unit>
    suspend fun markRead(matchId: String)
    suspend fun sendAuditionRequest(matchId: String, req: AuditionRequest): Result<Unit>   // cast roles only
    suspend fun respondAudition(messageId: String, response: AuditionResponse, tapeUrl: String?): Result<Unit>
    fun bookingCards(matchId: String): Flow<List<BookingCard>>          // all versions, oldest first
    /** Pre-filled terms for the next booking card (BookingTermsFactory, or the last version). */
    suspend fun draftBookingTerms(matchId: String): BookingTerms
    suspend fun sendBookingCard(matchId: String, terms: BookingTerms): Result<Unit>
    suspend fun respondBookingCard(cardId: String, accept: Boolean): Result<Unit>
    suspend fun withdraw(matchId: String, reason: WithdrawReason, note: String?): Result<String?>  // backup appId
    suspend fun release(matchId: String, reason: ReleaseReason): Result<Unit>
    suspend fun shareContacts(matchId: String): Result<Unit>

    // ── Shoot & wrap ──
    fun shootDays(projectId: String): Flow<List<ShootDayFull>>
    suspend fun publishShootDay(d: ShootDayDraft): Result<Unit>
    suspend fun ackShootDay(shootDayId: String): Result<Unit>
    suspend fun nudge(shootDayId: String, profileId: String): Result<Unit>
    suspend fun setAttendance(shootDayId: String, status: Attendance, profileId: String? = null): Result<Unit>
    suspend fun markWrapped(projectId: String): Result<Unit>
    fun wrapItems(matchId: String): Flow<List<WrapItem>>
    fun projectWrap(projectId: String): Flow<List<WrapGroup>>
    suspend fun markWrapItem(itemId: String, link: String?, note: String?): Result<Unit>  // by whoever owes it
    suspend fun confirmWrapItem(itemId: String, received: Boolean): Result<Unit>
    fun feedbackState(matchId: String): Flow<FeedbackState>
    suspend fun submitFeedback(matchId: String, answers: FeedbackAnswers): Result<Unit>
    suspend fun reputation(profileId: String): Reputation
    fun keepList(): Flow<List<ProfileCard>>

    // ── Safety & account ──
    suspend fun report(kind: ReportTarget, targetId: String, reason: ReportReason, details: String?): Result<Unit>
    fun myReports(): Flow<List<Report>>
    suspend fun block(profileId: String): Result<Unit>
    suspend fun unblock(profileId: String): Result<Unit>
    fun blocked(): Flow<List<ProfileCard>>
    fun notifications(): Flow<List<AppNotification>>
    suspend fun markNotificationRead(id: String)
    suspend fun exportMyData(): String                    // JSON
    suspend fun deleteAccount(): Result<Unit>
    suspend fun log(event: String, props: Map<String, String> = emptyMap())
}

/** Demo-only controls (S22). */
interface DemoControls {
    val demoDate: StateFlow<LocalDate>
    val speed: StateFlow<SimSpeed>
    fun setSpeed(s: SimSpeed)
    suspend fun reset()
    suspend fun advanceDays(days: Int)
    suspend fun jumpPastNextShoot()
    suspend fun jumpPastWrap()
    suspend fun injectFlaggedExample()
    suspend fun simulateWithdrawal()
    suspend fun switchPersona(p: DemoPersona?)
}

/** Repo failures carry a user-facing message ("Just filled. Here's a similar one."). */
class RepoException(val userMessage: String, val code: String? = null) : Exception(userMessage)

/** LIVE L1 features that aren't wired yet return this; their UI entry points are hidden via [Repo.features]. */
class NotLiveYet(feature: String) : Exception("$feature isn't available in this build yet.")
