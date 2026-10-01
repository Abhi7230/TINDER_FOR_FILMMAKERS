@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class)

package com.tff.app.model

import com.tff.app.core.LocalDateSerializer
import com.tff.app.core.LocalTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalTime

// Frozen contract (PRD §6, §9). Timestamps are epoch millis (Long). Dates are java.time.LocalDate.
// Add fields only with a default value, so demo JSON from older builds still loads.

// ───────────────────────── Reference ─────────────────────────

@Serializable
data class Area(val id: String, val cityId: String, val name: String, val lat: Double, val lng: Double)

@Serializable
data class City(
    val id: String,
    val name: String,
    val country: String,            // IN, US, GB
    val currency: String,           // INR, USD, GBP
    val lat: Double,
    val lng: Double,
    val paidFloorDay: Int,          // R04: paid amount/day below this is relabelled Stipend
    val emergencyNumber: String,    // 112 / 911 / 999
    val isLive: Boolean = false,    // launched city
    val isEarly: Boolean = false,   // listed, not launched (honest "early city" header)
    val areas: List<Area> = emptyList(),
)

/** Honest density header (F5): "Mumbai · 214 people · 31 open projects · 9 sound mixers". */
@Serializable
data class Density(
    val cityId: String,
    val cityName: String,
    val isLive: Boolean,
    val isEarly: Boolean,
    val people: Int,
    val openProjects: Int,
    val perRole: Map<RoleCode, Int> = emptyMap(),
)

// ───────────────────────── People ─────────────────────────

@Serializable
data class SecondaryRole(val code: RoleCode, val proficiency: Int = 2)   // 1..3

@Serializable
data class WorkLink(
    val id: String,
    val url: String,
    val kind: LinkKind,
    val roleCode: RoleCode? = null,
    val title: String? = null,
    val year: Int? = null,
)

/**
 * A person's profile. `min_day_rate` is NOT here: it is private (profile_private) and only ever read by
 * the owner's own device (Repo.myPrivateMinRate) or server-side gating. `contentWontDo` is gate-only and
 * is blanked by the Repo whenever the profile is shown to someone else.
 */
@Serializable
data class Profile(
    val id: String,
    val displayName: String,                      // full name; UI shows shortName ("Meera K.")
    val pronouns: String? = null,
    val primaryRole: RoleCode,
    val secondaryRoles: List<SecondaryRole> = emptyList(),   // ≤4
    val stepUpRoles: List<RoleCode> = emptyList(),           // ≤2
    val level: Level,
    val cityId: String,
    val areaId: String,
    val travelRadiusKm: Int = 15,
    val travelIfCovered: Boolean = false,
    val remoteOk: Boolean = false,
    val languages: List<String> = listOf("en"),              // ISO 639-1
    val availabilityPattern: AvailabilityPattern = AvailabilityPattern.ANY_DAY,
    val busyDates: Set<LocalDate> = emptySet(),
    val compMode: CompMode = CompMode.PAID_AND_SELECTIVE,
    val kitFeeDay: Int? = null,
    val unpaidMustHaves: List<Perk> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val contentWontDo: List<ContentFlag> = emptyList(),
    val workLinks: List<WorkLink> = emptyList(),
    val gear: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val tasteFilms: List<String> = emptyList(),              // ≤5
    val genresPref: List<String> = emptyList(),
    val formatsPref: List<Format> = emptyList(),
    val bio: String? = null,                                 // ≤280
    val affiliations: List<String> = emptyList(),
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val idVerified: Boolean = false,
    val adultConfirmed: Boolean = false,
    val createdAt: Long = 0,
    val lastActiveAt: Long = 0,
) {
    /** "Meera K." */
    val shortName: String get() {
        val parts = displayName.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) "${parts.first()} ${parts.last().first()}." else displayName.trim()
    }
    /** "MK" monogram (no profile photos in the MVP). */
    val initials: String get() = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }
}

