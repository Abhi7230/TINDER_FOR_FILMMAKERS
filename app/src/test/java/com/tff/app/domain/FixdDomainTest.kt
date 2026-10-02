package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FIXD: local-first deck order, booking-term wording by department, the zero-budget label rule. */
class FixdDomainTest {

    // ── MatchEngine: a local project never ranks below remote roles from other cities ──

    private val writer = Fx.profile(id = "u_writer", name = "New Writer", primaryRole = RoleCode.WRITER, level = Level.FIRST_PROJECT,
        cityId = "indore", areaId = "indore_city_centre", remoteOk = true, languages = listOf("hi", "en"))
    private val indore = Fx.viewer(writer, lat = 22.7196, lng = 75.8577)

    private fun writerRole(id: String, comp: CompType, amount: Int?) = Fx.role(id = id, projectId = "p_$id", roleCode = RoleCode.WRITER,
        compType = comp, amountMin = amount, remoteOk = true, postDeadline = Fx.TODAY.plusDays(40), gearPolicy = null)

    /** A strong, paid remote gig in Bengaluru vs a weak, unpaid one from a new owner in the viewer's own city. */
    private val remoteBlr = Fx.item(role = writerRole("blr", CompType.STIPEND, 8000),
        project = Fx.project(id = "p_blr", ownerId = "u_deepa", cityId = "bengaluru", areaId = "bengaluru_koramangala"),
        owner = Fx.owner(id = "u_deepa", idVerified = true, reputation = Fx.reputation(id = "u_deepa", shoots = 5, footage = Ratio(8, 8))))
    private val localIndore = Fx.item(role = writerRole("rr", CompType.UNPAID, null),
        project = Fx.project(id = "p_rr", ownerId = "u_057", cityId = "indore", areaId = "indore_city_centre", ambition = Ambition.FOR_FUN),
        owner = Fx.owner(id = "u_057", reputation = Fx.reputation(id = "u_057", shoots = 0, replyHours = null)),
        cityName = "Indore", areaName = "City centre", lat = 22.7196, lng = 75.8577)

    @Test fun `an Indore writer sees the local Indore project before stronger remote gigs from other cities`() {
        assertTrue("the remote gig still scores higher", MatchEngine.score(indore, remoteBlr, Fx.TODAY, Fx.NOW) > MatchEngine.score(indore, localIndore, Fx.TODAY, Fx.NOW))
        val deck = MatchEngine.buildDeck(indore, listOf(remoteBlr, localIndore), Fx.TODAY, Fx.NOW)
        assertEquals(listOf("rr", "blr"), deck.map { it.item.role.id })
        assertTrue(MatchEngine.isLocal(indore, localIndore))
        assertFalse(MatchEngine.isLocal(indore, remoteBlr))
    }

    @Test fun `remote work for a project in your own city earns proximity logistics plus the same-city bonus`() {
        val local = MatchEngine.fit(indore, localIndore, Fx.TODAY).logistics
        val remote = MatchEngine.fit(indore, remoteBlr, Fx.TODAY).logistics
        assertEquals(12.0, remote, 0.001)
        assertEquals(15.0, local, 0.001)
    }

    @Test fun `on-set cards nearby still rank among themselves by score`() {
        val v = Fx.viewer(Fx.profile(primaryRole = RoleCode.SOUND_MIXER), lat = Fx.JAYANAGAR_LAT, lng = Fx.JAYANAGAR_LNG)
        val paid = Fx.item(role = Fx.role(id = "a", projectId = "p_a"), project = Fx.project(id = "p_a"))
        val unpaid = Fx.item(role = Fx.role(id = "b", projectId = "p_b", compType = CompType.UNPAID), project = Fx.project(id = "p_b"))
        val deck = MatchEngine.buildDeck(v.copy(profile = v.profile.copy(cityId = "blr", areaId = "blr_jayanagar")), listOf(unpaid, paid), Fx.TODAY, Fx.NOW)
        assertEquals("a", deck.first().item.role.id)
    }

    // ── BookingTermsFactory: rights and credit wording by department ──

    private fun terms(role: ProjectRole, project: Project = Fx.project(id = role.projectId)) =
        BookingTermsFactory.fromRole(Fx.item(role = role, project = project), "Rohan K.")

