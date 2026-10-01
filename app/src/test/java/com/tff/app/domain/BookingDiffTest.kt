package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BookingDiffTest {

    private val soundItem = Fx.item(role = Fx.role(id = "r_sound", kitFeeDay = 1000))
    private val v1 = BookingTermsFactory.fromRole(soundItem, "Rohan K.")

    @Test fun factoryPrefillsFromRoleAndProject() {
        assertEquals(RoleCode.SOUND_MIXER, v1.roleCode)
        assertEquals("Sound Recordist", v1.creditTitle)
        assertEquals("Filter Coffee, 4 AM", v1.projectTitle)
        assertEquals(listOf(Fx.SAT_14_NOV, Fx.SUN_15_NOV), v1.dates)
        assertEquals(2500, v1.amount)
        assertEquals(1000, v1.kitFeeDay)
        assertEquals(PaymentTerms.ON_WRAP, v1.paymentTerms)
        assertEquals(Fx.SUN_15_NOV, v1.payByDate)                 // on wrap = last shoot day
        assertEquals(Meals.TWO_PLUS_MEALS, v1.meals)
        assertEquals(300, v1.travelAllowancePerDay)
        assertEquals("u_rohan", v1.payerId)
        assertEquals("Rohan K.", v1.payerName)
        assertFalse(v1.isPostRole)
        assertTrue(v1.noticePledge.startsWith("If you can't make it, tell the production at least 72 hours before."))
        assertTrue(v1.noticePledge.contains("less than 72 hours' notice, it shows on their record"))
        assertEquals("You're booked as Sound Mixer (credit: 'Sound Recordist') on Filter Coffee, 4 AM · Sat 14 & Sun 15 Nov · " +
            "₹2,500/day + ₹1,000 kit · paid on wrap · 2 meals · TA ₹300/day · Payer: Rohan K.", CardText.bookingSummary(v1))
    }

    @Test fun postRoleFactoryUsesDeliveryAndPictureLock() {
        val composer = Fx.role(id = "r_comp", roleCode = RoleCode.COMPOSER, compType = CompType.PAID_FLAT, amountMin = 12000,
            paymentTerms = PaymentTerms.WITHIN_7_DAYS, remoteOk = true, postDeadline = LocalDate.of(2027, 2, 7),
            roleDetails = mapOf("revisions" to "2 rounds"))
        val t = BookingTermsFactory.fromRole(Fx.item(role = composer, project = Fx.postProject(title = "Filter Coffee, 4 AM")), "Rohan K.")
        assertTrue(t.isPostRole)
        assertTrue(t.dates.isEmpty())
        assertEquals(LocalDate.of(2027, 2, 7), t.deliveryDate)
        assertEquals(LocalDate.of(2027, 1, 10), t.pictureLockDate)
        assertEquals(2, t.revisionRounds)
        assertNull(t.meals)
        assertTrue(t.noticePledge.startsWith("If you can't deliver by the date, tell the production at least 7 days before."))
        assertEquals("You're booked as Composer (credit: 'Music by') on Filter Coffee, 4 AM · remote · picture lock 10 Jan · " +
            "deliver by 7 Feb · 2 revision rounds · ₹12,000 flat · paid within 7 days of delivery · Payer: Rohan K.", CardText.bookingSummary(t))
    }

    @Test fun changedFieldsDetected() {
        val v2 = v1.copy(dates = listOf(Fx.SAT_14_NOV), amount = 2000)
        assertEquals(setOf("dates", "amount"), BookingDiff.changedFields(v1, v2))
        val v3 = v1.copy(kitFeeDay = 1500, paymentTerms = PaymentTerms.WITHIN_7_DAYS, callWindow = "~7:00–17:00", notes = "Bring a jacket",
            creditTitle = "Production Sound Mixer", meals = Meals.CATERED, transport = Transport.PRODUCTION_VAN,
            perks = v1.perks + Perk.STEP_UP_CREDIT)
        assertEquals(setOf("kitFeeDay", "paymentTerms", "callWindow", "notes", "creditTitle", "meals", "transport", "perks"),
            BookingDiff.changedFields(v1, v3))
        val post = v1.copy(isPostRole = true, dates = emptyList(), deliveryDate = LocalDate.of(2027, 2, 7),
            pictureLockDate = LocalDate.of(2027, 1, 10), revisionRounds = 2)
        val postV2 = post.copy(deliveryDate = LocalDate.of(2027, 2, 1), pictureLockDate = LocalDate.of(2027, 1, 12), revisionRounds = 3)
        assertEquals(setOf("deliveryDate", "pictureLockDate", "revisionRounds"), BookingDiff.changedFields(post, postV2))
    }

    @Test fun noOpEditsProduceNoNewVersion() {
        assertTrue(BookingDiff.isNoOp(v1, v1.copy()))
        // Whitespace, blank notes, reordered dates and a re-normalised copy are not changes.
        val same = v1.copy(notes = "  ", callWindow = "", dates = v1.dates.reversed(), creditTitle = " Sound Recordist ")
        assertEquals(emptySet<String>(), BookingDiff.changedFields(v1, same))
        assertTrue(BookingDiff.isNoOp(v1, BookingTermsFactory.normalize(v1)))
    }

    @Test fun cutsAreDetected() {
        assertTrue(BookingDiff.isCut(v1, v1.copy(dates = listOf(Fx.SAT_14_NOV))))
        assertTrue(BookingDiff.isCut(v1, v1.copy(amount = 2000)))
        assertTrue(BookingDiff.isCut(v1, v1.copy(kitFeeDay = null)))
        assertTrue(BookingDiff.isCut(v1, v1.copy(compType = CompType.STIPEND, amount = 2500)))
        assertFalse(BookingDiff.isCut(v1, v1.copy(amount = 3000)))
        assertFalse(BookingDiff.isCut(v1, v1.copy(notes = "Parking at the back")))
        assertFalse(BookingDiff.isCut(v1, v1.copy(dates = v1.dates + LocalDate.of(2026, 11, 21))))
    }

    @Test fun normalizeRecomputesDerivedFields() {
        val moved = BookingTermsFactory.normalize(v1.copy(dates = listOf(LocalDate.of(2026, 11, 21)), paymentTerms = PaymentTerms.WITHIN_15_DAYS))
        assertEquals(LocalDate.of(2026, 12, 6), moved.payByDate)
        assertEquals(LocalDate.of(2026, 12, 6), moved.deliverables.first { it.kind == WrapKind.PAID }.dueDate)
        val unpaid = BookingTermsFactory.normalize(v1.copy(compType = CompType.UNPAID))
        assertNull(unpaid.amount)
        assertNull(unpaid.payByDate)
        assertTrue(unpaid.deliverables.none { it.kind == WrapKind.PAID })
    }

    @Test fun noticeHoursAndNeutrality() {
        val zone = ZoneId.of("Asia/Kolkata")
        val t = v1.copy(callWindow = "~7:00–17:00")
        val wedMorning = LocalDate.of(2026, 11, 11).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(72, Notice.hoursUntil(t, wedMorning, zone))                       // to 07:00 Sat 14 Nov
        assertTrue(Notice.isNeutral(t, 72))
        assertFalse(Notice.isNeutral(t, 71))
        val noWindow = Notice.hoursUntil(v1, wedMorning, zone)                         // to midnight
        assertEquals(65, noWindow)
        val post = v1.copy(isPostRole = true, dates = emptyList(), deliveryDate = LocalDate.of(2026, 11, 20))
        assertEquals(9 * 24 - 7, Notice.hoursUntil(post, wedMorning, zone))
        assertTrue(Notice.isNeutral(post, 168))
        assertFalse(Notice.isNeutral(post, 167))
        assertEquals("4 days' notice: this won't count against you.", Notice.withdrawLine(t, 100))
        assertEquals("Less than 72 hours' notice. This will show in your Kept bookings.", Notice.releaseLine(30))
        assertNull(Notice.releaseLine(80))
        assertEquals("30 hours' notice", Notice.noticeText(30))
    }
}
