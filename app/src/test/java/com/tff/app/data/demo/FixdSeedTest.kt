package com.tff.app.data.demo

import com.tff.app.domain.ListingRules
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.Severity
import com.tff.app.domain.Viewer
import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.random.Random

/**
 * FIXD seed v3: Rohan's Scout has people for every crew role, two Keep-list people can be Rebooked, his draft has one
 * R02 blocker, Cutting Chai is a daytime studio shoot with a full team, blocking hides old notifications, the
 * simulator caps applicants at 3–8 and leaves Scout people, and the first Indore deck card is the local project.
 */
class FixdSeedTest {
    private val anchors = (0L..6L).map { T.anchor.plusDays(it) }

    private fun engineAt(anchor: java.time.LocalDate): DemoEngine {
        val now = SeedKit.millisAt(anchor, LocalTime.of(9, 30), T.zone)
        return DemoEngine(DemoSeed.build(anchor, Random(42), T.zone, now), FixedClock(now))
    }

    private val crewRoles = listOf(DemoIds.R_FC_DP, DemoIds.R_FC_SOUND, DemoIds.R_FC_EDITOR)

    @Test fun `rohan's Scout has at least 3 fitting people for every open crew role, on every weekday anchor`() {
        for (a in anchors) {
            val e = engineAt(a)
            val rohan = e.startDemo(DemoPersona.ROHAN).userId
            val q = DemoQueries(e)
            for (rid in crewRoles) {
                val scout = q.scout(e.state.value, rohan, rid)
                assertTrue("$rid on $a has ${scout.size}: ${scout.map { it.card.profile.id }}", scout.size >= 3)
                val applied = e.state.value.applications.values.filter { it.roleId == rid }.map { it.applicantId }.toSet()
                scout.forEach { assertFalse("${it.card.profile.id} already applied", it.card.profile.id in applied) }
            }
            // the applicant counts stay 7 / 3 / 10 / 4 (PRD §11.5)
            val counts = listOf(DemoIds.R_FC_DP, DemoIds.R_FC_SOUND, DemoIds.R_FC_ACTOR, DemoIds.R_FC_EDITOR).map { rid ->
                e.state.value.applications.values.count { it.roleId == rid && it.origin == ApplicationOrigin.APPLIED }
            }
            assertEquals(listOf(7, 3, 10, 4), counts)
        }
    }

    @Test fun `two people on rohan's Keep list can be asked back onto an open Filter Coffee role`() {
        for (a in anchors) {
            val e = engineAt(a)
            val rohan = e.startDemo(DemoPersona.ROHAN).userId
            val keep = DemoQueries(e).keepList(e.state.value, rohan).map { it.profile.id }
            val open = e.state.value.roles.values.filter { it.projectId == DemoIds.P_FILTER_COFFEE && it.status == RoleStatus.OPEN }
            val eligible = keep.filter { pid -> open.any { r -> e.askCheck(rohan, r.id, pid) == null } }
            assertTrue("eligible on $a: $eligible of $keep", eligible.size >= 2)
        }
        // and the Rebook really goes out
        val e = engineAt(T.anchor)
        val rohan = e.startDemo(DemoPersona.ROHAN).userId
        assertTrue(e.sendAsk(rohan, DemoIds.R_FC_DP, "u_035", "Bus Stop again?").isSuccess)
    }

    @Test fun `rohan's draft has one blocker, R02 meals, and 2+ meals fixes it`() {
        val e = engineAt(T.anchor)
        val rohan = e.startDemo(DemoPersona.ROHAN).userId
        val q = DemoQueries(e)
        val d = q.draft(e.state.value, rohan, DemoIds.P_ROHAN_DRAFT)!!
        val city = DemoCities.city(d.project.cityId)!!
        val owner = e.state.value.profiles.getValue(rohan)
        val blocks = ListingRules.check(d, owner, city, e.index().today).filter { it.severity == Severity.BLOCK }
        assertTrue(blocks.toString(), blocks.isNotEmpty() && blocks.all { it.rule == "R02" && it.field == "meals" })
        assertEquals("one message (shown once for both roles)", 1, blocks.map { it.message }.distinct().size)
        val fixed = d.copy(project = d.project.copy(meals = Meals.TWO_PLUS_MEALS))
        assertTrue(ListingRules.check(fixed, owner, city, e.index().today).none { it.severity == Severity.BLOCK })
    }

