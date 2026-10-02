package com.tff.app.notify

import com.tff.app.model.AppNotification
import com.tff.app.model.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTrackerTest {
    private fun n(id: String, at: Long, read: Boolean = false) =
        AppNotification(id, NotificationKind.BOOKING_CARD, "Booking card · $id", "body", "calltime://m/m_$id", at, if (read) at else null)

    @Test fun firstListIsBaselineAndNeverPosted() {
        val t = NotificationTracker()
        assertTrue(t.onList("u_meera", listOf(n("1", 1), n("2", 2)), foreground = false).isEmpty())
    }

    @Test fun newItemsInTheBackgroundArePostedOnceOldestFirst() {
        val t = NotificationTracker()
        t.onList("u_meera", listOf(n("1", 1)), false)
        val out = t.onList("u_meera", listOf(n("3", 30), n("2", 20), n("1", 1)), false)
        assertEquals(listOf("2", "3"), out.map { it.id })
        assertTrue(t.onList("u_meera", listOf(n("3", 30), n("2", 20), n("1", 1)), false).isEmpty())
    }

    @Test fun foregroundItemsAreSeenNotPostedLater() {
        val t = NotificationTracker()
        t.onList("u_meera", emptyList(), false)
        assertTrue(t.onList("u_meera", listOf(n("4", 40)), foreground = true).isEmpty())
        assertTrue(t.onList("u_meera", listOf(n("4", 40)), foreground = false).isEmpty())
    }

    @Test fun readItemsAndPersonaSwitchesAreNotPosted() {
        val t = NotificationTracker()
        t.onList("u_meera", emptyList(), false)
        assertTrue(t.onList("u_meera", listOf(n("5", 50, read = true)), false).isEmpty())
        // Rohan's existing items form his own baseline
        assertTrue(t.onList("u_rohan", listOf(n("9", 90)), false).isEmpty())
        assertEquals(listOf("10"), t.onList("u_rohan", listOf(n("10", 100), n("9", 90)), false).map { it.id })
    }

    @Test fun sameIdAfterADemoResetIsANewNotification() {
        val t = NotificationTracker()
        t.onList("u_meera", listOf(n("1005", 10)), false)
        assertEquals(listOf("1005"), t.onList("u_meera", listOf(n("1005", 99)), false).map { it.id })
    }
}
