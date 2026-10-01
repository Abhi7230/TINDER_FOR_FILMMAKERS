package com.tff.app.integration

import com.tff.app.data.demo.DemoCities
import com.tff.app.data.demo.DemoEngine
import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.DemoSeed
import com.tff.app.data.demo.DemoState
import com.tff.app.data.demo.FixedClock
import com.tff.app.data.demo.SeedKit
import com.tff.app.data.demo.T
import com.tff.app.domain.DeckCard
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.Viewer
import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Seed v2 deck density (FIX1): the real Gigs deck (MatchEngine.buildDeck over DemoRepo's discoverCandidates, with the
 * viewer built from Repo.me() + myPrivateMinRate() exactly as the Discover screen does) right after a reset, on every
 * weekday anchor. Only gate-passing cards count; small-market fallback cards ("Outside your radius") don't.
 */
class DeckDensityTest {

    private data class World(val anchor: LocalDate, val now: Long, val state: DemoState)

    private val worlds: List<World> = (0L..6L).map { off ->
        val anchor = T.anchor.plusDays(off)
        val now = SeedKit.millisAt(anchor, LocalTime.of(9, 30), T.zone)
        World(anchor, now, DemoSeed.build(anchor, Random(42), T.zone, now))
    }

    private fun engine(w: World) = DemoEngine(w.state, FixedClock(w.now))

    /** What Discover builds: Viewer(me(), myPrivateMinRate(), area centroid). */
    private fun deck(e: DemoEngine, uid: String): List<DeckCard> {
        val q = DemoQueries(e)
        val s = e.state.value
        val me = q.me(s, uid)!!
        val a = DemoCities.area(me.areaId)!!
        val ix = e.index()
        return MatchEngine.buildDeck(Viewer(me, q.myPrivateMinRate(s, uid), a.lat, a.lng), q.discoverCandidates(s, uid, null), ix.today, ix.now)
    }

    private fun inRadius(d: List<DeckCard>) = d.filter { !it.outsideRadius }

    @Test fun `every persona starts with at least 10 gate-passing cards on every weekday anchor`() {
        val table = StringBuilder("Starting Gigs deck sizes (gate-passing cards):\n")
        val sizes = mutableMapOf<DemoPersona, MutableList<Int>>()
        val ids = sortedMapOf<String, java.util.SortedSet<String>>()
        for (w in worlds) {
            table.append("  ${w.anchor.dayOfWeek.name.take(3)} ${w.anchor}:")
            for (persona in DemoPersona.entries) {
                val e = engine(w)
                val uid = e.startDemo(persona).userId
                val d = inRadius(deck(e, uid))
                sizes.getOrPut(persona) { mutableListOf() } += d.size
                table.append("  $persona=${d.size}")
                if (w === worlds.first() || persona == DemoPersona.MEERA) ids.getOrPut("$persona") { sortedSetOf() } += d.map { it.item.role.id }
                assertTrue("$persona has ${d.size} cards with anchor ${w.anchor} (${w.anchor.dayOfWeek}): ${d.map { it.item.role.id }}", d.size >= 10)
                // Every card really passes the gates for the viewer the app builds.
                val q = DemoQueries(e)
                val me = q.me(e.state.value, uid)!!
                val a = DemoCities.area(me.areaId)!!
                val v = Viewer(me, q.myPrivateMinRate(e.state.value, uid), a.lat, a.lng)
                d.forEach { c -> assertTrue("$persona ${c.item.role.id}", MatchEngine.failedGates(v, c.item, e.index().today).isEmpty()) }
            }
            table.append('\n')
        }
        sizes.forEach { (p, l) -> table.append("  $p min ${l.min()} max ${l.max()}\n") }
        ids.forEach { (p, l) -> table.append("  $p cards: ${l.joinToString(", ")}\n") }
        println(table)
    }

    @Test fun `meera's deck is actor roles and keeps the QA role`() {
        for (w in worlds) {
            val e = engine(w)
            val d = inRadius(deck(e, e.startDemo(DemoPersona.MEERA).userId))
            assertTrue(d.all { it.item.role.roleCode == RoleCode.ACTOR })
            assertTrue("Last Local lead missing on ${w.anchor}", d.any { it.item.role.id == DemoIds.R_LL_LEAD })
        }
    }

    @Test fun `kabir gets gaffer work and step-up DP roles`() {
        for (w in worlds) {
            val e = engine(w)
            val d = inRadius(deck(e, e.startDemo(DemoPersona.KABIR).userId))
            assertTrue(d.all { it.item.role.roleCode == RoleCode.GAFFER || (it.item.role.roleCode == RoleCode.DP && it.item.role.stepUpFriendly) })
            assertTrue(d.count { it.item.role.roleCode == RoleCode.DP } >= 3)
            assertTrue(d.count { it.item.role.roleCode == RoleCode.GAFFER } >= 3)
        }
    }

