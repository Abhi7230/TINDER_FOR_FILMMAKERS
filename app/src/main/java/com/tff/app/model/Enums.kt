package com.tff.app.model

import kotlinx.serialization.Serializable

// Kotlin enum names == DB text values (PRD Appendix B). Never rename a constant without a migration.

@Serializable enum class AppMode { DEMO, LIVE }
@Serializable enum class DemoPersona { MEERA, ROHAN, KABIR, DANA }

@Serializable enum class Dept { DIRECTION, WRITING, PRODUCTION, CAMERA, GE, SOUND, ART, CAST, POST, MUSIC, OTHER, SAFETY }
@Serializable enum class Scarcity { SCARCE, BALANCED, OVERSUPPLIED }

@Serializable enum class Level { FIRST_PROJECT, BUILDING_REEL, CREDITED_INDIE, WORKING_PRO }
@Serializable enum class CompMode { PAID_ONLY, PAID_AND_SELECTIVE, OPEN_TO_UNPAID }
@Serializable enum class CompType { PAID_DAY, PAID_FLAT, STIPEND, UNPAID }
@Serializable enum class PaymentTerms { ON_WRAP, HALF_UPFRONT, WITHIN_7_DAYS, WITHIN_15_DAYS, WITHIN_30_DAYS }

/** Role perks. The last three are derived by MatchEngine.perks() and never stored on a role. */
@Serializable enum class Perk { TRAVEL_COVERED, LODGING, FOOTAGE_BY_DATE, FINAL_FILM_BY_DATE, STEP_UP_CREDIT, MEALS, SHORT_COMMITMENT, SCRIPT_READY }

@Serializable enum class Goal { PAID_WORK, REEL_FOOTAGE, STEP_UP, FESTIVAL_CREDIT, BUILD_TEAM, FUN_COMMUNITY }
@Serializable enum class AvailabilityPattern { ANY_DAY, WEEKENDS_ONLY, EVENINGS_AND_WEEKENDS, WEEKDAYS_ONLY }

@Serializable enum class Format { SHORT, FEATURE, SERIES_PILOT, MUSIC_VIDEO, SPEC_AD, STUDENT_THESIS, CHALLENGE_48H, VERTICAL, DOCUMENTARY }
@Serializable enum class Stage { DEVELOPMENT, PRE, PRODUCTION, POST }
@Serializable enum class ScriptStatus { IDEA, DRAFT, LOCKED }
@Serializable enum class Ambition { FOR_FUN, PORTFOLIO, FESTIVAL, DISTRIBUTION, CLIENT_PAID }
@Serializable enum class LocationType { STUDIO, OUTDOOR_PUBLIC, PRIVATE_HOME, OFFICE_COMMERCIAL, MIXED }
@Serializable enum class DateCertainty { LOCKED, TENTATIVE }
/** Ordered: NONE < CRAFTY_ONLY < ONE_MEAL < TWO_PLUS_MEALS < CATERED (compare by ordinal). */
@Serializable enum class Meals { NONE, CRAFTY_ONLY, ONE_MEAL, TWO_PLUS_MEALS, CATERED }
@Serializable enum class Transport { NONE, CARPOOL_COORDINATED, MILEAGE_OR_TA_PAID, PRODUCTION_VAN }
@Serializable enum class ContentFlag { NUDITY, SIMULATED_SEX, INTIMACY_SCENE, STUNTS_FIGHTS, FIREARMS_PROPS, MINORS_ON_SET, NIGHT_SHOOT, WATER, ANIMALS, GORE }
@Serializable enum class AuditionMode { SELF_TAPE, VIDEO_CALL, STUDIO_OR_PUBLIC_WITH_OTHERS }
@Serializable enum class GearPolicy { PRODUCTION_PROVIDES, BYO_WITH_KIT_FEE, BYO_NO_FEE, NA }
@Serializable enum class YesNoNa { YES, NO, NOT_YET, NA }

@Serializable enum class ProjectStatus { DRAFT, OPEN, CREWED, WRAPPED, CANCELLED }
@Serializable enum class RoleStatus { OPEN, FILLED, CLOSED }

@Serializable enum class LinkKind { REEL, CREDIT, HEADSHOT, PORTFOLIO, IMDB, OTHER }

