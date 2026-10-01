package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MatchEngineTest {

    private val today = Fx.TODAY
    private val now = Fx.NOW
    private val sat21 = LocalDate.of(2026, 11, 21)
    private val wed18 = LocalDate.of(2026, 11, 18)

    /** Latitude [km] north of Versova, so distances are exact. */
    private fun latAt(km: Double) = Fx.VERSOVA_LAT + km / 111.19492664
    private fun near(km: Double, item: RoleWithProject) = item.copy(areaLat = latAt(km), areaLng = Fx.VERSOVA_LNG)

    private val meera = Fx.profile(goals = listOf(Goal.REEL_FOOTAGE), genresPref = listOf("Drama"))
    private val meeraV = Fx.viewer(meera)

    /** PRD §7.5 "Last Local". */
    private fun lastLocal(): RoleWithProject {
        val project = Fx.project(
            id = "p_ll", ownerId = "u_owner", title = "Last Local", languages = listOf("hi"), cityId = "mum", areaId = "mum_andheri",
            shootDates = listOf(Fx.SAT_14_NOV, sat21), meals = Meals.TWO_PLUS_MEALS, transport = Transport.MILEAGE_OR_TA_PAID,
            travelAllowancePerDay = 200, contentFlags = setOf(ContentFlag.NIGHT_SHOOT), contentNoneConfirmed = false,
            ambition = Ambition.FESTIVAL, genres = listOf("Drama"), format = Format.SHORT, scriptStatus = ScriptStatus.LOCKED,
        )
        val role = Fx.role(id = "r_ll_lead", projectId = "p_ll", roleCode = RoleCode.ACTOR, compType = CompType.UNPAID,
            perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 6, roleDetails = mapOf("size" to "LEAD"))
        val owner = Fx.owner(id = "u_owner", name = "Asha Rao", idVerified = false,
            reputation = Fx.reputation(id = "u_owner", shoots = 3, footage = Ratio(3, 3), kept = Ratio(3, 3), paidOnTime = null))
        return near(3.0, Fx.item(role = role, project = project, owner = owner, cityName = "Mumbai", areaName = "Andheri", paidRoles = 0))
    }

    // ───────────── Worked example ─────────────

    @Test fun workedExampleIs84point8() {
        val f = MatchEngine.fit(meeraV, lastLocal(), today)
        assertEquals(25.0, f.role, 1e-9)
        assertEquals(15.0, f.comp, 1e-9)
        assertEquals(14.8, f.logistics, 0.05)
        assertEquals(10.0, f.schedule, 1e-9)
        assertEquals(9.0, f.cred, 1e-9)
        assertEquals(6.0, f.creative, 1e-9)
        assertEquals(5.0, f.level, 1e-9)
        assertEquals(84.8, f.total, 0.5)
        assertTrue(MatchEngine.failedGates(meeraV, lastLocal(), today).isEmpty())
        // No multipliers apply (published 5 days ago, active owner, start > today + 10).
        assertEquals(f.total, MatchEngine.score(meeraV, lastLocal(), today, now), 1e-9)
    }

    @Test fun perksAndDayRate() {
        val ll = lastLocal()
        assertEquals(setOf(Perk.FOOTAGE_BY_DATE, Perk.MEALS, Perk.TRAVEL_COVERED, Perk.SHORT_COMMITMENT, Perk.SCRIPT_READY),
            MatchEngine.perks(ll.role, ll.project))
        val p = Fx.project()
        assertEquals(2500, MatchEngine.dayRate(Fx.role(amountMin = 2000, amountMax = 2500), p))
        assertEquals(5000, MatchEngine.dayRate(Fx.role(compType = CompType.PAID_FLAT, amountMin = 10000), p))
        assertNull(MatchEngine.dayRate(Fx.role(compType = CompType.UNPAID), p))
        val post = Fx.role(roleCode = RoleCode.EDITOR, compType = CompType.PAID_FLAT, amountMin = 30000, remoteOk = true,
            postDeadline = today.plusDays(40))
        assertNull(MatchEngine.dayRate(post, Fx.postProject()))
    }

    // ───────────── Gates ─────────────

    private fun gates(v: Viewer, c: RoleWithProject) = MatchEngine.failedGates(v, c, today)

    @Test fun g1Role() {
        val sound = near(3.0, Fx.item())
        assertTrue(Gate.G1_ROLE in gates(meeraV, sound))
        val kabir = Fx.profile(id = "u_kabir", primaryRole = RoleCode.GAFFER, stepUpRoles = listOf(RoleCode.DP), compMode = CompMode.OPEN_TO_UNPAID)
        val dpFriendly = near(3.0, Fx.item(role = Fx.role(roleCode = RoleCode.DP, stepUpFriendly = true)))
        val dpNotFriendly = near(3.0, Fx.item(role = Fx.role(roleCode = RoleCode.DP, stepUpFriendly = false)))
        assertFalse(Gate.G1_ROLE in gates(Fx.viewer(kabir), dpFriendly))
        assertTrue(Gate.G1_ROLE in gates(Fx.viewer(kabir), dpNotFriendly))
        val withSecondary = kabir.copy(secondaryRoles = listOf(SecondaryRole(RoleCode.SOUND_MIXER, 2)))
        assertFalse(Gate.G1_ROLE in gates(Fx.viewer(withSecondary), sound))
    }

    @Test fun g2Place() {
        val inRadius = lastLocal()
        assertFalse(Gate.G2_PLACE in gates(meeraV, inRadius))
        val far = near(30.0, lastLocal())
        assertTrue(Gate.G2_PLACE in gates(meeraV, far))
        // Remote role + remote-OK viewer passes from anywhere.
        val editor = Fx.profile(id = "u_ed", primaryRole = RoleCode.EDITOR, remoteOk = true)
        val remoteRole = near(900.0, Fx.item(role = Fx.role(roleCode = RoleCode.EDITOR, compType = CompType.PAID_FLAT, amountMin = 20000,
            remoteOk = true, postDeadline = today.plusDays(40)), project = Fx.postProject(id = "p_fc")))
        assertFalse(Gate.G2_PLACE in gates(Fx.viewer(editor), remoteRole))
        assertTrue(Gate.G2_PLACE in gates(Fx.viewer(editor.copy(remoteOk = false)), remoteRole))
        // Travel if covered: transport + 1 day (or lodging) + within 3× radius.
        val traveller = meera.copy(travelIfCovered = true)
        val oneDay = near(40.0, lastLocal().let { it.copy(project = it.project.copy(shootDates = listOf(Fx.SAT_14_NOV))) })
        assertFalse(Gate.G2_PLACE in gates(Fx.viewer(traveller), oneDay))
        val twoDaysNoLodging = near(40.0, lastLocal())
        assertTrue(Gate.G2_PLACE in gates(Fx.viewer(traveller), twoDaysNoLodging))
        val twoDaysLodging = near(40.0, lastLocal().let { it.copy(project = it.project.copy(lodging = true)) })
        assertFalse(Gate.G2_PLACE in gates(Fx.viewer(traveller), twoDaysLodging))
        assertTrue(Gate.G2_PLACE in gates(Fx.viewer(traveller), near(50.0, oneDay)))
    }

    @Test fun g3Time() {
        // Weekends-only actor and a weekday shoot.
        val weekday = lastLocal().let { it.copy(project = it.project.copy(shootDates = listOf(wed18))) }
        val weekendsOnly = Fx.viewer(meera.copy(availabilityPattern = AvailabilityPattern.WEEKENDS_ONLY))
        assertTrue(Gate.G3_TIME in gates(weekendsOnly, weekday))
        assertFalse(Gate.G3_TIME in gates(weekendsOnly, lastLocal()))
        // Cast roles need every day; crew need at least one.
        val busyOne = Fx.viewer(meera.copy(busyDates = setOf(sat21)))
        assertTrue(Gate.G3_TIME in gates(busyOne, lastLocal()))
        val gaffer = Fx.profile(id = "u_g", primaryRole = RoleCode.GAFFER, busyDates = setOf(Fx.SUN_15_NOV))
        val gafferRole = near(3.0, Fx.item(role = Fx.role(roleCode = RoleCode.GAFFER)))
        assertFalse(Gate.G3_TIME in gates(Fx.viewer(gaffer), gafferRole))
        // Evenings & weekends: a short weekday counts.
        val evenings = Fx.viewer(meera.copy(availabilityPattern = AvailabilityPattern.EVENINGS_AND_WEEKENDS))
        assertTrue(Gate.G3_TIME in gates(evenings, weekday))
        val shortWeekday = weekday.copy(project = weekday.project.copy(hoursCap = 4))
        assertFalse(Gate.G3_TIME in gates(evenings, shortWeekday))
        // Post roles: deadline at least a week out.
        val editor = Fx.viewer(Fx.profile(id = "u_ed", primaryRole = RoleCode.EDITOR, remoteOk = true))
        fun post(dl: LocalDate) = Fx.item(role = Fx.role(roleCode = RoleCode.EDITOR, compType = CompType.STIPEND, amountMin = 5000,
            remoteOk = true, postDeadline = dl), project = Fx.postProject(id = "p_fc"))
        assertTrue(Gate.G3_TIME in gates(editor, post(today.plusDays(6))))
        assertFalse(Gate.G3_TIME in gates(editor, post(today.plusDays(7))))
    }

    @Test fun g4Money() {
        val dana = Fx.profile(id = "u_dana", primaryRole = RoleCode.SOUND_MIXER, compMode = CompMode.PAID_ONLY, languages = listOf("en"))
        val paid = near(3.0, Fx.item(role = Fx.role(amountMin = 350, currency = "USD")))
        val unpaid = near(3.0, Fx.item(role = Fx.role(compType = CompType.UNPAID, perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 4)))
        val stipend = near(3.0, Fx.item(role = Fx.role(compType = CompType.STIPEND, amountMin = 400)))
        assertTrue(Gate.G4_MONEY in gates(Fx.viewer(dana), unpaid))
        assertTrue(Gate.G4_MONEY in gates(Fx.viewer(dana), stipend))
        assertFalse(Gate.G4_MONEY in gates(Fx.viewer(dana), paid))
        assertFalse(Gate.G4_MONEY in gates(Fx.viewer(dana, minDayRate = 350), paid))
        assertTrue(Gate.G4_MONEY in gates(Fx.viewer(dana, minDayRate = 400), paid))
        // Selective: unpaid only when every must-have is there.
        val selective = dana.copy(compMode = CompMode.PAID_AND_SELECTIVE, unpaidMustHaves = listOf(Perk.FOOTAGE_BY_DATE, Perk.MEALS))
        assertFalse(Gate.G4_MONEY in gates(Fx.viewer(selective), unpaid))         // MEALS derived from 2 meals
        val noMeals = unpaid.copy(project = unpaid.project.copy(meals = Meals.ONE_MEAL))
        assertTrue(Gate.G4_MONEY in gates(Fx.viewer(selective), noMeals))
        assertFalse(Gate.G4_MONEY in gates(Fx.viewer(selective), paid))
        // Open to unpaid: always passes.
        assertFalse(Gate.G4_MONEY in gates(Fx.viewer(dana.copy(compMode = CompMode.OPEN_TO_UNPAID)), unpaid))
    }

    @Test fun paidOnlyPassesG4OnFlatFeePostRole() {
        val dana = Fx.profile(id = "u_dana", primaryRole = RoleCode.SOUND_MIXER, compMode = CompMode.PAID_ONLY, remoteOk = true,
            secondaryRoles = listOf(SecondaryRole(RoleCode.SOUND_DESIGNER, 3)), languages = listOf("en"))
        val soft = Fx.item(
            role = Fx.role(id = "r_sd", projectId = "p_soft", roleCode = RoleCode.SOUND_DESIGNER, compType = CompType.PAID_FLAT, amountMin = 2400,
                currency = "USD", paymentTerms = PaymentTerms.WITHIN_7_DAYS, remoteOk = true, postDeadline = today.plusDays(33)),
            project = Fx.postProject(pictureLockDate = today.plusDays(5)),
        )
        // A flat fee has no day rate, so even a $350 minimum passes.
        assertTrue(MatchEngine.failedGates(Fx.viewer(dana, minDayRate = 350), soft, today).isEmpty())
        val f = MatchEngine.fit(Fx.viewer(dana, minDayRate = 350), soft, today)
        assertEquals(16.0 + 0.0, f.comp, 1e-9)       // no-min-rate base, WITHIN_7_DAYS gets no terms bonus
        assertEquals(12.0, f.logistics, 1e-9)
        assertEquals(10.0, f.schedule, 1e-9)
    }

    @Test fun g5Content() {
        val wontNight = Fx.viewer(meera.copy(contentWontDo = listOf(ContentFlag.NIGHT_SHOOT)))
        assertTrue(Gate.G5_CONTENT in gates(wontNight, lastLocal()))
        val wontGore = Fx.viewer(meera.copy(contentWontDo = listOf(ContentFlag.GORE)))
        assertFalse(Gate.G5_CONTENT in gates(wontGore, lastLocal()))
    }

    @Test fun g6Status() {
        val ok = lastLocal()
        assertFalse(Gate.G6_STATUS in gates(meeraV, ok))
        assertTrue(Gate.G6_STATUS in gates(meeraV, ok.copy(role = ok.role.copy(status = RoleStatus.FILLED))))
        assertTrue(Gate.G6_STATUS in gates(meeraV, ok.copy(role = ok.role.copy(applicationsCount = 20, applicationCap = 20))))
        assertTrue(Gate.G6_STATUS in gates(meeraV, ok.copy(owner = ok.owner.copy(lastActiveAt = now - 20 * Fx.DAY_MS))))
        assertTrue(Gate.G6_STATUS in gates(meeraV, ok.copy(project = ok.project.copy(shootDates = listOf(today.minusDays(1), today.plusDays(1))))))
        // Viewer is the owner.
        val own = ok.copy(project = ok.project.copy(ownerId = meera.id), owner = ok.owner.copy(id = meera.id))
        assertTrue(Gate.G6_STATUS in gates(meeraV, own))
    }

    @Test fun postOnlyRolesPassG6WithPastOrNullShootDates() {
        val editor = Fx.viewer(Fx.profile(id = "u_ed", primaryRole = RoleCode.EDITOR, remoteOk = true, languages = listOf("en")))
        val role = Fx.role(roleCode = RoleCode.EDITOR, compType = CompType.PAID_FLAT, amountMin = 30000, remoteOk = true,
            postDeadline = today.plusDays(30))
        val nullDates = Fx.item(role = role, project = Fx.postProject(id = "p_fc"))
        val pastDates = Fx.item(role = role, project = Fx.postProject(id = "p_fc").copy(shootDates = listOf(today.minusDays(40), today.minusDays(39))))
        assertTrue(MatchEngine.failedGates(editor, nullDates, today).isEmpty())
        assertTrue(MatchEngine.failedGates(editor, pastDates, today).isEmpty())
    }

    @Test fun g7Language() {
        val kannada = lastLocal().let { it.copy(project = it.project.copy(languages = listOf("kn"))) }
        assertTrue(Gate.G7_LANGUAGE in gates(meeraV, kannada))
        assertFalse(Gate.G7_LANGUAGE in gates(meeraV, lastLocal()))
        // Crew roles don't need the language.
        val mixer = Fx.viewer(Fx.profile(id = "u_m", primaryRole = RoleCode.SOUND_MIXER, languages = listOf("en")))
        val kannadaSound = near(3.0, Fx.item(project = Fx.project(languages = listOf("kn"))))
        assertFalse(Gate.G7_LANGUAGE in gates(mixer, kannadaSound))
    }

    // ───────────── Multipliers ─────────────

    @Test fun multipliers() {
        val ll = lastLocal()
        assertEquals(0.70, MatchEngine.mResp(ll.owner.copy(closureRate14d = 0.4)), 1e-9)
        assertEquals(1.05, MatchEngine.mResp(ll.owner.copy(closureRate14d = 0.95)), 1e-9)
        assertEquals(1.0, MatchEngine.mResp(ll.owner.copy(closureRate14d = null)), 1e-9)
        assertEquals(1.05, MatchEngine.mFresh(ll.copy(project = ll.project.copy(publishedAt = now - 3600_000)), now), 1e-9)
        assertEquals(0.80, MatchEngine.mFresh(ll.copy(owner = ll.owner.copy(lastActiveAt = now - 9 * Fx.DAY_MS)), now), 1e-9)
        val soon = ll.copy(project = ll.project.copy(shootDates = listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11))))
        assertEquals(1.10, MatchEngine.mUrgent(meeraV, soon, today), 1e-9)
        assertEquals(1.0, MatchEngine.mUrgent(meeraV, ll, today), 1e-9)
    }

    // ───────────── Deck assembly ─────────────

    /** A crew role for a gaffer viewer on its own project, [km] away, with an owner who has [shoots] shoots. */
    private fun gafferRole(i: Int, km: Double, shoots: Int = 3, projectId: String = "p_$i", amount: Int = 2500): RoleWithProject {
        val ownerId = "u_o$i"
        return near(km, Fx.item(
            role = Fx.role(id = "r_$i", projectId = projectId, roleCode = RoleCode.GAFFER, amountMin = amount),
            project = Fx.project(id = projectId, ownerId = ownerId),
            owner = Fx.owner(id = ownerId, reputation = Fx.reputation(id = ownerId, shoots = shoots, footage = Ratio(3, 3), kept = Ratio(3, 3))),
        ))
    }

    private val gafferViewer = Fx.viewer(Fx.profile(id = "u_kabir", primaryRole = RoleCode.GAFFER, radius = 10, compMode = CompMode.PAID_AND_SELECTIVE))

    @Test fun fallbackTriggersBelowTenCards() {
        val inside = gafferRole(1, 5.0)
        val outside = gafferRole(2, 13.0)       // > 10 km radius, < 15 km (1.5×)
        val wayOut = gafferRole(3, 40.0)
        val deck = MatchEngine.buildDeck(gafferViewer, listOf(inside, outside, wayOut), today, now)
        assertEquals(listOf("r_1", "r_2"), deck.map { it.item.role.id })
        assertFalse(deck[0].outsideRadius)
        assertTrue(deck[1].outsideRadius)
        // With 10+ cards inside the radius, no fallback.
        val many = (10..20).map { gafferRole(it, 2.0) } + outside
        val full = MatchEngine.buildDeck(gafferViewer, many, today, now)
        assertFalse(full.any { it.item.role.id == "r_2" })
        assertTrue(full.none { it.outsideRadius })
    }

    @Test fun fallbackIncludesAllRemoteRoles() {
        val editorNoRemote = Fx.viewer(Fx.profile(id = "u_ed", primaryRole = RoleCode.EDITOR, remoteOk = false, languages = listOf("en")))
        val remote = near(800.0, Fx.item(role = Fx.role(roleCode = RoleCode.EDITOR, compType = CompType.PAID_FLAT, amountMin = 20000,
            remoteOk = true, postDeadline = today.plusDays(30)), project = Fx.postProject(id = "p_fc")))
        assertTrue(Gate.G2_PLACE in MatchEngine.failedGates(editorNoRemote, remote, today))
        val deck = MatchEngine.buildDeck(editorNoRemote, listOf(remote), today, now)
        assertEquals(1, deck.size)
        assertTrue(deck[0].outsideRadius)
        assertNull(deck[0].distanceKm)
    }

    @Test fun diversityCapsTwoPerProjectInFirstTen() {
        // Six strong roles on one project (closest), then eight single-role projects further out.
        val big = (1..6).map { gafferRole(it, 0.5, projectId = "p_big", amount = 5000) }
        val others = (10..17).map { gafferRole(it, 6.0) }
        val deck = MatchEngine.buildDeck(gafferViewer, big + others, today, now)
        assertEquals(14, deck.size)
        val firstTen = deck.take(10)
        assertEquals(2, firstTen.count { it.item.project.id == "p_big" })
        // The top two are still the strongest cards.
        assertEquals("p_big", deck[0].item.project.id)
        assertEquals("p_big", deck[1].item.project.id)
        assertEquals(4, deck.drop(10).count { it.item.project.id == "p_big" })
    }

    @Test fun explorationFillsEverySeventhSlot() {
        val established = (1..10).map { gafferRole(it, 1.0 + it * 0.2) }
        val newOwner = gafferRole(99, 9.5, shoots = 0)            // weakest card by distance and credibility
        val deck = MatchEngine.buildDeck(gafferViewer, established + newOwner, today, now)
        assertEquals(11, deck.size)
        assertEquals("r_99", deck[6].item.role.id)
        assertTrue(deck[6].exploration)
        assertEquals(1, deck.count { it.exploration })
    }

    @Test fun deckIsSortedAndOnlyGatedCards() {
        val good = gafferRole(1, 1.0)
        val worse = gafferRole(2, 8.0)
        val wrongRole = near(1.0, Fx.item(role = Fx.role(id = "r_x", projectId = "p_x", roleCode = RoleCode.EDITOR), project = Fx.project(id = "p_x")))
        val deck = MatchEngine.buildDeck(gafferViewer, listOf(worse, wrongRole, good), today, now)
        assertEquals(listOf("r_1", "r_2"), deck.map { it.item.role.id })
        assertTrue(deck[0].score >= deck[1].score)
        assertEquals(1.0, deck[0].distanceKm!!, 0.01)
    }

    @Test fun guestDeckUsesStatusGatesOnly() {
        val open = gafferRole(1, 1.0)
        val filled = gafferRole(2, 1.0).let { it.copy(role = it.role.copy(status = RoleStatus.FILLED)) }
        val actor = lastLocal()
        val deck = MatchEngine.guestDeck(listOf(open, filled, actor), today, now)
        assertEquals(setOf("r_1", "r_ll_lead"), deck.map { it.item.role.id }.toSet())
        assertTrue(deck.all { it.fit == null && it.distanceKm == null })
    }

    @Test fun filters() {
        val paid = MatchEngine.buildDeck(gafferViewer, listOf(gafferRole(1, 1.0)), today, now)
        val unpaidRole = gafferRole(2, 1.0).let { it.copy(role = it.role.copy(compType = CompType.UNPAID, amountMin = null, amountMax = null, paymentTerms = null,
            perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 4)) }
        val cards = paid + DeckCard(unpaidRole, 50.0, null, 1.0)
        assertEquals(listOf("r_1"), MatchEngine.applyFilters(cards, DeckFilters(paidOnly = true), today).map { it.item.role.id })
        assertEquals(listOf("r_2"), MatchEngine.applyFilters(cards, DeckFilters(footageByDate = true), today).map { it.item.role.id })
        assertTrue(MatchEngine.applyFilters(cards, DeckFilters(remoteOnly = true), today).isEmpty())
        assertTrue(MatchEngine.applyFilters(cards, DeckFilters(dates = DateWindow.THIS_WEEK), today).isEmpty())
        assertEquals(2, MatchEngine.applyFilters(cards, DeckFilters(languages = setOf("kn")), today).size)
        assertTrue(MatchEngine.applyFilters(cards, DeckFilters(maxDistanceKm = 0), today).isEmpty())
    }
}