    @Test fun `cutting chai is a daytime studio shoot with a full team, and the card matches the day sheet`() {
        for (a in anchors) {
            val s = engineAt(a).state.value
            val p = s.projects.getValue(DemoIds.P_CUTTING_CHAI)
            assertEquals(LocationType.STUDIO, p.shootLocationType)
            val day = s.shootDays.getValue(DemoIds.SD_CUTTING_CHAI_1)
            assertFalse(day.address.contains("Niwas"))
            assertTrue("call ${day.generalCall}", !day.generalCall.isBefore(LocalTime.of(8, 0)))
            assertTrue("wrap ${day.wrapBy}", !day.wrapBy.isAfter(LocalTime.of(21, 0)) && day.wrapBy.isAfter(day.generalCall))
            val booked = s.matches.values.filter { it.projectId == p.id && it.state == MatchState.BOOKED }.map { it.collaboratorId }
            assertTrue("team $booked", booked.size >= 5)
            val women = booked.filter { it != DemoIds.MEERA }.mapNotNull { s.profiles[it] }.filter { it.pronouns == "she/her" }
            assertTrue("another woman on crew: $booked", women.isNotEmpty())
            val meeraCall = s.callTimes.single { it.shootDayId == day.id && it.profileId == DemoIds.MEERA }.callTime
            assertTrue(!meeraCall.isBefore(day.generalCall) && meeraCall.isBefore(day.wrapBy))
            val card = s.bookingCards.values.filter { it.matchId == DemoIds.M_MEERA_CUTTING_CHAI }.maxBy { it.version }
            assertEquals("~%s–%s".format(day.generalCall, day.wrapBy), card.terms.callWindow)
            val note = s.notifications.single { it.userId == DemoIds.MEERA && it.n.kind == NotificationKind.DAY_SHEET }
            assertTrue(note.n.body.contains(meeraCall.toString()))
        }
    }

    @Test fun `a project is honest zero-budget only when no role is paid`() {
        val s = engineAt(T.anchor).state.value
        assertFalse(s.projects.getValue(DemoIds.P_FILTER_COFFEE).honestZeroBudget)
        s.projects.values.filter { it.honestZeroBudget }.forEach { p ->
            assertTrue(p.id, s.roles.values.filter { it.projectId == p.id }.none { it.isPaid })
        }
    }

    @Test fun `blocking someone hides their older notifications and updates too`() {
        val e = engineAt(T.anchor)
        val meera = e.startDemo(DemoPersona.MEERA).userId
        val q = DemoQueries(e)
        fun mentionsFarida(list: List<AppNotification>) = list.any { it.title.contains("Farida") || it.body.contains("Farida") ||
            it.deepLink?.contains(DemoIds.M_MEERA_CUTTING_CHAI) == true || it.deepLink?.contains(DemoIds.P_CUTTING_CHAI) == true }
        assertTrue(mentionsFarida(q.notifications(e.state.value, meera)))
        assertTrue(e.block(meera, DemoIds.FARIDA).isSuccess)
        assertFalse(mentionsFarida(q.notifications(e.state.value, meera)))
        assertFalse(mentionsFarida(q.inbox(e.state.value, meera).updates))
        // other people's notifications stay
        assertTrue(q.notifications(e.state.value, meera).isNotEmpty())
        assertTrue(e.unblock(meera, DemoIds.FARIDA).isSuccess)
        assertTrue(mentionsFarida(q.notifications(e.state.value, meera)))
    }

    @Test fun `the simulator gives each role 3 to 8 applicants and leaves 3 fitting people for Scout`() {
        val ten = (1..10).toList()
        assertEquals(7, DemoSimulator.pickApplicants(ten, emptyList(), want = 8).size)      // 10 fit → leave 3
        assertEquals(3, DemoSimulator.pickApplicants(ten, emptyList(), want = 1).size)      // never fewer than 3
        assertEquals(8, DemoSimulator.pickApplicants((1..20).toList(), emptyList(), want = 30).size)
        assertEquals(3, DemoSimulator.pickApplicants(listOf(1, 2, 3, 4), emptyList(), want = 8).size)
        // a small market tops up to 3 from same-country people, never more
        assertEquals(listOf(1, 9, 8), DemoSimulator.pickApplicants(listOf(1), listOf(9, 8, 7), want = 8))
    }

