package com.tff.app.ui.screens.discover

import com.tff.app.data.demo.DemoCities
import com.tff.app.data.demo.DemoEngine
import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.T
import com.tff.app.domain.CardText
import com.tff.app.domain.DeckCard
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.Viewer
import com.tff.app.model.AvailabilityPattern
import com.tff.app.model.CompMode
import com.tff.app.model.DateWindow
import com.tff.app.model.DeckFilters
import com.tff.app.model.DemoPersona
import com.tff.app.model.Level
import com.tff.app.model.Profile
import com.tff.app.model.RoleCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S05 deck logic, driven through WS3's seeded world exactly as DiscoverViewModel does it. */
class DiscoverDeckLogicTest {

    private fun deckFor(e: DemoEngine, userId: String): Pair<Viewer, List<DeckCard>> {
        val s = e.state.value
        val q = DemoQueries(e)
        val me = s.profiles.getValue(userId)
        val v = DeckLogic.viewer(me, s.privateRates[userId], DemoCities.all)!!
        val deck = DeckLogic.build(v, q.discoverCandidates(s, userId, null), e.today(), e.now())
        return v to deck
    }

    /** Every card passes every gate (fallback cards at the widened radius, flagged "outside your radius"). */
    private fun assertGated(v: Viewer, deck: List<DeckCard>, e: DemoEngine) {
        deck.forEach { c ->
            val failed = if (c.outsideRadius) MatchEngine.failedGates(v, c.item, e.today(), MatchEngine.FALLBACK_RADIUS, includeAllRemote = true)
            else MatchEngine.failedGates(v, c.item, e.today())
            assertTrue("${c.item.role.id} fails $failed", failed.isEmpty())
        }
    }

