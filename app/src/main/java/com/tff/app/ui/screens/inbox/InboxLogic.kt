package com.tff.app.ui.screens.inbox

import com.tff.app.model.AppNotification
import com.tff.app.model.ConversationRow
import com.tff.app.model.Inbox
import com.tff.app.model.MatchState
import com.tff.app.model.Message
import com.tff.app.model.MessageFlag
import com.tff.app.model.NotificationKind
import com.tff.app.model.Report
import com.tff.app.ui.screens.chat.ENDED_STATES
import com.tff.app.ui.screens.chat.reportForThread
import com.tff.app.ui.screens.chat.threadRedFlags

// Pure Inbox rules (no Compose): flagged rows, "Needs you", ordering. Unit-tested.

/** A conversation's safety marker in the list: the worst red flag the other person raised, and the viewer's report. */
data class RowSafety(val flag: MessageFlag?, val report: Report?) {
    val marked: Boolean get() = flag != null || report != null
}

enum class NeedsKind { OFFER, CHANGE, NOTIFICATION }

/** One "Needs you" row: a booking card waiting on the viewer (from the match state) or an actionable update. */
data class NeedsYouItem(
    val kind: NeedsKind,
    val conversation: ConversationRow? = null,
    val notification: AppNotification? = null,
) {
    val key: String get() = conversation?.let { "nm_${it.match.id}" } ?: "nn_${notification?.id}"
}

data class InboxUi(
    val inbox: Inbox,
    val needsYou: List<NeedsYouItem> = emptyList(),
    val conversations: List<ConversationRow> = inbox.conversations,
    val updates: List<AppNotification> = inbox.updates,
    val safety: Map<String, RowSafety> = emptyMap(),
) {
    /** Things waiting on the viewer: asks plus Needs-you rows. Drives the "2 to do" subtitle. */
    val todo: Int get() = inbox.asks.size + needsYou.size
    val unread: Int get() = inbox.conversations.sumOf { it.unread }
    val isEmpty: Boolean get() = inbox.asks.isEmpty() && inbox.conversations.isEmpty() && inbox.updates.isEmpty()
}

/** Unread updates that ask the viewer to do something (confirm pay, answer "still coming?", review a card…). */
val ACTIONABLE_KINDS = setOf(
    NotificationKind.BOOKING_CARD, NotificationKind.BOOKING_CHANGED, NotificationKind.DAY_SHEET, NotificationKind.STILL_COMING,
    NotificationKind.WRAP_ITEM, NotificationKind.WRAP_OVERDUE, NotificationKind.FEEDBACK_UNLOCKED,
)

/** "Needs you" order: money and bookings before day sheets, feedback last. */
private val NEEDS_ORDER = listOf(
    NotificationKind.WRAP_ITEM, NotificationKind.BOOKING_CARD, NotificationKind.BOOKING_CHANGED, NotificationKind.STILL_COMING,
    NotificationKind.WRAP_OVERDUE, NotificationKind.DAY_SHEET, NotificationKind.FEEDBACK_UNLOCKED,
)

/** Severity order for the row marker: a fee request reads first. */
private val FLAG_ORDER = listOf(MessageFlag.MONEY_FROM_TALENT, MessageFlag.BODY_REQUEST, MessageFlag.PRIVATE_MEETING)

fun counterpartIdOf(row: ConversationRow): String = if (row.isOwner) row.match.collaboratorId else row.match.ownerId

private fun matchIdOfLink(link: String?): String? = link?.takeIf { it.startsWith("calltime://m/") }?.removePrefix("calltime://m/")
private fun matchIdOfWrapLink(link: String?): String? = link?.takeIf { it.startsWith("calltime://wrap/m/") }?.removePrefix("calltime://wrap/m/")

/**
 * An overdue wrap item is the viewer's to-do only when the viewer owes it: DELIVERY is owed by the
 * collaborator, everything else (pay, footage, credit, final film) by the production. The side that is
 * owed gets "we've nudged them", which is news, not a task. Unknown conversation: treat as news.
 */
