@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class)

package com.tff.app.data.remote

import com.tff.app.core.LocalDateSerializer
import com.tff.app.core.LocalTimeSerializer
import com.tff.app.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/*
 * Wire format of the Supabase backend (supabase/migrations/20261002000000_init.sql).
 * Keys are snake_case == column names. Enum values == Kotlin enum names, so model enums are used directly.
 * Timestamps: RPC JSON sends epoch millis, direct table reads / Realtime send ISO strings; [Millis] takes both.
 */

/** Epoch millis from a JSON number or a Postgres ISO timestamp ("2026-10-02T10:20:30.123456+00:00"). */
object Millis : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CalltimeMillis", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long {
        val e = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeLong()
        return parseMillis(e) ?: 0L
    }

    fun parseMillis(e: JsonElement?): Long? {
        if (e == null || e is JsonNull || e !is JsonPrimitive) return null
        if (!e.isString) return e.longOrNull ?: e.doubleOrNull?.toLong()
        val s = e.content.trim()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return it }
        var iso = s.replace(' ', 'T')
        if (Regex("[+-]\\d{2}$").containsMatchIn(iso)) iso += ":00"          // "+00" → "+00:00"
        if (!Regex("([+-]\\d{2}:\\d{2}|Z)$").containsMatchIn(iso)) iso += "Z"
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
    }
}

/** Nullable variant of [Millis]. */
object MillisOrNull : KSerializer<Long?> by Millis.nullable {
    override fun deserialize(decoder: Decoder): Long? {
        val e = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return Millis.nullable.deserialize(decoder)
        return Millis.parseMillis(e)
    }
}

// ───────────────────────── Reference ─────────────────────────

