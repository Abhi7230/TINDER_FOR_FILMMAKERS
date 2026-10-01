package com.tff.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.data.RepoException
import com.tff.app.model.AskDeclineReason
import com.tff.app.model.MatchState
import com.tff.app.model.NotificationKind
import com.tff.app.model.ReleaseReason
import com.tff.app.model.WithdrawReason
import com.tff.app.model.WrapKind
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Shared bits for WS6 screens (Inbox, Chat, Booking card, Shoot day, Wrap). Nothing here is a contract.

/** Screen load state: every WS6 screen renders Loading, Error ("Couldn't load. Retry") and its data. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String?) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}

/** The user-facing message of a Repo failure (RepoException carries one), or null for the generic copy. */
fun Throwable.userMessage(): String? = (this as? RepoException)?.userMessage ?: message?.takeIf { it.isNotBlank() && it.length < 160 }

/**
 * Cross-screen hand-offs inside WS6 that don't belong in navigation arguments:
 *  - [prefill]: "Ask to change" on the booking card returns to the chat with "Could we change…" typed.
 *  - [openChats]: which chats are alive on the back stack, so the booking card can go *back* to the chat
 *    instead of stacking a second copy of it.
 */
object ChatBridge {
    val prefill = MutableStateFlow<Map<String, String>>(emptyMap())
    private val open = mutableMapOf<String, Int>()

    fun setPrefill(matchId: String, text: String) = prefill.update { it + (matchId to text) }
    fun consumePrefill(matchId: String): String? {
        val t = prefill.value[matchId] ?: return null
        prefill.update { it - matchId }
        return t
    }

    @Synchronized fun chatOpened(matchId: String) { open[matchId] = (open[matchId] ?: 0) + 1 }
    @Synchronized fun chatClosed(matchId: String) {
        val n = (open[matchId] ?: 1) - 1
        if (n <= 0) open.remove(matchId) else open[matchId] = n
    }
    @Synchronized fun isChatOpen(matchId: String): Boolean = (open[matchId] ?: 0) > 0
}

// ───────────────────────── Time formatting (mono strings) ─────────────────────────

private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dow = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