/** A ratio fact shown publicly only when den ≥ 3 (F14). */
@Serializable
data class Ratio(val num: Int, val den: Int) {
    val value: Double get() = if (den == 0) 0.0 else num.toDouble() / den
    override fun toString(): String = "$num/$den"
}

/** Public, fact-only track record (F14). Ratio fields are null when fewer than 3 data points. */
@Serializable
data class Reputation(
    val profileId: String,
    val shootsCompleted: Int = 0,
    // as collaborator
    val showedUp: Ratio? = null,
    val deliveredOnTime: Ratio? = null,
    // as owner
    val paidOnTime: Ratio? = null,
    val footageDelivered: Ratio? = null,
    val keptBookings: Ratio? = null,
    val closesLoop: Ratio? = null,           // applications given a status within 14 days
    val replyHoursMedian: Int? = null,
    val confirmedCredits: Int = 0,
) {
    /** New on Calltime = no aggregate has n ≥ 3 yet. */
    val isNew: Boolean get() = listOf(showedUp, deliveredOnTime, paidOnTime, footageDelivered, keptBookings).all { it == null }
    /** Mean of the owner's non-null delivery ratios (PRD §7.2 cred). */
    val ownerDeliverableRate: Double? get() = listOfNotNull(paidOnTime, footageDelivered, keptBookings).takeIf { it.isNotEmpty() }?.map { it.value }?.average()
}

@Serializable
data class ConfirmedCredit(val projectTitle: String, val roleCode: RoleCode, val creditTitle: String, val year: Int)

/**
 * What another person sees of a profile. The Repo sanitises [profile]: contentWontDo emptied,
 * HEADSHOT links removed unless the viewer may see them (cast role the person applied to / counterpart).
 */
@Serializable
data class ProfileCard(
    val profile: Profile,
    val reputation: Reputation,
    val confirmedCredits: List<ConfirmedCredit> = emptyList(),
    val distanceKm: Double? = null,
    val areaName: String? = null,
    val cityName: String? = null,
)

/** Compact owner info used on Role Cards and Role detail ("Who's posting"). */
@Serializable
data class OwnerSummary(
    val id: String,
    val displayName: String,
    val level: Level,
    val idVerified: Boolean,
    val reputation: Reputation,
    val lastActiveAt: Long,
    val closureRate14d: Double? = null,      // m_resp input; null when n < 5
) {
    val shortName: String get() {
        val parts = displayName.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) "${parts.first()} ${parts.last().first()}." else displayName.trim()
    }
}

// ───────────────────────── Projects ─────────────────────────

@Serializable
data class Project(
    val id: String,
    val ownerId: String,
    val payerId: String,
    val title: String,
    val logline: String,
    val format: Format = Format.SHORT,
    val runtimeMin: Int = 10,
    val pageCount: Int? = null,
    val genres: List<String> = emptyList(),
    val languages: List<String> = listOf("en"),
    val stage: Stage = Stage.PRE,
    val scriptStatus: ScriptStatus = ScriptStatus.DRAFT,
    val scriptReadable: Boolean = false,
    val lookbookUrl: String? = null,
    val ambition: Ambition = Ambition.PORTFOLIO,
    val honestZeroBudget: Boolean = false,
    val cityId: String,
    val areaId: String,
    val shootLocationType: LocationType? = null,
    val exactAddress: String? = null,           // booked people + owner only
    val storySetting: String? = null,
    /** Exact shoot dates (authoritative). Empty on post-only projects. shootStart/End/Days are derived. */
    val shootDates: List<LocalDate> = emptyList(),
    val pictureLockDate: LocalDate? = null,
    val dateCertainty: DateCertainty? = null,
    val hoursCap: Int? = null,
    val meals: Meals? = null,
    val transport: Transport? = null,
    val travelAllowancePerDay: Int? = null,
    val lodging: Boolean? = null,
    val contentFlags: Set<ContentFlag> = emptySet(),
    val contentNoneConfirmed: Boolean = false,  // "None of these" explicitly ticked (R07)
    val intimacyCoordinator: Boolean? = null,
    val stuntOrArmourer: Boolean? = null,
    val guardianPresent: Boolean? = null,
    val safetyMeasures: String? = null,
    val auditionMode: AuditionMode? = null,
    val bringFriendWelcome: Boolean = false,
    val insurance: YesNoNa? = null,
    val permits: YesNoNa? = null,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val rescheduleCount: Int = 0,
    val createdAt: Long = 0,
    val publishedAt: Long? = null,
    val updatedAt: Long = 0,
) {
    val shootStart: LocalDate? get() = shootDates.minOrNull()
    val shootEnd: LocalDate? get() = shootDates.maxOrNull()
    val shootDays: Int get() = shootDates.size
}