@Serializable enum class SwipeDir { RIGHT, LEFT, SAVE, BACKUP }
@Serializable enum class Decision { TALK, BACKUP, DECLINE }
@Serializable enum class ApplicationOrigin { APPLIED, INVITED }
@Serializable enum class ApplicationStatus { PENDING, MATCHED, BACKUP, DECLINED, WITHDRAWN, CLOSED, EXPIRED }
@Serializable enum class MatchState { TALKING, OFFERED, BOOKED, WRAPPED, WITHDRAWN, RELEASED, NO_SHOW, CLOSED }
@Serializable enum class BookingStatus { SENT, ACCEPTED, DECLINED, SUPERSEDED, CANCELLED }

@Serializable enum class MessageKind { TEXT, SYSTEM, BOOKING, AUDITION }
@Serializable enum class MessageFlag { CONTACT, OFF_PLATFORM, MONEY_FROM_TALENT, BODY_REQUEST, PRIVATE_MEETING }
@Serializable enum class AuditionResponse { WILL_DO, CANT_MAKE_IT, TAPE_SENT }

@Serializable enum class WrapKind { PAID, FOOTAGE, FINAL_FILM, CREDIT, DELIVERY }
@Serializable enum class OwedBy { PRODUCTION, COLLABORATOR }
@Serializable enum class WrapStatus { OPEN, DONE, CONFIRMED, NOT_RECEIVED }
@Serializable enum class Attendance { CONFIRMED, CANT_MAKE_IT, ATTENDED, NO_SHOW, DISPUTED }

@Serializable enum class WithdrawReason { DATES_CHANGED, PAID_WORK, HEALTH_FAMILY, TERMS_CHANGED, SAFETY, OTHER }
@Serializable enum class ReleaseReason { ROLE_CUT, SCHEDULE_CHANGED, FOUND_SOMEONE_ELSE, PROJECT_CANCELLED, CONDUCT, OTHER }
@Serializable enum class AskDeclineReason { DATES_DONT_WORK, TOO_FAR, NOT_MY_KIND, PAID_ONLY_NOW }

@Serializable enum class ReportTarget { PROFILE, PROJECT, ROLE, MESSAGE, BOOKING }
@Serializable enum class ReportReason { MONEY_REQUEST, SEXUAL_INAPPROPRIATE, FAKE_IMPERSONATION, UNSAFE_SET, HARASSMENT, NOT_PAID, SPAM, OTHER }
@Serializable enum class ReportStatus { RECEIVED, REVIEWING, ACTION_TAKEN, NO_ACTION }

// Feedback (S18): forced-choice answers. Private; only aggregates of facts are ever shown.
@Serializable enum class PaidAnswer { ON_TIME, LATE, NO, NOT_PAID_ROLE }
@Serializable enum class DeliveredAnswer { YES, PARTLY, NOT_YET, NO }
@Serializable enum class KeptAnswer { YES, CUT_LATE, CANCELLED_LATE }
@Serializable enum class ShowedUpAnswer { YES, LATE, NO_SHOW }
@Serializable enum class OnTimeAnswer { YES, LATE, DIDNT_DELIVER }
@Serializable enum class TriAnswer { YES, SOMEWHAT, NO }

@Serializable enum class NotificationKind {
    APPLICATION_RECEIVED, APPLICATION_CLOSED, APPLICATION_BACKUP, ASK_RECEIVED, MATCH_CREATED, MESSAGE,
    BOOKING_CARD, BOOKING_ACCEPTED, BOOKING_CHANGED, DAY_SHEET, STILL_COMING, WRAP_ITEM, WRAP_OVERDUE,
    FEEDBACK_UNLOCKED, WITHDRAWAL, RELEASE, PROJECT_CHANGED, REPORT_UPDATE, SYSTEM
}

@Serializable enum class TrustPage { HOW_WE_MAKE_MONEY, NO_GEN_AI, COMMUNITY_RULES, PRIVACY, LICENCES, WHAT_NORMAL_LOOKS_LIKE }
@Serializable enum class SimSpeed(val factor: Double) { NORMAL(1.0), FAST(1.0 / 3.0), INSTANT(0.0) }