@Serializable
data class AreaDto(
    val id: String,
    @SerialName("city_id") val cityId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class CityDto(
    val id: String,
    val name: String,
    val country: String,
    val currency: String,
    val lat: Double,
    val lng: Double,
    @SerialName("paid_floor_day") val paidFloorDay: Int = 0,
    @SerialName("emergency_number") val emergencyNumber: String = "112",
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("is_early") val isEarly: Boolean = false,
    val areas: List<AreaDto> = emptyList(),
)

@Serializable
data class DensityDto(
    @SerialName("city_id") val cityId: String,
    @SerialName("city_name") val cityName: String,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("is_early") val isEarly: Boolean = false,
    val people: Int = 0,
    @SerialName("open_projects") val openProjects: Int = 0,
    @SerialName("per_role") val perRole: Map<String, Int> = emptyMap(),
)

// ───────────────────────── People ─────────────────────────

@Serializable
data class SecondaryRoleDto(val code: RoleCode, val proficiency: Int = 2)

@Serializable
data class WorkLinkDto(
    val id: String = "",
    val url: String,
    val kind: LinkKind = LinkKind.OTHER,
    @SerialName("role_code") val roleCode: RoleCode? = null,
    val title: String? = null,
    val year: Int? = null,
)

@Serializable
data class ProfileDto(
    val id: String = "",
    @SerialName("display_name") val displayName: String,
    val pronouns: String? = null,
    @SerialName("primary_role") val primaryRole: RoleCode,
    @SerialName("secondary_roles") val secondaryRoles: List<SecondaryRoleDto> = emptyList(),
    @SerialName("step_up_roles") val stepUpRoles: List<RoleCode> = emptyList(),
    val level: Level,
    @SerialName("city_id") val cityId: String,
    @SerialName("area_id") val areaId: String,
    @SerialName("travel_radius_km") val travelRadiusKm: Int = 15,
    @SerialName("travel_if_covered") val travelIfCovered: Boolean = false,
    @SerialName("remote_ok") val remoteOk: Boolean = false,
    val languages: List<String> = listOf("en"),
    @SerialName("availability_pattern") val availabilityPattern: AvailabilityPattern = AvailabilityPattern.ANY_DAY,
    @SerialName("busy_dates") val busyDates: List<LocalDate> = emptyList(),
    @SerialName("comp_mode") val compMode: CompMode = CompMode.PAID_AND_SELECTIVE,
    @SerialName("kit_fee_day") val kitFeeDay: Int? = null,
    @SerialName("unpaid_must_haves") val unpaidMustHaves: List<Perk> = emptyList(),
    val goals: List<Goal> = emptyList(),
    @SerialName("content_wont_do") val contentWontDo: List<ContentFlag> = emptyList(),
    @SerialName("work_links") val workLinks: List<WorkLinkDto> = emptyList(),
    val gear: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    @SerialName("taste_films") val tasteFilms: List<String> = emptyList(),
    @SerialName("genres_pref") val genresPref: List<String> = emptyList(),
    @SerialName("formats_pref") val formatsPref: List<Format> = emptyList(),
    val bio: String? = null,
    val affiliations: List<String> = emptyList(),
    @SerialName("email_verified") val emailVerified: Boolean = false,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    @SerialName("id_verified") val idVerified: Boolean = false,
    @SerialName("adult_confirmed") val adultConfirmed: Boolean = false,
    @Serializable(with = Millis::class) @SerialName("created_at") val createdAt: Long = 0,
    @Serializable(with = Millis::class) @SerialName("last_active_at") val lastActiveAt: Long = 0,
)

@Serializable
data class RatioDto(val num: Int, val den: Int)

@Serializable
data class ReputationDto(
    @SerialName("profile_id") val profileId: String,
    @SerialName("shoots_completed") val shootsCompleted: Int = 0,
    @SerialName("showed_up") val showedUp: RatioDto? = null,
    @SerialName("delivered_on_time") val deliveredOnTime: RatioDto? = null,
    @SerialName("paid_on_time") val paidOnTime: RatioDto? = null,
    @SerialName("footage_delivered") val footageDelivered: RatioDto? = null,
    @SerialName("kept_bookings") val keptBookings: RatioDto? = null,
    @SerialName("closes_loop") val closesLoop: RatioDto? = null,
    @SerialName("reply_hours_median") val replyHoursMedian: Int? = null,
    @SerialName("confirmed_credits") val confirmedCredits: Int = 0,
    @SerialName("closure_rate_14d") val closureRate14d: Double? = null,
)

@Serializable
data class ConfirmedCreditDto(
    @SerialName("project_title") val projectTitle: String,
    @SerialName("role_code") val roleCode: RoleCode,
    @SerialName("credit_title") val creditTitle: String,
    val year: Int,
)

@Serializable
data class CardDto(
    val profile: ProfileDto,
    val reputation: ReputationDto,
    @SerialName("confirmed_credits") val confirmedCredits: List<ConfirmedCreditDto> = emptyList(),
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("area_name") val areaName: String? = null,
    @SerialName("city_name") val cityName: String? = null,
)

@Serializable
data class OwnerDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val level: Level,
    @SerialName("id_verified") val idVerified: Boolean = false,
    val reputation: ReputationDto,
    @Serializable(with = Millis::class) @SerialName("last_active_at") val lastActiveAt: Long = 0,
    @SerialName("closure_rate_14d") val closureRate14d: Double? = null,
)

// ───────────────────────── Projects ─────────────────────────