@Serializable
data class ProjectRole(
    val id: String,
    val projectId: String,
    val roleCode: RoleCode,
    val count: Int = 1,
    val creditTitle: String,
    val scopeIncludes: List<String> = emptyList(),
    val scopeExcludes: List<String> = emptyList(),
    val stepUpFriendly: Boolean = false,
    val levelWanted: Level? = null,
    val compType: CompType,
    val amountMin: Int? = null,
    val amountMax: Int? = null,
    val currency: String,
    val kitFeeDay: Int? = null,
    val paymentTerms: PaymentTerms? = null,
    val perks: Set<Perk> = emptySet(),
    val footageDueWeeks: Int? = null,
    val finalFilmDueWeeks: Int? = null,
    val gearPolicy: GearPolicy? = null,
    val remoteOk: Boolean = false,
    val postDeadline: LocalDate? = null,      // set => post role (delivery loop) when remote & POST/MUSIC/WRITING
    val roleDetails: Map<String, String> = emptyMap(),
    val screeningQuestion: String? = null,
    val applicationCap: Int = 15,
    val applicationsCount: Int = 0,
    val filledCount: Int = 0,
    val status: RoleStatus = RoleStatus.OPEN,
) {
    /** Post role = remote and in POST / MUSIC / WRITING with a delivery date (PRD §3.4). */
    val isPostRole: Boolean get() = remoteOk && postDeadline != null && Taxonomy.dept(roleCode) in Taxonomy.POST_DEPTS
    val isCast: Boolean get() = Taxonomy.isCast(roleCode)
    val isPaid: Boolean get() = compType == CompType.PAID_DAY || compType == CompType.PAID_FLAT
}

/** Team attached to a project: the owner plus BOOKED people (initials monograms on the card). */
@Serializable
data class TeamMember(val profileId: String, val shortName: String, val initials: String, val roleCode: RoleCode, val isOwner: Boolean = false)

/** One Gigs-deck candidate: a role with everything the card face and MatchEngine need. */
@Serializable
data class RoleWithProject(
    val role: ProjectRole,
    val project: Project,
    val owner: OwnerSummary,
    val cityName: String,
    val areaName: String,
    val areaLat: Double,
    val areaLng: Double,
    val team: List<TeamMember> = emptyList(),
    val paidRoles: Int = 0,          // "X of Y roles paid"
    val totalRoles: Int = 1,
)

/** Wizard working copy. */
@Serializable
data class ProjectDraft(val project: Project, val roles: List<ProjectRole>)

// ───────────────────────── Matching ─────────────────────────

@Serializable
data class Application(
    val id: String,
    val roleId: String,
    val applicantId: String,
    val origin: ApplicationOrigin = ApplicationOrigin.APPLIED,
    val note: String? = null,                  // ≤140
    val workLinkId: String? = null,
    val screeningAnswer: String? = null,       // ≤300
    val kitOffered: Boolean = false,
    val status: ApplicationStatus = ApplicationStatus.PENDING,
    val backupRank: Int? = null,
    val declineReason: String? = null,
    val createdAt: Long = 0,
    val decidedAt: Long? = null,
)

