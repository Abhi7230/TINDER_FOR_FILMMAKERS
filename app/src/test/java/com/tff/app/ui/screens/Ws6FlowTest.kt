package com.tff.app.ui.screens

import com.tff.app.core.AppClock
import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.T
import com.tff.app.domain.AuditionRules
import com.tff.app.domain.BannerLevel
import com.tff.app.domain.MessageSafety
import com.tff.app.model.AuditionMode
import com.tff.app.model.AuditionRequest
import com.tff.app.model.AuditionResponse
import com.tff.app.model.BookingStatus
import com.tff.app.model.Decision
import com.tff.app.model.DemoPersona
import com.tff.app.model.FeedbackAnswers
import com.tff.app.model.MatchState
import com.tff.app.model.MessageFlag
import com.tff.app.model.MessageKind
import com.tff.app.model.OwedBy
import com.tff.app.model.WrapKind
import com.tff.app.model.WrapStatus
import com.tff.app.ui.screens.chat.bannersForMessage
import com.tff.app.ui.screens.chat.bookedCallLine
import com.tff.app.ui.screens.chat.changedFieldsText
import com.tff.app.ui.screens.chat.nextCallText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * WS6 screens drive these Repo flows; this checks the assumptions the UI makes about them against the
 * real demo engine (WS3) and domain (WS2): states, pinned-bar data, BOOKED line, wrap and feedback.
 */
class Ws6FlowTest {

