package com.tff.app.ui.screens

import com.tff.app.R
import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.T
import com.tff.app.model.DemoPersona
import com.tff.app.model.MatchState
import com.tff.app.model.Message
import com.tff.app.model.MessageFlag
import com.tff.app.model.NotificationKind
import com.tff.app.model.ReportReason
import com.tff.app.model.ReportTarget
import com.tff.app.model.BookingStatus
import com.tff.app.ui.screens.chat.ENDED_STATES
import com.tff.app.ui.screens.chat.bookingBubbleLead
import com.tff.app.ui.screens.chat.bookingBubbleSpans
import com.tff.app.ui.screens.chat.QuickReplyContext
import com.tff.app.ui.screens.chat.QuickReplyKind
import com.tff.app.ui.screens.chat.displayName
import com.tff.app.ui.screens.chat.isOrgName
import com.tff.app.ui.screens.chat.isShootDay
import com.tff.app.ui.screens.chat.latestRedFlagMessage
import com.tff.app.ui.screens.chat.offerShortLine
import com.tff.app.ui.screens.chat.quickReplySpecs
import com.tff.app.ui.screens.chat.threadRedFlags
import com.tff.app.ui.screens.inbox.NeedsKind
import com.tff.app.ui.screens.inbox.buildInboxUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** FIXC: chat safety mode, quick replies by state, Inbox markers and "Needs you" (PRD §5.2, S14, S15). */
class Ws6SafetyLogicTest {

    private fun ids(ctx: QuickReplyContext) = quickReplySpecs(ctx).map { it.text }

    @Test fun `organisation-style handles keep their full name, people get First L`() {
        assertTrue(isOrgName("Casting Hub Official"))
        assertTrue(isOrgName("Bright Star Casting"))
        assertTrue(isOrgName("Casting O."))                  // the short form still reads as an org
        assertFalse(isOrgName("Meera Kulkarni"))
        assertFalse(isOrgName("Elena Morales"))
        assertEquals("Casting Hub Official", displayName("Casting Hub Official", "Casting O."))
        assertEquals("Meera K.", displayName("Meera Kulkarni", "Meera K."))
    }

