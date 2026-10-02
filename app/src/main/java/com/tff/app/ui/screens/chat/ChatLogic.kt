package com.tff.app.ui.screens.chat

import com.tff.app.R
import com.tff.app.core.Money
import com.tff.app.core.Dates
import com.tff.app.domain.CardText
import com.tff.app.domain.MessageSafety
import com.tff.app.domain.TextSpan
import com.tff.app.model.BookingCard
import com.tff.app.model.BookingStatus
import com.tff.app.model.BookingTerms
import com.tff.app.model.CompType
import com.tff.app.model.Match
import com.tff.app.model.MatchState
import com.tff.app.model.Message
import com.tff.app.model.MessageFlag
import com.tff.app.model.Report
import java.time.LocalDate
import java.util.Locale

// Pure chat rules (no Compose), so the safety behaviour is unit-tested.

// ───────────────────────── Names ─────────────────────────

/** Words that mark an organisation-style handle. "Casting Hub Official" must not become "Casting O.", which reads as a person. */
private val ORG_WORDS = setOf(
    "official", "casting", "castings", "productions", "production", "films", "pictures", "studios", "studio",
    "media", "entertainment", "agency", "talent", "pvt", "ltd", "llc", "inc",
)

/** True when [name] looks like a company or agency handle rather than a person's name. */
fun isOrgName(name: String): Boolean =
    name.trim().split(Regex("\\s+")).any { it.lowercase(Locale.ENGLISH).trim('.', ',', '(', ')') in ORG_WORDS }

/** People get "Meera K."; organisation-style handles are shown in full so the impersonation cue stays visible. */
fun displayName(fullName: String, shortName: String): String = if (isOrgName(fullName)) fullName.trim() else shortName

// ───────────────────────── Thread safety ─────────────────────────

/** The flags that switch a chat into its safety-first mode. CONTACT and OFF_PLATFORM only get the soft banner. */
val RED_FLAGS = setOf(MessageFlag.MONEY_FROM_TALENT, MessageFlag.BODY_REQUEST, MessageFlag.PRIVATE_MEETING)

/**
 * Red flags raised by the OTHER person in this thread (the viewer's own messages never count).
 * Money only counts from the owner side, the same direction rule as the banners (§5.2): a collaborator
 * asking "paid via UPI?" is not a fee request.
 */
fun threadRedFlags(messages: List<Message>, match: Match, me: String?): Set<MessageFlag> {
    val out = mutableSetOf<MessageFlag>()
    for (m in messages) out += redFlagsOf(m, match, me)
    return out
}

/** The latest message from the other person that carries a red flag (the target of the Report quick reply). */
fun latestRedFlagMessage(messages: List<Message>, match: Match, me: String?): Message? =
    messages.lastOrNull { redFlagsOf(it, match, me).isNotEmpty() }

private fun redFlagsOf(m: Message, match: Match, me: String?): Set<MessageFlag> {
    val sender = m.senderId ?: return emptySet()
    if (me != null && sender == me) return emptySet()
    val flags = m.flags.ifEmpty { MessageSafety.analyze(m.body) }
    val ownerSide = sender == match.ownerId || sender != match.collaboratorId
    return buildSet {
        if (MessageFlag.MONEY_FROM_TALENT in flags && ownerSide) add(MessageFlag.MONEY_FROM_TALENT)
        if (MessageFlag.BODY_REQUEST in flags) add(MessageFlag.BODY_REQUEST)
        if (MessageFlag.PRIVATE_MEETING in flags) add(MessageFlag.PRIVATE_MEETING)
    }
}

/** The viewer's report on this conversation: on the person, the match, or any message in it. */
fun reportForThread(reports: List<Report>, counterpartId: String, matchId: String, messageIds: Set<String>): Report? =
    reports.firstOrNull { it.targetId == counterpartId || it.targetId == matchId || it.targetId in messageIds }

// ───────────────────────── Quick replies ─────────────────────────