    @Test fun `Meera's deck only has roles that pass the gates and includes Last Local`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.MEERA)
        val (v, deck) = deckFor(e, DemoIds.MEERA)
        assertTrue(deck.isNotEmpty())
        assertGated(v, deck, e)
        assertTrue(deck.any { it.item.role.id == DemoIds.R_LL_LEAD })
        // Meera is an actor: never a crew card.
        assertTrue(deck.all { it.item.role.isCast })
    }

    @Test fun `Dana's PAID_ONLY deck is paid, in dollars, and reaches remote LA sound design`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.DANA)
        val (v, deck) = deckFor(e, DemoIds.DANA)
        assertTrue(deck.isNotEmpty())
        assertGated(v, deck, e)
        deck.forEach { c ->
            assertTrue("${c.item.role.id} isn't paid", c.item.role.isPaid)
            assertEquals("USD", c.item.role.currency)
            assertTrue(CardText.payLine(c.item.role, c.item.project).startsWith("$"))
        }
        assertTrue("remote LA role missing", deck.any { it.item.role.remoteOk && it.item.cityName == "Los Angeles" })
    }

    @Test fun `Kabir's deck carries his step-up DP roles`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.KABIR)
        val (v, deck) = deckFor(e, DemoIds.KABIR)
        assertGated(v, deck, e)
        val ids = deck.map { it.item.role.id }.toSet()
        listOf(DemoIds.R_MS_DP, DemoIds.R_TR_DP, DemoIds.R_DD_DP).forEach { assertTrue("$it missing from $ids", it in ids) }
    }

    @Test fun `a new Indore writer gets an early-city header and a non-empty deck`() {
        val (e, _) = T.engine()
        val session = e.startDemo(null)
        val profile = Profile(
            id = session.userId, displayName = "Isha Verma", primaryRole = RoleCode.WRITER, level = Level.FIRST_PROJECT,
            cityId = "indore", areaId = "indore_city_centre", remoteOk = true, languages = listOf("hi", "en"),
            availabilityPattern = AvailabilityPattern.ANY_DAY, compMode = CompMode.OPEN_TO_UNPAID,
        )
        e.saveProfile(session.userId, profile, null).getOrThrow()
        val q = DemoQueries(e)
        val density = q.density(e.state.value, "indore")
        assertTrue(density.isEarly)
        assertTrue(CardText.densityHeader(density, RoleCode.WRITER).contains("early city"))
        val (v, deck) = deckFor(e, session.userId)
        assertTrue("Indore deck is blank", deck.isNotEmpty())
        assertGated(v, deck, e)
        assertTrue(deck.any { it.item.role.id == DemoIds.R_RR_WRITER })
        // Remote roles from across India fill the early-city deck.
        assertTrue(deck.any { it.item.role.remoteOk && it.item.project.cityId != "indore" })
        assertTrue(deck.any { CardText.badges(it.item.role).contains("BEGINNERS WELCOME") })
    }

    @Test fun `guests get a deck for the city they pick`() {
        val (e, _) = T.engine()
        e.signOut()
        val q = DemoQueries(e)
        listOf("mumbai", "bengaluru", "indore").forEach { city ->
            val deck = DeckLogic.build(null, q.discoverCandidates(e.state.value, null, city), e.today(), e.now())
            assertTrue("guest deck for $city is empty", deck.isNotEmpty())
        }
    }

    @Test fun `empty states are classified in priority order`() {
        // Daily cap wins over everything for signed-in people, never for guests.
        assertEquals(DeckEmpty.DAILY_CAP, DeckLogic.emptyKind(false, 0, 5, 5, 5, false))
        assertNull(DeckLogic.emptyKind(true, 0, 5, 5, 5, false))
        assertNull(DeckLogic.emptyKind(false, 3, 5, 5, 2, false))
        assertEquals(DeckEmpty.FILTERED, DeckLogic.emptyKind(false, 3, 5, 0, 0, false))
        assertEquals(DeckEmpty.EXHAUSTED, DeckLogic.emptyKind(false, 3, 5, 5, 0, false))
        assertEquals(DeckEmpty.EXHAUSTED, DeckLogic.emptyKind(false, 3, 0, 0, 0, hasHistory = true))
        assertEquals(DeckEmpty.NOTHING_IN_RANGE, DeckLogic.emptyKind(false, 3, 0, 0, 0, hasHistory = false))
        assertEquals(DeckEmpty.NOTHING_IN_RANGE, DeckLogic.emptyKind(true, 25, 0, 0, 0, hasHistory = true))
    }

    @Test fun `widen radius multiplies by one and a half within limits`() {
        assertEquals(23, DeckLogic.widenedRadius(15))
        assertEquals(3, DeckLogic.widenedRadius(2))
        assertEquals(150, DeckLogic.widenedRadius(120))
        assertNull(DeckLogic.widenedRadius(150))
    }

    @Test fun `filter count and filtering`() {
        assertEquals(0, DeckLogic.activeFilterCount(DeckFilters()))
        assertEquals(3, DeckLogic.activeFilterCount(DeckFilters(paidOnly = true, dates = DateWindow.THIS_WEEK, languages = setOf("hi"))))
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.MEERA)
        val (_, deck) = deckFor(e, DemoIds.MEERA)
        // Paid only keeps exactly the paid cards (FIX1's denser seed gives Meera some paid cast work too).
        val paid = DeckLogic.filter(deck, DeckFilters(paidOnly = true), e.today())
        assertTrue(paid.all { it.item.role.isPaid })
        assertEquals(deck.count { it.item.role.isPaid }, paid.size)
        assertTrue("Meera's deck should still have unpaid cards for Paid only to hide", paid.size < deck.size)
        assertEquals(deck, DeckLogic.filter(deck, DeckFilters(), e.today()))
    }

    @Test fun `near-miss numbers only count roles one fix away`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.MEERA)
        val s = e.state.value
        val q = DemoQueries(e)
        val cands = q.discoverCandidates(s, DemoIds.MEERA, null)
        val tight = s.profiles.getValue(DemoIds.MEERA).copy(travelRadiusKm = 2, compMode = CompMode.PAID_ONLY)
        val v = DeckLogic.viewer(tight, null, DemoCities.all)!!
        val b = DeckLogic.blockers(v, cands, e.today())
        assertEquals(cands.distinctBy { it.role.id }.size, b.nearby)
        assertTrue(b.unpaidTerms > 0)
        assertTrue(b.justOutside + b.remoteOnly + b.unpaidTerms + b.otherRoles + b.busyDates <= b.nearby)
        assertEquals(DeckBlockers(nearby = 3), DeckLogic.blockers(null, cands.take(3), e.today()))
    }

    @Test fun `language options list project languages and the viewer's own`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        val cands = q.discoverCandidates(e.state.value, DemoIds.ROHAN, null)
        val me = e.state.value.profiles.getValue(DemoIds.ROHAN)
        val langs = DeckLogic.languageOptions(cands, me)
        assertTrue(langs.containsAll(me.languages.map { it.lowercase() }))
        assertEquals(langs, langs.distinct())
        assertFalse(langs.any { it.isBlank() })
    }

    @Test fun `gating key ignores activity timestamps`() {
        val p = T.seed().profiles.getValue(DemoIds.MEERA)
        assertEquals(DeckLogic.gatingKey(p), DeckLogic.gatingKey(p.copy(lastActiveAt = p.lastActiveAt + 99_000)))
        assertFalse(DeckLogic.gatingKey(p) == DeckLogic.gatingKey(p.copy(travelRadiusKm = p.travelRadiusKm + 5)))
    }
}
