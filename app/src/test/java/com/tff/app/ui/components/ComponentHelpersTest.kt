package com.tff.app.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.tff.app.model.RoleCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure helpers behind the design-system components (no Compose runtime needed). */
class ComponentHelpersTest {

    @Test fun youTubeIdFromEveryLinkShape() {
        val id = "aqz-KE-bpKQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id",
            "https://youtu.be/$id?t=42",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://m.youtube.com/watch?v=$id&list=x",
        ).forEach { assertEquals(it, id, youTubeId(it)) }
        assertEquals("https://img.youtube.com/vi/$id/hqdefault.jpg", youTubeThumbnail("https://youtu.be/$id"))
    }

    @Test fun nonYouTubeLinksHaveNoThumbnail() {
        assertNull(youTubeId(null))
        assertNull(youTubeId("https://vimeo.com/123456789"))
        assertNull(youTubeId("https://drive.google.com/file/d/abc/view"))
        assertNull(youTubeThumbnail("not a url"))
    }

    @Test fun ratiosAreSpokenForTalkBack() {
        assertEquals("Paid on time 11 of 12", spokenRatios("Paid on time 11/12"))
        assertEquals("SOUND MIXER times 1", spokenRatios("SOUND MIXER ×1"))
        assertEquals("up to 10H", spokenRatios("≤10H"))
    }

    @Test fun ratioParsing() {
        assertEquals(3 to 7, ratioIn("Paid on time 3/7"))
        assertNull(ratioIn("New on Calltime"))
    }

    @Test fun monoNumbersStylesOnlyTheNumbers() {
        val mono = TextStyle(fontFamily = FontFamily.Monospace)
        val s = monoNumbers("₹2,500/day + ₹1,000 kit · paid on wrap", mono)
        assertEquals("₹2,500/day + ₹1,000 kit · paid on wrap", s.text)
        val styled = s.spanStyles.map { s.text.substring(it.start, it.end) }
        assertEquals(listOf("₹2,500", "₹1,000"), styled)
        val r = monoNumbers("Showed up 9/9", mono)
        assertEquals(listOf("9/9"), r.spanStyles.map { r.text.substring(it.start, it.end) })
    }

    @Test fun shortRoleNamesComeFromTheDomain() {
        assertEquals("DP", shortRoleLabel(RoleCode.DP))
        assertEquals("1st AD", shortRoleLabel(RoleCode.FIRST_AD))
        assertEquals("Sound Mixer", shortRoleLabel(RoleCode.SOUND_MIXER))
    }

    @Test fun deckActionsMirrorSwipeDirections() {
        assertEquals(SwipeDirection.RIGHT, DeckAction.INTERESTED.direction)
        assertEquals(SwipeDirection.LEFT, DeckAction.PASS.direction)
        assertEquals(SwipeDirection.UP, DeckAction.SAVE.direction)
        assertEquals(SwipeDirection.RIGHT, DeckAction.LETS_TALK.direction)
        assertEquals(SwipeDirection.LEFT, DeckAction.NOT_THIS_TIME.direction)
        assertEquals(SwipeDirection.UP, DeckAction.BACKUP.direction)
        assertEquals(SwipeDirection.RIGHT, DeckAction.SEND_ASK.direction)
        assertEquals(SwipeDirection.LEFT, DeckAction.SKIP.direction)
    }
}
