package com.tff.app.ui.screens.crewup

import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.T
import com.tff.app.model.ApplicantView
import com.tff.app.model.Application
import com.tff.app.model.DemoPersona
import com.tff.app.model.ProfileCard
import com.tff.app.model.Reputation
import com.tff.app.model.RoleCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S09 Crew up rules, plus Rohan's seeded Crew up through DemoQueries. */
class CrewUpLogicTest {
    private val seed = T.seed()

    private fun role(id: String) = seed.roles.getValue(id)

    @Test fun `cast roles have no Scout and crew roles do`() {
        assertFalse(CrewUpLogic.hasScout(role(DemoIds.R_FC_ACTOR)))
        assertTrue(CrewUpLogic.hasScout(role(DemoIds.R_FC_SOUND)))
        assertTrue(CrewUpLogic.hasScout(role(DemoIds.R_FC_DP)))
    }

    @Test fun `scarce roles open on Scout only while nobody is waiting for an answer`() {
        val sound = role(DemoIds.R_FC_SOUND)   // SCARCE
        assertEquals(RoleCode.SOUND_MIXER, sound.roleCode)
        assertEquals(CrewTab.SCOUT, CrewUpLogic.defaultTab(sound, 0))
        assertEquals(CrewTab.APPLICANTS, CrewUpLogic.defaultTab(sound, 3))
        assertEquals(CrewTab.APPLICANTS, CrewUpLogic.defaultTab(role(DemoIds.R_FC_DP), 0))      // BALANCED
        assertEquals(CrewTab.APPLICANTS, CrewUpLogic.defaultTab(role(DemoIds.R_FC_ACTOR), 0))   // cast: Applicants only
    }

    @Test fun scarceRolesWithFewApplicantsPointAtScout() {
        val sound = role(DemoIds.R_FC_SOUND)
        assertTrue(CrewUpLogic.showScoutHint(sound, 3, 6))
        assertFalse(CrewUpLogic.showScoutHint(sound, 3, 0))                         // Scout has nobody
        assertFalse(CrewUpLogic.showScoutHint(sound, CrewUpLogic.FEW_APPLICANTS, 6))  // enough applicants
        assertFalse(CrewUpLogic.showScoutHint(sound, 0, 6))                          // opens on Scout anyway
        assertFalse(CrewUpLogic.showScoutHint(role(DemoIds.R_FC_DP), 2, 6))          // BALANCED
        assertFalse(CrewUpLogic.showScoutHint(role(DemoIds.R_FC_ACTOR), 2, 6))       // cast: never Scout
    }

    private fun view(id: String, app: String?) = ApplicantView(
        application = app?.let { Application(it, "r", id) },
        card = ProfileCard(seed.profiles.getValue(DemoIds.MEERA).copy(id = id), Reputation(id)),
        score = 0.0, reasons = emptyList(), rateFit = 1.0, availDays = 0, totalDays = 0,
    )

    @Test fun `deck order stays put while new people join at the back`() {
        val a = view("u_a", "a1"); val b = view("u_b", "a2"); val c = view("u_c", "a3"); val d = view("u_d", "a4")
        val first = CrewUpLogic.stableOrder(emptyList(), listOf(a, b, c))
        assertEquals(listOf(a, b, c), first)
        // b was answered, d arrived ranked first: d goes to the back, a and c keep their places.
        val next = CrewUpLogic.stableOrder(first.map(CrewUpLogic::key), listOf(d, c, a))
        assertEquals(listOf(a, c, d), next)
        // Scout candidates (no application) are keyed by profile.
        assertEquals("p:u_x", CrewUpLogic.key(view("u_x", null)))
    }

    @Test fun `Rohan has Applicants for every open role and no Scout on the lead actor`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.ROHAN)
        val q = DemoQueries(e)
        val s = e.state.value
        val ids = CrewUpLogic.openRoleIds(q.myProjects(s, DemoIds.ROHAN))
        assertEquals(listOf(DemoIds.R_FC_ACTOR, DemoIds.R_FC_DP, DemoIds.R_FC_EDITOR, DemoIds.R_FC_SOUND), ids)
        ids.forEach { id -> assertTrue("no applicants for $id", q.applicants(s, DemoIds.ROHAN, id).isNotEmpty()) }
        assertTrue(q.scout(s, DemoIds.ROHAN, DemoIds.R_FC_ACTOR).isEmpty())
        assertFalse(CrewUpLogic.hasScout(s.roles.getValue(DemoIds.R_FC_ACTOR)))
        // The booked PD role isn't open any more, so it has no chip.
        assertFalse(DemoIds.R_FC_PD in ids)
    }

    @Test fun `in talks line names person, role and project`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.ROHAN)
        val q = DemoQueries(e)
        val s = e.state.value
        val item = q.roleWithProject(s, DemoIds.ROHAN, DemoIds.R_FC_SOUND)!!
        val v = q.applicants(s, DemoIds.ROHAN, DemoIds.R_FC_SOUND).first()
        val line = inTalksLine(v, item)
        assertTrue(line, line.startsWith(v.card.profile.shortName + " · Sound Mixer · "))
        assertTrue(line.endsWith(item.project.title))
        assertFalse(line.contains("match", ignoreCase = true))
    }
}