    @Test fun `writers keep feature and remake rights and get the credit type their scope implies`() {
        val polish = terms(Fx.role(id = "w1", roleCode = RoleCode.WRITER, compType = CompType.UNPAID, remoteOk = true,
            postDeadline = Fx.TODAY.plusDays(20), scopeIncludes = listOf("Dialogue polish"), gearPolicy = null))
        assertEquals(BookingTermsFactory.RIGHTS_WRITING, polish.rightsNote)
        assertTrue(polish.rightsNote.contains("feature"))
        assertEquals("Dialogue by", polish.creditTitle)
        assertEquals("Screenplay by (shared credit)", BookingTermsFactory.writerCredit(listOf("Rewrite", "Dialogue polish")))
        assertEquals("Story by", BookingTermsFactory.writerCredit(listOf("Treatment")))
        assertEquals("Written by", BookingTermsFactory.writerCredit(emptyList()))
    }

    @Test fun `composers keep the copyright, post crew get finished-film clips, cast and crew get reel clips`() {
        val composer = terms(Fx.role(id = "c", roleCode = RoleCode.COMPOSER, compType = CompType.PAID_FLAT, amountMin = 12000, remoteOk = true,
            postDeadline = Fx.TODAY.plusDays(50), gearPolicy = null))
        assertEquals(BookingTermsFactory.RIGHTS_MUSIC, composer.rightsNote)
        assertTrue(composer.rightsNote.startsWith("You keep the copyright"))
        val editor = terms(Fx.role(id = "e", roleCode = RoleCode.EDITOR, compType = CompType.STIPEND, amountMin = 6000, remoteOk = true,
            postDeadline = Fx.TODAY.plusDays(40), gearPolicy = null))
        assertEquals(BookingTermsFactory.RIGHTS_POST, editor.rightsNote)
        val sound = terms(Fx.role())
        assertEquals(BookingTermsFactory.RIGHTS_CREW, sound.rightsNote)
        assertEquals(BookingTermsFactory.FOOTAGE_CREW, sound.footageDefinition)
        assertEquals("Sound Recordist", sound.creditTitle)
    }

    @Test fun `cast credits keep the lead and the character`() {
        val lead = terms(Fx.role(id = "l", roleCode = RoleCode.ACTOR, compType = CompType.UNPAID, footageDueWeeks = 6, gearPolicy = null,
            perks = setOf(Perk.FOOTAGE_BY_DATE), roleDetails = mapOf("size" to "LEAD", "character" to "Kaveri, the darshini owner")))
        assertEquals("Lead Actor · Kaveri", lead.creditTitle)
        assertEquals(BookingTermsFactory.RIGHTS_CAST, lead.rightsNote)
        assertEquals(BookingTermsFactory.FOOTAGE_CAST, lead.footageDefinition)
        val typed = terms(Fx.role(id = "t", roleCode = RoleCode.ACTOR, creditTitle = "Kaveri", compType = CompType.UNPAID, gearPolicy = null))
        assertEquals("a typed credit is kept", "Kaveri", typed.creditTitle)
    }

    // ── ListingRules R23: "Honest zero-budget" only when nobody is paid ──

    @Test fun `the zero-budget label is dropped when any role pays a day rate or a flat fee`() {
        val p = Fx.project().copy(honestZeroBudget = true)
        val paid = ProjectDraft(p, listOf(Fx.role(compType = CompType.PAID_DAY), Fx.role(id = "r2", compType = CompType.UNPAID)))
        assertTrue(ListingRules.check(paid, Fx.profile(id = "u_rohan"), Fx.city(), Fx.TODAY).any { it.rule == "R23" && it.severity == Severity.AUTO })
        assertFalse(ListingRules.applyAuto(paid, Fx.city()).project.honestZeroBudget)
        val stipend = ProjectDraft(p, listOf(Fx.role(compType = CompType.STIPEND, amountMin = 800), Fx.role(id = "r2", compType = CompType.UNPAID)))
        assertTrue(ListingRules.applyAuto(stipend, Fx.city()).project.honestZeroBudget)
        assertTrue(ListingRules.check(stipend, Fx.profile(id = "u_rohan"), Fx.city(), Fx.TODAY).none { it.rule == "R23" })
    }
}