fun localDateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(AppClock.zone).toLocalDate()
fun localTimeOf(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(AppClock.zone).toLocalTime()

/** Inbox row time: "14:05" today, "TUE" this week, "14 NOV" otherwise. */
fun rowTime(millis: Long): String {
    if (millis <= 0) return ""
    val d = localDateOf(millis)
    val today = AppClock.today()
    return when {
        d == today -> hm.format(localTimeOf(millis))
        !d.isAfter(today) && ChronoUnit.DAYS.between(d, today) < 7 -> dow.format(d).uppercase(Locale.ENGLISH)
        else -> Dates.dayMonth(d).uppercase(Locale.ENGLISH)
    }
}

/** Chat day separator: "TODAY", "YESTERDAY", "SAT 14 NOV". */
@Composable
fun daySeparator(d: LocalDate): String {
    val today = AppClock.today()
    return when (d) {
        today -> stringResource(R.string.ws6_today)
        today.minusDays(1) -> stringResource(R.string.ws6_yesterday)
        else -> Dates.shortUpper(d)
    }
}

fun clock(millis: Long): String = hm.format(localTimeOf(millis))

/** "SAT 07:30" within the next 6 days, else "SAT 14 NOV · 07:30". */
fun nextCallText(day: LocalDate, time: LocalTime): String {
    val today = AppClock.today()
    val days = ChronoUnit.DAYS.between(today, day)
    return if (days in 0..6) "${dow.format(day).uppercase(Locale.ENGLISH)} ${Dates.time(time)}"
    else "${Dates.shortUpper(day)} · ${Dates.time(time)}"
}

// ───────────────────────── Labels ─────────────────────────

@Composable
fun withdrawReasonLabel(r: WithdrawReason): String = stringResource(
    when (r) {
        WithdrawReason.DATES_CHANGED -> R.string.ws6_withdraw_dates
        WithdrawReason.PAID_WORK -> R.string.ws6_withdraw_paid_work
        WithdrawReason.HEALTH_FAMILY -> R.string.ws6_withdraw_health
        WithdrawReason.TERMS_CHANGED -> R.string.ws6_withdraw_terms
        WithdrawReason.SAFETY -> R.string.ws6_withdraw_safety
        WithdrawReason.OTHER -> R.string.ws6_reason_other
    }
)

@Composable
fun releaseReasonLabel(r: ReleaseReason): String = stringResource(
    when (r) {
        ReleaseReason.ROLE_CUT -> R.string.ws6_release_role_cut
        ReleaseReason.SCHEDULE_CHANGED -> R.string.ws6_release_schedule
        ReleaseReason.FOUND_SOMEONE_ELSE -> R.string.ws6_release_someone_else
        ReleaseReason.PROJECT_CANCELLED -> R.string.ws6_release_cancelled
        ReleaseReason.CONDUCT -> R.string.ws6_release_conduct
        ReleaseReason.OTHER -> R.string.ws6_reason_other
    }
)

@Composable
fun askDeclineLabel(r: AskDeclineReason): String = stringResource(
    when (r) {
        AskDeclineReason.DATES_DONT_WORK -> R.string.ws6_ask_decline_dates
        AskDeclineReason.TOO_FAR -> R.string.ws6_ask_decline_far
        AskDeclineReason.NOT_MY_KIND -> R.string.ws6_ask_decline_kind
        AskDeclineReason.PAID_ONLY_NOW -> R.string.ws6_ask_decline_paid
    }
)

@Composable
fun wrapKindLabel(k: WrapKind): String = stringResource(
    when (k) {
        WrapKind.PAID -> R.string.ws6_wrap_kind_paid
        WrapKind.FOOTAGE -> R.string.ws6_wrap_kind_footage
        WrapKind.FINAL_FILM -> R.string.ws6_wrap_kind_final_film
        WrapKind.CREDIT -> R.string.ws6_wrap_kind_credit
        WrapKind.DELIVERY -> R.string.ws6_wrap_kind_delivery
    }
)

fun wrapKindIcon(k: WrapKind): ImageVector = when (k) {
    WrapKind.PAID -> Icons.Rounded.Payments
    WrapKind.FOOTAGE -> Icons.Rounded.VideoLibrary
    WrapKind.FINAL_FILM -> Icons.Rounded.Theaters
    WrapKind.CREDIT -> Icons.Rounded.Badge
    WrapKind.DELIVERY -> Icons.Rounded.CloudUpload
}

fun notificationIcon(k: NotificationKind): ImageVector = when (k) {
    NotificationKind.BOOKING_CARD, NotificationKind.BOOKING_ACCEPTED, NotificationKind.BOOKING_CHANGED -> Icons.Rounded.Movie
    NotificationKind.DAY_SHEET, NotificationKind.STILL_COMING -> Icons.Rounded.Schedule
    NotificationKind.WRAP_ITEM, NotificationKind.WRAP_OVERDUE -> Icons.Rounded.Payments
    NotificationKind.FEEDBACK_UNLOCKED -> Icons.Rounded.CheckCircle
    NotificationKind.WITHDRAWAL, NotificationKind.RELEASE -> Icons.Rounded.EventBusy
    NotificationKind.PROJECT_CHANGED -> Icons.Rounded.CalendarMonth
    NotificationKind.REPORT_UPDATE -> Icons.Rounded.Shield
    NotificationKind.APPLICATION_CLOSED, NotificationKind.APPLICATION_BACKUP, NotificationKind.APPLICATION_RECEIVED -> Icons.Rounded.Inbox
    NotificationKind.MATCH_CREATED, NotificationKind.ASK_RECEIVED, NotificationKind.MESSAGE -> Icons.Rounded.Campaign
    NotificationKind.SYSTEM -> Icons.Rounded.Info
}

/** Conversations that have ended: no composer, no actions. */
val ENDED_STATES = setOf(MatchState.CLOSED, MatchState.WITHDRAWN, MatchState.RELEASED, MatchState.NO_SHOW)
val ACTIVE_STATES = setOf(MatchState.TALKING, MatchState.OFFERED, MatchState.BOOKED)

// ───────────────────────── Small UI pieces ─────────────────────────

/** Darkroom-styled confirm dialog ("Block Rohan K.?"). [danger] colours the confirm action Safelight. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val c = Ct.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        titleContentColor = c.text,
        textContentColor = c.textSecondary,
        title = { Text(title, style = Ct.type.titleM) },
        text = { Text(body, style = Ct.type.bodyS) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirm, style = Ct.type.bodyStrong, color = if (danger) c.stop else c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ws1_cancel), style = Ct.type.bodyStrong, color = c.textSecondary)
            }
        },
    )
}

/** A tappable surface card row with an icon, title, body and optional trailing content. */
@Composable
fun SurfaceRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    tint: Color = Ct.colors.accent,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Ct.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(c.surface, CtShapes.field)
            .border(1.dp, c.outline.copy(alpha = 0.6f), CtShapes.field)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.bodyS.lineHeight), color = c.text)
            if (body != null) Text(body, style = Ct.type.bodyS, color = c.textSecondary)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box { trailing() }
        }
    }
}