/** Owner's view of one person for one role (Crew up: Applicants or Scout). Never carries min_day_rate. */
@Serializable
data class ApplicantView(
    val application: Application?,            // null for Scout candidates
    val card: ProfileCard,
    val score: Double,
    val reasons: List<String>,                 // top-3 reason chips
    val rateFit: Double,                       // 0..1, computed server-side / by DemoRepo
    val availDays: Int,
    val totalDays: Int,
    val headshotUrl: String? = null,           // cast roles the person applied to only
)

@Serializable
data class Match(
    val id: String,
    val applicationId: String,
    val projectId: String,
    val roleId: String,
    val ownerId: String,
    val collaboratorId: String,
    val state: MatchState = MatchState.TALKING,
    val isPostRole: Boolean = false,
    val changePending: Boolean = false,
    val contactsSharedByOwner: Boolean = false,
    val contactsSharedByCollab: Boolean = false,
    val withdrawReason: String? = null,
    val noticeHours: Int? = null,
    val releasedByOwner: Boolean = false,
    val createdAt: Long = 0,
    val bookedAt: Long? = null,
    val endedAt: Long? = null,
)

/** Everything a chat / booking screen needs about one match, from the viewer's side. */
@Serializable
data class MatchView(
    val match: Match,
    val item: RoleWithProject,
    val counterpart: ProfileCard,
    val isOwner: Boolean,
    val currentCard: BookingCard? = null,      // latest version (any status)
    val acceptedCard: BookingCard? = null,     // the terms currently in force
    val nextShootDay: ShootDay? = null,
    val myCallTime: LocalTime? = null,
)

// ───────────────────────── Messaging & booking ─────────────────────────

@Serializable
data class AuditionRequest(
    val mode: AuditionMode,
    val sidesUrl: String? = null,              // SELF_TAPE
    val dueDate: LocalDate? = null,            // SELF_TAPE
    val date: LocalDate? = null,               // VIDEO_CALL / STUDIO
    val time: LocalTime? = null,               // in-person: 08:00–21:00 only
    val meetingLink: String? = null,           // VIDEO_CALL only
    val venue: String? = null,                 // STUDIO_OR_PUBLIC_WITH_OTHERS
    val address: String? = null,
    val alsoPresent: List<String> = emptyList(),   // ≥1 role for in-person
    val note: String? = null,
)

@Serializable
data class AuditionPayload(
    val request: AuditionRequest,
    val response: AuditionResponse? = null,
    val tapeUrl: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val matchId: String,
    val senderId: String?,                     // null = system
    val kind: MessageKind = MessageKind.TEXT,
    val body: String,
    val audition: AuditionPayload? = null,     // kind == AUDITION
    val bookingCardId: String? = null,         // kind == BOOKING
    val flags: Set<MessageFlag> = emptySet(),
    val safetyExample: Boolean = false,        // seeded scam example (DemoSeedTest allows contact info here)
    val createdAt: Long = 0,
)

@Serializable
data class Deliverable(val kind: WrapKind, val dueDate: LocalDate?, val owedBy: OwedBy)

/** Booking terms (PRD §6.4), rendered in plain language on the booking card. */
@Serializable
data class BookingTerms(
    val roleCode: RoleCode,
    val creditTitle: String,
    val projectTitle: String,
    val isPostRole: Boolean = false,
    val dates: List<LocalDate> = emptyList(),
    val deliveryDate: LocalDate? = null,
    val pictureLockDate: LocalDate? = null,
    val revisionRounds: Int? = null,
    val callWindow: String? = null,            // "~7:00–17:00"
    val compType: CompType,
    val amount: Int? = null,
    val currency: String,
    val kitFeeDay: Int? = null,
    val paymentTerms: PaymentTerms? = null,
    val payByDate: LocalDate? = null,
    val perks: Set<Perk> = emptySet(),
    val meals: Meals? = null,
    val transport: Transport? = null,
    val travelAllowancePerDay: Int? = null,
    val footageDueWeeks: Int? = null,
    val finalFilmDueWeeks: Int? = null,
    val deliverables: List<Deliverable> = emptyList(),
    val footageDefinition: String = "Approved clips of your scenes, graded, for your reel. Not raw footage.",
    val rightsNote: String = "Production owns the film. You may use approved clips in your reel.",
    val notes: String? = null,                 // ≤280
    val payerId: String,
    val payerName: String,
    val noticePledge: String,
)

