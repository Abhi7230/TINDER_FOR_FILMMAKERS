package com.tff.app.ui.screens.booking

import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.domain.SpanStyleKind
import com.tff.app.domain.TextSpan
import com.tff.app.model.BookingStatus
import com.tff.app.model.BookingTerms
import com.tff.app.model.CompType
import com.tff.app.model.ContentFlag
import com.tff.app.model.Project
import com.tff.app.model.ProjectRole
import com.tff.app.model.ShootDay
import com.tff.app.model.Taxonomy
import com.tff.app.model.WrapItem
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure copy helpers for the booking card, shoot day and wrap screens (unit-tested in Ws6eCopyTest).
 * They adapt [CardText] output on the screen side, so the summary never reads "You're booked as…"
 * before the card is accepted and the role reads the way the listing defines it.
 */
object BookingCopy {
    /** The first clause [CardText.bookingSummarySpans] starts with. */
    const val BOOKED_PREFIX = "You're booked as "

    /** First clause of the summary for one version: only an ACCEPTED card says "You're booked as". */
    fun summaryPrefix(status: BookingStatus?, rebooking: Boolean): String = when (status) {
        BookingStatus.ACCEPTED -> BOOKED_PREFIX
        null, BookingStatus.SENT -> if (rebooking) "If you accept this version, you're booked as " else "If you accept, you're booked as "
        BookingStatus.DECLINED, BookingStatus.SUPERSEDED, BookingStatus.CANCELLED -> "This version: booked as "
    }

    /** Character name from the listing's role details (cast roles only). */
    fun character(role: ProjectRole?): String? =
        role?.takeIf { Taxonomy.isCast(it.roleCode) }?.roleDetails?.get("character")?.trim()?.takeIf { it.isNotBlank() }

    /** The role as the project defines it ("Lead Actor"), falling back to the plain role name. */
    fun roleName(t: BookingTerms, role: ProjectRole?): String =
        if (role != null && role.roleCode == t.roleCode) CardText.roleName(role) else CardText.roleName(t.roleCode)

    /** "Lead Actor · playing 'Asha, the pickpocket'" for the Role row. */
    fun roleLine(t: BookingTerms, role: ProjectRole?): String {
        val ch = if (role?.roleCode == t.roleCode) character(role) else null
        return roleName(t, role) + (ch?.let { " · playing '$it'" } ?: "")
    }

    /**
     * [CardText.bookingSummarySpans] with a state-aware first clause and the listing's role
     * ("If you accept, you're booked as **Lead Actor** playing 'Asha' (credit: 'Cast') on *Last Local* · …").
     * Leaves the spans alone when CardText's wording has changed under it.
     */
    fun summarySpans(t: BookingTerms, status: BookingStatus?, rebooking: Boolean, role: ProjectRole?): List<TextSpan> {
        val spans = CardText.bookingSummarySpans(t).toMutableList()
        if (spans.isNotEmpty() && spans[0].style == SpanStyleKind.NORMAL && spans[0].text == BOOKED_PREFIX) {
            spans[0] = TextSpan(summaryPrefix(status, rebooking))
        }
        if (role != null && role.roleCode == t.roleCode && spans.size > 1 && spans[1].style == SpanStyleKind.BOLD &&
            spans[1].text == CardText.roleName(t.roleCode)
        ) {
            spans[1] = TextSpan(roleName(t, role), SpanStyleKind.BOLD)
            character(role)?.let { spans.add(2, TextSpan(" playing '$it'")) }
        }
        return spans
    }

    /**
     * A call window to pre-fill when the draft has none: the first published day sheet's general call and
     * wrap, else a night window for night shoots, else a day from the project's hours cap. Null = nothing to go on.
     */
    fun suggestCallWindow(project: Project, days: List<ShootDay>): String? {
        val first = days.filter { it.cancelledAt == null }.minByOrNull { it.day }
        if (first != null) return "~${Dates.time(first.generalCall)}–${Dates.time(first.wrapBy)}"
        val cap = project.hoursCap?.takeIf { it in 1..20 }
        if (ContentFlag.NIGHT_SHOOT in project.contentFlags) {
            val end = (20 + (cap ?: 10)) % 24
            return "~20:00–%02d:00".format(end)
        }
        if (cap != null) return "~07:00–%02d:00".format((7 + cap).coerceAtMost(23))
        return null
    }

    /**
     * What the production owes for PAY, from the accepted terms: "₹7,000 · 2 days × ₹2,500 + kit ₹1,000 × 2".
     * Null for unpaid terms or when no amount was agreed.
     */
    fun payOwed(t: BookingTerms): String? {
        if (t.compType == CompType.UNPAID) return null
        val a = t.amount?.takeIf { it > 0 } ?: return null
        val days = if (t.isPostRole) 1 else maxOf(1, t.dates.distinct().size)
        val perDay = !t.isPostRole && (t.compType == CompType.PAID_DAY || t.compType == CompType.STIPEND)
        val kit = if (t.isPostRole) 0 else (t.kitFeeDay ?: 0)
        val total = (if (perDay) a.toLong() * days else a.toLong()) + kit.toLong() * days
        fun f(n: Long) = Money.format(n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), t.currency)
        val parts = mutableListOf<String>()
        if (perDay) parts += if (days == 1 && kit == 0) "1 day" else "$days ${if (days == 1) "day" else "days"} × ${f(a.toLong())}"
        else if (kit > 0) parts += "${f(a.toLong())} flat" else parts += "flat"
        if (kit > 0) parts += "kit ${f(kit.toLong())} × $days"
        return "${f(total)} · ${parts.joinToString(" + ")}"
    }

    /** Whole days past the due date (0 when not overdue or undated). */
    fun daysOverdue(item: WrapItem, today: LocalDate): Int {
        val due = item.dueDate ?: return 0
        return if (today.isAfter(due)) Dates.daysBetween(due, today).toInt() else 0
    }

    /** Wraps at 22:00 or later, or after midnight: people need a way home. */
    fun isLateWrap(day: ShootDay): Boolean =
        !day.wrapBy.isBefore(LocalTime.of(22, 0)) || day.wrapBy.isBefore(day.generalCall) || day.wrapBy.isBefore(LocalTime.of(5, 0))

    /** Reminder cooldown on the wrap checklist. */
    const val REMIND_EVERY_MS = 7L * 24 * 3600_000
}
