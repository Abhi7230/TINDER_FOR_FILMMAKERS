package com.tff.app.domain

import com.tff.app.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Facts about one booking (a match that reached BOOKED at some point), for aggregate reputation. */
data class BookingFact(
    val match: Match,
    val terms: BookingTerms?,                 // accepted terms (null if never accepted)
    val attendance: List<Attendance>,         // per shoot day for the collaborator
    val wrapItems: List<WrapItem>,
    val cutLate: Boolean = false,             // a version cutting days/pay was sent within 72h of day 1 / delivery
    val disputed: Boolean = false,
)

/** PRD F14. Ratios are null when n < 3. Withdrawals with ≥72h notice and disputes are excluded. */
object ReputationCalc {
    const val MIN_N = 3
    const val NEW_ON_CALLTIME = "New on Calltime"

    fun forProfile(
        profileId: String,
        facts: List<BookingFact>,
        applicationsClosedIn14d: Ratio?,       // owner side "closes the loop"
        replyHoursMedian: Int?,
        confirmedCredits: Int,
        today: LocalDate,
    ): Reputation = forProfile(profileId, facts, applicationsClosedIn14d, replyHoursMedian, confirmedCredits, today, ZoneId.systemDefault())

    /** As [forProfile] with an explicit zone for turning "marked at" timestamps into dates. */
    fun forProfile(
        profileId: String,
        facts: List<BookingFact>,
        applicationsClosedIn14d: Ratio?,
        replyHoursMedian: Int?,
        confirmedCredits: Int,
        today: LocalDate,
        zone: ZoneId,
    ): Reputation {
        val valid = facts.filter { !it.disputed }.distinctBy { it.match.id }
        val asCollab = valid.filter { it.match.collaboratorId == profileId }
        val asOwner = valid.filter { it.match.ownerId == profileId }

        // ── Collaborator side ──
        var showNum = 0; var showDen = 0
        var delNum = 0; var delDen = 0
        for (f in asCollab) {
            val m = f.match
            val post = m.isPostRole || f.terms?.isPostRole == true
            if (m.state == MatchState.WITHDRAWN) {
                // Neutral notice is excluded; a late withdrawal is a miss.
                val hours = m.noticeHours ?: continue
                if (Notice.isNeutral(post, hours)) continue
                if (post) delDen++ else showDen++
                continue
            }
            if (m.state == MatchState.RELEASED) continue          // the owner's call, not the collaborator's
            if (Attendance.DISPUTED in f.attendance) continue
            if (post) {
                val item = f.wrapItems.firstOrNull { it.kind == WrapKind.DELIVERY } ?: continue
                when (deliveryOutcome(item, today, zone)) {
                    true -> { delNum++; delDen++ }
                    false -> delDen++
                    null -> {}
                }
            } else {
                val happened = m.state == MatchState.WRAPPED || m.state == MatchState.NO_SHOW ||
                    f.attendance.any { it == Attendance.ATTENDED || it == Attendance.NO_SHOW }
                if (!happened) continue
                val noShow = m.state == MatchState.NO_SHOW || Attendance.NO_SHOW in f.attendance
                showDen++
                if (!noShow) showNum++
            }
        }

        // ── Owner side ──
        var paidNum = 0; var paidDen = 0
        var footNum = 0; var footDen = 0
        var keptNum = 0; var keptDen = 0
        for (f in asOwner) {
            val m = f.match
            for (item in f.wrapItems) {
                when (item.kind) {
                    WrapKind.PAID -> when (onTimeOutcome(item, today, zone)) {
                        true -> { paidNum++; paidDen++ }
                        false -> paidDen++
                        null -> {}
                    }
                    WrapKind.FOOTAGE -> when (deliveredOutcome(item, today)) {
                        true -> { footNum++; footDen++ }
                        false -> footDen++
                        null -> {}
                    }
                    else -> {}
                }
            }
            // Kept bookings: late release / cancellation or a late cut breaks it; ≥72h releases and
            // the collaborator's own withdrawals are neutral; ongoing bookings count once they're done.
            val lateRelease = m.state == MatchState.RELEASED && (m.noticeHours ?: 0) < Notice.ON_SET_NEUTRAL_HOURS
            when {
                f.cutLate || lateRelease -> keptDen++
                m.state == MatchState.RELEASED || m.state == MatchState.WITHDRAWN -> {}
                m.state == MatchState.WRAPPED || m.state == MatchState.NO_SHOW -> { keptNum++; keptDen++ }
                else -> {}
            }
        }

        val shoots = valid.filter { it.match.state == MatchState.WRAPPED &&
            (it.match.collaboratorId == profileId || it.match.ownerId == profileId) }
            .map { it.match.projectId }.distinct().size

        return Reputation(
            profileId = profileId,
            shootsCompleted = shoots,
            showedUp = ratio(showNum, showDen),
            deliveredOnTime = ratio(delNum, delDen),
            paidOnTime = ratio(paidNum, paidDen),
            footageDelivered = ratio(footNum, footDen),
            keptBookings = ratio(keptNum, keptDen),
            closesLoop = applicationsClosedIn14d?.takeIf { it.den >= MIN_N },
            replyHoursMedian = replyHoursMedian,
            confirmedCredits = confirmedCredits.coerceAtLeast(0),
        )
    }