private fun overdueIsMine(n: AppNotification, row: ConversationRow?): Boolean {
    row ?: return false
    val deliveryOwedByCollab = n.title.trim().startsWith("Delivery", ignoreCase = true)
    return if (deliveryOwedByCollab) !row.isOwner else row.isOwner
}

/**
 * Builds what the Inbox shows. [threads] holds the messages of the live conversations (matchId → messages);
 * [reports] are the viewer's own reports.
 *  - Rows whose thread has a red flag from the other person, or that the viewer reported, carry a marker.
 *  - "Needs you": booking cards waiting on the viewer (never from a flagged thread), then unread actionable updates.
 *  - Conversations: live first, then WRAPPED, then ended; newest first within each group.
 *  - Updates: everything not already in "Needs you".
 */
fun buildInboxUi(inbox: Inbox, threads: Map<String, List<Message>>, reports: List<Report>, me: String?): InboxUi {
    val safety = inbox.conversations.associate { row ->
        val msgs = threads[row.match.id].orEmpty()
        val flags = threadRedFlags(msgs, row.match, me)
        val report = reportForThread(reports, counterpartIdOf(row), row.match.id, msgs.mapTo(HashSet()) { it.id })
        row.match.id to RowSafety(FLAG_ORDER.firstOrNull { it in flags }, report)
    }
    val flaggedIds = safety.filterValues { it.marked }.keys

    val fromState = inbox.conversations.filter { it.match.id !in flaggedIds && !it.isOwner }.mapNotNull { row ->
        when {
            row.match.state == MatchState.OFFERED -> NeedsYouItem(NeedsKind.OFFER, conversation = row)
            row.match.state == MatchState.BOOKED && row.match.changePending -> NeedsYouItem(NeedsKind.CHANGE, conversation = row)
            else -> null
        }
    }
    val coveredMatches = fromState.mapNotNull { it.conversation?.match?.id }.toSet()
    val rowsByMatch = inbox.conversations.associateBy { it.match.id }
    // The same update repeated (a seeded reminder plus the daily one) shows once: the newest copy.
    val superseded = inbox.updates.sortedByDescending { it.createdAt }
        .groupBy { Triple(it.kind, it.deepLink, it.title) }.values.flatMap { it.drop(1) }.mapTo(HashSet()) { it.id }
    val coveredByCard = { n: AppNotification ->
        n.readAt == null && matchIdOfLink(n.deepLink) in coveredMatches &&
            (n.kind == NotificationKind.BOOKING_CARD || n.kind == NotificationKind.BOOKING_CHANGED)
    }
    val fromUpdates = inbox.updates.filter { n ->
        val mid = matchIdOfLink(n.deepLink) ?: matchIdOfWrapLink(n.deepLink)
        n.readAt == null && n.kind in ACTIONABLE_KINDS && n.id !in superseded && mid !in flaggedIds && !coveredByCard(n) &&
            (n.kind != NotificationKind.WRAP_OVERDUE || overdueIsMine(n, mid?.let(rowsByMatch::get)))
    }.sortedBy { NEEDS_ORDER.indexOf(it.kind).let { i -> if (i < 0) NEEDS_ORDER.size else i } }      // stable: newest first within a kind
        .map { NeedsYouItem(NeedsKind.NOTIFICATION, notification = it) }
    val needs = fromState + fromUpdates
    val inNeeds = fromUpdates.mapNotNull { it.notification?.id }.toSet()
    // Booking-card updates that the state-based row already covers are dropped from Updates too.
    val updates = inbox.updates.filter { n -> n.id !in inNeeds && n.id !in superseded && !coveredByCard(n) }

    val conversations = inbox.conversations.sortedWith(
        compareBy<ConversationRow> { r ->
            when (r.match.state) {
                in ENDED_STATES -> 2
                MatchState.WRAPPED -> 1
                else -> 0
            }
        }.thenByDescending { it.lastAt }
    )
    return InboxUi(inbox, needs, conversations, updates, safety)
}