@Serializable
data class BookingCard(
    val id: String,
    val matchId: String,
    val version: Int,
    val terms: BookingTerms,
    val status: BookingStatus = BookingStatus.SENT,
    val sentBy: String,
    val sentAt: Long = 0,
    val respondedAt: Long? = null,
    val changedFields: Set<String> = emptySet(),   // vs the previous version (BookingDiff keys)
)

// ───────────────────────── Shoot & wrap ─────────────────────────

@Serializable
data class ShootDay(
    val id: String,
    val projectId: String,
    val day: LocalDate,
    val generalCall: LocalTime,
    val wrapBy: LocalTime,
    val address: String,
    val mapsUrl: String,
    val parkingNote: String? = null,
    val hospitalNote: String? = null,
    val mealsPlan: String? = null,
    val notes: String? = null,                 // ≤500
    val version: Int = 1,
    val publishedAt: Long? = null,
    val cancelledAt: Long? = null,
)

@Serializable
data class CallTime(
    val shootDayId: String,
    val profileId: String,
    val callTime: LocalTime,
    val ackAt: Long? = null,
    val attendance: Attendance? = null,
)

@Serializable
data class CallTimeView(val call: CallTime, val shortName: String, val roleCode: RoleCode)

@Serializable
data class ShootDayFull(
    val day: ShootDay,
    val dayIndex: Int,                         // 1-based "Day 1 of 2"
    val dayCount: Int,
    val calls: List<CallTimeView>,             // owner sees all; collaborators see role + first name only
    val myCall: CallTime? = null,
)

@Serializable
data class ShootDayDraft(
    val projectId: String,
    val shootDayId: String? = null,            // null = new
    val day: LocalDate,
    val generalCall: LocalTime,
    val wrapBy: LocalTime,
    val address: String,
    val parkingNote: String? = null,
    val hospitalNote: String? = null,
    val mealsPlan: String? = null,
    val notes: String? = null,
    val perPersonCall: Map<String, LocalTime> = emptyMap(),   // profileId → call time (default: general call)
)

@Serializable
data class WrapItem(
    val id: String,
    val matchId: String,
    val kind: WrapKind,
    val dueDate: LocalDate?,
    val owedBy: OwedBy,
    val status: WrapStatus = WrapStatus.OPEN,
    val link: String? = null,
    val note: String? = null,
    val markedAt: Long? = null,
    val confirmedAt: Long? = null,
    val autoConfirmed: Boolean = false,
)

/** Owner's Wrap tab: one person's checklist. */
@Serializable
data class WrapGroup(val match: Match, val person: ProfileCard, val roleCode: RoleCode, val items: List<WrapItem>)

@Serializable
data class FeedbackAnswers(
    // collaborator → production
    val paid: PaidAnswer? = null,
    val delivered: DeliveredAnswer? = null,
    val feltSafe: Boolean? = null,
    val kept: KeptAnswer? = null,
    // owner → collaborator (on-set)
    val showedUp: ShowedUpAnswer? = null,
    val prepared: TriAnswer? = null,
    // owner → collaborator (post roles)
    val deliveredOnTime: OnTimeAnswer? = null,
    val revisions: TriAnswer? = null,
    // both
    val workAgain: Boolean? = null,
    val privateNote: String? = null,
)

@Serializable
data class Feedback(
    val id: String,
    val matchId: String,
    val fromId: String,
    val toId: String,
    val answers: FeedbackAnswers,
    val submittedAt: Long,
    val revealedAt: Long? = null,
)

