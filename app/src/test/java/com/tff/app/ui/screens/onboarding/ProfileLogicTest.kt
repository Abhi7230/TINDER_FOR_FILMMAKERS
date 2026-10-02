package com.tff.app.ui.screens.onboarding

import com.tff.app.data.demo.DemoCities
import com.tff.app.model.CompMode
import com.tff.app.model.Density
import com.tff.app.model.Goal
import com.tff.app.model.Level
import com.tff.app.model.LinkKind
import com.tff.app.model.RoleCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLogicTest {

    // ── S04 role tap / long-press rules ──

    @Test fun firstTapMakesMainRoleThenOthersAreAlso() {
        val s = RoleSelection().tap(RoleCode.WRITER).tap(RoleCode.EDITOR)
        assertEquals(RoleCode.WRITER, s.primary)
        assertEquals(listOf(RoleCode.EDITOR), s.secondary)
        assertEquals(RoleTag.MAIN, s.tagOf(RoleCode.WRITER))
        assertEquals(RoleTag.ALSO, s.tagOf(RoleCode.EDITOR))
    }

    @Test fun tappingMainAgainMakesItSecondaryThenOff() {
        val once = RoleSelection().tap(RoleCode.WRITER)
        val twice = once.tap(RoleCode.WRITER)
        assertNull(twice.primary)
        assertEquals(listOf(RoleCode.WRITER), twice.secondary)
        val thrice = twice.tap(RoleCode.WRITER)
        assertTrue(thrice.all.isEmpty())
        // with no main role, the next tap picks the main role again
        assertEquals(RoleCode.DIRECTOR, twice.tap(RoleCode.DIRECTOR).primary)
    }

    @Test fun atMostFourOtherRoles() {
        var s = RoleSelection().tap(RoleCode.DP)
        listOf(RoleCode.CAMERA_OP, RoleCode.FIRST_AC, RoleCode.GAFFER, RoleCode.GRIP, RoleCode.EDITOR).forEach { s = s.tap(it) }
        assertEquals(4, s.secondary.size)
        assertFalse(RoleCode.EDITOR in s.all)
    }

    @Test fun longPressStepUpMaxTwoAndMovesRoleOutOfOthers() {
        var s = RoleSelection().tap(RoleCode.GAFFER).tap(RoleCode.GRIP)
        s = s.longPress(RoleCode.DP).longPress(RoleCode.GRIP)
        assertEquals(listOf(RoleCode.DP, RoleCode.GRIP), s.stepUp)
        assertTrue(s.secondary.isEmpty())
        val third = s.longPress(RoleCode.DIRECTOR)
        assertEquals(s, third)                      // max 2: unchanged
        assertEquals(listOf(RoleCode.GRIP), s.longPress(RoleCode.DP).stepUp)   // long-press again removes
    }

    @Test fun tappingAStepUpRoleMakesItARoleYouDo() {
        val s = RoleSelection().longPress(RoleCode.DP).tap(RoleCode.DP)
        assertEquals(RoleCode.DP, s.primary)
        assertTrue(s.stepUp.isEmpty())
    }

    // ── defaults ──

    @Test fun remoteDefaultsOnForPostMusicWriting() {
        assertTrue(ProfileLogic.remoteDefault(RoleCode.WRITER))
        assertTrue(ProfileLogic.remoteDefault(RoleCode.COMPOSER))
        assertTrue(ProfileLogic.remoteDefault(RoleCode.EDITOR))
        assertFalse(ProfileLogic.remoteDefault(RoleCode.ACTOR))
        assertFalse(ProfileLogic.remoteDefault(null))
    }

    @Test fun skippedStepThreeDefaults() {
        assertEquals(CompMode.OPEN_TO_UNPAID, ProfileLogic.defaultCompMode(Level.FIRST_PROJECT))
        assertEquals(CompMode.PAID_AND_SELECTIVE, ProfileLogic.defaultCompMode(Level.WORKING_PRO))
        val goals = ProfileLogic.defaultGoals(Level.FIRST_PROJECT, CompMode.OPEN_TO_UNPAID, emptyList())
        assertTrue(Goal.REEL_FOOTAGE in goals && Goal.BUILD_TEAM in goals)
        assertTrue(Goal.STEP_UP in ProfileLogic.defaultGoals(Level.CREDITED_INDIE, CompMode.PAID_AND_SELECTIVE, listOf(RoleCode.DP)))
    }

    // ── links, rates, names ──

    @Test fun linkKindIsDetectedFromTheUrl() {
        assertEquals(LinkKind.REEL, ProfileLogic.detectLinkKind("https://youtu.be/abc"))
        assertEquals(LinkKind.REEL, ProfileLogic.detectLinkKind("vimeo.com/123"))
        assertEquals(LinkKind.IMDB, ProfileLogic.detectLinkKind("https://www.imdb.com/name/nm1"))
        assertEquals(LinkKind.PORTFOLIO, ProfileLogic.detectLinkKind("https://drive.google.com/file/x"))
        assertEquals(LinkKind.OTHER, ProfileLogic.detectLinkKind("https://example.org"))
    }

    @Test fun urlsAreNormalisedOrRejected() {
        assertEquals("https://youtu.be/abc", ProfileLogic.normaliseUrl("youtu.be/abc"))
        assertEquals("http://x.org", ProfileLogic.normaliseUrl("http://x.org"))
        assertNull(ProfileLogic.normaliseUrl("not a link"))
        assertNull(ProfileLogic.normaliseUrl("hello"))
        assertNull(ProfileLogic.normaliseUrl(""))
    }

    @Test fun ratesAreWholePositiveNumbers() {
        assertNull(ProfileLogic.parseRate(""))
        assertEquals(2500, ProfileLogic.parseRate("2,500"))
        assertEquals(-1, ProfileLogic.parseRate("0"))
        assertEquals(-1, ProfileLogic.parseRate("abc"))
    }

    @Test fun shortNameMatchesTheCardFace() {
        assertEquals("Meera K.", ProfileLogic.shortName("Meera Kulkarni"))
        assertEquals("Asha", ProfileLogic.shortName(" Asha "))
        assertEquals("Ravi S.", ProfileLogic.shortName("Ravi Kumar sharma"))
    }

    // ── live radius line ──

    @Test fun nearbyCountsGrowWithRadiusAndEarlyCityCountsEveryone() {
        val mumbai = DemoCities.city("mumbai")!!
        val d = Density("mumbai", "Mumbai", isLive = true, isEarly = false, people = 32, openProjects = 9)
        val small = ProfileLogic.nearby(mumbai, "mumbai_versova", 2, d, emptyList())
        val wide = ProfileLogic.nearby(mumbai, "mumbai_versova", 50, d, emptyList())
        assertTrue(small.people!! < wide.people!!)
        assertEquals(32, wide.people)
        val indore = DemoCities.city("indore")!!
        val di = Density("indore", "Indore", isLive = false, isEarly = true, people = 3, openProjects = 1)
        assertEquals(3, ProfileLogic.nearby(indore, indore.areas.first().id, 2, di, emptyList()).people)
        assertNull(ProfileLogic.nearby(indore, null, 15, null, emptyList()).people)
    }
}
