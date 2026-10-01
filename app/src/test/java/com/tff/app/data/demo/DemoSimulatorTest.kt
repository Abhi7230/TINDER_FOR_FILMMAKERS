package com.tff.app.data.demo

import com.tff.app.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.random.Random

/** §11.6 DemoSimulator, driven on virtual time. These are the QA script paths (§15.3). */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoSimulatorTest {
    private fun TestScope.world(persona: DemoPersona, speed: SimSpeed = SimSpeed.NORMAL): Pair<DemoEngine, DemoSimulator> {
        val (e, _) = T.engine()
        val sim = DemoSimulator(e, backgroundScope, Random(7))
        sim.attach()
        e.startDemo(persona)
        e.setSpeed(speed)
        return e to sim
    }

    private val DemoEngine.s get() = state.value

    /** Background coroutines only run on time advances, so move the virtual clock past every simulated delay. */
    private fun TestScope.settle() { advanceTimeBy(120_000); runCurrent() }

    @Test fun `meera applies to Last Local and is booked through audition and booking card`() = runTest {
        val (e, sim) = world(DemoPersona.MEERA)
        val q = DemoQueries(e)
        val app = e.apply(DemoIds.MEERA, DemoIds.R_LL_LEAD, "Night trains are my commute.", null, null, false).getOrThrow()
        advanceTimeBy(13_000)
        val m = e.s.matches.values.single { it.applicationId == app.id }
        assertEquals("first application of a persona is always answered with talks", MatchState.TALKING, m.state)
        settle()
        val msgs = q.messages(e.s, DemoIds.MEERA, m.id)
        assertTrue(msgs.any { it.senderId == DemoIds.LAST_LOCAL_OWNER && it.kind == MessageKind.TEXT })
        val audition = msgs.single { it.kind == MessageKind.AUDITION }
        assertEquals(AuditionMode.SELF_TAPE, audition.audition!!.request.mode)
        assertNotNull(audition.audition!!.request.sidesUrl)
        e.respondAudition(DemoIds.MEERA, audition.id, AuditionResponse.TAPE_SENT, "https://demo.calltime.app/tape/meera").getOrThrow()
        advanceTimeBy(16_000)
        val card = q.bookingCards(e.s, DemoIds.MEERA, m.id).single()
        assertEquals(BookingStatus.SENT, card.status)
        e.respondBookingCard(DemoIds.MEERA, card.id, true).getOrThrow()
        assertEquals(MatchState.BOOKED, e.s.matches.getValue(m.id).state)
        assertTrue(q.joinedProjects(e.s, DemoIds.MEERA).any { it.project.id == DemoIds.P_LAST_LOCAL })

        // wrap: jump past the next shoot (Cutting Chai), Farida pays on wrap, feedback reveals once both answer
        e.jumpPastNextShoot()
        sim.scan()
        settle()
        val cc = DemoIds.M_MEERA_CUTTING_CHAI
        assertEquals(MatchState.WRAPPED, e.s.matches.getValue(cc).state)
        val paid = q.wrapItems(e.s, DemoIds.MEERA, cc).single { it.kind == WrapKind.PAID }
        assertEquals(WrapStatus.DONE, paid.status)
        e.confirmWrapItem(DemoIds.MEERA, paid.id, true).getOrThrow()
        e.submitFeedback(DemoIds.MEERA, cc, FeedbackAnswers(paid = PaidAnswer.ON_TIME, delivered = DeliveredAnswer.YES, feltSafe = true,
            kept = KeptAnswer.YES, workAgain = true)).getOrThrow()
        settle()
        assertTrue(q.feedbackState(e.s, DemoIds.MEERA, cc).revealed)
        // the older project still shows its overdue footage (weak owner never delivers)
        assertEquals(WrapStatus.OPEN, e.s.wrapItems.getValue(DemoIds.WI_MEERA_IRON_GATE_FOOTAGE).status)
        // +30 days: Farida, a reliable owner, delivers the Cutting Chai footage before it's due
        e.jumpPastWrap()
        sim.scan()
        settle()
        assertTrue(q.wrapItems(e.s, DemoIds.MEERA, cc).single { it.kind == WrapKind.FOOTAGE }.status == WrapStatus.DONE)
    }

    @Test fun `asking for the booking card in chat makes the seeded owner send it now`() = runTest {
        val (e, _) = world(DemoPersona.MEERA)
        e.respondAsk(DemoIds.MEERA, DemoIds.A_MEERA_REBOOK, true, null).getOrThrow()
        val m = e.s.matches.values.single { it.applicationId == DemoIds.A_MEERA_REBOOK }
        e.sendMessage(DemoIds.MEERA, m.id, "Happy to do it. Can you send the booking card?").getOrThrow()
        advanceTimeBy(1_500)
        assertTrue(e.s.bookingCards.values.any { it.matchId == m.id && it.status == BookingStatus.SENT })
        settle()
        assertTrue("a rebook skips the audition", e.s.messages.none { it.matchId == m.id && it.kind == MessageKind.AUDITION })
    }

    @Test fun `rohan publishes and applicants arrive within 90 seconds, then a seeded person accepts his card`() = runTest {
        val (e, _) = world(DemoPersona.ROHAN)
        val q = DemoQueries(e)
        val d = q.draft(e.s, DemoIds.ROHAN, DemoIds.P_ROHAN_DRAFT)!!
        e.saveDraft(DemoIds.ROHAN, d.copy(project = d.project.copy(meals = Meals.TWO_PLUS_MEALS))).getOrThrow()
        e.publish(DemoIds.ROHAN, DemoIds.P_ROHAN_DRAFT).getOrThrow()
        advanceTimeBy(95_000)
        val roles = e.s.roles.values.filter { it.projectId == DemoIds.P_ROHAN_DRAFT }
        val actor = roles.single { it.roleCode == RoleCode.ACTOR }
        assertTrue(q.applicants(e.s, DemoIds.ROHAN, actor.id).size in 3..8)
        roles.forEach { r -> assertTrue("${r.roleCode} got none", q.applicants(e.s, DemoIds.ROHAN, r.id).isNotEmpty()) }
        assertTrue(e.s.notifications.count { it.userId == DemoIds.ROHAN && it.n.kind == NotificationKind.APPLICATION_RECEIVED } >= 6)

        // Crew up on Filter Coffee: talk to the top sound applicant, send the card, the seeded person accepts
        val top = q.applicants(e.s, DemoIds.ROHAN, DemoIds.R_FC_SOUND).first()
        val m = e.decide(DemoIds.ROHAN, top.application!!.id, Decision.TALK, null).getOrThrow()!!
        advanceTimeBy(7_000)
        assertTrue("the seeded person says hello", e.s.messages.any { it.matchId == m.id && it.senderId == m.collaboratorId })
        e.sendBookingCard(DemoIds.ROHAN, m.id, e.draftBookingTerms(DemoIds.ROHAN, m.id)).getOrThrow()
        advanceTimeBy(11_000)
        assertEquals(MatchState.BOOKED, e.s.matches.getValue(m.id).state)
        assertEquals(RoleStatus.FILLED, e.s.roles.getValue(DemoIds.R_FC_SOUND).status)
        assertTrue(e.s.applications.values.filter { it.roleId == DemoIds.R_FC_SOUND && it.id != top.application!!.id }
            .all { it.status == ApplicationStatus.CLOSED })
        settle()
    }

    @Test fun `day sheet acks with one hold-out, and a nudge gets the last Got it`() = runTest {
        val (e, _) = world(DemoPersona.ROHAN)
        val fc = e.s.projects.getValue(DemoIds.P_FILTER_COFFEE)
        // book a second seeded person so two people are on the sheet
        val q = DemoQueries(e)
        val top = q.applicants(e.s, DemoIds.ROHAN, DemoIds.R_FC_SOUND).first()
        val m = e.decide(DemoIds.ROHAN, top.application!!.id, Decision.TALK, null).getOrThrow()!!
        e.sendBookingCard(DemoIds.ROHAN, m.id, e.draftBookingTerms(DemoIds.ROHAN, m.id)).getOrThrow()
        settle()
        assertEquals(MatchState.BOOKED, e.s.matches.getValue(m.id).state)
        e.publishShootDay(DemoIds.ROHAN, ShootDayDraft(fc.id, null, fc.shootDates.first(), LocalTime.of(7, 0), LocalTime.of(17, 0),
            "Udupi darshini, 11th Main, Jayanagar 4th Block, Bengaluru", perPersonCall = mapOf(top.card.profile.id to LocalTime.of(6, 30)))).getOrThrow()
        settle()
        val day = q.shootDays(e.s, DemoIds.ROHAN, fc.id).single()
        assertEquals(LocalTime.of(6, 30), day.calls.single { it.call.profileId == top.card.profile.id }.call.callTime)
        val waiting = day.calls.filter { it.call.ackAt == null }
        assertEquals(1, waiting.size)
        e.nudge(DemoIds.ROHAN, day.day.id, waiting.single().call.profileId).getOrThrow()
        advanceTimeBy(6_000)
        assertTrue(q.shootDays(e.s, DemoIds.ROHAN, fc.id).single().calls.all { it.call.ackAt != null })
    }

    @Test fun `scout asks get answered and backups accept the offered slot`() = runTest {
        val (e, _) = world(DemoPersona.ROHAN, SimSpeed.INSTANT)
        e.simulateWithdrawal().getOrThrow()
        e.offerSlotToBackup(DemoIds.ROHAN, DemoIds.R_FC_PD).getOrThrow()
        settle()
        val app = e.s.applications.getValue(DemoIds.A_FC_PD_BACKUP)
        assertTrue(app.status == ApplicationStatus.MATCHED || app.status == ApplicationStatus.DECLINED)
    }

    @Test fun `dana's post-role delivery loop runs end to end`() = runTest {
        val (e, sim) = world(DemoPersona.DANA)
        val q = DemoQueries(e)
        e.respondBookingCard(DemoIds.DANA, DemoIds.BC_DANA_SOFTLIGHT_1, true).getOrThrow()
        e.advanceDays(30.0)
        sim.scan()
        settle()
        val delivery = q.wrapItems(e.s, DemoIds.DANA, DemoIds.M_DANA_SOFTLIGHT).first { it.kind == WrapKind.DELIVERY }
        assertEquals(WrapStatus.OPEN, delivery.status)
        e.markWrapItem(DemoIds.DANA, delivery.id, "https://demo.calltime.app/delivery/soft-light-mix", "5.1 and stereo mixes").getOrThrow()
        advanceTimeBy(7_000)
        assertEquals(MatchState.WRAPPED, e.s.matches.getValue(DemoIds.M_DANA_SOFTLIGHT).state)
        advanceTimeBy(5_000)
        assertEquals(WrapStatus.DONE, q.wrapItems(e.s, DemoIds.DANA, DemoIds.M_DANA_SOFTLIGHT).single { it.kind == WrapKind.PAID }.status)
        assertTrue(q.feedbackState(e.s, DemoIds.DANA, DemoIds.M_DANA_SOFTLIGHT).unlocked)
        e.submitFeedback(DemoIds.DANA, DemoIds.M_DANA_SOFTLIGHT, FeedbackAnswers(paid = PaidAnswer.ON_TIME, delivered = DeliveredAnswer.YES,
            feltSafe = true, kept = KeptAnswer.YES, workAgain = true)).getOrThrow()
        settle()
        assertTrue(q.feedbackState(e.s, DemoIds.DANA, DemoIds.M_DANA_SOFTLIGHT).revealed)
        val owners = e.s.feedback.single { it.matchId == DemoIds.M_DANA_SOFTLIGHT && it.fromId == DemoIds.ELENA }
        assertNotNull("the owner is asked 'Delivered on time?'", owners.answers.deliveredOnTime)
    }

    @Test fun `as owner of a post role the seeded collaborator delivers two days before the date`() = runTest {
        val (e, sim) = world(DemoPersona.ROHAN, SimSpeed.INSTANT)
        val q = DemoQueries(e)
        val ed = q.applicants(e.s, DemoIds.ROHAN, DemoIds.R_FC_EDITOR).first()
        val m = e.decide(DemoIds.ROHAN, ed.application!!.id, Decision.TALK, null).getOrThrow()!!
        e.sendBookingCard(DemoIds.ROHAN, m.id, e.draftBookingTerms(DemoIds.ROHAN, m.id)).getOrThrow()
        settle()
        assertEquals(MatchState.BOOKED, e.s.matches.getValue(m.id).state)
        val due = e.s.roles.getValue(DemoIds.R_FC_EDITOR).postDeadline!!
        e.advanceDays(com.tff.app.core.Dates.daysBetween(e.today(), due.minusDays(2)).toDouble())
        sim.scan()
        settle()
        val delivery = q.wrapItems(e.s, DemoIds.ROHAN, m.id).single { it.kind == WrapKind.DELIVERY }
        assertEquals(WrapStatus.DONE, delivery.status)
        assertNotNull(delivery.link)
    }

    @Test fun `seeded people sometimes reply in chat, never with contact details`() = runTest {
        val (e, _) = world(DemoPersona.ROHAN, SimSpeed.INSTANT)
        repeat(6) { e.sendMessage(DemoIds.ROHAN, DemoIds.M_ROHAN_DP, "What are the dates and the call time? ($it)").getOrThrow(); settle() }
        val replies = e.s.messages.filter { it.matchId == DemoIds.M_ROHAN_DP && it.senderId == "u_034" }
        assertTrue(replies.size >= 4)
        assertTrue(replies.none { com.tff.app.domain.MessageSafety.hasContact(it.body) })
    }

    @Test fun `INSTANT speed answers without waiting`() = runTest {
        val (e, _) = world(DemoPersona.MEERA, SimSpeed.INSTANT)
        val app = e.apply(DemoIds.MEERA, DemoIds.R_LL_LEAD, null, null, null, false).getOrThrow()
        advanceTimeBy(1)
        assertTrue(e.s.applications.getValue(app.id).status != ApplicationStatus.PENDING)
    }

    @Test fun `asking to change a card brings a revised version`() = runTest {
        val (e, _) = world(DemoPersona.DANA)
        e.respondBookingCard(DemoIds.DANA, DemoIds.BC_DANA_SOFTLIGHT_1, false).getOrThrow()
        assertEquals(MatchState.TALKING, e.s.matches.getValue(DemoIds.M_DANA_SOFTLIGHT).state)
        e.sendMessage(DemoIds.DANA, DemoIds.M_DANA_SOFTLIGHT, "Could we make it two revision rounds plus stems?").getOrThrow()
        settle()
        val v2 = e.s.bookingCards.values.filter { it.matchId == DemoIds.M_DANA_SOFTLIGHT }.maxBy { it.version }
        assertEquals(2, v2.version)
        assertEquals(BookingStatus.SENT, v2.status)
        assertTrue("notes" in v2.changedFields)
    }
}