/**
 * "Block Rohan K.?" (§5.5): hides both people from each other everywhere and ends active talks with a
 * neutral message. Calls the Repo itself, like [com.tff.app.ui.components.ReportSheet].
 */
@Composable
fun BlockDialog(name: String, profileId: String, nav: com.tff.app.nav.AppNavigator, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    ConfirmDialog(
        title = stringResource(R.string.ws6_block_title, name),
        body = stringResource(R.string.ws6_block_body),
        confirm = stringResource(R.string.ws1_safety_block),
        danger = true,
        onDismiss = onDismiss,
        onConfirm = {
            scope.launch {
                val r = runCatching { com.tff.app.di.AppContainer.repo.block(profileId) }.getOrElse { Result.failure(it) }
                r.onSuccess { nav.toast(res.getString(R.string.ws6_blocked_toast, name)); onDone() }
                    .onFailure { nav.toast(it.userMessage() ?: res.getString(R.string.ws6_generic_error)); onDismiss() }
            }
        },
    )
}

// ───────────────────────── Booking helpers ─────────────────────────

/** Plain words for BookingDiff keys ("v2 · changed: dates, pay"). */
fun changedFieldLabel(key: String): String = when (key) {
    "dates" -> "dates"
    "deliveryDate" -> "delivery date"
    "amount" -> "pay"
    "kitFeeDay" -> "kit fee"
    "paymentTerms" -> "payment terms"
    "perks" -> "perks"
    "meals" -> "meals"
    "transport" -> "transport"
    "callWindow" -> "call window"
    "creditTitle" -> "credit"
    "notes" -> "notes"
    "revisionRounds" -> "revision rounds"
    "pictureLockDate" -> "picture lock"
    else -> key
}

fun changedFieldsText(keys: Set<String>): String = keys.joinToString(", ") { changedFieldLabel(it) }

/** "Rohan K. · Sound Mixer · Filter Coffee, 4 AM": who, what, where, for the IN TALKS / BOOKED moments. */
fun momentSubtitle(v: com.tff.app.model.MatchView): String =
    "${v.counterpart.profile.shortName} · ${com.tff.app.domain.CardText.roleName(v.item.role)} · ${v.item.project.title}"

/**
 * The BOOKED overlay's typed line: "CALL SAT 14 NOV · 07:30" when a day sheet gives the call, else the
 * first shoot day ("FIRST DAY SAT 14 NOV"); post roles: "DELIVER BY 7 FEB".
 */
fun bookedCallLine(v: com.tff.app.model.MatchView, terms: com.tff.app.model.BookingTerms?): String? {
    val t = terms ?: v.acceptedCard?.terms ?: v.currentCard?.terms
    if (v.match.isPostRole || t?.isPostRole == true) {
        val d = t?.deliveryDate ?: v.item.role.postDeadline ?: return null
        return "DELIVER BY ${Dates.dayMonth(d).uppercase(Locale.ENGLISH)}"
    }
    val day = v.nextShootDay
    val call = v.myCallTime
    if (day != null && call != null) return "CALL ${Dates.shortUpper(day.day)} · ${Dates.time(call)}"
    val first = t?.dates?.minOrNull() ?: v.item.project.shootStart ?: return null
    return "FIRST DAY ${Dates.shortUpper(first)}"
}
