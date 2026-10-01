@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class)

package com.tff.app.data.demo

import com.tff.app.core.LocalDateSerializer
import com.tff.app.core.LocalTimeSerializer
import com.tff.app.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/**
 * The whole offline world in one immutable, @Serializable value. DemoEngine replaces it on every
 * mutation; DemoRepo exposes it as a StateFlow (so every screen updates live) and writes it behind to
 * a JSON file. Add fields only with defaults so older demo files still load (unknown keys are ignored).
 */
@Serializable
data class DemoState(
    val schema: Int = SCHEMA,
    /** Device date at first launch / last reset. Every seeded date is relative to it. */
    val anchor: LocalDate,
    /** Demo clock offset (AppClock.offsetMillis): Settings → +1 day, Jump past next shoot... */
    val offsetMillis: Long = 0,
    val speed: SimSpeed = SimSpeed.NORMAL,
    val session: Session? = null,
    /** People who can log in (personas + "build my own" users). Notifications are stored only for them. */
    val realUsers: Set<String> = emptySet(),
    val profiles: Map<String, Profile> = emptyMap(),
    /** profile_private.min_day_rate. Never leaves DemoEngine except to its own owner. */
    val privateRates: Map<String, Int> = emptyMap(),
    val emails: Map<String, String> = emptyMap(),
    val projects: Map<String, Project> = emptyMap(),
    val roles: Map<String, ProjectRole> = emptyMap(),
    val applications: Map<String, Application> = emptyMap(),
    val matches: Map<String, Match> = emptyMap(),
    val messages: List<Message> = emptyList(),
    val bookingCards: Map<String, BookingCard> = emptyMap(),
    val shootDays: Map<String, ShootDay> = emptyMap(),
    val callTimes: List<CallTime> = emptyList(),
    val wrapItems: Map<String, WrapItem> = emptyMap(),
    val feedback: List<Feedback> = emptyList(),
    val reports: List<ReportRow> = emptyList(),
    val blocks: List<BlockRow> = emptyList(),
    val swipes: List<SwipeRow> = emptyList(),
    val notifications: List<NotificationRow> = emptyList(),
    /** "$userId|$matchId" → last-read millis (unread dots). */
    val readMarks: Map<String, Long> = emptyMap(),
    val events: List<LogEvent> = emptyList(),
    /** One-shot markers so time rules fire once ("still:<dayId>:<uid>", "nudge72:<matchId>"...). */
    val flags: Set<String> = emptySet(),
    val seq: Long = 1,
) {
    companion object { const val SCHEMA = 1 }
}

@Serializable
data class SwipeRow(
    val actorId: String,
    val targetKind: String,           // ROLE | PROFILE
    val targetId: String,
    val contextRoleId: String? = null,
    val dir: SwipeDir,
    val at: Long,
)

@Serializable
data class BlockRow(val blockerId: String, val blockedId: String, val at: Long)

@Serializable
data class ReportRow(
    val reporterId: String,
    val report: Report,
    /** The profile the report is ultimately about (for the ≥3 independent reports auto-hide rule). */
    val subjectProfileId: String? = null,
)

@Serializable
data class NotificationRow(val userId: String, val n: AppNotification)

@Serializable
data class LogEvent(val userId: String?, val name: String, val props: Map<String, String> = emptyMap(), val at: Long)
