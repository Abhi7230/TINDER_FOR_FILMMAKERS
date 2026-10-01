package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CardTextTest {

    private val project = Fx.project()

    @Test fun paidDayLine() {
        val r = Fx.role(compType = CompType.PAID_DAY, amountMin = 2500, kitFeeDay = 1000, paymentTerms = PaymentTerms.ON_WRAP)
        assertEquals("₹2,500/day + ₹1,000 kit · paid on wrap", CardText.payLine(r, project))
    }

    @Test fun stipendLine() {
        val r = Fx.role(roleCode = RoleCode.DP, compType = CompType.STIPEND, amountMin = 800, paymentTerms = PaymentTerms.ON_WRAP)
        assertEquals("Stipend ₹800/day · 2 meals · TA ₹300", CardText.payLine(r, project))
    }

    @Test fun unpaidLine() {
        val r = Fx.role(roleCode = RoleCode.ACTOR, compType = CompType.UNPAID,
            perks = setOf(Perk.FOOTAGE_BY_DATE, Perk.STEP_UP_CREDIT), footageDueWeeks = 6)
        val p = Fx.project(transport = Transport.NONE, travelAllowancePerDay = null)
        assertEquals("Unpaid · 2 meals · your scenes in 6 wks · step-up credit", CardText.payLine(r, p))
    }

    @Test fun paidRangeAndFlat() {
        val day = Fx.role(amountMin = 2000, amountMax = 3000, paymentTerms = PaymentTerms.WITHIN_7_DAYS)
        assertEquals("₹2,000–3,000/day · paid within 7 days", CardText.payLine(day, project))
        val flat = Fx.role(compType = CompType.PAID_FLAT, amountMin = 12000, paymentTerms = PaymentTerms.HALF_UPFRONT)
        assertEquals("₹12,000 flat · half upfront, rest on wrap", CardText.payLine(flat, project))
    }

    @Test fun postRoleFlatLineCountsFromDelivery() {
        val r = Fx.role(id = "r_comp", roleCode = RoleCode.COMPOSER, compType = CompType.PAID_FLAT, amountMin = 12000,
            paymentTerms = PaymentTerms.WITHIN_7_DAYS, remoteOk = true, postDeadline = LocalDate.of(2027, 2, 7))
        assertEquals("₹12,000 flat · paid within 7 days of delivery", CardText.payLine(r, Fx.postProject()))
    }

    @Test fun usdLine() {
        val r = Fx.role(amountMin = 350, currency = "USD", kitFeeDay = 75)
        assertEquals("$350/day + $75 kit · paid on wrap", CardText.payLine(r, project))
    }

    @Test fun payLineNeverSaysPaidForStipendOrCredit() {
        val r = Fx.role(compType = CompType.STIPEND, amountMin = 500)
        val line = CardText.payLine(r, project)
        assertFalse(line.contains("Paid"))
        assertFalse(line.lowercase().contains("credit"))
    }

    @Test fun whenLineOnSet() {
        val r = Fx.role()
        assertEquals("SAT 14 – SUN 15 NOV · 2 DAYS · ≤10H · LOCKED", CardText.whenLine(r, project))
        val one = Fx.project(shootDates = listOf(Fx.SAT_14_NOV), dateCertainty = DateCertainty.TENTATIVE, hoursCap = 12)
        assertEquals("SAT 14 NOV · 1 DAY · ≤12H · TENTATIVE", CardText.whenLine(r, one))
    }

    @Test fun whenLinePostRoles() {
        val composer = Fx.role(roleCode = RoleCode.COMPOSER, compType = CompType.PAID_FLAT, amountMin = 12000,
            remoteOk = true, postDeadline = LocalDate.of(2027, 2, 7))
        assertEquals("REMOTE · LOCK 10 JAN → DUE 7 FEB · 4 WKS", CardText.whenLine(composer, Fx.postProject()))
        val editor = Fx.role(roleCode = RoleCode.EDITOR, compType = CompType.STIPEND, amountMin = 5000,
            remoteOk = true, postDeadline = LocalDate.of(2027, 1, 30))
        assertEquals("REMOTE · DUE 30 JAN", CardText.whenLine(editor, Fx.postProject()))
    }

    @Test fun whereLabelChipFooter() {
        val item = Fx.item(role = Fx.role(applicationsCount = 6, applicationCap = 15))
        assertEquals("Jayanagar, Bengaluru · Kannada/English", CardText.whereLine(item))
        assertEquals("SOUND MIXER ×1", CardText.roleLabel(item.role))
        assertEquals("SHORT · 12 MIN", CardText.formatChip(item.project))
        assertEquals("Open · 6/15 applied · replies in ~1 day", CardText.footer(item))
        val fast = Fx.item(owner = Fx.owner(reputation = Fx.reputation(replyHours = 3)))
        assertEquals("Open · 6/15 applied · replies in ~3h", CardText.footer(fast))
        val none = Fx.item(owner = Fx.owner(reputation = Fx.reputation(replyHours = null)))
        assertEquals("Open · 6/15 applied", CardText.footer(none))
    }

    @Test fun leadActorLabel() {
        val r = Fx.role(roleCode = RoleCode.ACTOR, count = 2, roleDetails = mapOf("size" to "Lead"))
        assertEquals("LEAD ACTOR ×2", CardText.roleLabel(r))
    }

    @Test fun languagesAndLabels() {
        assertEquals("Kannada/English", CardText.languageNames(listOf("kn", "en")))
        assertEquals("Hindi", CardText.languageNames(listOf("hi")))
        assertEquals("Building a reel", CardText.levelLabel(Level.BUILDING_REEL))
        assertEquals("paid on wrap", CardText.termsLabel(PaymentTerms.ON_WRAP))
        assertEquals("paid within 7 days of delivery", CardText.termsLabel(PaymentTerms.WITHIN_7_DAYS, postRole = true))
        assertEquals("Music video", CardText.formatLabel(Format.MUSIC_VIDEO))
        for (f in ContentFlag.entries) assertTrue(CardText.contentFlagLabel(f).isNotBlank())
        for (p in Perk.entries) assertTrue(CardText.perkLabel(p).isNotBlank())
        for (m in Meals.entries) assertTrue(CardText.mealsLabel(m).isNotBlank())
        for (t in Transport.entries) assertTrue(CardText.transportLabel(t).isNotBlank())
        for (c in RoleCode.entries) assertTrue(CardText.roleName(c).isNotBlank())
    }

    @Test fun perksDetailedHasDates() {
        val r = Fx.role(roleCode = RoleCode.ACTOR, compType = CompType.UNPAID,
            perks = setOf(Perk.FOOTAGE_BY_DATE, Perk.STEP_UP_CREDIT), footageDueWeeks = 6)
        val list = CardText.perksDetailed(r, project)
        assertTrue(list.contains("2 meals a day on set"))
        assertTrue(list.contains("Travel allowance ₹300/day"))
        // 15 Nov + 6 weeks = 27 Dec
        assertTrue(list.any { it.startsWith("Approved clips of your scenes, graded, by 27 Dec") })
        assertTrue(list.any { it.startsWith("Step-up credit") })
    }

    @Test fun bookingSummaryOnSet() {
        val t = BookingTerms(
            roleCode = RoleCode.SOUND_MIXER, creditTitle = "Sound Recordist", projectTitle = "Filter Coffee, 4 AM",
            dates = listOf(Fx.SAT_14_NOV, Fx.SUN_15_NOV), callWindow = "~7:00–17:00",
            compType = CompType.PAID_DAY, amount = 2500, currency = "INR", kitFeeDay = 1000,
            paymentTerms = PaymentTerms.ON_WRAP, meals = Meals.TWO_PLUS_MEALS, transport = Transport.MILEAGE_OR_TA_PAID,
            travelAllowancePerDay = 300,
            deliverables = listOf(Deliverable(WrapKind.FOOTAGE, LocalDate.of(2027, 1, 10), OwedBy.PRODUCTION)),
            payerId = "u_rohan", payerName = "Rohan K.", noticePledge = "x",
        )
        assertEquals(
            "You're booked as Sound Mixer (credit: 'Sound Recordist') on Filter Coffee, 4 AM · Sat 14 & Sun 15 Nov · " +
                "call ~07:00, wrap by 17:00 · ₹2,500/day + ₹1,000 kit · paid on wrap · 2 meals · TA ₹300/day · " +
                "approved clips of your scenes by 10 Jan · Payer: Rohan K.",
            CardText.bookingSummary(t),
        )
        val spans = CardText.bookingSummarySpans(t)
        assertEquals(SpanStyleKind.BOLD, spans.first { it.text == "Sound Mixer" }.style)
        assertEquals(SpanStyleKind.ITALIC, spans.first { it.text == "Filter Coffee, 4 AM" }.style)
    }

    @Test fun bookingSummaryPostRole() {
        val t = BookingTerms(
            roleCode = RoleCode.COMPOSER, creditTitle = "Music by", projectTitle = "Filter Coffee, 4 AM", isPostRole = true,
            deliveryDate = LocalDate.of(2027, 2, 7), pictureLockDate = LocalDate.of(2027, 1, 10), revisionRounds = 2,
            compType = CompType.PAID_FLAT, amount = 12000, currency = "INR", paymentTerms = PaymentTerms.WITHIN_7_DAYS,
            payerId = "u_rohan", payerName = "Rohan K.", noticePledge = "x",
        )
        assertEquals(
            "You're booked as Composer (credit: 'Music by') on Filter Coffee, 4 AM · remote · picture lock 10 Jan · " +
                "deliver by 7 Feb · 2 revision rounds · ₹12,000 flat · paid within 7 days of delivery · Payer: Rohan K.",
            CardText.bookingSummary(t),
        )
    }

    @Test fun dateListsAndCallWindow() {
        assertEquals("Sat 14 & Sun 15 Nov", CardText.dateList(listOf(Fx.SUN_15_NOV, Fx.SAT_14_NOV)))
        assertEquals("Sat 28, Sun 29 Nov & Sat 5 Dec",
            CardText.dateList(listOf(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 5))))
        assertEquals("Sat 14 Nov", CardText.dateList(listOf(Fx.SAT_14_NOV)))
        assertEquals("call ~07:00, wrap by 17:00", CardText.callWindowText("~7:00-17:00"))
        assertEquals("call after lunch", CardText.callWindowText("after lunch"))
    }
}