    private fun ratio(num: Int, den: Int): Ratio? = if (den >= MIN_N) Ratio(num, den) else null

    private fun markedDate(item: WrapItem, zone: ZoneId): LocalDate? =
        item.markedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    /** Paid on time: true = marked by the due date and not disputed as not received; null = not due yet. */
    private fun onTimeOutcome(item: WrapItem, today: LocalDate, zone: ZoneId): Boolean? {
        val due = item.dueDate
        val marked = markedDate(item, zone)
        return when (item.status) {
            WrapStatus.CONFIRMED, WrapStatus.DONE -> if (due == null || marked == null) true else !marked.isAfter(due)
            WrapStatus.NOT_RECEIVED, WrapStatus.OPEN -> if (due != null && today.isAfter(due)) false else null
        }
    }

    /** Footage delivered: true once marked (late still counts as delivered); false only once overdue. */
    private fun deliveredOutcome(item: WrapItem, today: LocalDate): Boolean? = when (item.status) {
        WrapStatus.CONFIRMED, WrapStatus.DONE -> true
        WrapStatus.NOT_RECEIVED, WrapStatus.OPEN -> if (item.dueDate != null && today.isAfter(item.dueDate)) false else null
    }

    /** Delivered on time (post roles): the collaborator's DELIVERY marked by its due date. */
    private fun deliveryOutcome(item: WrapItem, today: LocalDate, zone: ZoneId): Boolean? = when (item.status) {
        WrapStatus.CONFIRMED -> {
            val due = item.dueDate
            val marked = markedDate(item, zone)
            if (due == null || marked == null) true else !marked.isAfter(due)
        }
        // Marked but not confirmed yet: judge once the owner answers (or the 7-day auto-confirm runs).
        WrapStatus.DONE -> null
        WrapStatus.NOT_RECEIVED, WrapStatus.OPEN -> if (item.dueDate != null && today.isAfter(item.dueDate)) false else null
    }

    /** The one chip a card shows: the WEAKER of the owner's footage/kept ratios, else "New on Calltime". */
    fun headlineChip(r: Reputation, asOwner: Boolean): String {
        // Owners: the weakest of Footage / Kept bookings / Paid on time, so a card never hides a bad number
        // (a paid-only mixer must see "Paid on time 2/4" even when Kept bookings is 4/4).
        val candidates: List<Pair<String, Ratio>> = if (asOwner) {
            listOfNotNull(
                r.footageDelivered?.let { "Footage $it" to it },
                r.keptBookings?.let { "Kept bookings $it" to it },
                r.paidOnTime?.let { "Paid on time $it" to it },
            )
        } else {
            listOfNotNull(r.showedUp?.let { "Showed up $it" to it }, r.deliveredOnTime?.let { "Delivered on time $it" to it })
        }
        // minBy keeps the first on ties, so Footage wins a tie with Kept bookings.
        candidates.minByOrNull { it.second.value }?.let { return it.first }
        // No aggregate with n ≥ 3 yet. "New on Calltime" only while someone has fewer than 3 shoots; after that
        // the "N shoots done" chip speaks for itself (empty = no headline chip), so the card never contradicts itself.
        return if (showsNew(r)) NEW_ON_CALLTIME else ""
    }

    /** "New on Calltime" applies while no aggregate has n ≥ 3 AND fewer than 3 shoots are done. */
    fun showsNew(r: Reputation): Boolean = r.isNew && r.shootsCompleted < 3

    /** Chips for detail views ("Paid on time 11/12", "Kept bookings 6/6", "Replies in ~1 day"). */
    fun chips(r: Reputation, asOwner: Boolean): List<String> {
        val out = mutableListOf<String>()
        if (asOwner) {
            r.paidOnTime?.let { out += "Paid on time $it" }
            r.footageDelivered?.let { out += "Footage delivered $it" }
            r.keptBookings?.let { out += "Kept bookings $it" }
        } else {
            r.showedUp?.let { out += "Showed up $it" }
            r.deliveredOnTime?.let { out += "Delivered on time $it" }
        }
        if (r.shootsCompleted > 0) out += if (r.shootsCompleted == 1) "1 shoot done" else "${r.shootsCompleted} shoots done"
        if (asOwner) {
            r.replyHoursMedian?.let { out += "Replies in ${CardText.replyTime(it)}" }
            r.closesLoop?.let { out += "Closes the loop ${(it.value * 100).roundToInt()}%" }
        } else if (r.confirmedCredits > 0) {
            out += if (r.confirmedCredits == 1) "1 confirmed credit" else "${r.confirmedCredits} confirmed credits"
        }
        if (showsNew(r)) out.add(0, NEW_ON_CALLTIME)
        return out
    }

    /** TalkBack description for a ratio chip ("Paid on time 11 of 12 projects"). */
    fun describe(label: String, r: Ratio, unit: String = "bookings"): String = "$label ${r.num} of ${r.den} $unit"
}
