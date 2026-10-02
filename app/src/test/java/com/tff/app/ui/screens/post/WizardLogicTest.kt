package com.tff.app.ui.screens.post

import com.tff.app.data.demo.DemoCities
import com.tff.app.data.demo.DemoEngine
import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.T
import com.tff.app.domain.ListingRules
import com.tff.app.domain.Severity
import com.tff.app.domain.ShareText
import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.util.UUID

/** WS5: the S11 wizard's pure logic, and that what it builds really publishes through the demo engine (§15.3 step 5). */
class WizardLogicTest {
    private val today = T.anchor
    private val bengaluru = DemoCities.city("bengaluru")!!
    private val rohanUid = DemoIds.ROHAN

    private fun engine(): Pair<DemoEngine, Profile> {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.ROHAN)
        return e to e.state.value.profiles.getValue(rohanUid)
    }

    private fun fresh(owner: Profile) = WizardLogic.newDraft(WizardLogic.newId(), owner, bengaluru, T.now)

    private fun blocks(d: ProjectDraft, owner: Profile, postOnly: Boolean = false) =
        WizardLogic.blockers(WizardLogic.check(d, postOnly, owner, bengaluru, today))

    /** A 3-role on-set weekend shoot built the fast way: pay set once, then Add, Duplicate (+ change role), Add. */
    private fun threeRoleDraft(owner: Profile): Pair<ProjectDraft, PayDefaults> {
        var d = fresh(owner)
        val (sat, sun) = WizardLogic.upcomingWeekends(today).first()
        d = d.copy(project = d.project.copy(
            title = "Idli Sambar Noir", logline = "A night-shift cook solves the case of the missing batter before the breakfast rush.",
            shootDates = listOf(sat, sun), dateCertainty = DateCertainty.LOCKED, meals = Meals.TWO_PLUS_MEALS,
            transport = Transport.NONE, shootLocationType = LocationType.OUTDOOR_PUBLIC, contentNoneConfirmed = true,
        ))
        val pay = PayDefaults(compType = CompType.PAID_DAY, amount = 2500, paymentTerms = PaymentTerms.ON_WRAP)
        val dp = WizardLogic.newRole(WizardLogic.newId(), RoleCode.DP, d.project, pay, "INR", postOnly = false).copy(gearPolicy = GearPolicy.PRODUCTION_PROVIDES)
        val gaffer = WizardLogic.changeCode(WizardLogic.duplicate(dp, WizardLogic.newId()), RoleCode.GAFFER, postOnly = false)
        val sound = WizardLogic.newRole(WizardLogic.newId(), RoleCode.SOUND_MIXER, d.project, pay, "INR", postOnly = false)
            .copy(gearPolicy = GearPolicy.BYO_WITH_KIT_FEE, kitFeeDay = 1000)
        return d.copy(roles = listOf(dp, gaffer, sound)) to pay
    }

    // ───────────── defaults ─────────────

    @Test fun `a new draft starts with the research defaults and the owner's city, area and languages`() {
        val (_, rohan) = engine()
        val d = fresh(rohan)
        assertEquals(Stage.PRE, d.project.stage)
        assertEquals(Ambition.PORTFOLIO, d.project.ambition)
        assertEquals(10, d.project.hoursCap)
        assertEquals(rohan.cityId, d.project.cityId)
        assertEquals(rohan.areaId, d.project.areaId)
        assertEquals(rohan.languages.take(2), d.project.languages)
        assertTrue(d.roles.isEmpty())
    }

    @Test fun `new roles inherit Pay for all roles, the taxonomy credit and the cap by scarcity`() {
        val (_, rohan) = engine()
        val d = fresh(rohan)
        val pay = PayDefaults(CompType.UNPAID, perks = setOf(Perk.FOOTAGE_BY_DATE, Perk.STEP_UP_CREDIT), footageWeeks = 6)
        val actor = WizardLogic.newRole("r1", RoleCode.ACTOR, d.project, pay, "INR", postOnly = false)
        assertEquals(CompType.UNPAID, actor.compType)
        assertNull(actor.amountMin)
        assertNull(actor.paymentTerms)
        assertEquals(setOf(Perk.FOOTAGE_BY_DATE, Perk.STEP_UP_CREDIT), actor.perks)
        assertEquals(6, actor.footageDueWeeks)
        assertEquals("Cast", actor.creditTitle)
        assertEquals(20, actor.applicationCap)
        val sound = WizardLogic.newRole("r2", RoleCode.SOUND_MIXER, d.project, PayDefaults(), "INR", postOnly = false)
        assertEquals(25, sound.applicationCap)
        assertNull("gear policy is asked, not assumed", sound.gearPolicy)
        val editor = WizardLogic.newRole("r3", RoleCode.EDITOR, d.project, PayDefaults(), "INR", postOnly = false)
        assertTrue("post departments default to remote", editor.remoteOk)
    }

    @Test fun `duplicate copies the terms with a new id and nothing counted, and changing the role follows its defaults`() {
        val (_, rohan) = engine()
        val d = fresh(rohan)
        val dp = WizardLogic.newRole("r1", RoleCode.DP, d.project, PayDefaults(amount = 3000), "INR", postOnly = false)
            .copy(applicationsCount = 4, filledCount = 1, gearPolicy = GearPolicy.BYO_WITH_KIT_FEE)
        val copy = WizardLogic.duplicate(dp, "r2")
        assertEquals("r2", copy.id)
        assertEquals(0, copy.applicationsCount)
        assertEquals(0, copy.filledCount)
        assertEquals(3000, copy.amountMin)
        val gaffer = WizardLogic.changeCode(copy, RoleCode.GAFFER, postOnly = false)
        assertEquals("Gaffer", gaffer.creditTitle)
        assertEquals(25, gaffer.applicationCap)
        assertEquals("gear stays for camera → G&E", GearPolicy.BYO_WITH_KIT_FEE, gaffer.gearPolicy)
        val actor = WizardLogic.changeCode(gaffer, RoleCode.ACTOR, postOnly = false)
        assertNull("actors don't have a gear policy", actor.gearPolicy)
        val custom = WizardLogic.changeCode(dp.copy(creditTitle = "Cinematography"), RoleCode.CAMERA_OP, postOnly = false)
        assertEquals("a typed credit is kept", "Cinematography", custom.creditTitle)
    }

    @Test fun `apply to all only touches roles nobody is booked on`() {
        val (_, rohan) = engine()
        val d = fresh(rohan)
        val roles = listOf(
            WizardLogic.newRole("a", RoleCode.PA, d.project, PayDefaults(amount = 800), "INR", false),
            WizardLogic.newRole("b", RoleCode.GRIP, d.project, PayDefaults(amount = 900), "INR", false),
        )
        val pay = PayDefaults(CompType.STIPEND, amount = 1000)
        assertTrue(WizardLogic.payDiffers(roles, pay))
        val applied = roles.map { WizardLogic.withPay(it, pay) }
        assertFalse(WizardLogic.payDiffers(applied, pay))
        assertTrue(applied.all { it.compType == CompType.STIPEND && it.amountMin == 1000 })
    }

    // ───────────── live rules ─────────────

    @Test fun `before any role exists, an on-set project is still asked for its logistics`() {
        val (_, rohan) = engine()
        val rules = WizardLogic.check(fresh(rohan), postOnly = false, rohan, bengaluru, today)
        val fields = WizardLogic.blockers(rules).map { it.field }.toSet()
        assertTrue(fields.containsAll(setOf(Fields.SHOOT_DATES, Fields.CERTAINTY, Fields.MEALS, Fields.TRANSPORT, Fields.LOCATION)))
        assertTrue("needs a role", WizardLogic.blockers(rules).any { it.rule == "R18" && it.field == Fields.ROLES })
        assertTrue("the probe role never leaks", rules.none { it.roleId == WizardLogic.PROBE_ROLE_ID })
        assertTrue("R22 waits for real roles", rules.none { it.rule == "R22" })
        assertEquals(WizardStep.WHEN_WHERE, WizardLogic.stepOf(rules.first { it.field == Fields.MEALS }))
    }

    @Test fun `camera, sound and G&E roles must say who brings the gear`() {
        val (_, rohan) = engine()
        val (d, _) = threeRoleDraft(rohan)
        val noGear = d.copy(roles = d.roles.map { if (it.roleCode == RoleCode.SOUND_MIXER) it.copy(gearPolicy = null) else it })
        val w01 = blocks(noGear, rohan).filter { it.rule == WizardLogic.W01 }
        assertEquals(1, w01.size)
        assertEquals(Fields.GEAR, w01.single().field)
        assertTrue(WizardLogic.opensRoleEditor(w01.single()))
    }

    @Test fun `a runtime outside 1 to 240 blocks on Basics`() {
        val (_, rohan) = engine()
        val (d, _) = threeRoleDraft(rohan)
        val bad = blocks(d.copy(project = d.project.copy(runtimeMin = 0)), rohan).single()
        assertEquals(WizardLogic.W03, bad.rule)
        assertEquals(WizardStep.BASICS, WizardLogic.stepOf(bad))
    }

    @Test fun `a below-floor day rate is shown as Stipend in the preview, exactly as publish relabels it`() {
        val (_, rohan) = engine()
        val (d, _) = threeRoleDraft(rohan)
        val cheap = d.copy(roles = d.roles.map { it.copy(amountMin = 800, amountMax = 800) })
        val rules = WizardLogic.check(cheap, false, rohan, bengaluru, today)
        assertTrue(rules.any { it.rule == "R04" && it.severity == Severity.AUTO })
        val preview = WizardLogic.previewDraft(cheap, false, bengaluru)
        assertTrue(preview.roles.all { it.compType == CompType.STIPEND })
        assertTrue(WizardLogic.notes(rules).any { it.rule == "R04" })
    }

    @Test fun `step blockers keep each step to its own fields`() {
        val (_, rohan) = engine()
        val rules = WizardLogic.check(fresh(rohan), false, rohan, bengaluru, today)
        assertTrue(WizardLogic.stepBlockers(WizardStep.BASICS, rules).all { it.field in setOf(Fields.TITLE, Fields.LOGLINE, Fields.LANGUAGES, Fields.RUNTIME) })
        assertTrue(WizardLogic.stepBlockers(WizardStep.SAFETY, rules).any { it.rule == "R07" })
        assertTrue(WizardLogic.stepBlockers(WizardStep.ROLES, rules).any { it.rule == "R18" })
    }

    @Test fun `forSave drops post-only logistics and answers that no longer apply`() {
        val (_, rohan) = engine()
        val (d, _) = threeRoleDraft(rohan)
        val messy = d.copy(project = d.project.copy(
            transport = Transport.CARPOOL_COORDINATED, travelAllowancePerDay = 300, intimacyCoordinator = true,
            guardianPresent = true, contentFlags = setOf(ContentFlag.NIGHT_SHOOT), contentNoneConfirmed = true,
        ))
        val saved = WizardLogic.forSave(messy, postOnly = false)
        assertNull(saved.project.travelAllowancePerDay)
        assertNull(saved.project.intimacyCoordinator)
        assertNull(saved.project.guardianPresent)
        assertFalse(saved.project.contentNoneConfirmed)
        assertEquals("2-day shoots answer lodging", false, saved.project.lodging)
        val post = WizardLogic.forSave(messy, postOnly = true).project
        assertTrue(post.shootDates.isEmpty())
        assertNull(post.meals)
        assertNull(post.hoursCap)
        assertNull(post.shootLocationType)
    }

    @Test fun `review checklist turns green group by group and shows every blocker somewhere`() {
        val (_, rohan) = engine()
        val (d, _) = threeRoleDraft(rohan)
        val rows = WizardLogic.checklist(d, false, WizardLogic.check(d, false, rohan, bengaluru, today))
        assertTrue(rows.all { it.issues.isEmpty() })
        assertTrue(rows.any { it.group == CheckGroup.GEAR })
        val broken = d.copy(project = d.project.copy(title = "", contentNoneConfirmed = false))
        val rules = WizardLogic.check(broken, false, rohan, bengaluru, today)
        val rows2 = WizardLogic.checklist(broken, false, rules)
        assertEquals(WizardLogic.blockers(rules).toSet(), rows2.flatMap { it.issues }.toSet())
        assertTrue(rows2.first { it.group == CheckGroup.CONTENT }.issues.any { it.rule == "R07" })
    }

    // ───────────── end to end through the demo engine (§15.3 step 5) ─────────────

    @Test fun `Rohan's draft shows the R02 blocker with a one-tap fix, then publishes`() {
        val (e, rohan) = engine()
        val d = DemoQueries(e).draft(e.state.value, rohanUid, DemoIds.P_ROHAN_DRAFT)!!
        val rules = WizardLogic.check(d, postOnly = false, rohan, bengaluru, today)
        val b = WizardLogic.blockers(rules)
        assertTrue(b.isNotEmpty())
        assertTrue("fails R02 only", b.all { it.rule == "R02" })
        val meals = b.first { it.field == Fields.MEALS }
        assertEquals(WizardStep.WHEN_WHERE, WizardLogic.stepOf(meals))
        val fixed = d.copy(project = d.project.copy(meals = Meals.TWO_PLUS_MEALS))
        assertTrue(blocks(fixed, rohan).isEmpty())
        e.saveDraft(rohanUid, WizardLogic.forSave(fixed, false)).getOrThrow()
        e.publish(rohanUid, DemoIds.P_ROHAN_DRAFT).getOrThrow()
        assertEquals(ProjectStatus.OPEN, e.state.value.projects.getValue(DemoIds.P_ROHAN_DRAFT).status)
    }

    @Test fun `a 3-role on-set project built with Pay for all roles and Duplicate publishes and shares with the template`() {
        val (e, rohan) = engine()
        val (d, _) = threeRoleDraft(rohan)
        assertTrue("the wizard and publish agree", blocks(d, rohan).isEmpty())
        val pid = e.saveDraft(rohanUid, WizardLogic.forSave(d, false)).getOrThrow()
        assertEquals("the wizard's own ids are kept", d.project.id, pid)
        e.publish(rohanUid, pid).getOrThrow()
        val stored = DemoQueries(e).draft(e.state.value, rohanUid, pid)!!
        assertEquals("roles come back in the order they were added", d.roles.map { it.id }, stored.roles.map { it.id })
        assertEquals(listOf(RoleCode.DP, RoleCode.GAFFER, RoleCode.SOUND_MIXER), stored.roles.map { it.roleCode })
        val item = DemoQueries(e).roleWithProject(e.state.value, rohanUid, stored.roles.last().id)!!
        val text = ShareText.roleCard(item, "")
        val lines = text.lines()
        assertEquals(4, lines.size)
        assertTrue(lines[0], lines[0].startsWith("CREW CALL · Sound Mixer · \"Idli Sambar Noir\" ("))
        assertTrue(lines[1], lines[1].contains("2 days · ≤10h · Jayanagar, Bengaluru"))
        assertTrue(lines[2], lines[2].startsWith("Pay: ₹2,500/day + ₹1,000 kit · paid on wrap · 2 meals"))
        assertEquals("Apply free on Calltime (Google Play): ${ShareText.PLAY_URL}", lines[3])
    }

    @Test fun `a post-only project needs a picture-lock date for the composer, then publishes with no shoot dates`() {
        val (e, rohan) = engine()
        var d = fresh(rohan)
        d = d.copy(project = d.project.copy(
            title = "Second Sunday", logline = "Already shot on weekends: a retired umpire teaches his granddaughter to bowl.",
            stage = Stage.POST, contentNoneConfirmed = true,
        ))
        val pay = PayDefaults(CompType.PAID_FLAT, amount = 12000, paymentTerms = PaymentTerms.WITHIN_7_DAYS)
        val editor = WizardLogic.newRole(WizardLogic.newId(), RoleCode.EDITOR, d.project, pay, "INR", postOnly = true).copy(postDeadline = today.plusDays(30))
        val composer = WizardLogic.newRole(WizardLogic.newId(), RoleCode.COMPOSER, d.project, pay, "INR", postOnly = true).copy(postDeadline = today.plusDays(45))
        d = d.copy(roles = listOf(editor, composer))
        val before = blocks(d, rohan, postOnly = true)
        assertEquals(listOf("R20"), before.map { it.rule }.distinct())
        assertTrue("no shoot logistics are asked", before.none { it.rule == "R05" })
        d = d.copy(project = d.project.copy(pictureLockDate = today.plusDays(14)))
        assertTrue(blocks(d, rohan, postOnly = true).isEmpty())
        val pid = e.saveDraft(rohanUid, WizardLogic.forSave(d, postOnly = true)).getOrThrow()
        e.publish(rohanUid, pid).getOrThrow()
        val p = e.state.value.projects.getValue(pid)
        assertEquals(ProjectStatus.OPEN, p.status)
        assertTrue(p.shootDates.isEmpty())
        assertTrue(ListingRules.isPostOnly(e.state.value.roles.values.filter { it.projectId == pid }))
        val item = DemoQueries(e).roleWithProject(e.state.value, rohanUid, composer.id)!!
        assertTrue(ShareText.roleCard(item, "").lines()[1].startsWith("Remote · picture lock"))
    }

    @Test fun `R21 and R22 are warnings, never blockers`() {
        val (_, rohan) = engine()
        val (d, _) = threeRoleDraft(rohan)
        val rules = WizardLogic.check(d, false, rohan, bengaluru, today)
        val r22 = rules.firstOrNull { it.rule == "R22" }
        assertNotNull("pre-production with on-set roles and no post role", r22)
        assertEquals(Severity.WARN, r22!!.severity)
        assertFalse("nudges aren't listed twice", WizardLogic.notes(rules).any { it.rule == "R22" })
    }

    // ───────────── helpers ─────────────

    @Test fun `quick weekends are Saturdays at least 5 days out`() {
        val w = WizardLogic.upcomingWeekends(today)
        assertEquals(3, w.size)
        w.forEach { (sat, sun) ->
            assertEquals(DayOfWeek.SATURDAY, sat.dayOfWeek)
            assertEquals(sat.plusDays(1), sun)
            assertTrue(!sat.isBefore(today.plusDays(5)))
        }
        assertEquals(5, WizardLogic.range(today, today.plusDays(4)).size)
        assertEquals(listOf(today), WizardLogic.range(today, today.minusDays(3)))
    }

    @Test fun `ids are valid UUIDs that sort in creation order`() {
        val ids = (1..50).map { WizardLogic.newId(1_790_000_000_000L) }
        ids.forEach { UUID.fromString(it) }
        assertEquals(ids, ids.sorted())
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(ids.first(), WizardLogic.newId())
    }

    @Test fun `identical messages across roles collapse into one review item`() {
        val (e, rohan) = engine()
        val d = DemoQueries(e).draft(e.state.value, rohanUid, DemoIds.P_ROHAN_DRAFT)!!
        val b = WizardLogic.blockers(WizardLogic.check(d, false, rohan, bengaluru, today))
        val grouped = groupIssues(b, d.roles.associate { it.id to com.tff.app.domain.CardText.roleName(it) })
        // seed v3: the draft's only blocker is the meals one ("feed people"), so both roles collapse into one item
        assertEquals(1, grouped.size)
        assertEquals("the project-level meals fix comes first", Fields.MEALS, grouped.first().first.field)
        assertEquals("Lead Actor · Gaffer", grouped.first().second)
    }
}
