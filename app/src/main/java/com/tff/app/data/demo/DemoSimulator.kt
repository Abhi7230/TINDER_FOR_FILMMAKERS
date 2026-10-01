package com.tff.app.data.demo

import com.tff.app.domain.Gate
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.Viewer
import com.tff.app.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.random.Random

/**
 * Plays every seeded person (PRD §11.6). Listens to DemoEngine events caused by the person using the app
 * and answers after a short, human delay × the demo speed (INSTANT = no wait). Runs in a scope owned by
 * DemoRepo; never blocks the caller. Every scheduled action re-checks the state before acting.
 */
class DemoSimulator(
    private val engine: DemoEngine,
    private val scope: CoroutineScope,
    private val rnd: Random = Random.Default,
) {
    private val scheduled = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var jobs = SupervisorJob(scope.coroutineContext[Job])

    /** Drop everything still pending (reset re-seeds, so old ids must never be acted on). */
    fun cancelAll() {
        jobs.cancel()
        jobs = SupervisorJob(scope.coroutineContext[Job])
        scheduled.clear()
    }

    fun attach() { engine.listener = { e -> runCatching { on(e) } } }

    private val s get() = engine.state.value
    private val me get() = s.session?.userId
    private fun simulated(userId: String?) = userId != null && userId != me && userId in s.profiles

    private fun seconds(lo: Double, hi: Double): Long {
        val f = s.speed.factor
        if (f <= 0.0) return 0
        return ((lo + rnd.nextDouble() * (hi - lo)) * 1000 * f).toLong()
    }

    private fun later(key: String?, lo: Double, hi: Double, action: () -> Unit) {
        if (key != null && !scheduled.add(key)) return
        val wait = seconds(lo, hi)
        scope.launch(jobs) {
            if (wait > 0) delay(wait)
            runCatching { action() }
            if (key != null) scheduled.remove(key)
        }
    }

    fun on(e: SimEvent) {
        // Wraps happen on a clock tick or after a seeded owner confirms a delivery; everything else is
        // only answered when the person using the app caused it.
        if (e is SimEvent.Wrapped) { onWrapped(e.matchIds); return }
        if (e.actor != null && e.actor != me) return
        when (e) {
            is SimEvent.Applied -> onApplied(e.appId)
            is SimEvent.Decided -> if (e.decision == Decision.TALK && e.matchId != null) onOwnerTalked(e.matchId)
            is SimEvent.AskSent -> onAskSent(e.appId, e.isBackupOffer)
            is SimEvent.AskAnswered -> e.matchId?.let { onCollaboratorAcceptedAsk(it) }
            is SimEvent.MessageSent -> onMessage(e.matchId, e.body)
            is SimEvent.AuditionSent -> onAuditionSent(e.matchId, e.messageId)
            is SimEvent.AuditionResponded -> if (e.response != AuditionResponse.CANT_MAKE_IT) scheduleCard(e.matchId, 6.0, 15.0)
            is SimEvent.BookingSent -> onBookingSent(e.matchId, e.cardId)
            is SimEvent.BookingResponded -> {}
            is SimEvent.Published -> onPublished(e.projectId)
            is SimEvent.DaySheetPublished -> onDaySheet(e.dayId)
            is SimEvent.Nudged -> if (simulated(e.profileId)) later("ack:${e.dayId}:${e.profileId}", 5.0, 5.0) { engine.ackShootDay(e.profileId, e.dayId) }
            is SimEvent.Wrapped -> {}
            is SimEvent.WrapMarked -> onWrapMarked(e.itemId)
            is SimEvent.FeedbackSubmitted -> onFeedback(e.matchId)
            is SimEvent.ClockMoved -> {}
            is SimEvent.Withdrawn -> {}
        }
    }

    // ── Applications: the seeded owner decides in 4–12 s ──
    private fun onApplied(appId: String) {
        val a = s.applications[appId] ?: return
        val r = s.roles[a.roleId] ?: return
        val p = s.projects[r.projectId] ?: return
        if (!simulated(p.ownerId)) return
        later("decide:$appId", 4.0, 12.0) {
            val now = engine.state.value
            val app = now.applications[appId]?.takeIf { it.status == ApplicationStatus.PENDING } ?: return@later
            val firstKey = "firstApplyMatched:${app.applicantId}"
            val first = firstKey !in now.flags
            val score = scoreFor(app.applicantId, r.id)
            val prob = (0.15 + 0.7 * score / 100.0).coerceIn(0.2, 0.85)
            val talk = first || rnd.nextDouble() < prob
            if (first) engine.mutate { flag(firstKey) }
            val res = engine.decide(ownerOf(app), appId, if (talk) Decision.TALK else Decision.DECLINE, null)
            res.getOrNull()?.let { m -> afterOwnerOpensTalks(m.id, rebook = false) }
        }
    }

    private fun ownerOf(a: Application): String = s.projects[s.roles[a.roleId]?.projectId]?.ownerId ?: ""

    private fun scoreFor(userId: String, roleId: String): Double = runCatching {
        val ix = engine.index()
        val u = ix.profile(userId)!!
        val a = ix.area(u.areaId)!!
        val item = ix.roleWithProject(ix.s.roles.getValue(roleId), userId)!!
        MatchEngine.score(Viewer(u, ix.s.privateRates[userId], a.lat, a.lng), item, ix.today, ix.now)
    }.getOrDefault(60.0)

    /** Seeded owner opened talks with the user: opener, then audition (cast) or booking card. */
    private fun afterOwnerOpensTalks(matchId: String, rebook: Boolean) {
        later("opener:$matchId", 2.0, 5.0) {
            val st = engine.state.value
            val m = st.matches[matchId] ?: return@later
            val r = st.roles[m.roleId] ?: return@later
            val pr = st.projects[m.projectId] ?: return@later
            val collab = st.profiles[m.collaboratorId]
            val hinglish = DemoCities.city(pr.cityId)?.country == "IN"
            engine.sendMessage(m.ownerId, matchId, DemoText.ownerOpener(DemoText.firstName(collab), pr, r, rnd, hinglish))
            if (r.isCast && pr.auditionMode != null && !rebook) later("audition:$matchId", 3.0, 6.0) { sendAudition(matchId) }
            else scheduleCard(matchId, 6.0, 15.0)
        }
    }

    private fun sendAudition(matchId: String) {
        val st = engine.state.value
        val m = st.matches[matchId]?.takeIf { it.state == MatchState.TALKING } ?: return
        val pr = st.projects[m.projectId] ?: return
        val mode = pr.auditionMode ?: return
        val today = engine.today()
        val area = DemoCities.area(pr.areaId)
        val req = when (mode) {
            AuditionMode.SELF_TAPE -> AuditionRequest(mode, sidesUrl = DemoText.DEMO_SIDES + pr.id, dueDate = today.plusDays(3),
                note = "Two short scenes. Phone is fine, landscape, natural light.")
            AuditionMode.VIDEO_CALL -> AuditionRequest(mode, date = today.plusDays(2), time = LocalTime.of(18, 30),
                meetingLink = "https://demo.calltime.app/call/${pr.id}", note = "Fifteen minutes. We'll read one scene together.")
            AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> AuditionRequest(mode, date = today.plusDays(3), time = LocalTime.of(11, 0),
                venue = "Rehearsal studio, ${area?.name ?: "city centre"}", address = "${area?.name ?: ""}, ${DemoCities.city(pr.cityId)?.name ?: ""}",
                alsoPresent = listOf("Casting assistant", "Director"), note = "Read two scenes. Bring a friend if you like.")
        }
        engine.sendAuditionRequest(m.ownerId, matchId, req)
    }

    private fun scheduleCard(matchId: String, lo: Double, hi: Double) {
        val m = s.matches[matchId] ?: return
        if (!simulated(m.ownerId)) return
        later("card:$matchId", lo, hi) { sendCardAsOwner(matchId) }
    }

    private fun sendCardAsOwner(matchId: String) {
        val st = engine.state.value
        val m = st.matches[matchId]?.takeIf { it.state == MatchState.TALKING } ?: return
        if (st.bookingCards.values.any { it.matchId == matchId && it.status == BookingStatus.SENT }) return
        val terms = runCatching { engine.draftBookingTerms(m.ownerId, matchId) }.getOrNull() ?: return
        engine.sendBookingCard(m.ownerId, matchId, terms)
    }

    // ── The user (as owner) said "Let's talk": the seeded person answers ──
    private fun onOwnerTalked(matchId: String) {
        val m = s.matches[matchId] ?: return
        if (!simulated(m.collaboratorId)) return
        later("collabOpener:$matchId", 3.0, 6.0) {
            engine.sendMessage(m.collaboratorId, matchId, DemoText.collaboratorOpener(DemoText.firstName(engine.state.value.profiles[m.ownerId]), rnd))
        }
    }

    // ── Asks: accept 5–15 s later (p = 0.6; a backup offer p = 0.9) ──
    private fun onAskSent(appId: String, backupOffer: Boolean) {
        val a = s.applications[appId] ?: return
        if (!simulated(a.applicantId)) return
        later("ask:$appId", 5.0, 15.0) {
            val st = engine.state.value
            val app = st.applications[appId]?.takeIf { it.status == ApplicationStatus.PENDING } ?: return@later
            val accept = rnd.nextDouble() < if (backupOffer) 0.9 else 0.6
            val reason = if (accept) null else {
                val u = st.profiles[app.applicantId]
                if (u?.compMode == CompMode.PAID_ONLY) AskDeclineReason.PAID_ONLY_NOW
                else listOf(AskDeclineReason.DATES_DONT_WORK, AskDeclineReason.NOT_MY_KIND, AskDeclineReason.TOO_FAR).random(rnd)
            }
            val m = engine.respondAsk(app.applicantId, appId, accept, reason).getOrNull()
            if (m != null) later("collabOpener:${m.id}", 2.0, 4.0) {
                engine.sendMessage(m.collaboratorId, m.id, DemoText.collaboratorOpener(DemoText.firstName(engine.state.value.profiles[m.ownerId]), rnd))
            }
        }
    }

    /** The user accepted a seeded owner's Ask (e.g. Meera's Rebook): opener, then the card. */
    private fun onCollaboratorAcceptedAsk(matchId: String) {
        val m = s.matches[matchId] ?: return
        if (!simulated(m.ownerId)) return
        val rebook = s.feedback.any { it.fromId == m.ownerId && it.toId == m.collaboratorId && it.answers.workAgain == true }
        afterOwnerOpensTalks(matchId, rebook = rebook)
    }

    // ── Chat: 60% reply in 3–8 s; "booking/confirm/offer" makes the seeded owner send the card now ──
    private fun onMessage(matchId: String, body: String) {
        val m = s.matches[matchId] ?: return
        val userIsOwner = m.ownerId == me
        val other = if (userIsOwner) m.collaboratorId else m.ownerId
        if (!simulated(other)) return
        val wantsCard = !userIsOwner && Regex("\\b(booking|confirm|offer)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (wantsCard && m.state == MatchState.TALKING) { later("card:$matchId", 0.5, 1.0) { sendCardAsOwner(matchId) }; return }
        if (rnd.nextDouble() >= 0.6) return
        later("reply:$matchId:${body.hashCode()}", 3.0, 8.0) {
            val st = engine.state.value
            val mm = st.matches[matchId] ?: return@later
            val r = st.roles[mm.roleId] ?: return@later
            val pr = st.projects[mm.projectId] ?: return@later
            val hinglish = DemoCities.city(pr.cityId)?.country == "IN"
            engine.sendMessage(other, matchId, DemoText.reply(DemoText.intentOf(body), fromOwner = !userIsOwner, pr, r, rnd, hinglish))
        }
    }

    // ── The user (owner) requested an audition from a seeded actor ──
    private fun onAuditionSent(matchId: String, messageId: String) {
        val m = s.matches[matchId] ?: return
        if (!simulated(m.collaboratorId)) return
        later("audReply:$messageId", 4.0, 10.0) {
            val msg = engine.state.value.messages.firstOrNull { it.id == messageId } ?: return@later
            val mode = msg.audition?.request?.mode ?: return@later
            if (mode == AuditionMode.SELF_TAPE) engine.respondAudition(m.collaboratorId, messageId, AuditionResponse.TAPE_SENT, DemoText.DEMO_TAPE + messageId)
            else engine.respondAudition(m.collaboratorId, messageId, AuditionResponse.WILL_DO, null)
        }
    }

    // ── The user (owner) sent a booking card: accept in 4–10 s (p = 0.9), else ask to change ──
    private fun onBookingSent(matchId: String, cardId: String) {
        val m = s.matches[matchId] ?: return
        if (!simulated(m.collaboratorId)) return
        later("respond:$cardId", 4.0, 10.0) {
            val st = engine.state.value
            val card = st.bookingCards[cardId]?.takeIf { it.status == BookingStatus.SENT } ?: return@later
            val firstKey = "firstCardAccepted:${m.ownerId}"
            val first = firstKey !in st.flags
            val reschedule = "reschedule:$cardId" in st.flags
            val accept = first || reschedule || rnd.nextDouble() < 0.9
            if (first) engine.mutate { flag(firstKey) }
            if (accept) engine.respondBookingCard(m.collaboratorId, card.id, true)
            else {
                engine.respondBookingCard(m.collaboratorId, card.id, false)
                engine.sendMessage(m.collaboratorId, matchId, DemoText.ASK_TO_CHANGE.random(rnd))
            }
        }
    }

    // ── The user published a project: 3–8 applicants per role over ~90 s ──
    private fun onPublished(projectId: String) {
        val ix = engine.index()
        val p = ix.s.projects[projectId] ?: return
        val roles = ix.rolesByProject[projectId].orEmpty().filter { it.status == RoleStatus.OPEN }
        val usedPeople = mutableSetOf<String>()
        roles.forEach { r ->
            val item = ix.roleWithProject(r, p.ownerId) ?: return@forEach
            val pool = ix.s.profiles.values.filter { u ->
                u.id != p.ownerId && u.id !in ix.s.realUsers && u.id !in ix.hidden && !ix.blocked(u.id, p.ownerId) && ix.roleMatches(u, r)
            }
            val passing = pool.filter { u ->
                val a = ix.area(u.areaId) ?: return@filter false
                runCatching { (MatchEngine.failedGates(Viewer(u, ix.s.privateRates[u.id], a.lat, a.lng), item, ix.today) - Gate.G6_STATUS).isEmpty() }.getOrDefault(true)
            }
            val sameCountry = pool.filter { DemoCities.city(it.cityId)?.country == DemoCities.city(p.cityId)?.country }
            val chosen = (passing.shuffled(rnd) + sameCountry.shuffled(rnd)).distinct().filter { it.id !in usedPeople || rnd.nextBoolean() }
                .take(3 + rnd.nextInt(6))
            chosen.forEachIndexed { i, u ->
                usedPeople += u.id
                val at = 5.0 + (85.0 * (i + 1) / (chosen.size + 1)) + rnd.nextDouble() * 4
                later("pubApply:${r.id}:${u.id}", at, at) {
                    val q = engine.state.value.roles[r.id]?.screeningQuestion
                    engine.apply(u.id, r.id, DemoText.applicantNote(u, r, rnd), u.workLinks.firstOrNull()?.id, q?.let { DemoText.screeningAnswer(rnd) }, kit = u.gear.isNotEmpty())
                }
            }
        }
    }

    // ── Day sheet published: seeded people ack in 2–20 s (90%); one stays un-acked to show Nudge ──
    private fun onDaySheet(dayId: String) {
        val st = s
        val calls = st.callTimes.filter { it.shootDayId == dayId && it.ackAt == null && simulated(it.profileId) }
        if (calls.isEmpty()) return
        val holdOut = if (calls.size >= 2) calls[rnd.nextInt(calls.size)].profileId else null
        calls.filter { it.profileId != holdOut }.forEach { c ->
            if (rnd.nextDouble() < 0.9) later("ack:$dayId:${c.profileId}", 2.0, 20.0) { engine.ackShootDay(c.profileId, dayId) }
        }
    }

    // ── Wrap: seeded owners pay (50% right away, the rest within the terms); seeded collaborators confirm ──
    private fun onWrapped(matchIds: List<String>) {
        matchIds.forEach { mid ->
            val m = s.matches[mid] ?: return@forEach
            if (!simulated(m.ownerId)) return@forEach
            if (m.collaboratorId !in s.realUsers) return@forEach
            val paid = s.wrapItems.values.firstOrNull { it.matchId == mid && it.kind == WrapKind.PAID && it.status == WrapStatus.OPEN } ?: return@forEach
            val terms = s.bookingCards.values.filter { it.matchId == mid && it.status == BookingStatus.ACCEPTED }.maxByOrNull { it.version }?.terms
            val quick = m.isPostRole || terms?.paymentTerms in setOf(PaymentTerms.ON_WRAP, PaymentTerms.HALF_UPFRONT) || rnd.nextBoolean()
            if (quick && m.ownerId !in WEAK_PAYERS) later("pay:${paid.id}", 2.0, 4.0) { engine.markWrapItem(m.ownerId, paid.id, null, payNote()) }
        }
    }

    private fun payNote() = listOf("Paid via UPI", "Bank transfer done", "Paid in cash on wrap").random(rnd)

    private fun onWrapMarked(itemId: String) {
        val item = s.wrapItems[itemId] ?: return
        val m = s.matches[item.matchId] ?: return
        val confirmer = if (item.owedBy == OwedBy.PRODUCTION) m.collaboratorId else m.ownerId
        if (!simulated(confirmer)) return
        later("confirm:$itemId", 3.0, 6.0) { engine.confirmWrapItem(confirmer, itemId, true) }
    }

    // ── Feedback: the seeded counterpart answers 5 s after the user, so the reveal happens ──
    private fun onFeedback(matchId: String) {
        val m = s.matches[matchId] ?: return
        val other = if (m.ownerId == me) m.collaboratorId else m.ownerId
        if (!simulated(other)) return
        later("fb:$matchId", 5.0, 5.0) {
            val st = engine.state.value
            if (st.feedback.any { it.matchId == matchId && it.fromId == other }) return@later
            val answers = if (other == m.ownerId) {
                FeedbackAnswers(
                    showedUp = if (m.isPostRole) null else ShowedUpAnswer.YES,
                    prepared = if (m.isPostRole) null else TriAnswer.YES,
                    deliveredOnTime = if (m.isPostRole) OnTimeAnswer.YES else null,
                    revisions = if (m.isPostRole) TriAnswer.YES else null,
                    workAgain = true,
                )
            } else {
                val paid = st.wrapItems.values.firstOrNull { it.matchId == matchId && it.kind == WrapKind.PAID }
                FeedbackAnswers(
                    paid = when { paid == null -> PaidAnswer.NOT_PAID_ROLE; paid.status == WrapStatus.CONFIRMED || paid.status == WrapStatus.DONE -> PaidAnswer.ON_TIME; else -> PaidAnswer.LATE },
                    delivered = DeliveredAnswer.YES, feltSafe = true, kept = KeptAnswer.YES, workAgain = true,
                )
            }
            engine.submitFeedback(other, matchId, answers)
        }
    }

    /**
     * Time-driven behaviour, run after every tick / clock jump: seeded owners pay within the terms and
     * deliver footage before it's due (except the weak-record owners); seeded post collaborators mark
     * DELIVERY two days before the delivery date.
     */
    fun scan() {
        val st = s
        val user = me ?: return
        val today = engine.today()
        st.matches.values.filter { it.ownerId == user || it.collaboratorId == user }.forEach { m ->
            val items = st.wrapItems.values.filter { it.matchId == m.id && it.status == WrapStatus.OPEN }
            if (simulated(m.ownerId) && m.state == MatchState.WRAPPED) {
                items.filter { it.owedBy == OwedBy.PRODUCTION }.forEach { w ->
                    val due = w.dueDate ?: return@forEach
                    val ready = when (w.kind) {
                        WrapKind.PAID -> if (m.ownerId in WEAK_PAYERS) today.isAfter(due.plusDays(3)) else !today.isBefore(due.minusDays(1))
                        WrapKind.FOOTAGE -> m.ownerId != DemoIds.WEAK_FOOTAGE_OWNER && !today.isBefore(due.minusDays(7))
                        WrapKind.FINAL_FILM, WrapKind.CREDIT -> m.ownerId != DemoIds.WEAK_FOOTAGE_OWNER && !today.isBefore(due.minusDays(3))
                        WrapKind.DELIVERY -> false
                    }
                    if (ready) later("own:${w.id}", 1.0, 3.0) {
                        val link = if (w.kind == WrapKind.PAID || w.kind == WrapKind.CREDIT) null else DemoText.DEMO_FOOTAGE + w.id
                        engine.markWrapItem(m.ownerId, w.id, link, if (w.kind == WrapKind.PAID) payNote() else null)
                    }
                }
            }
            if (simulated(m.collaboratorId) && m.isPostRole && m.state == MatchState.BOOKED) {
                items.filter { it.kind == WrapKind.DELIVERY }.forEach { w ->
                    val due = w.dueDate ?: return@forEach
                    if (!today.isBefore(due.minusDays(2))) later("deliver:${w.id}", 5.0, 5.0) {
                        engine.markWrapItem(m.collaboratorId, w.id, DemoText.DEMO_DELIVERY + w.id, "Final mix and stems in the folder.")
                    }
                }
            }
        }
    }

    companion object {
        val WEAK_PAYERS = setOf(DemoIds.WEAK_PAY_OWNER, DemoIds.WEAK_KEPT_OWNER)
    }
}
