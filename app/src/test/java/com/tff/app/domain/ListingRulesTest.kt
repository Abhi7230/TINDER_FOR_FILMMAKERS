package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListingRulesTest {

    private val today = Fx.TODAY
    private val city = Fx.city()
    private val rohan = Fx.profile(id = "u_rohan", name = "Rohan Kulkarni", primaryRole = RoleCode.DIRECTOR, cityId = "blr", areaId = "blr_jayanagar")

    private val sound = Fx.role(id = "r_sound", roleCode = RoleCode.SOUND_MIXER, amountMin = 2500, kitFeeDay = 1000)
    private val editor = Fx.role(id = "r_edit", roleCode = RoleCode.EDITOR, compType = CompType.PAID_FLAT, amountMin = 15000,
        paymentTerms = PaymentTerms.WITHIN_7_DAYS, remoteOk = true, postDeadline = today.plusDays(60))
    private val base = ProjectDraft(Fx.project(), listOf(sound, editor))

    private fun check(d: ProjectDraft, owner: Profile = rohan) = ListingRules.check(d, owner, city, today)
    private fun rules(d: ProjectDraft, owner: Profile = rohan) = check(d, owner).map { it.rule }.toSet()
    private fun withProject(f: (Project) -> Project) = base.copy(project = f(base.project))
    private fun withRoles(vararg r: ProjectRole) = base.copy(roles = r.toList())
    private fun result(d: ProjectDraft, rule: String, owner: Profile = rohan) = check(d, owner).first { it.rule == rule }

    @Test fun cleanDraftHasNoResults() {
        assertEquals(emptyList<RuleResult>(), check(base))
        assertTrue(ListingRules.canPublish(check(base)))
    }

    @Test fun r01PayIsStructured() {
        val noAmount = withRoles(sound.copy(amountMin = null, amountMax = null), editor)
        val r = result(noAmount, "R01")
        assertEquals(Severity.BLOCK, r.severity)
        assertEquals("Add pay, or mark it unpaid and say what people get.", r.message)
        assertEquals("r_sound", r.roleId)
        assertEquals("amount", r.field)
        assertFalse(ListingRules.canPublish(check(noAmount)))
        val noTerms = withRoles(sound.copy(paymentTerms = null), editor)
        assertEquals("paymentTerms", result(noTerms, "R01").field)
        val stipendZero = withRoles(sound.copy(compType = CompType.STIPEND, amountMin = 0, amountMax = 0), editor)
        assertTrue("R01" in rules(stipendZero))
        val backwards = withRoles(sound.copy(amountMin = 3000, amountMax = 2000), editor)
        assertTrue("R01" in rules(backwards))
    }

    @Test fun r02UnpaidOnSetNeedsTwoPerksAndAMeal() {
        val dp = Fx.role(id = "r_dp", roleCode = RoleCode.DP, compType = CompType.UNPAID, perks = setOf(Perk.STEP_UP_CREDIT))
        val bare = base.copy(project = base.project.copy(transport = Transport.NONE, travelAllowancePerDay = null, meals = Meals.ONE_MEAL),
            roles = listOf(sound, editor, dp))
        val r = result(bare, "R02")
        assertEquals(Severity.BLOCK, r.severity)
        assertEquals("perks", r.field)
        assertEquals("r_dp", r.roleId)
        // Footage + step-up credit and one meal is enough.
        val fixed = bare.copy(roles = listOf(sound, editor, dp.copy(perks = setOf(Perk.STEP_UP_CREDIT, Perk.FOOTAGE_BY_DATE), footageDueWeeks = 8)))
        assertFalse("R02" in rules(fixed))
        // Derived perks count: 2+ meals (MEALS) + TA (TRAVEL_COVERED).
        val derived = base.copy(roles = listOf(sound, editor, dp.copy(perks = emptySet())))
        assertFalse("R02" in rules(derived))
        // Snacks only fails the meal rule.
        val snacks = fixed.copy(project = fixed.project.copy(meals = Meals.CRAFTY_ONLY))
        val meal = check(snacks).first { it.rule == "R02" }
        assertEquals("meals", meal.field)
        assertEquals("If you can't pay people, feed people. Unpaid shoots need at least one meal a day.", meal.message)
    }

    @Test fun r03UnpaidActorNeedsFootageWithin26Weeks() {
        val actor = Fx.role(id = "r_lead", roleCode = RoleCode.ACTOR, compType = CompType.UNPAID, perks = setOf(Perk.STEP_UP_CREDIT))
        assertTrue("R03" in rules(withRoles(sound, editor, actor)))
        assertTrue("R03" in rules(withRoles(sound, editor, actor.copy(perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 30))))
        assertFalse("R03" in rules(withRoles(sound, editor, actor.copy(perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 6))))
    }

    @Test fun r04BelowFloorBecomesStipend() {
        val low = sound.copy(amountMin = 800, amountMax = 800)
        val d = withRoles(low, editor)
        val r = result(d, "R04")
        assertEquals(Severity.AUTO, r.severity)
        assertTrue(ListingRules.canPublish(check(d)))
        val fixed = ListingRules.applyAuto(d, city)
        assertEquals(CompType.STIPEND, fixed.roles[0].compType)
        assertEquals(800, fixed.roles[0].amountMax)
        assertEquals(CompType.PAID_FLAT, fixed.roles[1].compType)          // post flat fee has no day rate
        assertFalse(CardText.payLine(fixed.roles[0], fixed.project).contains("Paid"))
        // A 1,500 flat fee over 2 days is 750/day → Stipend 750/day.
        val flat = ListingRules.applyAuto(withRoles(sound.copy(compType = CompType.PAID_FLAT, amountMin = 1500, amountMax = 1500)), city)
        assertEquals(CompType.STIPEND, flat.roles[0].compType)
        assertEquals(750, flat.roles[0].amountMax)
        // At or above the floor stays paid.
        assertFalse("R04" in rules(withRoles(sound.copy(amountMin = 1000, amountMax = 1000), editor)))
    }

    @Test fun r05OnSetLogistics() {
        assertEquals("shootDates", result(withProject { it.copy(shootDates = listOf(today.minusDays(1), today.plusDays(1))) }, "R05").field)
        assertEquals("shootDates", result(withProject { it.copy(shootDates = emptyList()) }, "R05").field)
        assertEquals("hoursCap", result(withProject { it.copy(hoursCap = 18) }, "R05").field)
        assertEquals("hoursCap", result(withProject { it.copy(hoursCap = 1) }, "R05").field)
        assertEquals("meals", result(withProject { it.copy(meals = null) }, "R05").field)
        assertEquals("transport", result(withProject { it.copy(transport = null) }, "R05").field)
        assertEquals("travelAllowancePerDay", result(withProject { it.copy(travelAllowancePerDay = null) }, "R05").field)
        assertEquals("shootLocationType", result(withProject { it.copy(shootLocationType = null) }, "R05").field)
        assertEquals("dateCertainty", result(withProject { it.copy(dateCertainty = null) }, "R05").field)
        assertTrue(check(withProject { it.copy(shootDates = emptyList()) }).all { it.severity == Severity.BLOCK || it.rule != "R05" })
    }

    @Test fun r05SkipsOnPostOnlyProjects() {
        val composer = Fx.role(id = "r_comp", roleCode = RoleCode.COMPOSER, compType = CompType.PAID_FLAT, amountMin = 12000,
            paymentTerms = PaymentTerms.WITHIN_7_DAYS, remoteOk = true, postDeadline = today.plusDays(60))
        val postOnly = ProjectDraft(Fx.postProject(id = "p_fc", pictureLockDate = today.plusDays(20)), listOf(editor, composer))
        assertTrue(ListingRules.isPostOnly(postOnly.roles))
        assertEquals(emptyList<RuleResult>(), check(postOnly))
        // Each post role needs a delivery date at least a week out.
        val tooSoon = postOnly.copy(roles = listOf(editor.copy(postDeadline = today.plusDays(5)), composer))
        assertEquals("postDeadline", result(tooSoon, "R05").field)
        val none = postOnly.copy(roles = listOf(editor.copy(postDeadline = null), composer))
        assertEquals("r_edit", result(none, "R05").roleId)
        assertFalse(ListingRules.isPostOnly(base.roles))
        assertFalse(ListingRules.isPostOnly(emptyList()))
    }

    @Test fun r06LongDaysWarn() {
        val r = result(withProject { it.copy(hoursCap = 14) }, "R06")
        assertEquals(Severity.WARN, r.severity)
        assertEquals("Long days lose crew. Can you cap at 10-12?", r.message)
        assertTrue(ListingRules.canPublish(check(withProject { it.copy(hoursCap = 14) })))
        assertFalse("R06" in rules(withProject { it.copy(hoursCap = 12) }))
    }

    @Test fun r07ContentMustBeAnswered() {
        assertTrue("R07" in rules(withProject { it.copy(contentNoneConfirmed = false) }))
        assertFalse("R07" in rules(withProject { it.copy(contentNoneConfirmed = false, contentFlags = setOf(ContentFlag.NIGHT_SHOOT)) }))
    }

    @Test fun r08IntimacyNeedsCoordinatorAnswerAndSafetyMeasures() {
        val flagged = withProject { it.copy(contentFlags = setOf(ContentFlag.INTIMACY_SCENE)) }
        val fields = check(flagged).filter { it.rule == "R08" }.map { it.field }.toSet()
        assertEquals(setOf("intimacyCoordinator", "safetyMeasures"), fields)
        val short = withProject { it.copy(contentFlags = setOf(ContentFlag.INTIMACY_SCENE), intimacyCoordinator = true, safetyMeasures = "Closed set") }
        assertEquals("safetyMeasures", result(short, "R08").field)
        val ok = withProject { it.copy(contentFlags = setOf(ContentFlag.INTIMACY_SCENE), intimacyCoordinator = false,
            safetyMeasures = "Closed set, choreographed in rehearsal, robes on hand.") }
        assertFalse("R08" in rules(ok))
    }

    @Test fun r09StuntsAndFirearms() {
        val flagged = withProject { it.copy(contentFlags = setOf(ContentFlag.FIREARMS_PROPS)) }
        assertEquals(setOf("stuntOrArmourer", "safetyMeasures"), check(flagged).filter { it.rule == "R09" }.map { it.field }.toSet())
        val ok = withProject { it.copy(contentFlags = setOf(ContentFlag.STUNTS_FIGHTS), stuntOrArmourer = true, safetyMeasures = "Fight coordinator on set, mats.") }
        assertFalse("R09" in rules(ok))
    }

    @Test fun r10MinorsNeedAGuardian() {
        assertTrue("R10" in rules(withProject { it.copy(contentFlags = setOf(ContentFlag.MINORS_ON_SET)) }))
        assertTrue("R10" in rules(withProject { it.copy(contentFlags = setOf(ContentFlag.MINORS_ON_SET), guardianPresent = false) }))
        assertFalse("R10" in rules(withProject { it.copy(contentFlags = setOf(ContentFlag.MINORS_ON_SET), guardianPresent = true) }))
    }

    @Test fun r11CastRolesNeedAnAuditionMode() {
        val actor = Fx.role(id = "r_lead", roleCode = RoleCode.ACTOR, amountMin = 3000)
        val d = base.copy(project = base.project.copy(auditionMode = null), roles = listOf(sound, editor, actor))
        assertEquals("auditionMode", result(d, "R11").field)
        assertFalse("R11" in rules(d.copy(project = d.project.copy(auditionMode = AuditionMode.VIDEO_CALL))))
        // No cast, no audition needed.
        assertFalse("R11" in rules(withProject { it.copy(auditionMode = null) }))
    }

    @Test fun r12MoneyFromTalentBlocks() {
        val d = withProject { it.copy(logline = "A dark comedy about a call centre. Registration fee of ₹500 for all selected actors.") }
        val r = result(d, "R12")
        assertEquals("Calltime never allows charging cast or crew.", r.message)
        assertEquals("logline", r.field)
        val q = withRoles(sound.copy(screeningQuestion = "Can you pay a security deposit for the kit?"), editor)
        assertEquals("r_sound", result(q, "R12").roleId)
        assertFalse("R12" in rules(withProject { it.copy(logline = "No registration fee, ever. A night-shift coder and a darshini owner keep the same hour.") }))
    }

    @Test fun r13NoSpecWorkOnUnpaidRoles() {
        val unpaidEditor = editor.copy(compType = CompType.UNPAID, amountMin = null, amountMax = null, paymentTerms = null,
            perks = setOf(Perk.FINAL_FILM_BY_DATE, Perk.STEP_UP_CREDIT), screeningQuestion = "Send a test edit of scene 3")
        assertTrue("R13" in rules(withRoles(sound, unpaidEditor)))
        assertTrue("R13" in rules(withRoles(sound, unpaidEditor.copy(screeningQuestion = "We need a custom demo reel"))))
        assertFalse("R13" in rules(withRoles(sound, unpaidEditor.copy(screeningQuestion = "Which short are you proudest of?"))))
        assertFalse("R13" in rules(withRoles(sound, editor.copy(screeningQuestion = "Send a test edit of scene 3"))))
    }

    @Test fun r14NoContactInPublicText() {
        val r = result(withProject { it.copy(logline = "Two strangers share an umbrella. Call 98201 23456 to audition today.") }, "R14")
        assertEquals("Keep contact in Calltime until you're booked.", r.message)
        assertTrue("R14" in rules(withRoles(sound.copy(screeningQuestion = "Mail your reel to rohan@example.com"), editor)))
        assertTrue("R14" in rules(withRoles(sound.copy(scopeIncludes = listOf("DM @filtercoffee.films")), editor)))
        // A lookbook on a YouTube channel URL isn't contact info.
        assertFalse("R14" in rules(withProject { it.copy(lookbookUrl = "https://youtube.com/@filtercoffeefilms") }))
    }

    @Test fun r15EuphemismsOnUnpaidRoles() {
        val unpaidDp = Fx.role(id = "r_dp", roleCode = RoleCode.DP, compType = CompType.UNPAID,
            perks = setOf(Perk.STEP_UP_CREDIT, Perk.FOOTAGE_BY_DATE), footageDueWeeks = 6)
        val passion = base.copy(project = base.project.copy(logline = "A passion project about two strangers and one umbrella in the rain."),
            roles = listOf(sound, editor, unpaidDp))
        val r = result(passion, "R15")
        assertEquals(Severity.WARN, r.severity)
        assertEquals("Say what people get instead.", r.message)
        val deferred = passion.copy(roles = listOf(sound, editor, unpaidDp.copy(roleDetails = mapOf("package" to "Deferred pay once we sell it"))))
        assertEquals("Deferred pay shows as Unpaid. Say what people get now.", check(deferred).first { it.rule == "R15" && it.roleId == "r_dp" }.message)
        // Paid-only projects don't get the warning.
        assertFalse("R15" in rules(passion.copy(roles = listOf(sound, editor))))
    }

    @Test fun r16ScopeSpanningThreeDepartments() {
        val dp = Fx.role(id = "r_dp", roleCode = RoleCode.DP, amountMin = 3000, scopeIncludes = listOf("Camera", "Lighting", "Picture cut", "Dialogue recording"))
        val r = result(withRoles(sound, editor, dp), "R16")
        assertEquals("This reads like 3 jobs. Split it into roles?", r.message)
        assertEquals("r_dp", r.roleId)
        assertFalse("R16" in rules(withRoles(sound, editor, dp.copy(scopeIncludes = listOf("Camera", "Lighting design", "Shot list with director")))))
        assertTrue(ListingRules.scopeDepts(dp).containsAll(setOf(Dept.CAMERA, Dept.GE, Dept.POST, Dept.SOUND)))
    }

    @Test fun r17OwnGearNoKitFeeWarns() {
        val r = result(withRoles(sound.copy(gearPolicy = GearPolicy.BYO_NO_FEE, kitFeeDay = null), editor), "R17")
        assertEquals(Severity.WARN, r.severity)
        assertEquals("gearPolicy", r.field)
        assertFalse("R17" in rules(withRoles(Fx.role(id = "r_pd", roleCode = RoleCode.PRODUCTION_DESIGNER, gearPolicy = GearPolicy.BYO_NO_FEE), editor)))
    }

    @Test fun r18Basics() {
        assertEquals("title", result(withProject { it.copy(title = "A") }, "R18").field)
        assertEquals("logline", result(withProject { it.copy(logline = "Too short") }, "R18").field)
        assertEquals("languages", result(withProject { it.copy(languages = emptyList()) }, "R18").field)
        assertEquals("roles", result(base.copy(roles = emptyList()), "R18").field)
        assertTrue(check(withProject { it.copy(title = "x".repeat(61)) }).any { it.rule == "R18" && it.severity == Severity.BLOCK })
    }

    @Test fun r19IntimateCastProjectsNeedIdVerifiedOwner() {
        val actor = Fx.role(id = "r_lead", roleCode = RoleCode.ACTOR, amountMin = 3000)
        val d = base.copy(project = base.project.copy(contentFlags = setOf(ContentFlag.INTIMACY_SCENE), intimacyCoordinator = true,
            safetyMeasures = "Intimacy coordinator on set, closed set, rehearsed."), roles = listOf(sound, editor, actor))
        val r = result(d, "R19")
        assertEquals(Severity.BLOCK, r.severity)
        assertEquals("Projects with intimate scenes need an ID-verified owner. Book a 5-minute check.", r.message)
        assertFalse("R19" in rules(d, rohan.copy(idVerified = true)))
        // No cast role: no R19.
        assertFalse("R19" in rules(d.copy(roles = listOf(sound, editor))))
    }

    @Test fun r20MusicAndSoundDesignNeedPictureLock() {
        val composer = Fx.role(id = "r_comp", roleCode = RoleCode.COMPOSER, compType = CompType.PAID_FLAT, amountMin = 12000,
            remoteOk = true, postDeadline = today.plusDays(60))
        val r = result(withRoles(sound, editor, composer), "R20")
        assertEquals("Composers and sound designers need a picture-lock date to judge the job.", r.message)
        assertEquals("r_comp", r.roleId)
        val locked = base.copy(project = base.project.copy(pictureLockDate = today.plusDays(30)), roles = listOf(sound, editor, composer))
        assertFalse("R20" in rules(locked))
    }

    @Test fun r21UnderThreeWeeksAfterLockWarns() {
        val lock = LocalDate.of(2027, 1, 10)
        val composer = Fx.role(id = "r_comp", roleCode = RoleCode.SOUND_DESIGNER, compType = CompType.PAID_FLAT, amountMin = 12000,
            remoteOk = true, postDeadline = LocalDate.of(2027, 1, 24))
        val d = base.copy(project = base.project.copy(pictureLockDate = lock), roles = listOf(sound, editor, composer))
        assertEquals(Severity.WARN, result(d, "R21").severity)
        assertFalse("R21" in rules(d.copy(roles = listOf(sound, editor, composer.copy(postDeadline = LocalDate.of(2027, 2, 7))))))
    }

    @Test fun r22AttachPostEarly() {
        val r = result(withRoles(sound), "R22")
        assertEquals(Severity.WARN, r.severity)
        assertEquals("Editors and composers do better work when they're in early. Add them now as remote roles?", r.message)
        assertFalse("R22" in rules(withRoles(sound, editor)))
        assertFalse("R22" in rules(base.copy(project = base.project.copy(stage = Stage.PRODUCTION), roles = listOf(sound))))
    }

    @Test fun privateHomeForcesBringAFriend() {
        val home = withProject { it.copy(shootLocationType = LocationType.PRIVATE_HOME, bringFriendWelcome = false) }
        assertTrue(ListingRules.applyAuto(home, city).project.bringFriendWelcome)
    }
}
