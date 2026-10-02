package com.tff.app.ui.screens.me

import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.T
import com.tff.app.model.CompMode
import com.tff.app.model.DemoPersona
import com.tff.app.model.LinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** S20: the edit form must round-trip every persona's profile without inventing changes, and save what was edited. */
class EditFormTest {
    private val seed = T.seed()

    @Test fun everyPersonaRoundTripsWithoutChanges() {
        DemoPersona.entries.forEach { persona ->
            val p = seed.profiles.getValue(DemoIds.personaUser(persona))
            val rate = seed.privateRates[p.id]
            val form = EditForm.from(p, rate)
            val back = form.toProfile()
            assertEquals(p.displayName, back.displayName)
            assertEquals(p.primaryRole, back.primaryRole)
            assertEquals(p.secondaryRoles.map { it.code }, back.secondaryRoles.map { it.code })
            assertEquals(p.stepUpRoles, back.stepUpRoles)
            assertEquals(p.unpaidMustHaves.toSet(), back.unpaidMustHaves.toSet())
            assertEquals(p.contentWontDo.toSet(), back.contentWontDo.toSet())
            assertEquals(p.workLinks.map { it.url }, back.workLinks.map { it.url })
            assertEquals(p.busyDates, back.busyDates)
            assertEquals(p.kitFeeDay, back.kitFeeDay)
            // a form built from the saved profile is the same form: no phantom "unsaved changes"
            assertEquals(form.copy(base = back), EditForm.from(back, rate))
            assertTrue("${persona.name} form should be valid", form.valid)
        }
    }

    @Test fun editsLandInTheProfile() {
        val dana = seed.profiles.getValue(DemoIds.personaUser(DemoPersona.DANA))
        val form = EditForm.from(dana, 350).copy(
            bio = "Production sound, 9 yrs.",
            busy = dana.busyDates + LocalDate.of(2026, 10, 20),
            compMode = CompMode.PAID_ONLY,
            links = EditForm.from(dana, 350).links + LinkDraft(99, "", "vimeo.com/123", LinkKind.REEL, null, "Reel 2026", "2026"),
        )
        val p = form.toProfile()
        assertEquals("Production sound, 9 yrs.", p.bio)
        assertTrue(LocalDate.of(2026, 10, 20) in p.busyDates)
        assertTrue(p.unpaidMustHaves.isEmpty())                     // paid only: no unpaid conditions
        assertEquals("https://vimeo.com/123", p.workLinks.last().url)
        assertEquals(2026, p.workLinks.last().year)
    }

    @Test fun invalidFieldsBlockSaving() {
        val meera = seed.profiles.getValue(DemoIds.personaUser(DemoPersona.MEERA))
        val base = EditForm.from(meera, null)
        assertFalse(base.copy(name = "M").valid)
        assertFalse(base.copy(bio = "x".repeat(281)).valid)
        assertFalse(base.copy(minRate = "0").valid)
        assertFalse(base.copy(languages = emptySet()).valid)
        assertFalse(base.copy(links = listOf(LinkDraft(1, "", "not a link", LinkKind.REEL, null, "", ""))).valid)
        assertTrue(base.copy(minRate = "2,500").valid)
    }
}