@Serializable
data class ProjectDto(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("payer_id") val payerId: String = "",
    val title: String = "",
    val logline: String = "",
    val format: Format = Format.SHORT,
    @SerialName("runtime_min") val runtimeMin: Int = 10,
    @SerialName("page_count") val pageCount: Int? = null,
    val genres: List<String> = emptyList(),
    val languages: List<String> = listOf("en"),
    val stage: Stage = Stage.PRE,
    @SerialName("script_status") val scriptStatus: ScriptStatus = ScriptStatus.DRAFT,
    @SerialName("script_readable") val scriptReadable: Boolean = false,
    @SerialName("lookbook_url") val lookbookUrl: String? = null,
    @SerialName("ambition_tier") val ambitionTier: Ambition = Ambition.PORTFOLIO,
    @SerialName("honest_zero_budget") val honestZeroBudget: Boolean = false,
    @SerialName("city_id") val cityId: String,
    @SerialName("area_id") val areaId: String,
    @SerialName("shoot_location_type") val shootLocationType: LocationType? = null,
    @SerialName("exact_address") val exactAddress: String? = null,
    @SerialName("story_setting") val storySetting: String? = null,
    @SerialName("shoot_dates") val shootDates: List<LocalDate> = emptyList(),
    @SerialName("picture_lock_date") val pictureLockDate: LocalDate? = null,
    @SerialName("date_certainty") val dateCertainty: DateCertainty? = null,
    @SerialName("hours_cap") val hoursCap: Int? = null,
    val meals: Meals? = null,
    val transport: Transport? = null,
    @SerialName("travel_allowance_per_day") val travelAllowancePerDay: Int? = null,
    val lodging: Boolean? = null,
    @SerialName("content_flags") val contentFlags: List<ContentFlag> = emptyList(),
    @SerialName("content_none_confirmed") val contentNoneConfirmed: Boolean = false,
    @SerialName("intimacy_coordinator") val intimacyCoordinator: Boolean? = null,
    @SerialName("stunt_or_armourer") val stuntOrArmourer: Boolean? = null,
    @SerialName("guardian_present") val guardianPresent: Boolean? = null,
    @SerialName("safety_measures") val safetyMeasures: String? = null,
    @SerialName("audition_mode") val auditionMode: AuditionMode? = null,
    @SerialName("bring_friend_welcome") val bringFriendWelcome: Boolean = false,
    val insurance: YesNoNa? = null,
    val permits: YesNoNa? = null,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    @SerialName("reschedule_count") val rescheduleCount: Int = 0,
    @Serializable(with = Millis::class) @SerialName("created_at") val createdAt: Long = 0,
    @Serializable(with = MillisOrNull::class) @SerialName("published_at") val publishedAt: Long? = null,
    @Serializable(with = Millis::class) @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class RoleDto(
    val id: String = "",
    @SerialName("project_id") val projectId: String = "",
    @SerialName("role_code") val roleCode: RoleCode,
    val count: Int = 1,
    @SerialName("credit_title") val creditTitle: String = "",
    @SerialName("scope_includes") val scopeIncludes: List<String> = emptyList(),
    @SerialName("scope_excludes") val scopeExcludes: List<String> = emptyList(),
    @SerialName("step_up_friendly") val stepUpFriendly: Boolean = false,
    @SerialName("level_wanted") val levelWanted: Level? = null,
    @SerialName("comp_type") val compType: CompType,
    @SerialName("amount_min") val amountMin: Int? = null,
    @SerialName("amount_max") val amountMax: Int? = null,
    val currency: String = "INR",
    @SerialName("kit_fee_day") val kitFeeDay: Int? = null,
    @SerialName("payment_terms") val paymentTerms: PaymentTerms? = null,
    val perks: List<Perk> = emptyList(),
    @SerialName("footage_due_weeks") val footageDueWeeks: Int? = null,
    @SerialName("final_film_due_weeks") val finalFilmDueWeeks: Int? = null,
    @SerialName("gear_policy") val gearPolicy: GearPolicy? = null,
    @SerialName("remote_ok") val remoteOk: Boolean = false,
    @SerialName("post_deadline") val postDeadline: LocalDate? = null,
    @SerialName("role_details") val roleDetails: JsonObject = JsonObject(emptyMap()),
    @SerialName("screening_question") val screeningQuestion: String? = null,
    @SerialName("application_cap") val applicationCap: Int = 15,
    @SerialName("applications_count") val applicationsCount: Int = 0,
    @SerialName("filled_count") val filledCount: Int = 0,
    val status: RoleStatus = RoleStatus.OPEN,
)

@Serializable
data class TeamDto(
    @SerialName("profile_id") val profileId: String,
    @SerialName("short_name") val shortName: String,
    val initials: String,
    @SerialName("role_code") val roleCode: RoleCode,
    @SerialName("is_owner") val isOwner: Boolean = false,
)

@Serializable
data class RwpDto(
    val role: RoleDto,
    val project: ProjectDto,
    val owner: OwnerDto,
    @SerialName("city_name") val cityName: String = "",
    @SerialName("area_name") val areaName: String = "",
    @SerialName("area_lat") val areaLat: Double = 0.0,
    @SerialName("area_lng") val areaLng: Double = 0.0,
    val team: List<TeamDto> = emptyList(),
    @SerialName("paid_roles") val paidRoles: Int = 0,
    @SerialName("total_roles") val totalRoles: Int = 1,
)

@Serializable
data class DraftDto(val project: ProjectDto, val roles: List<RoleDto> = emptyList())

// ───────────────────────── Matching ─────────────────────────

@Serializable
data class ApplicationDto(
    val id: String,
    @SerialName("role_id") val roleId: String,
    @SerialName("applicant_id") val applicantId: String,
    val origin: ApplicationOrigin = ApplicationOrigin.APPLIED,
    val note: String? = null,
    @SerialName("work_link_id") val workLinkId: String? = null,
    @SerialName("screening_answer") val screeningAnswer: String? = null,
    @SerialName("kit_offered") val kitOffered: Boolean = false,
    @SerialName("is_rebook") val isRebook: Boolean = false,
    val status: ApplicationStatus = ApplicationStatus.PENDING,
    @SerialName("backup_rank") val backupRank: Int? = null,
    @SerialName("decline_reason") val declineReason: String? = null,
    @Serializable(with = Millis::class) @SerialName("created_at") val createdAt: Long = 0,
    @Serializable(with = MillisOrNull::class) @SerialName("decided_at") val decidedAt: Long? = null,
)

@Serializable
data class ApplicantRowDto(
    val application: ApplicationDto? = null,
    val card: CardDto,
    @SerialName("rate_fit") val rateFit: Double = 1.0,
    @SerialName("headshot_url") val headshotUrl: String? = null,
    @SerialName("answer_duplicate") val answerDuplicate: Boolean = false,
)

@Serializable
data class MatchDto(
    val id: String,
    @SerialName("application_id") val applicationId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("role_id") val roleId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("collaborator_id") val collaboratorId: String,
    val state: MatchState = MatchState.TALKING,
    @SerialName("is_post_role") val isPostRole: Boolean = false,
    @SerialName("change_pending") val changePending: Boolean = false,
    @SerialName("contacts_shared_by_owner") val contactsSharedByOwner: Boolean = false,
    @SerialName("contacts_shared_by_collab") val contactsSharedByCollab: Boolean = false,
    @SerialName("withdraw_reason") val withdrawReason: String? = null,
    @SerialName("notice_hours") val noticeHours: Int? = null,
    @SerialName("released_by_owner") val releasedByOwner: Boolean = false,
    @Serializable(with = Millis::class) @SerialName("created_at") val createdAt: Long = 0,
    @Serializable(with = MillisOrNull::class) @SerialName("booked_at") val bookedAt: Long? = null,
    @Serializable(with = MillisOrNull::class) @SerialName("ended_at") val endedAt: Long? = null,
)

// ───────────────────────── Messaging & booking ─────────────────────────

@Serializable
data class AuditionRequestDto(
    val mode: AuditionMode,
    @SerialName("sides_url") val sidesUrl: String? = null,
    @SerialName("due_date") val dueDate: LocalDate? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    @SerialName("meeting_link") val meetingLink: String? = null,
    val venue: String? = null,
    val address: String? = null,
    @SerialName("also_present") val alsoPresent: List<String> = emptyList(),
    val note: String? = null,
)

@Serializable
data class AuditionPayloadDto(
    val request: AuditionRequestDto,
    val response: AuditionResponse? = null,
    @SerialName("tape_url") val tapeUrl: String? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val kind: MessageKind = MessageKind.TEXT,
    val body: String = "",
    val payload: JsonElement? = null,                 // AUDITION → AuditionPayloadDto
    @SerialName("booking_card_id") val bookingCardId: String? = null,
    val flags: List<MessageFlag> = emptyList(),
    @SerialName("safety_example") val safetyExample: Boolean = false,
    @Serializable(with = Millis::class) @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class MessageInsertDto(
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: MessageKind = MessageKind.TEXT,
    val body: String,
)

@Serializable
data class DeliverableDto(
    val kind: WrapKind,
    @SerialName("due_date") val dueDate: LocalDate? = null,
    @SerialName("owed_by") val owedBy: OwedBy = OwedBy.PRODUCTION,
)

@Serializable
data class BookingTermsDto(
    @SerialName("role_code") val roleCode: RoleCode,
    @SerialName("credit_title") val creditTitle: String = "",
    @SerialName("project_title") val projectTitle: String = "",
    @SerialName("is_post_role") val isPostRole: Boolean = false,
    val dates: List<LocalDate> = emptyList(),
    @SerialName("delivery_date") val deliveryDate: LocalDate? = null,
    @SerialName("picture_lock_date") val pictureLockDate: LocalDate? = null,
    @SerialName("revision_rounds") val revisionRounds: Int? = null,
    @SerialName("call_window") val callWindow: String? = null,
    @SerialName("comp_type") val compType: CompType,
    val amount: Int? = null,
    val currency: String = "INR",
    @SerialName("kit_fee_day") val kitFeeDay: Int? = null,
    @SerialName("payment_terms") val paymentTerms: PaymentTerms? = null,
    @SerialName("pay_by_date") val payByDate: LocalDate? = null,
    val perks: List<Perk> = emptyList(),
    val meals: Meals? = null,
    val transport: Transport? = null,
    @SerialName("travel_allowance_per_day") val travelAllowancePerDay: Int? = null,
    @SerialName("footage_due_weeks") val footageDueWeeks: Int? = null,
    @SerialName("final_film_due_weeks") val finalFilmDueWeeks: Int? = null,
    val deliverables: List<DeliverableDto> = emptyList(),
    @SerialName("footage_definition") val footageDefinition: String = "Approved clips of your scenes, graded, for your reel. Not raw footage.",
    @SerialName("rights_note") val rightsNote: String = "Production owns the film. You may use approved clips in your reel.",
    val notes: String? = null,
    @SerialName("payer_id") val payerId: String = "",
    @SerialName("payer_name") val payerName: String = "",
    @SerialName("notice_pledge") val noticePledge: String = "",
)

@Serializable
data class BookingCardDto(
    val id: String,
    @SerialName("match_id") val matchId: String,
    val version: Int,
    val terms: BookingTermsDto,
    val status: BookingStatus = BookingStatus.SENT,
    @SerialName("changed_fields") val changedFields: List<String> = emptyList(),
    @SerialName("sent_by") val sentBy: String? = null,
    @Serializable(with = Millis::class) @SerialName("sent_at") val sentAt: Long = 0,
    @Serializable(with = MillisOrNull::class) @SerialName("responded_at") val respondedAt: Long? = null,
)

@Serializable
data class MatchViewDto(
    val match: MatchDto,
    val item: RwpDto,
    val counterpart: CardDto,
    @SerialName("is_owner") val isOwner: Boolean,
    @SerialName("current_card") val currentCard: BookingCardDto? = null,
    @SerialName("accepted_card") val acceptedCard: BookingCardDto? = null,
    @SerialName("next_shoot_day") val nextShootDay: ShootDayDto? = null,
    @SerialName("my_call_time") val myCallTime: LocalTime? = null,
)

@Serializable
data class WrapItemDto(
    val id: String,
    @SerialName("match_id") val matchId: String,
    val kind: WrapKind,
    @SerialName("due_date") val dueDate: LocalDate? = null,
    @SerialName("owed_by") val owedBy: OwedBy = OwedBy.PRODUCTION,
    val status: WrapStatus = WrapStatus.OPEN,
    val link: String? = null,
    val note: String? = null,
    @Serializable(with = MillisOrNull::class) @SerialName("marked_at") val markedAt: Long? = null,
    @Serializable(with = MillisOrNull::class) @SerialName("confirmed_at") val confirmedAt: Long? = null,
    @SerialName("auto_confirmed") val autoConfirmed: Boolean = false,
)

// ───────────────────────── Inbox, safety, system ─────────────────────────

@Serializable
data class NotificationDto(
    val id: String,
    val kind: NotificationKind = NotificationKind.SYSTEM,
    val title: String = "",
    val body: String = "",
    @SerialName("deep_link") val deepLink: String? = null,
    @Serializable(with = Millis::class) @SerialName("created_at") val createdAt: Long = 0,
    @Serializable(with = MillisOrNull::class) @SerialName("read_at") val readAt: Long? = null,
)

@Serializable
data class AskDto(
    val application: ApplicationDto,
    val item: RwpDto,
    @SerialName("is_rebook") val isRebook: Boolean = false,
)

@Serializable
data class ConversationDto(
    val match: MatchDto,
    @SerialName("counterpart_name") val counterpartName: String,
    @SerialName("counterpart_initials") val counterpartInitials: String,
    @SerialName("role_code") val roleCode: RoleCode,
    @SerialName("project_title") val projectTitle: String,
    @SerialName("is_owner") val isOwner: Boolean,
    @SerialName("last_message") val lastMessage: String? = null,
    @Serializable(with = Millis::class) @SerialName("last_at") val lastAt: Long = 0,
    val unread: Int = 0,
)

@Serializable
data class InboxDto(
    val asks: List<AskDto> = emptyList(),
    val conversations: List<ConversationDto> = emptyList(),
    val updates: List<NotificationDto> = emptyList(),
)

@Serializable
data class ReportDto(
    val id: String,
    @SerialName("target_kind") val targetKind: ReportTarget,
    @SerialName("target_id") val targetId: String,
    val reason: ReportReason,
    val details: String? = null,
    val status: ReportStatus = ReportStatus.RECEIVED,
    @Serializable(with = Millis::class) @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class ReportInsertDto(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_kind") val targetKind: ReportTarget,
    @SerialName("target_id") val targetId: String,
    val reason: ReportReason,
    val details: String? = null,
)

@Serializable
data class EventInsertDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    val props: Map<String, String> = emptyMap(),
)

@Serializable
data class ProjectSummaryDto(
    val project: ProjectDto,
    val roles: List<RoleDto> = emptyList(),
    @SerialName("filled_slots") val filledSlots: Int = 0,
    @SerialName("total_slots") val totalSlots: Int = 0,
    @SerialName("next_shoot_date") val nextShootDate: LocalDate? = null,
    @SerialName("overdue_wrap_items") val overdueWrapItems: Int = 0,
    @SerialName("new_applicants") val newApplicants: Int = 0,
    @SerialName("my_role_code") val myRoleCode: RoleCode? = null,
    @SerialName("my_match_id") val myMatchId: String? = null,
    @SerialName("my_next_call") val myNextCall: String? = null,
    @SerialName("my_delivery_date") val myDeliveryDate: LocalDate? = null,
)

@Serializable
data class TeamEntryDto(
    val match: MatchDto,
    val person: CardDto,
    @SerialName("role_code") val roleCode: RoleCode,
    @SerialName("next_ack") val nextAck: Boolean? = null,
    @SerialName("wrap_done") val wrapDone: Int = 0,
    @SerialName("wrap_total") val wrapTotal: Int = 0,
)

@Serializable
data class ProjectFullDto(
    val project: ProjectDto,
    val roles: List<RoleDto> = emptyList(),
    val owner: OwnerDto,
    @SerialName("is_owner") val isOwner: Boolean = false,
    val team: List<TeamEntryDto> = emptyList(),
    val backups: Map<String, List<ApplicantRowDto>> = emptyMap(),
    @SerialName("my_match") val myMatch: MatchDto? = null,
    @SerialName("paid_roles") val paidRoles: Int = 0,
    @SerialName("total_roles") val totalRoles: Int = 0,
    @SerialName("city_name") val cityName: String = "",
    @SerialName("area_name") val areaName: String = "",
    @SerialName("area_lat") val areaLat: Double = 0.0,
    @SerialName("area_lng") val areaLng: Double = 0.0,
)

// ───────────────────────── L1: shoot days, wrap, feedback, exits ─────────────────────────

@Serializable
data class ShootDayDto(
    val id: String,
    @SerialName("project_id") val projectId: String,
    val day: LocalDate,
    @SerialName("general_call") val generalCall: LocalTime,
    @SerialName("wrap_by") val wrapBy: LocalTime,
    val address: String = "",
    @SerialName("maps_url") val mapsUrl: String = "",
    @SerialName("parking_note") val parkingNote: String? = null,
    @SerialName("hospital_note") val hospitalNote: String? = null,
    @SerialName("meals_plan") val mealsPlan: String? = null,
    val notes: String? = null,
    val version: Int = 1,
    @Serializable(with = MillisOrNull::class) @SerialName("published_at") val publishedAt: Long? = null,
    @Serializable(with = MillisOrNull::class) @SerialName("cancelled_at") val cancelledAt: Long? = null,
)

@Serializable
data class CallTimeDto(
    @SerialName("shoot_day_id") val shootDayId: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("call_time") val callTime: LocalTime,
    @Serializable(with = MillisOrNull::class) @SerialName("ack_at") val ackAt: Long? = null,
    val attendance: Attendance? = null,
)

@Serializable
data class CallTimeViewDto(
    val call: CallTimeDto,
    @SerialName("short_name") val shortName: String,
    @SerialName("role_code") val roleCode: RoleCode,
)

@Serializable
data class ShootDayFullDto(
    val day: ShootDayDto,
    @SerialName("day_index") val dayIndex: Int = 1,
    @SerialName("day_count") val dayCount: Int = 1,
    val calls: List<CallTimeViewDto> = emptyList(),
    @SerialName("my_call") val myCall: CallTimeDto? = null,
)

@Serializable
data class ShootDayDraftDto(
    @SerialName("project_id") val projectId: String,
    @SerialName("shoot_day_id") val shootDayId: String? = null,
    val day: LocalDate,
    @SerialName("general_call") val generalCall: LocalTime,
    @SerialName("wrap_by") val wrapBy: LocalTime,
    val address: String,
    @SerialName("parking_note") val parkingNote: String? = null,
    @SerialName("hospital_note") val hospitalNote: String? = null,
    @SerialName("meals_plan") val mealsPlan: String? = null,
    val notes: String? = null,
    @SerialName("per_person_call") val perPersonCall: Map<String, LocalTime> = emptyMap(),
)

@Serializable
data class WrapGroupDto(
    val match: MatchDto,
    val person: CardDto,
    @SerialName("role_code") val roleCode: RoleCode,
    val items: List<WrapItemDto> = emptyList(),
)

@Serializable
data class FeedbackAnswersDto(
    val paid: PaidAnswer? = null,
    val delivered: DeliveredAnswer? = null,
    @SerialName("felt_safe") val feltSafe: Boolean? = null,
    val kept: KeptAnswer? = null,
    @SerialName("showed_up") val showedUp: ShowedUpAnswer? = null,
    val prepared: TriAnswer? = null,
    @SerialName("delivered_on_time") val deliveredOnTime: OnTimeAnswer? = null,
    val revisions: TriAnswer? = null,
    @SerialName("work_again") val workAgain: Boolean? = null,
)

@Serializable
data class FeedbackStateDto(
    val unlocked: Boolean = false,
    @SerialName("i_submitted") val iSubmitted: Boolean = false,
    @SerialName("other_submitted") val otherSubmitted: Boolean = false,
    val revealed: Boolean = false,
)

@Serializable
data class WithdrawResultDto(
    val match: MatchDto,
    @SerialName("backup_application_id") val backupApplicationId: String? = null,
)
