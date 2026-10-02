package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReputationCalcTest {

    private val today = Fx.TODAY
    private val zone = Fx.ZONE
    private val owner = "u_rohan"
    private val collab = "u_meera"
    private var n = 0

    private fun millis(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun item(kind: WrapKind, due: LocalDate, status: WrapStatus = WrapStatus.CONFIRMED, markedOn: LocalDate? = due,
                     owedBy: OwedBy = if (kind == WrapKind.DELIVERY) OwedBy.COLLABORATOR else OwedBy.PRODUCTION) =
        WrapItem("w_${n++}", "m", kind, due, owedBy, status, markedAt = markedOn?.let(::millis))

    private fun fact(
        state: MatchState = MatchState.WRAPPED,
        post: Boolean = false,
        notice: Int? = null,
        released: Boolean = false,
        items: List<WrapItem> = emptyList(),
        attendance: List<Attendance> = if (post) emptyList() else listOf(Attendance.ATTENDED),
        cutLate: Boolean = false,
        disputed: Boolean = false,
        project: String = "p_${n}",
    ): BookingFact {
        val id = "m_${n++}"
        return BookingFact(
            match = Fx.match(id = id, ownerId = owner, collaboratorId = collab, state = state, isPostRole = post,
                noticeHours = notice, releasedByOwner = released).copy(projectId = project),
            terms = null, attendance = attendance, wrapItems = items, cutLate = cutLate, disputed = disputed,
        )
    }

    private fun rep(id: String, facts: List<BookingFact>, closes: Ratio? = null) =
        ReputationCalc.forProfile(id, facts, closes, replyHoursMedian = 20, confirmedCredits = 2, today = today, zone = zone)

    @Test fun fewerThanThreeDataPointsGiveNullRatios() {
        val r = rep(collab, listOf(fact(), fact()))
        assertNull(r.showedUp)
        assertEquals(2, r.shootsCompleted)
        assertTrue(r.isNew)
        assertEquals(ReputationCalc.NEW_ON_CALLTIME, ReputationCalc.headlineChip(r, asOwner = false))
        val three = rep(collab, listOf(fact(), fact(), fact()))
        assertEquals(Ratio(3, 3), three.showedUp)
        assertEquals("Showed up 3/3", ReputationCalc.headlineChip(three, asOwner = false))
    }

    @Test fun withdrawalsWithEnoughNoticeAreExcluded() {
        val base = listOf(fact(), fact(), fact())
        assertEquals(Ratio(3, 3), rep(collab, base + fact(state = MatchState.WITHDRAWN, notice = 100)).showedUp)
        assertEquals(Ratio(3, 4), rep(collab, base + fact(state = MatchState.WITHDRAWN, notice = 24)).showedUp)
        // A no-show counts against.
        assertEquals(Ratio(3, 4), rep(collab, base + fact(state = MatchState.NO_SHOW, attendance = listOf(Attendance.NO_SHOW))).showedUp)
    }

    @Test fun disputesAreExcluded() {
        val base = listOf(fact(), fact(), fact())
        assertEquals(Ratio(3, 3), rep(collab, base + fact(state = MatchState.NO_SHOW, attendance = listOf(Attendance.NO_SHOW), disputed = true)).showedUp)
        assertEquals(Ratio(3, 3), rep(collab, base + fact(attendance = listOf(Attendance.DISPUTED))).showedUp)
    }

    @Test fun lateOwnerReleasesAndCutsLowerKeptBookings() {
        val wrapped = listOf(fact(), fact(), fact())
        assertEquals(Ratio(3, 3), rep(owner, wrapped).keptBookings)
        assertEquals(Ratio(3, 4), rep(owner, wrapped + fact(state = MatchState.RELEASED, notice = 30, released = true)).keptBookings)
        // A cancelled project releases everyone; under 72h counts against too.
        assertEquals(Ratio(3, 5), rep(owner, wrapped + fact(state = MatchState.RELEASED, notice = 10, released = true) +
            fact(state = MatchState.RELEASED, notice = 40, released = true)).keptBookings)
        // With enough notice it's neutral.
        assertEquals(Ratio(3, 3), rep(owner, wrapped + fact(state = MatchState.RELEASED, notice = 120, released = true)).keptBookings)
        // A late cut of days or pay counts against even though the shoot happened.
        assertEquals(Ratio(3, 4), rep(owner, wrapped + fact(cutLate = true)).keptBookings)
        // The collaborator's own withdrawal doesn't touch the owner's record.
        assertEquals(Ratio(3, 3), rep(owner, wrapped + fact(state = MatchState.WITHDRAWN, notice = 10)).keptBookings)
        // ...and a release never touches the collaborator's Showed up.
        assertEquals(Ratio(3, 3), rep(collab, wrapped + fact(state = MatchState.RELEASED, notice = 10, released = true)).showedUp)
    }

    @Test fun postRolesFeedDeliveredOnTimeNotShowedUp() {
        val due = LocalDate.of(2026, 9, 1)
        val onTime = { fact(post = true, items = listOf(item(WrapKind.DELIVERY, due, markedOn = due.minusDays(1)))) }
        val late = fact(post = true, items = listOf(item(WrapKind.DELIVERY, due, markedOn = due.plusDays(3))))
        val r = rep(collab, listOf(onTime(), onTime(), onTime(), late))
        assertEquals(Ratio(3, 4), r.deliveredOnTime)
        assertNull(r.showedUp)
        assertEquals("Delivered on time 3/4", ReputationCalc.headlineChip(r, asOwner = false))
        // Never delivered and past due: a miss. Marked but waiting for the owner: not judged yet.
        val missing = fact(post = true, state = MatchState.BOOKED, items = listOf(item(WrapKind.DELIVERY, due, WrapStatus.OPEN, markedOn = null)))
        val waiting = fact(post = true, state = MatchState.BOOKED, items = listOf(item(WrapKind.DELIVERY, today.minusDays(2), WrapStatus.DONE)))
        assertEquals(Ratio(3, 4), rep(collab, listOf(onTime(), onTime(), onTime(), missing, waiting)).deliveredOnTime)
        // Post-role withdrawals use the 7-day rule.
        assertEquals(Ratio(3, 3), rep(collab, listOf(onTime(), onTime(), onTime(), fact(post = true, state = MatchState.WITHDRAWN, notice = 200))).deliveredOnTime)
        assertEquals(Ratio(3, 4), rep(collab, listOf(onTime(), onTime(), onTime(), fact(post = true, state = MatchState.WITHDRAWN, notice = 100))).deliveredOnTime)
    }

    @Test fun paidOnTimeAndFootageDelivered() {
        val due = LocalDate.of(2026, 9, 10)
        fun paid(markedOn: LocalDate?, status: WrapStatus = WrapStatus.CONFIRMED) = item(WrapKind.PAID, due, status, markedOn)
        fun footage(status: WrapStatus, markedOn: LocalDate?) = item(WrapKind.FOOTAGE, due, status, markedOn)
        val facts = listOf(
            fact(items = listOf(paid(due), footage(WrapStatus.CONFIRMED, due.plusDays(20)))),        // footage late but delivered
            fact(items = listOf(paid(due.minusDays(2)), footage(WrapStatus.CONFIRMED, due))),
            fact(items = listOf(paid(due.plusDays(5)), footage(WrapStatus.OPEN, null))),            // paid late, footage overdue
            fact(items = listOf(paid(null, WrapStatus.OPEN).copy(dueDate = today.plusDays(3)))),    // not due yet: excluded
        )
        val r = rep(owner, facts)
        assertEquals(Ratio(2, 3), r.paidOnTime)
        assertEquals(Ratio(2, 3), r.footageDelivered)
        assertEquals(Ratio(4, 4), r.keptBookings)
        assertEquals("Footage 2/3", ReputationCalc.headlineChip(r, asOwner = true))
        val chips = ReputationCalc.chips(r, asOwner = true)
        assertEquals(listOf("Paid on time 2/3", "Footage delivered 2/3", "Kept bookings 4/4", "4 shoots done", "Replies in ~1 day"), chips)
    }

    @Test fun headlineChipShowsTheWeakerNumber() {
        val r = Fx.reputation(shoots = 6, footage = Ratio(5, 5), kept = Ratio(4, 6))
        assertEquals("Kept bookings 4/6", ReputationCalc.headlineChip(r, asOwner = true))
        val r2 = Fx.reputation(shoots = 6, footage = Ratio(3, 5), kept = Ratio(6, 6))
        assertEquals("Footage 3/5", ReputationCalc.headlineChip(r2, asOwner = true))
        assertEquals(ReputationCalc.NEW_ON_CALLTIME, ReputationCalc.headlineChip(Fx.reputation(shoots = 1), asOwner = true))
    }

    @Test fun headlineChipNeverHidesLatePay() {
        val r = Fx.reputation(shoots = 4, footage = Ratio(4, 4), kept = Ratio(4, 4)).copy(paidOnTime = Ratio(2, 4))
        assertEquals("Paid on time 2/4", ReputationCalc.headlineChip(r, asOwner = true))
    }

    @Test fun noNewOnCalltimeOnceThreeShootsAreDone() {
        val r = Fx.reputation(shoots = 3)
        assertEquals("", ReputationCalc.headlineChip(r, asOwner = true))
        assertTrue(ReputationCalc.NEW_ON_CALLTIME !in ReputationCalc.chips(r, asOwner = true))
        assertTrue(ReputationCalc.NEW_ON_CALLTIME in ReputationCalc.chips(Fx.reputation(shoots = 2), asOwner = true))
    }

    @Test fun closesTheLoopFollowsTheThreeRule() {
        val wrapped = listOf(fact(), fact(), fact())
        assertNull(rep(owner, wrapped, closes = Ratio(2, 2)).closesLoop)
        val r = rep(owner, wrapped, closes = Ratio(11, 12))
        assertEquals(Ratio(11, 12), r.closesLoop)
        assertTrue(ReputationCalc.chips(r, asOwner = true).contains("Closes the loop 92%"))
        assertEquals(2, r.confirmedCredits)
        assertEquals(20, r.replyHoursMedian)
    }

    @Test fun newProfilesSayNewOnCalltime() {
        val r = rep(collab, emptyList())
        assertEquals(listOf(ReputationCalc.NEW_ON_CALLTIME, "2 confirmed credits"), ReputationCalc.chips(r, asOwner = false))
        assertEquals(0, r.shootsCompleted)
    }
}
