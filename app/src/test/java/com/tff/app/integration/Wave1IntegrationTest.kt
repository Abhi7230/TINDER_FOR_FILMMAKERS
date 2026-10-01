package com.tff.app.integration

import com.tff.app.data.demo.DemoEngine
import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.DemoSeed
import com.tff.app.data.demo.DemoState
import com.tff.app.data.demo.FixedClock
import com.tff.app.data.demo.SeedKit
import com.tff.app.data.demo.T
import com.tff.app.domain.CardText
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.ShareText
import com.tff.app.model.DemoPersona
import com.tff.app.model.ProjectStatus
import com.tff.app.model.Ratio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalTime
import kotlin.random.Random

/**
 * Wave-1 integration checks: WS3's seeded world (DemoEngine / DemoQueries) driven through WS2's domain
 * (MatchEngine, CardText, ShareText), and data/demo/DEMO_IDS.md kept honest against the seed.
 */
class Wave1IntegrationTest {
    private val seed: DemoState = T.seed()

    private fun idsDoc(): String {
        val candidates = listOf(
            File("src/main/java/com/tff/app/data/demo/DEMO_IDS.md"),
            File("app/src/main/java/com/tff/app/data/demo/DEMO_IDS.md"),
        )
        val f = candidates.firstOrNull { it.exists() }
        assertNotNull("DEMO_IDS.md not found (cwd ${File(".").absolutePath})", f)
        return f!!.readText()
    }

    /** True if [id] names something in the seed (or is a documented runtime id). */
    private fun exists(s: DemoState, id: String): Boolean = when {
        id.startsWith("bc_") -> id in s.bookingCards
        id.startsWith("sd_") -> id in s.shootDays
        id.startsWith("wi_") -> id in s.wrapItems
        id.startsWith("rep_") -> s.reports.any { it.report.id == id }
        id.startsWith("msg_") -> s.messages.any { it.id == id }
        id.startsWith("n_") -> s.notifications.any { it.n.id == id }
        id.startsWith("u_") -> id in s.profiles
        id.startsWith("p_") -> id in s.projects
        id.startsWith("r_") -> id in s.roles
        id.startsWith("m_") -> id in s.matches
        id.startsWith("a_") -> id in s.applications
        else -> true
    }

    private val idPrefixes = listOf("u_", "p_", "r_", "m_", "a_", "bc_", "sd_", "wi_", "rep_", "msg_", "n_")

    @Test fun `every id in DEMO_IDS md exists in the seed`() {
        val tokens = Regex("`([^`]+)`").findAll(idsDoc()).map { it.groupValues[1].trim() }.toSet()
        val ids = tokens.filter { t ->
            idPrefixes.any { t.startsWith(it) } && t.none { it.isWhitespace() || it in "<>…/." }
        }
        assertTrue("expected the doc to list ids, got $ids", ids.size > 60)
        // Created only by "Inject flagged example".
        val runtime = setOf(DemoIds.U_SCAM_2, DemoIds.P_SCAM_2)
        val missing = ids.filter { it !in runtime && !exists(seed, it) }
        assertTrue("ids in DEMO_IDS.md missing from the seed: $missing", missing.isEmpty())

        val (e, _) = T.engine()
        e.startDemo(DemoPersona.MEERA)
        e.injectFlaggedExample().getOrThrow()
        assertTrue(DemoIds.U_SCAM_2 in e.state.value.profiles)
        assertTrue(DemoIds.P_SCAM_2 in e.state.value.projects)
    }

    @Test fun `every DemoIds constant exists in the seed`() {
        val consts = DemoIds::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { it.name to it.get(null) as String }
        assertTrue(consts.size > 50)
        val runtime = setOf(DemoIds.U_SCAM_2, DemoIds.P_SCAM_2)
        val missing = consts.filter { (_, id) -> id !in runtime && !exists(seed, id) }
        assertTrue("DemoIds constants missing from the seed: $missing", missing.isEmpty())
    }

    @Test fun `wrapped history and persona emails match the doc`() {
        val doc = idsDoc()
        val wrappedLine = doc.lines().first { it.startsWith("Wrapped history") }
        val wrapped = Regex("`(p_[a-z_]+)`").findAll(wrappedLine).map { it.groupValues[1] }.toList()
        assertEquals(20, wrapped.size)
        wrapped.forEach { assertEquals(it, ProjectStatus.WRAPPED, seed.projects.getValue(it).status) }
        DemoPersona.entries.forEach { p ->
            val uid = DemoIds.personaUser(p)
            assertEquals(DemoIds.personaEmail(p), seed.emails[uid])
            assertTrue("persona email for $p not in DEMO_IDS.md", doc.contains(p.name.lowercase() + "@"))
        }
    }

