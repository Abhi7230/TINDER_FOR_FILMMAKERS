package com.tff.app.domain

import com.tff.app.model.AuditionMode
import com.tff.app.model.AuditionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AuditionRequestTest {

    private val day = LocalDate.of(2026, 10, 10)
    private val selfTape = AuditionRequest(AuditionMode.SELF_TAPE, sidesUrl = "https://drive.google.com/file/d/abc", dueDate = day)
    private val video = AuditionRequest(AuditionMode.VIDEO_CALL, date = day, time = LocalTime.of(11, 0), meetingLink = "https://meet.google.com/abc-defg-hij")
    private val studio = AuditionRequest(AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS, venue = "Prithvi Theatre foyer", address = "Juhu Church Rd, Mumbai",
        date = day, time = LocalTime.of(15, 30), alsoPresent = listOf("Casting assistant", "DP"))

    @Test fun validRequestsPass() {
        assertNull(AuditionRules.validate(selfTape, AuditionMode.SELF_TAPE))
        assertNull(AuditionRules.validate(video, AuditionMode.VIDEO_CALL))
        assertNull(AuditionRules.validate(studio, AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
    }

    @Test fun modeMustEqualTheListing() {
        assertEquals("This listing says auditions are by self-tape. Use that.", AuditionRules.validate(video, AuditionMode.SELF_TAPE))
        assertEquals(AuditionRules.ERR_NO_MODE, AuditionRules.validate(selfTape, null))
    }

    @Test fun inPersonOutsideDaytimeRejected() {
        assertEquals(AuditionRules.ERR_HOURS, AuditionRules.validate(studio.copy(time = LocalTime.of(7, 30)), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        assertEquals(AuditionRules.ERR_HOURS, AuditionRules.validate(studio.copy(time = LocalTime.of(21, 30)), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        assertEquals(AuditionRules.ERR_HOURS, AuditionRules.validate(studio.copy(time = LocalTime.of(23, 0)), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        assertNull(AuditionRules.validate(studio.copy(time = LocalTime.of(8, 0)), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        assertNull(AuditionRules.validate(studio.copy(time = LocalTime.of(21, 0)), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        // Late-night video "auditions" are blocked too.
        assertEquals(AuditionRules.ERR_HOURS, AuditionRules.validate(video.copy(time = LocalTime.of(23, 15)), AuditionMode.VIDEO_CALL))
    }

    @Test fun studioNeedsSomeoneElsePresent() {
        assertEquals(AuditionRules.ERR_ALSO_PRESENT, AuditionRules.validate(studio.copy(alsoPresent = emptyList()), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        assertEquals(AuditionRules.ERR_ALSO_PRESENT, AuditionRules.validate(studio.copy(alsoPresent = listOf(" ")), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
    }

    @Test fun noPrivateHomes() {
        assertEquals(AuditionRules.ERR_PRIVATE, AuditionRules.validate(studio.copy(venue = "My flat", address = "Versova"), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        assertEquals(AuditionRules.ERR_PRIVATE, AuditionRules.validate(studio.copy(venue = "Hotel room 304", address = "Andheri"), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
    }

    @Test fun requiredFieldsPerMode() {
        assertEquals(AuditionRules.ERR_SIDES, AuditionRules.validate(selfTape.copy(sidesUrl = null), AuditionMode.SELF_TAPE))
        assertEquals(AuditionRules.ERR_SIDES, AuditionRules.validate(selfTape.copy(sidesUrl = "the sides"), AuditionMode.SELF_TAPE))
        assertEquals(AuditionRules.ERR_DUE, AuditionRules.validate(selfTape.copy(dueDate = null), AuditionMode.SELF_TAPE))
        assertEquals(AuditionRules.ERR_WHEN, AuditionRules.validate(video.copy(time = null), AuditionMode.VIDEO_CALL))
        assertEquals(AuditionRules.ERR_VENUE, AuditionRules.validate(studio.copy(venue = " "), AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS))
        assertEquals(AuditionRules.ERR_LINK_ONLY_VIDEO, AuditionRules.validate(selfTape.copy(meetingLink = "https://zoom.us/j/1"), AuditionMode.SELF_TAPE))
        assertEquals(AuditionRules.ERR_PAST, AuditionRules.validate(selfTape, AuditionMode.SELF_TAPE, today = day.plusDays(1)))
    }

    @Test fun freeTextIsScreened() {
        assertEquals(AuditionRules.ERR_MONEY, AuditionRules.validate(selfTape.copy(note = "Audition fee ₹500 payable on GPay"), AuditionMode.SELF_TAPE))
        assertEquals(AuditionRules.ERR_BODY, AuditionRules.validate(selfTape.copy(note = "Also send your measurements"), AuditionMode.SELF_TAPE))
        assertNull(AuditionRules.validate(selfTape.copy(note = "Scene 4, both pages. Natural light is fine."), AuditionMode.SELF_TAPE))
    }

    @Test fun safetyLinesAreFixed() {
        assertEquals("No nudity at auditions · No fees · Bring a friend if you like · You never have to meet alone", AuditionRules.SAFETY_LINES)
    }
}