enum class QuickReplyKind { DRAFT, REPORT, BLOCK }

/** One chip: [DRAFT] fills the composer with [text]; [REPORT] and [BLOCK] open those sheets. */
data class QuickReplySpec(val text: Int, val kind: QuickReplyKind = QuickReplyKind.DRAFT)

data class QuickReplyContext(
    val isOwner: Boolean,
    val state: MatchState,
    val post: Boolean,
    val isCast: Boolean = false,
    val redFlags: Set<MessageFlag> = emptySet(),
    val reported: Boolean = false,
    val shootToday: Boolean = false,
    val daySheetOut: Boolean = false,
)

/**
 * Quick replies for the composer, by state. In a thread where the other person asked for money, body
 * photos or a private meeting, the affirmative chips ("Yes, I'm in") are never offered: the chips become
 * safety-first answers plus Report (Block once reported).
 */
fun quickReplySpecs(ctx: QuickReplyContext): List<QuickReplySpec> {
    fun d(vararg ids: Int) = ids.map { QuickReplySpec(it) }
    val s = ctx.state
    if (s in ENDED_STATES) return emptyList()
    val flags = ctx.redFlags
    if (flags.isNotEmpty()) {
        val out = mutableListOf<QuickReplySpec>()
        if (MessageFlag.MONEY_FROM_TALENT in flags) {
            out += QuickReplySpec(if (ctx.isCast) R.string.ws6c_qr_no_fee_cast else R.string.ws6c_qr_no_fee_crew)
        }
        if (MessageFlag.BODY_REQUEST in flags) out += QuickReplySpec(R.string.ws6c_qr_no_body)
        out += if (ctx.reported) QuickReplySpec(R.string.ws6c_qr_block, QuickReplyKind.BLOCK)
        else QuickReplySpec(R.string.ws6c_qr_report, QuickReplyKind.REPORT)
        if (MessageFlag.PRIVATE_MEETING in flags) out += QuickReplySpec(R.string.ws6c_qr_public_audition)
        out += QuickReplySpec(R.string.ws6c_qr_keep_here)
        return out
    }
    val booked = s == MatchState.BOOKED
    return when {
        s == MatchState.WRAPPED -> d(R.string.ws6_qr_thanks_wrap, R.string.ws6_qr_work_again)
        booked && ctx.post && ctx.isOwner -> d(R.string.ws6_qr_owner_lock_on_track, R.string.ws6_qr_owner_notes_soon, R.string.ws6_qr_owner_questions_any)
        booked && ctx.post -> d(R.string.ws6_qr_got_it, R.string.ws6_qr_delivery_soon, R.string.ws6_qr_question)
        // On set: "running late" only on a shoot day; "got the day sheet" only once one is out.
        booked && ctx.isOwner && ctx.shootToday -> d(R.string.ws6_qr_see_you, R.string.ws6_qr_late)
        booked && ctx.isOwner && ctx.daySheetOut -> d(R.string.ws6_qr_owner_sheet_out, R.string.ws6_qr_owner_questions_any)
        booked && ctx.isOwner -> d(R.string.ws6c_qr_owner_sheet_soon, R.string.ws6_qr_owner_questions_any)
        booked && ctx.shootToday -> d(R.string.ws6c_qr_on_my_way, R.string.ws6_qr_late, R.string.ws6_qr_see_you)
        booked && ctx.daySheetOut -> d(R.string.ws6_qr_got_sheet, R.string.ws6c_qr_bring, R.string.ws6c_qr_parking)
        booked -> d(R.string.ws6c_qr_bring, R.string.ws6c_qr_parking, R.string.ws6c_qr_sheet_when)
        s == MatchState.OFFERED && ctx.isOwner -> d(R.string.ws6c_qr_owner_card_sent, R.string.ws6_qr_owner_questions)
        s == MatchState.OFFERED && ctx.post -> d(R.string.ws6c_qr_reviewing, R.string.ws6_qr_spec, R.string.ws6_qr_cant_deadline)
        s == MatchState.OFFERED -> d(R.string.ws6c_qr_reviewing, R.string.ws6_qr_call_times, R.string.ws6_qr_cant_dates)
        ctx.isOwner -> d(R.string.ws6_qr_owner_dates, R.string.ws6_qr_owner_card, R.string.ws6_qr_owner_questions)
        ctx.post -> d(R.string.ws6_qr_in, R.string.ws6_qr_spec, R.string.ws6_qr_cant_deadline, R.string.ws6_qr_send_card)
        else -> d(R.string.ws6_qr_in, R.string.ws6_qr_call_times, R.string.ws6_qr_cant_dates, R.string.ws6_qr_send_card)
    }
}

