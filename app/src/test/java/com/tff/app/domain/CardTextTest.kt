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

    @Test fun whenLineListsDaysThatAreNotInARow() {
        val r = Fx.role()
        val sat17 = LocalDate.of(2026, 10, 17)
        val twoSaturdays = Fx.project(shootDates = listOf(sat17, sat17.plusDays(7)))
        assertEquals("SAT 17 & SAT 24 OCT · 2 DAYS · ≤10H · LOCKED", CardText.whenLine(r, twoSaturdays))
        val three = Fx.project(shootDates = listOf(sat17, sat17.plusDays(7), sat17.plusDays(14)), hoursCap = null, dateCertainty = null)
        assertEquals("3 DAYS: 17, 24, 31 OCT", CardText.whenLine(r, three))
        val acrossMonths = Fx.project(shootDates = listOf(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 5)), hoursCap = null, dateCertainty = null)
        assertEquals("3 DAYS: 28, 29 NOV, 5 DEC", CardText.whenLine(r, acrossMonths))
        val many = Fx.project(shootDates = (0 until 6).map { sat17.plusDays(it * 3L) }, hoursCap = null, dateCertainty = null)
        assertEquals("6 DAYS, SAT 17 OCT – SUN 1 NOV", CardText.whenLine(r, many))
        // A run of days is still a range.
        val run = Fx.project(shootDates = listOf(sat17, sat17.plusDays(1), sat17.plusDays(2)), hoursCap = null, dateCertainty = null)
        assertEquals("SAT 17 – MON 19 OCT · 3 DAYS", CardText.whenLine(r, run))
        // Share text and the Ask card say the same days.
        val item = Fx.item(project = twoSaturdays)
        assertTrue(ShareText.roleCard(item, "").lines()[1].startsWith("Sat 17 & Sat 24 Oct · 2 days · ≤10h"))
        assertTrue(CardText.askLine(item).contains("? Sat 17 & Sat 24 Oct · "))
        assertTrue(CardText.termsMessage(item).contains("Sat 17 & Sat 24 Oct · 2 days"))
    }

    @Test fun boomOpChipOnSoundMixerCards() {
        assertEquals("Boom op budgeted", CardText.boomOpChip(Fx.role(roleDetails = mapOf("boom_op" to "Y"))))
        assertEquals("No boom op", CardText.boomOpChip(Fx.role(roleDetails = mapOf("boom_op" to "no"))))
        assertEquals(null, CardText.boomOpChip(Fx.role()))
        assertEquals(null, CardText.boomOpChip(Fx.role(roleCode = RoleCode.BOOM_OP, roleDetails = mapOf("boom_op" to "Y"))))
    }

    @Test fun oneResolvedRoleNameForAListing() {
        fun actor(size: String?) = Fx.role(roleCode = RoleCode.ACTOR, roleDetails = size?.let { mapOf("size" to it) } ?: emptyMap())
        assertEquals("Lead Actor", CardText.roleName(actor("LEAD")))
        assertEquals("Lead Actor", CardText.roleName(actor("lead role")))
        assertEquals("Supporting Actor", CardText.roleName(actor("Supporting")))
        assertEquals("Day Player", CardText.roleName(actor("day player")))
        assertEquals("Day Player", CardText.roleName(actor("DAY_PLAYER")))
        assertEquals("Actor", CardText.roleName(actor(null)))
        assertEquals("Sound Mixer", CardText.roleName(Fx.role()))
        // The booking summary can carry the listing's name instead of the bare code.
        val t = BookingTerms(
            roleCode = RoleCode.ACTOR, creditTitle = "Cast", projectTitle = "Last Local", dates = listOf(Fx.SAT_14_NOV),
            compType = CompType.UNPAID, currency = "INR", payerId = "u_rohan", payerName = "Rohan K.", noticePledge = "x",
        )
        assertTrue(CardText.bookingSummary(t).startsWith("You're booked as Actor (credit: "))
        assertTrue(CardText.bookingSummary(t, CardText.roleName(actor("LEAD"))).startsWith("You're booked as Lead Actor (credit: 'Cast')"))
        assertEquals(SpanStyleKind.BOLD, CardText.bookingSummarySpans(t, "Lead Actor").first { it.text == "Lead Actor" }.style)
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

    // ───────────── Card face helpers ─────────────

    @Test fun chipsBadgesAndRows() {
        assertEquals("4 KM", CardText.distanceChip(4.3, remote = false))
        assertEquals("<1 KM", CardText.distanceChip(0.4, remote = false))
        assertEquals("REMOTE", CardText.distanceChip(null, remote = true))
        assertEquals(null, CardText.distanceChip(null, remote = false))
        assertEquals(listOf("STEP-UP OK", "BEGINNERS WELCOME"),
            CardText.badges(Fx.role(stepUpFriendly = true, levelWanted = Level.BUILDING_REEL)))
        assertTrue(CardText.badges(Fx.role(levelWanted = Level.WORKING_PRO)).isEmpty())
        val team = listOf(
            TeamMember("u_rohan", "Rohan K.", "RK", RoleCode.DIRECTOR, isOwner = true),
            TeamMember("u_a", "Arjun M.", "AM", RoleCode.DP),
            TeamMember("u_b", "Bela S.", "BS", RoleCode.SOUND_MIXER),
        )
        assertEquals("DP · Sound attached", CardText.teamLine(team))
        assertEquals(null, CardText.teamLine(team.take(1)))
        val item = Fx.item(team = team, owner = Fx.owner(idVerified = true,
            reputation = Fx.reputation(shoots = 3, footage = Ratio(5, 5), kept = Ratio(6, 6))), paidRoles = 2, totalRoles = 4)
        assertEquals(listOf("ID verified", "3 shoots done", "Footage 5/5", "Script locked", "DP · Sound attached"), CardText.credibilityChips(item))
        assertEquals("2 of 4 roles paid", CardText.rolesPaidChip(item))
        val home = Fx.project(contentFlags = setOf(ContentFlag.NIGHT_SHOOT), shootLocationType = LocationType.PRIVATE_HOME, rescheduleCount = 2)
        assertEquals(listOf("Night shoot", "Private home · friend welcome", "Rescheduled 2×"), CardText.flagsRow(home))
        assertEquals("Posted 5d ago", CardText.postedAgo(Fx.project(), Fx.NOW))
    }

    @Test fun personCardLines() {
        val dana = Fx.profile(id = "u_dana", name = "Dana Reyes", primaryRole = RoleCode.SOUND_MIXER, level = Level.CREDITED_INDIE,
            stepUpRoles = listOf(RoleCode.DP), gear = listOf("Sound Devices 833"), kitFeeDay = 1500)
        assertEquals("SOUND MIXER · CREDITED INDIE", CardText.personRoleLine(dana))
        assertEquals("→ DP", CardText.stepUpChip(dana))
        val item = Fx.item()
        val view = ApplicantView(null, ProfileCard(dana, Fx.reputation(id = dana.id), distanceKm = 6.2), 80.0, emptyList(), 1.0, 2, 2)
        assertEquals("2/2 days · 6 km · Kit ₹1,500/day · rate in budget", CardText.fitLine(item, view))
        val unpaid = Fx.item(role = Fx.role(compType = CompType.UNPAID, perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 4))
        assertEquals("2/2 days · 6 km · Kit ₹1,500/day · Open to unpaid · conditions met", CardText.fitLine(unpaid, view))
    }

    @Test fun askAndTermsMessages() {
        val item = Fx.item(role = Fx.role(kitFeeDay = 1000))
        assertEquals("Would you be Sound Mixer on Filter Coffee, 4 AM? Sat 14 – Sun 15 Nov · ₹2,500/day + kit · 2 meals", CardText.askLine(item))
        val terms = CardText.termsMessage(item)
        assertTrue(terms.startsWith("Sound Mixer on Filter Coffee, 4 AM\nPay: ₹2,500/day + ₹1,000 kit · paid on wrap\n"))
        assertTrue(terms.contains("Sat 14 – Sun 15 Nov · 2 days · ≤10h · dates locked"))
        assertTrue(terms.contains("Where: Jayanagar, Bengaluru (exact address once you're booked)"))
        assertTrue(terms.contains("You get: 2 meals a day on set; Travel allowance ₹300/day"))
    }

    @Test fun densityHeaders() {
        val mumbai = Density("mum", "Mumbai", isLive = true, isEarly = false, people = 214, openProjects = 31,
            perRole = mapOf(RoleCode.SOUND_MIXER to 9, RoleCode.DP to 1))
        assertEquals("Mumbai · 214 people · 31 open projects · 9 sound mixers", CardText.densityHeader(mumbai, RoleCode.SOUND_MIXER))
        assertEquals("Mumbai · 214 people · 31 open projects · 1 DP", CardText.densityHeader(mumbai, RoleCode.DP))
        assertEquals("Mumbai · 214 people · 31 open projects", CardText.densityHeader(mumbai))
        val indore = Density("idr", "Indore", isLive = false, isEarly = true, people = 3, openProjects = 1)
        assertEquals("Indore · early city · 3 people · 1 open project · + remote roles", CardText.densityHeader(indore))
    }
}