    @Test fun `dana only gets US roles, paid, in dollars`() {
        for (w in worlds) {
            val e = engine(w)
            val d = inRadius(deck(e, e.startDemo(DemoPersona.DANA).userId))
            d.forEach { c ->
                assertEquals(c.item.role.id, "US", DemoCities.city(c.item.project.cityId)!!.country)
                assertEquals(c.item.role.id, "USD", c.item.role.currency)
                assertTrue(c.item.role.id, c.item.role.isPaid)
            }
            assertTrue(d.count { it.item.role.isPostRole && it.item.role.roleCode == RoleCode.SOUND_DESIGNER } >= 3)
            assertTrue(d.count { it.item.role.roleCode == RoleCode.SOUND_MIXER } >= 4)
        }
    }

    @Test fun `rohan gets writing directing and remote roles in India only`() {
        for (w in worlds) {
            val e = engine(w)
            val d = inRadius(deck(e, e.startDemo(DemoPersona.ROHAN).userId))
            assertTrue(d.all { it.item.role.roleCode == RoleCode.DIRECTOR || it.item.role.roleCode == RoleCode.WRITER })
            assertTrue(d.count { it.item.role.roleCode == RoleCode.DIRECTOR } >= 3)
            assertTrue(d.count { it.item.role.isPostRole } >= 5)
            assertTrue(d.all { DemoCities.city(it.item.project.cityId)!!.country == "IN" })
        }
    }

    /** A brand-new user ("Build my own") with one of the common roles, in either launch city. */
    @Test fun `a new user in Mumbai or Bengaluru with a common role gets at least 8 cards`() {
        val places = listOf(
            Triple("mumbai", "mumbai_andheri_west", listOf("en", "hi")),
            Triple("mumbai", "mumbai_bandra", listOf("en", "hi", "mr")),
            Triple("bengaluru", "bengaluru_koramangala", listOf("en", "kn")),
            Triple("bengaluru", "bengaluru_jayanagar", listOf("en", "kn")),
        )
        val roles = listOf(RoleCode.ACTOR, RoleCode.DP, RoleCode.EDITOR, RoleCode.SOUND_MIXER)
        val patterns = listOf(AvailabilityPattern.ANY_DAY, AvailabilityPattern.WEEKENDS_ONLY)
        val modes = listOf(CompMode.PAID_AND_SELECTIVE, CompMode.OPEN_TO_UNPAID)
        val report = StringBuilder("New-user deck sizes (min over 7 anchors):\n")
        for ((city, area, langs) in places) for (role in roles) {
            var min = Int.MAX_VALUE
            for (w in worlds) for (avail in patterns) for (mode in modes) {
                val e = engine(w)
                val uid = e.startDemo(null).userId
                val p = Profile(uid, "New Person", primaryRole = role, level = Level.BUILDING_REEL, cityId = city, areaId = area,
                    remoteOk = Taxonomy.dept(role) in Taxonomy.POST_DEPTS, languages = langs, availabilityPattern = avail, compMode = mode)
                e.saveProfile(uid, p, null).getOrThrow()
                val d = inRadius(deck(e, uid))
                min = minOf(min, d.size)
                assertTrue("$role in $area ($avail, $mode) has ${d.size} cards on ${w.anchor}: ${d.map { it.item.role.id }}", d.size >= 8)
            }
            report.append("  $area $role: $min\n")
        }
        println(report)
    }

    // ───────────── FIX1 item 2: booked dates are busy; live projects aren't offered again ─────────────

    @Test fun `meera never sees cutting chai roles while booked on it, on any weekday start`() {
        for (w in worlds) {
            val e = engine(w)
            val uid = e.startDemo(DemoPersona.MEERA).userId
            val q = DemoQueries(e)
            val candidates = q.discoverCandidates(e.state.value, uid, null)
            assertFalse(candidates.any { it.role.id == "r_cc_support" })
            assertFalse(candidates.any { it.project.id == DemoIds.P_CUTTING_CHAI })
            // Nor any project she still has business on: pending applications, the Rebook ask, the TALKING scam match.
            val live = setOf(DemoIds.P_PAPER_BOATS, DemoIds.P_DEAD_DROP, DemoIds.P_TIFFIN_WARS, DemoIds.P_ROOMMATES, DemoIds.P_CUTTING_CHAI)
            assertTrue(candidates.none { it.project.id in live })
            assertTrue(deck(e, uid).none { it.item.project.id in live })
            // Her booked shoot day is busy in the profile the deck is built from (and only there: the stored profile is unchanged).
            val ccDay = e.state.value.projects.getValue(DemoIds.P_CUTTING_CHAI).shootDates.single()
            assertTrue(ccDay in q.me(e.state.value, uid)!!.busyDates)
            assertFalse(ccDay in e.state.value.profiles.getValue(uid).busyDates)
        }
    }