    @Test fun `an Indore first-project writer sees Rajwada Rain first`() {
        for (a in anchors) {
            val e = engineAt(a)
            val uid = e.startDemo(null).userId
            val p = Profile(uid, "New Writer", primaryRole = RoleCode.WRITER, level = Level.FIRST_PROJECT, cityId = "indore",
                areaId = "indore_city_centre", remoteOk = true, languages = listOf("hi", "en"), compMode = CompMode.OPEN_TO_UNPAID)
            e.saveProfile(uid, p, null).getOrThrow()
            val q = DemoQueries(e)
            val me = q.me(e.state.value, uid)!!
            val area = DemoCities.area(me.areaId)!!
            val ix = e.index()
            val deck = MatchEngine.buildDeck(Viewer(me, null, area.lat, area.lng), q.discoverCandidates(e.state.value, uid, null), ix.today, ix.now)
            assertTrue("deck on $a: ${deck.map { it.item.role.id }}", deck.size >= 2)
            assertEquals("deck on $a: ${deck.map { it.item.role.id }}", DemoIds.R_RR_WRITER, deck.first().item.role.id)
        }
    }

    @Test fun `owners with a wrapped project have shoots done, and Rohan shows Kept bookings 3 of 3`() {
        val e = engineAt(T.anchor)
        val s = e.state.value
        val q = DemoQueries(e)
        s.projects.values.filter { it.status == ProjectStatus.WRAPPED }.map { it.ownerId }.distinct().forEach { oid ->
            val rep = q.reputation(s, oid)
            assertTrue("$oid shoots ${rep.shootsCompleted}", rep.shootsCompleted >= 1)
        }
        // Rohan (Bus Stop, kept 3/3) shows his owner record, not "New on Calltime"
        assertEquals("Kept bookings 3/3", com.tff.app.domain.ReputationCalc.headlineChip(q.reputation(s, DemoIds.ROHAN), asOwner = true))
    }

    @Test fun `read models are shared across a log event but recomputed after a real change`() {
        val e = engineAt(T.anchor)
        val meera = e.startDemo(DemoPersona.MEERA).userId
        val q = DemoQueries(e)
        val first = q.discoverCandidates(e.state.value, meera, null)
        e.log(meera, "app_open", emptyMap())
        assertTrue("same world but for an analytics event", first === q.discoverCandidates(e.state.value, meera, null))
        val inbox = q.inbox(e.state.value, meera)
        e.sendMessage(meera, DemoIds.M_MEERA_CUTTING_CHAI, "See you at 12:30.").getOrThrow()
        val after = q.inbox(e.state.value, meera)
        assertFalse(inbox === after)
        assertEquals("See you at 12:30.", after.conversations.first { it.match.id == DemoIds.M_MEERA_CUTTING_CHAI }.lastMessage)
        val role = first.first().role.id
        e.swipeRole(meera, role, SwipeDir.LEFT).getOrThrow()
        assertTrue(q.discoverCandidates(e.state.value, meera, null) !== first)
    }

    @Test fun `booking cards carry rights wording by department`() {
        val s = engineAt(T.anchor).state.value
        val dana = s.bookingCards.getValue(DemoIds.BC_DANA_SOFTLIGHT_1).terms
        assertEquals(com.tff.app.domain.BookingTermsFactory.RIGHTS_POST, dana.rightsNote)
        val meera = s.bookingCards.values.filter { it.matchId == DemoIds.M_MEERA_CUTTING_CHAI }.maxBy { it.version }.terms
        assertEquals("Lead Actor · Sister Rekha", meera.creditTitle)
        assertEquals(com.tff.app.domain.BookingTermsFactory.RIGHTS_CAST, meera.rightsNote)
        val e = engineAt(T.anchor)
        val rohan = e.startDemo(DemoPersona.ROHAN).userId
        assertNotNull(e.draftBookingTerms(rohan, DemoIds.M_ROHAN_DP).rightsNote)
        assertNull(s.profiles.keys.firstOrNull { it in DemoPeople.SCOUT_POOL && s.applications.values.any { a -> a.applicantId == it && a.roleId in crewRoles && it != "u_079" && it != "u_083" } })
    }
}
