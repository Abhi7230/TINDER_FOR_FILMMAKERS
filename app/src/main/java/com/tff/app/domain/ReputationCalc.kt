package com.tff.app.domain

import com.tff.app.model.*
import java.time.LocalDate

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
    fun forProfile(
        profileId: String,
        facts: List<BookingFact>,
        applicationsClosedIn14d: Ratio?,       // owner side "closes the loop"
        replyHoursMedian: Int?,
        confirmedCredits: Int,
        today: LocalDate,
    ): Reputation = TODO()

    /** The one chip a card shows: the WEAKER of the owner's footage/kept ratios, else "New on Calltime". */
    fun headlineChip(r: Reputation, asOwner: Boolean): String = TODO()
    /** Chips for detail views ("Paid on time 11/12", "Kept bookings 6/6", "Replies in ~1 day"). */
    fun chips(r: Reputation, asOwner: Boolean): List<String> = TODO()
}