    @Test fun `an application or match takes the whole project out of the deck`() {
        val w = worlds.first()
        val e = engine(w)
        val uid = e.startDemo(DemoPersona.KABIR).userId
        val q = DemoQueries(e)
        val before = q.discoverCandidates(e.state.value, uid, null)
        val monsoon = before.filter { it.project.id == DemoIds.P_MONSOON_STATIC }.map { it.role.id }
        assertTrue("Kabir sees both Monsoon Static roles first: $monsoon", monsoon.containsAll(listOf("r_001_2", DemoIds.R_MS_DP)))
        e.apply(uid, "r_001_2", "Gaffer for this one, if the DP slot goes elsewhere.", null, null, kit = true).getOrThrow()
        val after = q.discoverCandidates(e.state.value, uid, null)
        assertTrue(after.none { it.project.id == DemoIds.P_MONSOON_STATIC })
        assertTrue(deck(e, uid).none { it.item.project.id == DemoIds.P_MONSOON_STATIC })
    }

    @Test fun `booked shoot dates are busy for cast and crew roles elsewhere`() {
        for (w in worlds) {
            val ccDay = w.state.projects.getValue(DemoIds.P_CUTTING_CHAI).shootDates.single()
            // Move Chawl No. 7's first day onto Meera's Cutting Chai day: she can't do every day, so the cast roles go.
            val chawl = w.state.projects.getValue("p_014")
            val clashing = w.state.copy(projects = w.state.projects + (chawl.id to chawl.copy(shootDates = listOf(ccDay, chawl.shootDates.last()))))
            val e = DemoEngine(clashing, FixedClock(w.now))
            val uid = e.startDemo(DemoPersona.MEERA).userId
            val ids = DemoQueries(e).discoverCandidates(e.state.value, uid, null).map { it.role.id }
            assertFalse("r_014_1" in ids)
            assertFalse("r_014_2" in ids)
            val clean = DemoQueries(engine(w).also { it.startDemo(DemoPersona.MEERA) }).discoverCandidates(w.state, DemoIds.MEERA, null).map { it.role.id }
            assertTrue("r_014_1" in clean && "r_014_2" in clean)
        }
        // Crew: Kabir booked as gaffer on Chawl No. 7 can't take a one-day shoot on the same Saturday,
        // but a two-day shoot that has a free day still shows (crew need one free day).
        val w = worlds.first()
        val e0 = engine(w)
        val kabir = e0.startDemo(DemoPersona.KABIR).userId
        val chawl = w.state.projects.getValue("p_014")
        val lullaby = w.state.projects.getValue("p_019")
        val rain = w.state.projects.getValue("p_020")
        val s1 = w.state.copy(projects = w.state.projects +
            (lullaby.id to lullaby.copy(shootDates = listOf(chawl.shootDates.first()))) +
            (rain.id to rain.copy(shootDates = listOf(chawl.shootDates.first(), chawl.shootDates.first().plusDays(7)))))
        val e1 = DemoEngine(s1.copy(session = e0.state.value.session), FixedClock(w.now))
        val app = Application("a_test_kabir", "r_014_4", kabir, status = ApplicationStatus.MATCHED, createdAt = w.now - 3_600_000)
        val m = Match("m_test_kabir", app.id, chawl.id, "r_014_4", chawl.ownerId, kabir, MatchState.BOOKED, bookedAt = w.now - 1_000)
        e1.replace(e1.state.value.copy(applications = e1.state.value.applications + (app.id to app), matches = e1.state.value.matches + (m.id to m)))
        val q = DemoQueries(e1)
        val ids = q.discoverCandidates(e1.state.value, kabir, null).map { it.role.id }
        assertFalse("one-day clash", "r_019_1" in ids || "r_019_2" in ids)
        assertTrue("a free second day keeps the role", "r_020_2" in ids)
        assertTrue(chawl.shootDates.first() in q.me(e1.state.value, kabir)!!.busyDates)
        // Saving the profile doesn't turn the booking into a manual busy date.
        e1.saveProfile(kabir, q.me(e1.state.value, kabir)!!, 3000).getOrThrow()
        assertFalse(chawl.shootDates.first() in e1.state.value.profiles.getValue(kabir).busyDates)
    }

    @Test fun `remote roles in a deck are always in the viewer's own country`() {
        for (w in worlds) for (persona in DemoPersona.entries) {
            val e = engine(w)
            val uid = e.startDemo(persona).userId
            val home = DemoCities.city(e.state.value.profiles.getValue(uid).cityId)!!.country
            DemoQueries(e).discoverCandidates(e.state.value, uid, null).filter { it.role.remoteOk }.forEach {
                assertEquals("$persona ${it.role.id}", home, DemoCities.city(it.project.cityId)!!.country)
            }
            deck(e, uid).forEach { assertEquals("$persona ${it.item.role.id}", home, DemoCities.city(it.item.project.cityId)!!.country) }
        }
        // Guests browsing a city get that city's country's remote roles.
        val e = engine(worlds.first())
        val la = DemoQueries(e).discoverCandidates(e.state.value, null, "los_angeles")
        assertNotNull(la.firstOrNull { it.role.remoteOk })
        assertTrue(la.filter { it.role.remoteOk }.all { DemoCities.city(it.project.cityId)!!.country == "US" })
    }
}