/** True when [today] is one of the booked shoot days (accepted card dates, or the next published day sheet). */
fun isShootDay(today: LocalDate, cardDates: Collection<LocalDate>, nextSheetDay: LocalDate?): Boolean =
    today in cardDates || nextSheetDay == today

// ───────────────────────── Pinned bar ─────────────────────────

/** "$2,400 flat · deliver by 4 Nov", "₹2,500/day · Sat 14 & Sun 15 Nov": the short offer line for the pinned bar. */
fun offerShortLine(t: BookingTerms): String {
    val pay = when (t.compType) {
        CompType.PAID_DAY -> t.amount?.let { "${Money.format(it, t.currency)}/day" } ?: "Paid"
        CompType.PAID_FLAT -> t.amount?.let { "${Money.format(it, t.currency)} flat" } ?: "Paid"
        CompType.STIPEND -> t.amount?.let { if (t.isPostRole) "Stipend ${Money.format(it, t.currency)}" else "Stipend ${Money.format(it, t.currency)}/day" } ?: "Stipend"
        CompType.UNPAID -> "Unpaid"
    }
    val whenText = if (t.isPostRole) t.deliveryDate?.let { "deliver by ${Dates.dayMonth(it)}" }
    else CardText.dateList(t.dates).takeIf { it.isNotBlank() }
    return listOfNotNull(pay, whenText).joinToString(" · ")
}

// ───────────────────────── Booking card bubble ─────────────────────────

private const val BOOKED_LEAD = "You're booked as "

/**
 * The lead of the in-chat booking card bubble, by the card's state and who is reading:
 * an offer that is only SENT never says "You're booked as…".
 *  - recipient: "If you accept, you're booked as" · a change to an existing booking: "If you accept this version,
 *    you're booked as" · ACCEPTED: "You're booked as"
 *  - sender (the production): "If they accept, they're booked as" · "If they accept this version, …" · "Booked as"
 *  - declined, replaced or cancelled versions: "Offered as"
 */
fun bookingBubbleLead(status: BookingStatus, changeToBooking: Boolean, senderView: Boolean): String = when (status) {
    BookingStatus.SENT -> when {
        senderView && changeToBooking -> "If they accept this version, they're booked as "
        senderView -> "If they accept, they're booked as "
        changeToBooking -> "If you accept this version, you're booked as "
        else -> "If you accept, you're booked as "
    }
    BookingStatus.ACCEPTED -> if (senderView) "Booked as " else BOOKED_LEAD
    BookingStatus.DECLINED, BookingStatus.SUPERSEDED, BookingStatus.CANCELLED -> "Offered as "
}

/** [CardText.bookingSummarySpans] with the state-aware lead from [bookingBubbleLead]. */
fun bookingBubbleSpans(card: BookingCard, changeToBooking: Boolean, senderView: Boolean): List<TextSpan> {
    val spans = CardText.bookingSummarySpans(card.terms)
    val lead = bookingBubbleLead(card.status, changeToBooking, senderView)
    return if (spans.firstOrNull()?.text == BOOKED_LEAD) listOf(spans.first().copy(text = lead)) + spans.drop(1) else spans
}