    @Test fun `the seeded scam thread is red-flagged for Meera, and the safety replies never say yes`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.MEERA)
        val v = q.matchView(e.state.value, DemoIds.MEERA, DemoIds.M_MEERA_SCAM)!!
        val msgs = q.messages(e.state.value, DemoIds.MEERA, DemoIds.M_MEERA_SCAM)
        val flags = threadRedFlags(msgs, v.match, DemoIds.MEERA)
        assertTrue(MessageFlag.MONEY_FROM_TALENT in flags)
        assertNotNull(latestRedFlagMessage(msgs, v.match, DemoIds.MEERA))

        val ctx = QuickReplyContext(isOwner = false, state = v.match.state, post = false, isCast = v.item.role.isCast, redFlags = flags)
        val chips = quickReplySpecs(ctx)
        assertFalse("no 'Yes, I'm in' under a fee request", chips.any { it.text == R.string.ws6_qr_in })
        assertFalse(chips.any { it.text == R.string.ws6_qr_call_times })
        assertEquals(R.string.ws6c_qr_no_fee_cast, chips.first().text)
        assertTrue(chips.any { it.kind == QuickReplyKind.REPORT })

        val reported = quickReplySpecs(ctx.copy(reported = true))
        assertFalse(reported.any { it.kind == QuickReplyKind.REPORT })
        assertTrue("once reported, Block is the next step", reported.any { it.kind == QuickReplyKind.BLOCK })
        assertFalse(reported.any { it.text == R.string.ws6_qr_in })
    }

    @Test fun `only the other side's red flags count, and money only from the owner side`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.MEERA)
        val m = q.matchView(e.state.value, DemoIds.MEERA, DemoIds.M_MEERA_SCAM)!!.match
        val mine = Message("x1", m.id, DemoIds.MEERA, body = "Is there a registration fee? I don't pay to audition.")
        assertTrue(threadRedFlags(listOf(mine), m, DemoIds.MEERA).isEmpty())
        // A collaborator asking how they get paid is not a fee request, seen from the owner's side.
        val collabAsks = Message("x2", m.id, m.collaboratorId, body = "Will you pay me via UPI? Send ₹ to my UPI id after wrap.")
        assertFalse(MessageFlag.MONEY_FROM_TALENT in threadRedFlags(listOf(collabAsks), m, m.ownerId))
        val body = Message("x3", m.id, m.ownerId, body = "Send your measurements and bikini photos first.")
        assertTrue(MessageFlag.BODY_REQUEST in threadRedFlags(listOf(body), m, DemoIds.MEERA))
        val clean = QuickReplyContext(isOwner = false, state = MatchState.TALKING, post = false)
        assertTrue(R.string.ws6_qr_in in ids(clean))
    }

    @Test fun `booked quick replies follow the day sheet and the shoot day`() {
        val base = QuickReplyContext(isOwner = false, state = MatchState.BOOKED, post = false)
        val early = ids(base)
        assertFalse("no 'running late' days before the shoot", R.string.ws6_qr_late in early)
        assertFalse("no 'got the day sheet' before there is one", R.string.ws6_qr_got_sheet in early)
        assertTrue(R.string.ws6c_qr_bring in early)
        val sheet = ids(base.copy(daySheetOut = true))
        assertTrue(R.string.ws6_qr_got_sheet in sheet)
        assertFalse(R.string.ws6_qr_late in sheet)
        assertTrue(R.string.ws6_qr_late in ids(base.copy(daySheetOut = true, shootToday = true)))
        val owner = ids(base.copy(isOwner = true))
        assertFalse("owner can't say 'day sheet is out' before publishing it", R.string.ws6_qr_owner_sheet_out in owner)
        assertTrue(R.string.ws6_qr_owner_sheet_out in ids(base.copy(isOwner = true, daySheetOut = true)))
        assertTrue(quickReplySpecs(base.copy(state = MatchState.CLOSED)).isEmpty())

        val d = LocalDate.of(2026, 10, 10)
        assertTrue(isShootDay(d, listOf(d), null))
        assertTrue(isShootDay(d, emptyList(), d))
        assertFalse(isShootDay(d.minusDays(3), listOf(d), d))
    }

    @Test fun `an offer out shows reviewing replies, not yes I'm in`() {
        val offered = ids(QuickReplyContext(isOwner = false, state = MatchState.OFFERED, post = true))
        assertFalse(R.string.ws6_qr_in in offered)
        assertTrue(R.string.ws6c_qr_reviewing in offered)
        assertFalse(R.string.ws6_qr_owner_card in ids(QuickReplyContext(isOwner = true, state = MatchState.OFFERED, post = false)))
    }

    @Test fun `meera's inbox marks the scam row, and the report shows on it`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.MEERA)
        fun ui(): com.tff.app.ui.screens.inbox.InboxUi {
            val s = e.state.value
            val inbox = q.inbox(s, DemoIds.MEERA)
            val threads = inbox.conversations.filter { it.match.state !in ENDED_STATES }
                .associate { it.match.id to q.messages(s, DemoIds.MEERA, it.match.id) }
            return buildInboxUi(inbox, threads, q.myReports(s, DemoIds.MEERA), DemoIds.MEERA)
        }
        val before = ui()
        val row = before.safety.getValue(DemoIds.M_MEERA_SCAM)
        assertEquals(MessageFlag.MONEY_FROM_TALENT, row.flag)
        assertNull(row.report)
        assertTrue("the scam thread never feeds Needs you", before.needsYou.none { it.conversation?.match?.id == DemoIds.M_MEERA_SCAM })
        assertTrue("an ask and the day sheet wait on Meera", before.todo >= 2)
        // The owed side's "we've nudged them" overdue footage is news, not a to-do; the copies collapse to one.
        assertTrue(before.needsYou.none { it.notification?.kind == NotificationKind.WRAP_OVERDUE })
        assertEquals(1, before.updates.count { it.kind == NotificationKind.WRAP_OVERDUE })

        val msg = q.messages(e.state.value, DemoIds.MEERA, DemoIds.M_MEERA_SCAM).first { it.senderId == DemoIds.CASTING_HUB }
        e.report(DemoIds.MEERA, ReportTarget.MESSAGE, msg.id, ReportReason.MONEY_REQUEST, null).getOrThrow()
        assertNotNull(ui().safety.getValue(DemoIds.M_MEERA_SCAM).report)
    }

    @Test fun `kabir's pay confirmation is a to-do, and ended talks sort last`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.KABIR)
        val inbox = q.inbox(e.state.value, DemoIds.KABIR)
        val ui = buildInboxUi(inbox, emptyMap(), emptyList(), DemoIds.KABIR)
        // FIXD #64: one template for the pay event, "Farida A. says you've been paid"
        assertTrue(ui.needsYou.any { it.notification?.kind == NotificationKind.WRAP_ITEM && "paid" in it.notification!!.title })
        assertTrue(ui.todo > 0)
        assertTrue("Needs-you updates aren't repeated under Updates",
            ui.updates.none { u -> ui.needsYou.any { it.notification?.id == u.id } })
        val rank = ui.conversations.map { if (it.match.state in ENDED_STATES) 2 else if (it.match.state == MatchState.WRAPPED) 1 else 0 }
        assertEquals(rank.sorted(), rank)
    }

    @Test fun `dana's offer is one Needs-you row and the pinned line is short`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.DANA)
        val inbox = q.inbox(e.state.value, DemoIds.DANA)
        val ui = buildInboxUi(inbox, emptyMap(), emptyList(), DemoIds.DANA)
        val offers = ui.needsYou.filter { it.conversation?.match?.id == "m_dana_softlight" }
        assertEquals(1, offers.size)
        assertEquals(NeedsKind.OFFER, offers.single().kind)
        assertTrue("the booking-card update isn't a second row",
            ui.needsYou.none { it.notification?.kind == NotificationKind.BOOKING_CARD && it.notification!!.deepLink == "calltime://m/m_dana_softlight" })
        val card = q.matchView(e.state.value, DemoIds.DANA, "m_dana_softlight")!!.currentCard!!
        val line = offerShortLine(card.terms)
        assertTrue(line, line.startsWith("$2,400 flat · deliver by "))
    }

    @Test fun `the booking bubble only says you're booked once the card is accepted`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.DANA)
        val card = q.matchView(e.state.value, DemoIds.DANA, "m_dana_softlight")!!.currentCard!!
        assertEquals(BookingStatus.SENT, card.status)
        val text = bookingBubbleSpans(card, changeToBooking = false, senderView = false).joinToString("") { it.text }
        assertTrue(text, text.startsWith("If you accept, you're booked as Sound Designer"))
        assertFalse(text.startsWith("You're booked"))
        val owner = bookingBubbleSpans(card, changeToBooking = false, senderView = true).joinToString("") { it.text }
        assertTrue(owner, owner.startsWith("If they accept, they're booked as "))
        assertEquals("If you accept this version, you're booked as ", bookingBubbleLead(BookingStatus.SENT, changeToBooking = true, senderView = false))
        assertEquals("You're booked as ", bookingBubbleLead(BookingStatus.ACCEPTED, changeToBooking = false, senderView = false))
        assertEquals("Offered as ", bookingBubbleLead(BookingStatus.SUPERSEDED, changeToBooking = true, senderView = false))
        val accepted = bookingBubbleSpans(card.copy(status = BookingStatus.ACCEPTED), false, false).joinToString("") { it.text }
        assertTrue(accepted.startsWith("You're booked as Sound Designer"))
    }
}