    @Test fun `track records QA relies on match the doc`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        val s = e.state.value
        fun rep(id: String) = q.reputation(s, id)
        val aniket = rep(DemoIds.LAST_LOCAL_OWNER)
        assertEquals(3, aniket.shootsCompleted)
        assertEquals(Ratio(3, 3), aniket.footageDelivered)
        assertEquals(Ratio(3, 3), aniket.keptBookings)
        assertEquals(null, aniket.paidOnTime)
        assertFalse(s.profiles.getValue(DemoIds.LAST_LOCAL_OWNER).idVerified)
        assertEquals(Ratio(3, 3), rep(DemoIds.ROHAN).keptBookings)
        assertEquals(Ratio(2, 4), rep(DemoIds.WEAK_KEPT_OWNER).keptBookings)
        assertEquals(Ratio(1, 4), rep(DemoIds.WEAK_FOOTAGE_OWNER).footageDelivered)
        assertEquals(Ratio(2, 4), rep(DemoIds.WEAK_PAY_OWNER).paidOnTime)
    }

    @Test fun `every persona deck builds and renders through CardText and ShareText`() {
        for (persona in DemoPersona.entries) {
            val (e, _) = T.engine()
            val sess = e.startDemo(persona)
            val q = DemoQueries(e)
            val ix = e.index()
            val candidates = q.discoverCandidates(ix.s, sess.userId, null)
            assertTrue("$persona has no discover candidates", candidates.isNotEmpty())
            val deck = MatchEngine.buildDeck(T.viewer(ix, ix.profile(sess.userId)!!), candidates, ix.today, ix.now)
            assertTrue("$persona deck is empty", deck.isNotEmpty())
            for (c in deck) {
                val item = c.item
                val r = item.role
                val p = item.project
                val where = "$persona ${r.id}"
                assertTrue(where, CardText.roleLabel(r).isNotBlank())
                assertTrue(where, CardText.payLine(r, p).isNotBlank())
                assertTrue(where, CardText.whenLine(r, p).isNotBlank())
                assertTrue(where, CardText.whereLine(item).isNotBlank())
                assertTrue(where, CardText.formatChip(p).isNotBlank())
                assertTrue(where, CardText.footer(item).isNotBlank())
                CardText.credibilityChips(item).forEach { assertTrue(where, it.isNotBlank()) }
                CardText.flagsRow(p).forEach { assertTrue(where, it.isNotBlank()) }
                CardText.badges(r).forEach { assertTrue(where, it.isNotBlank()) }
                CardText.perksDetailed(r, p)
                CardText.distanceChip(c.distanceKm, r.remoteOk)
                val share = ShareText.roleCard(item, "https://calltime.app")
                assertTrue(where, share.contains(p.title))
            }
        }
    }

    @Test fun `meera's gigs deck holds the QA role and only gate-passing cards`() {
        val (e, _) = T.engine()
        val sess = e.startDemo(DemoPersona.MEERA)
        val ix = e.index()
        val v = T.viewer(ix, ix.profile(sess.userId)!!)
        val deck = MatchEngine.buildDeck(v, DemoQueries(e).discoverCandidates(ix.s, sess.userId, null), ix.today, ix.now)
        assertTrue(deck.isNotEmpty())
        assertTrue(deck.any { it.item.role.id == DemoIds.R_LL_LEAD })
        deck.filter { !it.outsideRadius }.forEach {
            assertTrue(it.item.role.id, MatchEngine.failedGates(v, it.item, ix.today).isEmpty())
        }
    }

    /** The device seeds at "today", whatever weekday that is; every persona must still get a deck. */
    @Test fun `persona decks hold up on every weekday anchor`() {
        for (offset in 0L..6L) {
            val anchor = T.anchor.plusDays(offset)
            val now = SeedKit.millisAt(anchor, LocalTime.of(9, 30), T.zone)
            val state = DemoSeed.build(anchor, Random(42), T.zone, now)
            for (persona in DemoPersona.entries) {
                val e = DemoEngine(state, FixedClock(now))
                val sess = e.startDemo(persona)
                val ix = e.index()
                val deck = MatchEngine.buildDeck(T.viewer(ix, ix.profile(sess.userId)!!),
                    DemoQueries(e).discoverCandidates(ix.s, sess.userId, null), ix.today, ix.now)
                assertTrue("$persona deck empty with anchor $anchor (${anchor.dayOfWeek})", deck.isNotEmpty())
                if (persona == DemoPersona.MEERA) {
                    assertTrue("Last Local lead missing from Meera's deck with anchor $anchor (${anchor.dayOfWeek})",
                        deck.any { it.item.role.id == DemoIds.R_LL_LEAD })
                }
            }
        }
    }
}