    @Test fun `meera applies, tapes, accepts the booking card and gets a call line`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.MEERA)
        val app = e.apply(DemoIds.MEERA, "r_ll_lead", "Keen to read.", null, null, false).getOrThrow()
        val owner = e.state.value.projects.getValue("p_last_local").ownerId
        val m = e.decide(owner, app.id, Decision.TALK, null).getOrThrow()!!
        assertEquals(MatchState.TALKING, q.matchView(e.state.value, DemoIds.MEERA, m.id)!!.match.state)

        // Owner's audition request: mode locked to the listing, validated before sending.
        val listing = e.state.value.projects.getValue("p_last_local").auditionMode
        assertEquals(AuditionMode.SELF_TAPE, listing)
        val bad = AuditionRequest(AuditionMode.VIDEO_CALL, date = e.today().plusDays(1), time = LocalTime.of(22, 0))
        assertNotNull(AuditionRules.validate(bad, listing, e.today()))
        val req = AuditionRequest(AuditionMode.SELF_TAPE, sidesUrl = "https://example.com/sides.pdf", dueDate = e.today().plusDays(3))
        assertNull(AuditionRules.validate(req, listing, e.today()))
        e.sendAuditionRequest(owner, m.id, req).getOrThrow()
        val audition = q.messages(e.state.value, DemoIds.MEERA, m.id).last { it.kind == MessageKind.AUDITION }
        e.respondAudition(DemoIds.MEERA, audition.id, AuditionResponse.TAPE_SENT, "https://youtu.be/tape").getOrThrow()
        val answered = q.messages(e.state.value, DemoIds.MEERA, m.id).first { it.id == audition.id }
        assertEquals(AuditionResponse.TAPE_SENT, answered.audition!!.response)

        // Booking card v1, then Meera accepts.
        e.sendBookingCard(owner, m.id, e.draftBookingTerms(owner, m.id)).getOrThrow()
        val offered = q.matchView(e.state.value, DemoIds.MEERA, m.id)!!
        assertEquals(MatchState.OFFERED, offered.match.state)
        assertEquals(BookingStatus.SENT, offered.currentCard!!.status)
        e.respondBookingCard(DemoIds.MEERA, offered.currentCard!!.id, true).getOrThrow()
        val booked = q.matchView(e.state.value, DemoIds.MEERA, m.id)!!
        assertEquals(MatchState.BOOKED, booked.match.state)
        assertNotNull("Last Local has published day sheets, so the pinned bar can say 'next call'", booked.nextShootDay)
        assertNotNull(booked.myCallTime)
        assertTrue(bookedCallLine(booked, null)!!.startsWith("CALL "))
        assertTrue(q.wrapItems(e.state.value, DemoIds.MEERA, m.id).isNotEmpty())
        // Contacts are no longer masked once booked.
        e.sendMessage(owner, m.id, "My number is 98201 47736 if you get lost.").getOrThrow()
        assertTrue(q.messages(e.state.value, DemoIds.MEERA, m.id).last().body.contains("98201"))
    }

    @Test fun `ask to change sends the talks back to TALKING with a new version possible`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.DANA)
        val v = q.matchView(e.state.value, DemoIds.DANA, "m_dana_softlight")!!
        val card = v.currentCard!!
        e.respondBookingCard(DemoIds.DANA, card.id, false).getOrThrow()
        val after = q.matchView(e.state.value, DemoIds.DANA, "m_dana_softlight")!!
        assertEquals(MatchState.TALKING, after.match.state)
        assertEquals(BookingStatus.DECLINED, after.currentCard!!.status)
        // The owner edits and sends v2: the diff names what changed.
        val t2 = card.terms.copy(amount = (card.terms.amount ?: 0) + 200)
        e.sendBookingCard(after.match.ownerId, after.match.id, t2).getOrThrow()
        val v2 = q.bookingCards(e.state.value, DemoIds.DANA, after.match.id).last()
        assertEquals(2, v2.version)
        assertEquals("pay", changedFieldsText(v2.changedFields))
    }

    @Test fun `dana's post role books with a delivery line, wraps on confirmed delivery, unlocks feedback`() {
        val (e, clock) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.DANA)
        val mid = "m_dana_softlight"
        val v = q.matchView(e.state.value, DemoIds.DANA, mid)!!
        assertTrue(v.match.isPostRole)
        e.respondBookingCard(DemoIds.DANA, v.currentCard!!.id, true).getOrThrow()
        val booked = q.matchView(e.state.value, DemoIds.DANA, mid)!!
        assertTrue(bookedCallLine(booked, null)!!.startsWith("DELIVER BY "))
        assertNull("post roles have no day sheet", booked.nextShootDay)
        val items = q.wrapItems(e.state.value, DemoIds.DANA, mid)
        assertEquals(WrapKind.DELIVERY, items.first().kind)
        assertEquals(OwedBy.COLLABORATOR, items.first().owedBy)
        assertTrue(items.none { it.kind == WrapKind.FOOTAGE })

        clock.millis += 30L * 24 * 3600_000
        e.markWrapItem(DemoIds.DANA, items.first().id, "https://example.com/final-mix", "Stems included").getOrThrow()
        assertFalse(q.feedbackState(e.state.value, DemoIds.DANA, mid).unlocked)
        e.confirmWrapItem(booked.match.ownerId, items.first().id, true).getOrThrow()
        val wrapped = q.matchView(e.state.value, DemoIds.DANA, mid)!!
        assertEquals(MatchState.WRAPPED, wrapped.match.state)
        val fb = q.feedbackState(e.state.value, DemoIds.DANA, mid)
        assertTrue(fb.unlocked)
        e.submitFeedback(DemoIds.DANA, mid, FeedbackAnswers(workAgain = true)).getOrThrow()
        assertTrue(q.feedbackState(e.state.value, DemoIds.DANA, mid).iSubmitted)
        assertEquals(WrapStatus.CONFIRMED, q.wrapItems(e.state.value, DemoIds.DANA, mid).first().status)
    }

    @Test fun `banner direction rule - money is red only from the owner side, off-platform shows to both until booked`() {
        val scam = MessageSafety.analyze("One-time registration fee of ₹2,500. WhatsApp me on 98201 47736.")
        assertTrue(MessageFlag.MONEY_FROM_TALENT in scam)
        val toActor = bannersForMessage(scam, mine = false, senderIsOwnerSide = true, bookedLike = false)
        assertEquals(BannerLevel.DANGER, toActor.first().level)
        assertTrue(toActor.any { it.flag == MessageFlag.OFF_PLATFORM })

        val collabAsks = MessageSafety.analyze("Will it be paid via UPI?")
        assertTrue(bannersForMessage(collabAsks, mine = false, senderIsOwnerSide = false, bookedLike = false).none { it.level == BannerLevel.DANGER })

        val mineOff = MessageSafety.analyze("WhatsApp me please")
        assertTrue(bannersForMessage(mineOff, mine = true, senderIsOwnerSide = true, bookedLike = false).single().flag == MessageFlag.OFF_PLATFORM)
        assertTrue(bannersForMessage(mineOff, mine = true, senderIsOwnerSide = true, bookedLike = true).isEmpty())
        assertTrue("the sender never sees the red banner on their own message",
            bannersForMessage(scam, mine = true, senderIsOwnerSide = true, bookedLike = false).none { it.level == BannerLevel.DANGER })
    }

    @Test fun `rohan releases inside 72h - the sheet's honest line applies and Kept bookings drops`() {
        val (e, clock) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.ROHAN)
        val mid = "m_fc_pd"
        val v0 = q.matchView(e.state.value, DemoIds.ROHAN, mid)!!
        assertEquals(MatchState.BOOKED, v0.match.state)
        val before = q.reputation(e.state.value, DemoIds.ROHAN).keptBookings
        val terms = v0.acceptedCard!!.terms
        val first = terms.dates.min()
        // Move the clock to 40 hours before day 1.
        val target = first.atStartOfDay(T.zone).toInstant().toEpochMilli() - 40L * 3600_000
        clock.millis = target - e.state.value.offsetMillis
        val hours = com.tff.app.domain.Notice.hoursUntil(terms, e.now(), T.zone)
        assertTrue("notice $hours", hours in 1..71)
        assertNotNull(com.tff.app.domain.Notice.releaseLine(hours))
        e.release(DemoIds.ROHAN, mid, com.tff.app.model.ReleaseReason.SCHEDULE_CHANGED).getOrThrow()
        val after = q.reputation(e.state.value, DemoIds.ROHAN).keptBookings
        assertEquals(MatchState.RELEASED, q.matchView(e.state.value, DemoIds.ROHAN, mid)!!.match.state)
        assertNotNull(after)
        assertTrue("kept bookings $before -> $after", after!!.den == (before?.den ?: 0) + 1 && after.num == (before?.num ?: 0))
    }

    @Test fun `post-role booking card reads deliver-by and paid within 7 days of delivery, no call times`() {
        val (e, _) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.DANA)
        val t = q.matchView(e.state.value, DemoIds.DANA, "m_dana_softlight")!!.currentCard!!.terms
        val summary = com.tff.app.domain.CardText.bookingSummary(t)
        assertTrue(summary, summary.contains("deliver by"))
        assertTrue(summary, summary.contains("paid within 7 days of delivery"))
        assertFalse(summary, summary.contains("call ~"))
        assertEquals("Paid within 7 days of delivery", com.tff.app.ui.screens.booking.TermsText.terms(t))
        assertNull(com.tff.app.ui.screens.booking.TermsText.callWindow(t))
        assertTrue(com.tff.app.ui.screens.booking.TermsText.pay(t).startsWith("$"))
    }

    @Test fun `next call text is short this week and dated further out`() {
        val today = AppClock.today()
        val soon = nextCallText(today.plusDays(1), LocalTime.of(7, 30))
        assertTrue(soon, Regex("^[A-Z]{3} 07:30$").matches(soon))
        val later = nextCallText(today.plusDays(10), LocalTime.of(7, 30))
        assertTrue(later, later.endsWith(" · 07:30") && later.length > soon.length)
    }
}
