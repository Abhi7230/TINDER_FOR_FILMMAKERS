package com.tff.app.ui.screens.role

import com.tff.app.model.GearPolicy
import com.tff.app.model.LinkKind
import com.tff.app.model.RoleCode
import com.tff.app.model.WorkLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S07 Apply sheet helpers and S06 role-details formatting. */
class ApplyActionsTest {
    @Test fun `link kind is detected from the url`() {
        assertEquals(LinkKind.REEL, ApplyActions.kindFor("https://youtu.be/abcdefghijk"))
        assertEquals(LinkKind.REEL, ApplyActions.kindFor("https://vimeo.com/123"))
        assertEquals(LinkKind.IMDB, ApplyActions.kindFor("https://www.imdb.com/name/nm1/"))
        assertEquals(LinkKind.PORTFOLIO, ApplyActions.kindFor("https://drive.google.com/x"))
    }

    @Test fun `only full links are accepted`() {
        assertTrue(ApplyActions.isValidUrl("https://youtu.be/abcdefghijk"))
        assertTrue(ApplyActions.isValidUrl(" http://example.com/reel "))
        assertFalse(ApplyActions.isValidUrl("youtu.be/abc"))
        assertFalse(ApplyActions.isValidUrl("https://"))
        assertFalse(ApplyActions.isValidUrl("https://my reel.com"))
    }

    @Test fun `reels for this role come first and headshots only for cast roles`() {
        val links = listOf(
            WorkLink("1", "https://x.com/p", LinkKind.PORTFOLIO),
            WorkLink("2", "https://youtu.be/aaaaaaaaaaa", LinkKind.REEL, roleCode = RoleCode.GAFFER),
            WorkLink("3", "https://youtu.be/bbbbbbbbbbb", LinkKind.REEL, roleCode = RoleCode.DP),
            WorkLink("4", "https://x.com/h", LinkKind.HEADSHOT),
        )
        assertEquals(listOf("3", "2", "1"), ApplyActions.orderedLinks(links, RoleCode.DP, isCast = false).map { it.id })
        assertTrue(ApplyActions.orderedLinks(links, RoleCode.ACTOR, isCast = true).any { it.id == "4" })
    }

    @Test fun `kit toggle only for bring-your-own roles`() {
        assertTrue(ApplyActions.isBringYourOwn(GearPolicy.BYO_WITH_KIT_FEE))
        assertTrue(ApplyActions.isBringYourOwn(GearPolicy.BYO_NO_FEE))
        assertFalse(ApplyActions.isBringYourOwn(GearPolicy.PRODUCTION_PROVIDES))
        assertFalse(ApplyActions.isBringYourOwn(null))
    }

    @Test fun `role details read as facts, not form hints`() {
        assertEquals("Size", detailLabel("Size (lead / supporting / day player)"))
        assertEquals("Sides available", detailLabel("Sides available (Y/N)"))
        assertEquals("Character name", detailLabel("Character name"))
        assertEquals("Yes", detailValue("Y", "Yes", "No"))
        assertEquals("No", detailValue("n", "Yes", "No"))
        assertEquals("Day player", detailValue("DAY_PLAYER", "Yes", "No"))
        assertEquals("Lead", detailValue("LEAD", "Yes", "No"))
        assertEquals("22-28", detailValue("22-28", "Yes", "No"))
        assertEquals("Asha, the pickpocket", detailValue("Asha, the pickpocket", "Yes", "No"))
    }

    @Test fun `link host for untitled links`() {
        assertEquals("youtube.com", linkHost("https://www.youtube.com/watch?v=abc"))
        assertEquals("vimeo.com", linkHost("https://vimeo.com/1"))
    }
}
