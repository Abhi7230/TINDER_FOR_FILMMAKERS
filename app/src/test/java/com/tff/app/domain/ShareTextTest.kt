package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ShareTextTest {

    private val soundItem = Fx.item(role = Fx.role(id = "r_sound", kitFeeDay = 1000))

    @Test fun roleCardMatchesS11Template() {
        val text = ShareText.roleCard(soundItem, "https://calltime.app")
        assertEquals(
            """
            CREW CALL · Sound Mixer · "Filter Coffee, 4 AM" (Kannada/English short, 12 min)
            Sat 14 – Sun 15 Nov · 2 days · ≤10h · Jayanagar, Bengaluru
            Pay: ₹2,500/day + ₹1,000 kit · paid on wrap · 2 meals
            Apply free on Calltime: https://calltime.app/r/r_sound
            """.trimIndent(),
            text,
        )
    }

    @Test fun blankBaseUrlFallsBackToPlayStore() {
        val text = ShareText.roleCard(soundItem, "")
        assertTrue(text.endsWith("Apply free on Calltime (Google Play): https://play.google.com/store/apps/details?id=com.tff.app"))
        assertFalse(text.contains("/r/"))
        // trailing slash on the base is tolerated
        assertTrue(ShareText.roleCard(soundItem, "https://calltime.app/").endsWith("https://calltime.app/r/r_sound"))
    }

    @Test fun postRoleReplacesDatesLine() {
        val composer = Fx.role(id = "r_comp", projectId = "p_soft", roleCode = RoleCode.COMPOSER, compType = CompType.PAID_FLAT,
            amountMin = 12000, paymentTerms = PaymentTerms.WITHIN_7_DAYS, remoteOk = true, postDeadline = LocalDate.of(2027, 2, 7))
        val item = Fx.item(role = composer, project = Fx.postProject(languages = listOf("kn", "en"), title = "Filter Coffee, 4 AM"))
        val lines = ShareText.roleCard(item, "https://calltime.app").lines()
        assertEquals("CREW CALL · Composer · \"Filter Coffee, 4 AM\" (Kannada/English short, 12 min)", lines[0])
        assertEquals("Remote · picture lock 10 Jan · deliver by 7 Feb", lines[1])
        assertEquals("Pay: ₹12,000 flat · paid within 7 days of delivery", lines[2])
    }

    @Test fun castRoleSaysCastingCallAndUnpaidPerks() {
        val actor = Fx.role(id = "r_lead", roleCode = RoleCode.ACTOR, compType = CompType.UNPAID,
            perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 6, roleDetails = mapOf("size" to "LEAD"))
        val lines = ShareText.roleCard(Fx.item(role = actor), "https://calltime.app").lines()
        assertTrue(lines[0].startsWith("CASTING CALL · Lead Actor · "))
        assertEquals("Pay: Unpaid · 2 meals · TA ₹300 · your scenes in 6 wks", lines[2])
    }

    @Test fun daySheetMatchesS16VTemplate() {
        val day = Fx.shootDay(address = "12, 4th Cross, Jayanagar")
        val text = ShareText.daySheet(Fx.project(), day, 1, 2, "https://calltime.app")
        assertEquals(
            """
            CALL SHEET · "Filter Coffee, 4 AM" · Day 1 of 2 · Sat 14 Nov
            General call 07:00 · Wrap by 17:00
            Location: 12, 4th Cross, Jayanagar · Map: https://maps.google.com/?q=12%2C%204th%20Cross%2C%20Jayanagar
            Parking: Street parking on 4th Cross · Nearest hospital: Sagar Hospital, Jayanagar (2 km)
            Meals: breakfast + lunch
            Your individual call time is in Calltime: https://calltime.app/d/sd_1
            """.trimIndent(),
            text,
        )
    }

    @Test fun daySheetWithoutBaseOrOptionals() {
        val day = Fx.shootDay(parking = null, hospital = null, meals = null, mapsUrl = "https://maps.google.com/?q=x")
        val lines = ShareText.daySheet(Fx.project(), day, 2, 2, "").lines()
        assertEquals(4, lines.size)
        assertEquals("Location: ${day.address} · Map: https://maps.google.com/?q=x", lines[2])
        assertEquals("Your individual call time is in Calltime (Google Play): ${ShareText.PLAY_URL}", lines[3])
    }

    @Test fun trustedContactHasNoOtherPeople() {
        val text = ShareText.trustedContact(Fx.project(), Fx.shootDay(), LocalTime.of(7, 30))
        assertTrue(text.contains("Filter Coffee, 4 AM"))
        assertTrue(text.contains("My call time 07:30 · Wrap by 17:00"))
        assertTrue(text.contains("Address: 12, 4th Cross, Jayanagar 4th Block, Bengaluru 560011"))
        assertFalse(text.contains("Rohan"))
        // falls back to the general call
        assertTrue(ShareText.trustedContact(Fx.project(), Fx.shootDay(), null).contains("My call time 07:00"))
    }

    @Test fun mapsUrlEncodesAndFlattens() {
        assertEquals("https://maps.google.com/?q=Versova%2C%20Mumbai", ShareText.mapsUrl("Versova,\nMumbai"))
        assertEquals("https://maps.google.com/?q=A%26B%20Studio", ShareText.mapsUrl("A&B Studio"))
    }

    @Test fun calendarDescriptionHasLogistics() {
        val d = ShareText.calendarDescription(Fx.project(), Fx.shootDay(notes = "Bring a jacket"))
        assertTrue(d.contains("General call 07:00 · Wrap by 17:00"))
        assertTrue(d.contains("Parking: Street parking on 4th Cross"))
        assertTrue(d.contains("Notes: Bring a jacket"))
    }
}