/** Viewer's feedback status for a match (double-blind: the other side's raw answers are never exposed). */
@Serializable
data class FeedbackState(val unlocked: Boolean, val iSubmitted: Boolean, val otherSubmitted: Boolean, val revealed: Boolean)

// ───────────────────────── Inbox, safety, system ─────────────────────────

@Serializable
data class AskView(val application: Application, val item: RoleWithProject, val isRebook: Boolean = false)

@Serializable
data class ConversationRow(
    val match: Match,
    val counterpartName: String,               // short name
    val counterpartInitials: String,
    val roleCode: RoleCode,
    val projectTitle: String,
    val isOwner: Boolean,
    val lastMessage: String? = null,
    val lastAt: Long = 0,
    val unread: Int = 0,
)

@Serializable
data class AppNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val deepLink: String? = null,              // calltime://m/{matchId}, calltime://r/{roleId}, calltime://p/{projectId}
    val createdAt: Long = 0,
    val readAt: Long? = null,
)

@Serializable
data class Inbox(
    val asks: List<AskView> = emptyList(),
    val conversations: List<ConversationRow> = emptyList(),
    val updates: List<AppNotification> = emptyList(),
) {
    val unreadCount: Int get() = conversations.sumOf { it.unread } + asks.size
}

@Serializable
data class Report(
    val id: String,
    val targetKind: ReportTarget,
    val targetId: String,
    val reason: ReportReason,
    val details: String? = null,
    val status: ReportStatus = ReportStatus.RECEIVED,
    val createdAt: Long = 0,
)

@Serializable
data class Session(
    val userId: String,
    val email: String? = null,
    val persona: DemoPersona? = null,
    val hasProfile: Boolean = false,
)

/** Projects tab rows (Mine / Joined). */
@Serializable
data class ProjectSummary(
    val project: Project,
    val roles: List<ProjectRole>,
    val filledSlots: Int,
    val totalSlots: Int,
    val nextShootDate: LocalDate? = null,
    val overdueWrapItems: Int = 0,
    val newApplicants: Int = 0,
    // Joined only:
    val myRoleCode: RoleCode? = null,
    val myMatchId: String? = null,
    val myNextCall: String? = null,            // "SAT 14 NOV · 07:30"
    val myDeliveryDate: LocalDate? = null,
)

/** Project dashboard (S13), owner or collaborator view. */
@Serializable
data class ProjectFull(
    val project: Project,
    val roles: List<ProjectRole>,
    val owner: OwnerSummary,
    val isOwner: Boolean,
    val team: List<TeamEntry> = emptyList(),
    val backups: Map<String, List<ApplicantView>> = emptyMap(),   // roleId → ranked backups
    val myMatch: Match? = null,
    val paidRoles: Int = 0,
    val totalRoles: Int = 0,
    val cityName: String = "",
    val areaName: String = "",
)

@Serializable
data class TeamEntry(
    val match: Match,
    val person: ProfileCard,
    val roleCode: RoleCode,
    val nextAck: Boolean? = null,              // acked the next published day sheet?
    val wrapDone: Int = 0,
    val wrapTotal: Int = 0,
)

/** Gigs deck filters (S08, 7 filters). */
@Serializable
data class DeckFilters(
    val paidOnly: Boolean = false,
    val maxDistanceKm: Int? = null,
    val remoteOnly: Boolean = false,
    val dates: DateWindow = DateWindow.ANY,
    val idVerifiedOwnersOnly: Boolean = false,
    val footageByDate: Boolean = false,
    val languages: Set<String> = emptySet(),
)

@Serializable enum class DateWindow { THIS_WEEK, NEXT_30_DAYS, ANY }

/** Which LIVE (Supabase) features are wired up; DEMO has everything (PRD §4.2 L0/L1). */
@Serializable
data class Features(
    val shootDays: Boolean = true,
    val wrap: Boolean = true,
    val feedback: Boolean = true,
    val reschedule: Boolean = true,
    val withdrawRelease: Boolean = true,
    val closedAppNotifications: Boolean = true,
)
