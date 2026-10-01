package com.tff.app.data.demo

import com.tff.app.data.RepoException
import com.tff.app.domain.MessageSafety
import com.tff.app.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DemoEngineTest {
    private val meera = DemoIds.MEERA
    private val rohan = DemoIds.ROHAN
    private val dana = DemoIds.DANA
    private val kabir = DemoIds.KABIR

    private fun fresh(): Triple<DemoEngine, FixedClock, DemoQueries> {
        val (e, c) = T.engine()
        return Triple(e, c, DemoQueries(e))
    }

    private fun DemoEngine.s() = state.value

    // ───────────── core loop ─────────────

    @Test fun `apply then talk then booking card then accept fills the role and closes pending applicants`() {
        val (e, _, q) = fresh()
        val other = e.s().profiles.values.first { it.primaryRole == RoleCode.ACTOR && it.cityId == "mumbai" && it.id != meera &&
            e.s().applications.values.none { a -> a.roleId == DemoIds.R_LL_LEAD && a.applicantId == it.id } }
        e.apply(other.id, DemoIds.R_LL_LEAD, null, null, null, false).getOrThrow()

        val before = e.s().roles.getValue(DemoIds.R_LL_LEAD).applicationsCount
        val app = e.apply(meera, DemoIds.R_LL_LEAD, "Night trains are my commute.", null, null, false).getOrThrow()
        assertEquals(ApplicationStatus.PENDING, app.status)
        assertEquals(before + 1, e.s().roles.getValue(DemoIds.R_LL_LEAD).applicationsCount)
        assertTrue(q.discoverCandidates(e.s(), meera, null).none { it.role.id == DemoIds.R_LL_LEAD })
        assertTrue(e.apply(meera, DemoIds.R_LL_LEAD, null, null, null, false).isFailure)

        val owner = DemoIds.LAST_LOCAL_OWNER
        assertTrue("only the owner decides", e.decide(meera, app.id, Decision.TALK, null).isFailure)
        val m = e.decide(owner, app.id, Decision.TALK, null).getOrThrow()!!
        assertEquals(MatchState.TALKING, m.state)
        val first = q.messages(e.s(), meera, m.id).first()
        assertEquals(MessageKind.SYSTEM, first.kind)
        assertTrue(first.body.contains("Last Local") && first.body.contains("Unpaid"))
        assertTrue(q.inbox(e.s(), meera).conversations.any { it.match.id == m.id })
        assertTrue(e.s().notifications.any { it.userId == meera && it.n.kind == NotificationKind.MATCH_CREATED })

        val terms = e.draftBookingTerms(owner, m.id)
        assertTrue("collaborator can't send", e.sendBookingCard(meera, m.id, terms).isFailure)
        e.sendBookingCard(owner, m.id, terms).getOrThrow()
        assertEquals(MatchState.OFFERED, e.s().matches.getValue(m.id).state)
        val nothingChanged = e.sendBookingCard(owner, m.id, terms)
        assertEquals("Nothing changed. Edit a term to send a new version.", nothingChanged.exceptionOrNull()?.message)

        val card = q.bookingCards(e.s(), meera, m.id).single()
        assertTrue("owner can't accept for them", e.respondBookingCard(owner, card.id, true).isFailure)
        e.respondBookingCard(meera, card.id, true).getOrThrow()
        val booked = e.s().matches.getValue(m.id)
        assertEquals(MatchState.BOOKED, booked.state)
        assertNotNull(booked.bookedAt)
        val role = e.s().roles.getValue(DemoIds.R_LL_LEAD)
        assertEquals(1, role.filledCount)
        assertEquals(RoleStatus.FILLED, role.status)
        val closed = e.s().applications.values.filter { it.roleId == DemoIds.R_LL_LEAD && it.id != app.id }
        assertTrue(closed.isNotEmpty())
        assertTrue(closed.all { it.status == ApplicationStatus.CLOSED && it.declineReason == DemoText.CLOSURE_FILLED })
        // booked onto both published day sheets, wrap checklist from the terms
        val days = q.shootDays(e.s(), meera, DemoIds.P_LAST_LOCAL)
        assertEquals(2, days.size)
        assertTrue(days.all { it.myCall != null })
        val items = q.wrapItems(e.s(), meera, m.id).map { it.kind }.toSet()
        assertEquals(setOf(WrapKind.FOOTAGE, WrapKind.CREDIT), items)
        assertTrue(q.joinedProjects(e.s(), meera).any { it.project.id == DemoIds.P_LAST_LOCAL && it.myNextCall != null })
        // exact address only once booked
        assertNotNull(q.matchView(e.s(), meera, m.id)!!.item.project.exactAddress)
        assertNull(q.roleWithProject(e.s(), kabir, DemoIds.R_LL_LEAD)!!.project.exactAddress)
        // got it
        e.ackShootDay(meera, days.first().day.id).getOrThrow()
        assertNotNull(q.shootDays(e.s(), meera, DemoIds.P_LAST_LOCAL).first().myCall!!.ackAt)
    }

    @Test fun `decline and backup give every applicant an answer`() {
        val (e, _, q) = fresh()
        val apps = q.applicants(e.s(), rohan, DemoIds.R_FC_ACTOR)
        assertEquals(10, apps.size)
        assertTrue(apps.all { it.reasons.size <= 3 })
        assertTrue("ranked", apps.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue("cast applicants show the headshot to the owner", apps.any { it.headshotUrl != null })
        val (a1, a2) = apps.take(2).map { it.application!! }
        val count = e.s().roles.getValue(DemoIds.R_FC_ACTOR).applicationsCount
        e.decide(rohan, a1.id, Decision.DECLINE, "not right for it").getOrThrow()
        assertEquals(ApplicationStatus.DECLINED, e.s().applications.getValue(a1.id).status)
        assertEquals(DemoText.CLOSURE_DECLINED, e.s().applications.getValue(a1.id).declineReason)
        assertEquals(count - 1, e.s().roles.getValue(DemoIds.R_FC_ACTOR).applicationsCount)
        e.decide(rohan, a2.id, Decision.BACKUP, null).getOrThrow()
        assertEquals(1, e.s().applications.getValue(a2.id).backupRank)
        assertTrue(e.decide(rohan, a1.id, Decision.TALK, null).isFailure)
        assertEquals(8, q.applicants(e.s(), rohan, DemoIds.R_FC_ACTOR).size)
        assertTrue(q.projectFull(e.s(), rohan, DemoIds.P_FILTER_COFFEE)!!.backups.getValue(DemoIds.R_FC_ACTOR).isNotEmpty())
    }

    @Test fun `post role loop delivery then confirm then wrap then paid and double-blind feedback`() {
        val (e, _, q) = fresh()
        val owner = DemoIds.ELENA
        e.respondBookingCard(dana, DemoIds.BC_DANA_SOFTLIGHT_1, true).getOrThrow()
        val m = e.s().matches.getValue(DemoIds.M_DANA_SOFTLIGHT)
        assertEquals(MatchState.BOOKED, m.state)
        assertTrue(q.shootDays(e.s(), dana, DemoIds.P_SOFT_LIGHT).isEmpty())
        val items = q.wrapItems(e.s(), dana, m.id)
        assertEquals(WrapKind.DELIVERY, items.first().kind)
        assertTrue(items.none { it.kind == WrapKind.FOOTAGE })
        val delivery = items.first()
        assertEquals(OwedBy.COLLABORATOR, delivery.owedBy)
        assertTrue(q.joinedProjects(e.s(), dana).single { it.project.id == DemoIds.P_SOFT_LIGHT }.myDeliveryDate != null)

        assertTrue("the owner doesn't mark delivery", e.markWrapItem(owner, delivery.id, "https://demo.calltime.app/x", null).isFailure)
        assertTrue("a link is required", e.markWrapItem(dana, delivery.id, null, null).isFailure)
        e.markWrapItem(dana, delivery.id, "https://demo.calltime.app/delivery/soft-light", "Final mix").getOrThrow()
        assertTrue("the collaborator can't confirm her own delivery", e.confirmWrapItem(dana, delivery.id, true).isFailure)
        assertFalse(q.feedbackState(e.s(), dana, m.id).unlocked)
        e.confirmWrapItem(owner, delivery.id, true).getOrThrow()
        assertEquals(MatchState.WRAPPED, e.s().matches.getValue(m.id).state)
        assertTrue(q.feedbackState(e.s(), dana, m.id).unlocked)

        val paid = q.wrapItems(e.s(), dana, m.id).single { it.kind == WrapKind.PAID }
        e.markWrapItem(owner, paid.id, null, "Bank transfer").getOrThrow()
        e.confirmWrapItem(dana, paid.id, true).getOrThrow()
        assertEquals(WrapStatus.CONFIRMED, e.s().wrapItems.getValue(paid.id).status)

        e.submitFeedback(dana, m.id, FeedbackAnswers(paid = PaidAnswer.ON_TIME, delivered = DeliveredAnswer.YES, feltSafe = true, kept = KeptAnswer.YES, workAgain = true)).getOrThrow()
        val half = q.feedbackState(e.s(), dana, m.id)
        assertTrue(half.iSubmitted && !half.otherSubmitted && !half.revealed)
        assertTrue(e.submitFeedback(dana, m.id, FeedbackAnswers(workAgain = true)).isFailure)
        e.submitFeedback(owner, m.id, FeedbackAnswers(deliveredOnTime = OnTimeAnswer.YES, revisions = TriAnswer.YES, workAgain = true)).getOrThrow()
        assertTrue(q.feedbackState(e.s(), dana, m.id).revealed)
        assertTrue(q.keepList(e.s(), owner).any { it.profile.id == dana })
    }

    @Test fun `withdraw reopens the slot and backup 1 gets an ask with the same terms`() {
        val (e, _, q) = fresh()
        e.startDemo(DemoPersona.ROHAN)
        val backupId = e.simulateWithdrawal().getOrThrow()
        assertEquals(DemoIds.A_FC_PD_BACKUP, backupId)
        val m = e.s().matches.getValue(DemoIds.M_FC_PD)
        assertEquals(MatchState.WITHDRAWN, m.state)
        assertNotNull(m.noticeHours)
        assertTrue(m.noticeHours!! >= 72)
        val role = e.s().roles.getValue(DemoIds.R_FC_PD)
        assertEquals(RoleStatus.OPEN, role.status)
        assertEquals(0, role.filledCount)
        assertTrue(e.s().notifications.any { it.userId == rohan && it.n.kind == NotificationKind.WITHDRAWAL && it.n.body.contains("Backup #1") })
        // withdrawals with ≥72h notice don't count against the collaborator
        assertNull(e.index().reputation("u_039").showedUp?.takeIf { it.num < it.den })

        val asksBefore = q.asksLeftToday(e.s(), DemoIds.P_FILTER_COFFEE)
        e.offerSlotToBackup(rohan, DemoIds.R_FC_PD).getOrThrow()
        assertEquals(asksBefore - 1, q.asksLeftToday(e.s(), DemoIds.P_FILTER_COFFEE))
        val ask = e.s().applications.getValue(DemoIds.A_FC_PD_BACKUP)
        assertEquals(ApplicationOrigin.INVITED, ask.origin)
        assertEquals(ApplicationStatus.PENDING, ask.status)
        assertTrue(q.inbox(e.s(), "u_040").asks.any { it.application.id == ask.id })
        val match = e.respondAsk("u_040", ask.id, true, null).getOrThrow()!!
        assertEquals(MatchState.TALKING, match.state)
        assertTrue(e.offerSlotToBackup(rohan, DemoIds.R_FC_PD).isFailure)
    }

    @Test fun `a release inside 72 hours lowers the owner's kept bookings`() {
        val (e, clock, _) = fresh()
        assertEquals(Ratio(3, 3), e.index().reputation(rohan).keptBookings)
        val start = e.s().projects.getValue(DemoIds.P_FILTER_COFFEE).shootStart!!
        clock.millis = SeedKit.millisAt(start.minusDays(1), LocalTime.of(10, 0), T.zone)
        e.release(rohan, DemoIds.M_FC_PD, ReleaseReason.SCHEDULE_CHANGED).getOrThrow()
        val m = e.s().matches.getValue(DemoIds.M_FC_PD)
        assertEquals(MatchState.RELEASED, m.state)
        assertTrue(m.releasedByOwner && m.noticeHours!! < 72)
        assertEquals(Ratio(3, 4), e.index().reputation(rohan).keptBookings)
        assertTrue(e.s().messages.any { it.matchId == m.id && it.body.contains("Less than 72 hours") })
    }

    @Test fun `reschedule sends a new version with a diff and declining it is penalty free`() {
        val (e, _, _) = fresh()
        val p = e.s().projects.getValue(DemoIds.P_FILTER_COFFEE)
        val newDates = p.shootDates.map { it.plusDays(7) }
        e.reschedule(rohan, p.id, newDates).getOrThrow()
        assertEquals(1, e.s().projects.getValue(p.id).rescheduleCount)
        val v2 = e.s().bookingCards.values.filter { it.matchId == DemoIds.M_FC_PD }.maxBy { it.version }
        assertEquals(2, v2.version)
        assertTrue("dates" in v2.changedFields)
        assertTrue(e.s().matches.getValue(DemoIds.M_FC_PD).changePending)
        e.respondBookingCard("u_039", v2.id, false).getOrThrow()
        val m = e.s().matches.getValue(DemoIds.M_FC_PD)
        assertEquals(MatchState.WITHDRAWN, m.state)
        assertEquals(RoleStatus.OPEN, e.s().roles.getValue(DemoIds.R_FC_PD).status)
        assertEquals("a penalty-free exit", Ratio(3, 3), e.index().reputation("u_039").showedUp)
    }

    @Test fun `cancel project releases everyone and closes every application`() {
        val (e, _, _) = fresh()
        e.cancelProject(rohan, DemoIds.P_FILTER_COFFEE, "Lost the location").getOrThrow()
        assertEquals(ProjectStatus.CANCELLED, e.s().projects.getValue(DemoIds.P_FILTER_COFFEE).status)
        assertEquals(MatchState.RELEASED, e.s().matches.getValue(DemoIds.M_FC_PD).state)
        assertEquals(MatchState.CLOSED, e.s().matches.getValue(DemoIds.M_ROHAN_DP).state)
        val roleIds = e.s().roles.values.filter { it.projectId == DemoIds.P_FILTER_COFFEE }.map { it.id }.toSet()
        assertTrue(e.s().applications.values.filter { it.roleId in roleIds }.none { it.status == ApplicationStatus.PENDING || it.status == ApplicationStatus.BACKUP })
    }

    // ───────────── safety & privacy ─────────────

    @Test fun `profile is null without a relationship and cards are sanitised`() {
        val (e, _, q) = fresh()
        assertNull("QA step 3: Dana has no relationship with Meera", q.profileCard(e.s(), dana, meera))
        assertNull(q.profileCard(e.s(), meera, "u_045"))
        val applicant = q.profileCard(e.s(), rohan, "u_045")!!
        assertTrue(applicant.profile.contentWontDo.isEmpty())
        assertTrue("headshot shown to the owner of the cast role she applied to", applicant.profile.workLinks.any { it.kind == LinkKind.HEADSHOT })
        // Farida owns a cast role Meera applied to, so she may see the headshot; Kabir (a teammate) may not
        assertTrue(q.profileCard(e.s(), DemoIds.FARIDA, meera)!!.profile.workLinks.any { it.kind == LinkKind.HEADSHOT })
        val teammate = q.profileCard(e.s(), kabir, meera)!!
        assertTrue(teammate.profile.contentWontDo.isEmpty())
        assertFalse(teammate.profile.workLinks.any { it.kind == LinkKind.HEADSHOT })
        assertNotNull("posters are public", q.profileCard(e.s(), dana, DemoIds.ELENA))
        assertNotNull("own profile", q.profileCard(e.s(), meera, meera)!!.profile.contentWontDo.takeIf { it.isNotEmpty() })
        assertNotNull("past counterpart", q.profileCard(e.s(), kabir, DemoIds.WEAK_KEPT_OWNER))
        // the private rate is never part of anything another person receives
        val apps = q.applicants(e.s(), rohan, DemoIds.R_FC_DP)
        assertTrue(apps.all { it.rateFit in 0.0..1.0 })
        assertFalse(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ApplicantView.serializer()), apps).contains("minDayRate"))
    }

    @Test fun `block hides both people from each other everywhere`() {
        val (e, _, q) = fresh()
        assertTrue(q.discoverCandidates(e.s(), meera, null).any { it.project.id == DemoIds.P_ROOMMATES })
        e.block(meera, DemoIds.CASTING_HUB).getOrThrow()
        val m = e.s().matches.getValue(DemoIds.M_MEERA_SCAM)
        assertEquals(MatchState.CLOSED, m.state)
        assertEquals(DemoText.BLOCK_ENDED, e.s().messages.filter { it.matchId == m.id }.maxBy { it.createdAt }.body)
        assertTrue(q.discoverCandidates(e.s(), meera, null).none { it.project.ownerId == DemoIds.CASTING_HUB })
        assertNull(q.profileCard(e.s(), meera, DemoIds.CASTING_HUB))
        assertNull(q.profileCard(e.s(), DemoIds.CASTING_HUB, meera))
        assertTrue(q.inbox(e.s(), meera).conversations.none { it.match.id == m.id })
        assertTrue(q.inbox(e.s(), DemoIds.CASTING_HUB).conversations.none { it.match.id == m.id })
        assertTrue(e.sendMessage(DemoIds.CASTING_HUB, m.id, "hello?").isFailure)
        assertEquals(DemoIds.CASTING_HUB, q.blocked(e.s(), meera).single().profile.id)
        e.unblock(meera, DemoIds.CASTING_HUB).getOrThrow()
        assertNotNull(q.profileCard(e.s(), meera, DemoIds.CASTING_HUB))
    }

    @Test fun `reports track status and three reports hide an account from decks`() {
        val (e, clock, q) = fresh()
        val scamMsg = e.s().messages.single { it.safetyExample }
        e.report(meera, ReportTarget.MESSAGE, scamMsg.id, ReportReason.MONEY_REQUEST, "Asked for a fee").getOrThrow()
        val mine = q.myReports(e.s(), meera).single()
        assertEquals(ReportStatus.RECEIVED, mine.status)
        assertTrue(DemoIds.CASTING_HUB in e.index().hidden)
        assertTrue(q.discoverCandidates(e.s(), kabir, "mumbai").none { it.project.ownerId == DemoIds.CASTING_HUB })
        clock.millis += 4 * SeedKit.HOUR
        e.tick()
        assertEquals(ReportStatus.REVIEWING, q.myReports(e.s(), meera).single().status)
        clock.millis += 25 * SeedKit.HOUR
        e.tick()
        assertEquals(ReportStatus.ACTION_TAKEN, q.myReports(e.s(), meera).single().status)
        assertTrue(e.report(meera, ReportTarget.PROFILE, meera, ReportReason.SPAM, null).isFailure)
        assertTrue(e.report(meera, ReportTarget.PROFILE, DemoIds.CASTING_HUB, ReportReason.OTHER, "x".repeat(501)).isFailure)
    }

    @Test fun `messages carry safety flags and contact stays masked until booked`() {
        val (e, _, q) = fresh()
        e.sendMessage(DemoIds.CASTING_HUB, DemoIds.M_MEERA_SCAM, "Call me on 98200 11223 or mail cast@example.com").getOrThrow()
        val sent = e.s().messages.last()
        assertTrue(MessageFlag.CONTACT in sent.flags)
        assertTrue(MessageFlag.OFF_PLATFORM in sent.flags)
        val shown = q.messages(e.s(), meera, DemoIds.M_MEERA_SCAM).last()
        assertFalse(shown.body.contains("98200") || shown.body.contains("example.com"))
        assertTrue("sender sees their own text", q.messages(e.s(), DemoIds.CASTING_HUB, DemoIds.M_MEERA_SCAM).last().body.contains("98200"))
        val unread = q.inbox(e.s(), meera).conversations.single { it.match.id == DemoIds.M_MEERA_SCAM }.unread
        assertTrue(unread >= 2)
        e.markRead(meera, DemoIds.M_MEERA_SCAM)
        assertEquals(0, q.inbox(e.s(), meera).conversations.single { it.match.id == DemoIds.M_MEERA_SCAM }.unread)
        assertTrue("share contacts needs a booking", e.shareContacts(meera, DemoIds.M_MEERA_SCAM).isFailure)
        e.shareContacts(meera, DemoIds.M_MEERA_CUTTING_CHAI).getOrThrow()
        assertTrue(e.s().matches.getValue(DemoIds.M_MEERA_CUTTING_CHAI).contactsSharedByCollab)
    }

    @Test fun `audition requests are locked to the listing mode`() {
        val (e, _, _) = fresh()
        val owner = DemoIds.CASTING_HUB
        val today = e.today()
        val wrongMode = e.sendAuditionRequest(owner, DemoIds.M_MEERA_SCAM, AuditionRequest(AuditionMode.SELF_TAPE, sidesUrl = "https://demo.calltime.app/s", dueDate = today.plusDays(2)))
        assertTrue(wrongMode.isFailure)
        e.sendAuditionRequest(owner, DemoIds.M_MEERA_SCAM, AuditionRequest(AuditionMode.VIDEO_CALL, date = today.plusDays(2), time = LocalTime.of(18, 0), meetingLink = "https://demo.calltime.app/call")).getOrThrow()
        val msg = e.s().messages.last()
        assertEquals(MessageKind.AUDITION, msg.kind)
        assertTrue(e.respondAudition(meera, msg.id, AuditionResponse.TAPE_SENT, "https://demo.calltime.app/tape").isFailure)
        assertTrue(e.respondAudition(owner, msg.id, AuditionResponse.WILL_DO, null).isFailure)
        e.respondAudition(meera, msg.id, AuditionResponse.WILL_DO, null).getOrThrow()
        assertEquals(AuditionResponse.WILL_DO, e.s().messages.single { it.id == msg.id }.audition!!.response)
        assertTrue("crew roles have no auditions", e.sendAuditionRequest(rohan, DemoIds.M_ROHAN_DP,
            AuditionRequest(AuditionMode.SELF_TAPE, sidesUrl = "https://demo.calltime.app/s", dueDate = today.plusDays(2))).isFailure)
    }

    // ───────────── caps ─────────────

    @Test fun `25 likes a day then the cap holds until tomorrow`() {
        val (e, clock, q) = fresh()
        val sess = e.startDemo(null)
        val me = Profile(sess.userId, "Cap Tester", primaryRole = RoleCode.ACTOR, level = Level.BUILDING_REEL, cityId = "mumbai",
            areaId = "mumbai_versova", languages = listOf("en", "hi"), compMode = CompMode.OPEN_TO_UNPAID)
        e.saveProfile(sess.userId, me, null).getOrThrow()
        val targets = e.s().roles.values.filter { r -> r.status == RoleStatus.OPEN && e.s().projects[r.projectId]?.status == ProjectStatus.OPEN &&
            (e.s().projects[r.projectId]?.shootStart?.isAfter(e.today()) ?: true) && r.applicationsCount < r.applicationCap }.take(26)
        assertEquals(26, targets.size)
        targets.take(25).forEach { e.apply(sess.userId, it.id, null, null, "Yes", false).getOrThrow() }
        assertEquals(0, q.likesLeftToday(e.s(), sess.userId))
        val res = e.apply(sess.userId, targets[25].id, null, null, "Yes", false)
        assertEquals("daily_cap", (res.exceptionOrNull() as RepoException).code)
        clock.millis += SeedKit.DAY
        assertEquals(25, q.likesLeftToday(e.s(), sess.userId))
        e.apply(sess.userId, targets[25].id, null, null, "Yes", false).getOrThrow()
    }

    @Test fun `scout is crew only and runs the reverse money and content gates`() {
        val (e, _, q) = fresh()
        assertTrue(q.scout(e.s(), rohan, DemoIds.R_FC_ACTOR).isEmpty())
        // a new Rohan project: a paid sound role, an unpaid gaffer role and a cast role
        val base = e.s().projects.getValue(DemoIds.P_FILTER_COFFEE)
        val p = base.copy(id = "", title = "Night Bus", status = ProjectStatus.DRAFT, shootDates = base.shootDates.map { it.plusDays(14) },
            publishedAt = null)
        fun role(code: RoleCode, comp: CompType, amount: Int?, perks: Set<Perk> = emptySet(), footage: Int? = null) = ProjectRole(
            "", "", code, creditTitle = "", compType = comp, amountMin = amount, amountMax = amount, currency = "INR",
            paymentTerms = if (comp == CompType.UNPAID) null else PaymentTerms.ON_WRAP, perks = perks, footageDueWeeks = footage,
            gearPolicy = if (Taxonomy.dept(code) in Taxonomy.GEAR_DEPTS) GearPolicy.PRODUCTION_PROVIDES else null)
        val pid = e.saveDraft(rohan, ProjectDraft(p, listOf(
            role(RoleCode.SOUND_MIXER, CompType.PAID_DAY, 3000),
            role(RoleCode.GAFFER, CompType.UNPAID, null, setOf(Perk.STEP_UP_CREDIT, Perk.FINAL_FILM_BY_DATE)).copy(finalFilmDueWeeks = 8),
            role(RoleCode.ACTOR, CompType.UNPAID, null, setOf(Perk.FOOTAGE_BY_DATE), 6),
            role(RoleCode.EDITOR, CompType.STIPEND, 5000).copy(remoteOk = true, postDeadline = e.today().plusDays(60), paymentTerms = PaymentTerms.WITHIN_7_DAYS),
        ))).getOrThrow()
        e.publish(rohan, pid).getOrThrow()
        val roles = e.s().roles.values.filter { it.projectId == pid }.associateBy { it.roleCode }
        val sound = q.scout(e.s(), rohan, roles.getValue(RoleCode.SOUND_MIXER).id)
        assertTrue(sound.isNotEmpty())
        assertTrue(sound.all { it.application == null && !Taxonomy.isCast(it.card.profile.primaryRole) })
        assertTrue(q.scout(e.s(), rohan, roles.getValue(RoleCode.ACTOR).id).isEmpty())
        val gaffer = q.scout(e.s(), rohan, roles.getValue(RoleCode.GAFFER).id)
        assertTrue("PAID_ONLY gaffer never gets an unpaid ask", gaffer.none { it.card.profile.id == "u_036" })
        assertNotNull(e.askCheck(rohan, roles.getValue(RoleCode.GAFFER).id, "u_036"))
        assertTrue(e.sendAsk(rohan, roles.getValue(RoleCode.GAFFER).id, "u_036", null).isFailure)
        // cast roles: no cold asks, only Rebook from the Keep list
        assertEquals("Actors apply to you. Calltime doesn't let anyone browse actors.", e.askCheck(rohan, roles.getValue(RoleCode.ACTOR).id, "u_046"))
        assertNull("Rebook a keep-list actor", e.askCheck(DemoIds.LAST_LOCAL_OWNER, "r_ll_support", meera))
        // 10 asks per project per day
        val soundRole = roles.getValue(RoleCode.SOUND_MIXER).id
        val first = sound.first().card.profile.id
        e.sendAsk(rohan, soundRole, first, "Saw your reel").getOrThrow()
        assertEquals(9, q.asksLeftToday(e.s(), pid))
        assertTrue(q.inbox(e.s(), first).asks.any { it.item.project.id == pid })
        assertNotNull(e.askCheck(rohan, soundRole, first))
    }

    @Test fun `listing rules gate publishing and the draft fix publishes`() {
        val (e, _, _) = fresh()
        val res = e.publish(rohan, DemoIds.P_ROHAN_DRAFT)
        assertEquals("R02", (res.exceptionOrNull() as RepoException).code)
        val d = DemoQueries(e).draft(e.s(), rohan, DemoIds.P_ROHAN_DRAFT)!!
        e.saveDraft(rohan, d.copy(project = d.project.copy(meals = Meals.TWO_PLUS_MEALS))).getOrThrow()
        e.publish(rohan, DemoIds.P_ROHAN_DRAFT).getOrThrow()
        assertEquals(ProjectStatus.OPEN, e.s().projects.getValue(DemoIds.P_ROHAN_DRAFT).status)
        assertTrue(DemoQueries(e).discoverCandidates(e.s(), "u_045", null).any { it.project.id == DemoIds.P_ROHAN_DRAFT })
        assertTrue("only the owner publishes", e.publish(meera, DemoIds.P_FILTER_COFFEE).isFailure)
    }

    // ───────────── time rules ─────────────

    @Test fun `14 days without an answer expires an application with a closure`() {
        val (e, _, _) = fresh()
        val pending = e.s().applications.values.filter { it.status == ApplicationStatus.PENDING && it.origin == ApplicationOrigin.APPLIED }
        assertTrue(pending.isNotEmpty())
        e.startDemo(DemoPersona.MEERA)
        e.advanceDays(15.0)
        val after = pending.map { e.s().applications.getValue(it.id) }
        assertTrue(after.all { it.status != ApplicationStatus.PENDING })
        assertTrue(after.filter { it.status == ApplicationStatus.EXPIRED }.all { it.declineReason == DemoText.CLOSURE_EXPIRED })
        assertTrue(e.s().notifications.any { it.userId == meera && it.n.body == DemoText.CLOSURE_EXPIRED })
    }

    @Test fun `T-48h still coming then jump past the shoot wraps the booking`() {
        val (e, _, q) = fresh()
        e.startDemo(DemoPersona.MEERA)
        e.advanceDays(5.0)
        assertTrue(e.s().notifications.any { it.userId == meera && it.n.kind == NotificationKind.STILL_COMING })
        e.jumpPastNextShoot()
        val m = e.s().matches.getValue(DemoIds.M_MEERA_CUTTING_CHAI)
        assertEquals(MatchState.WRAPPED, m.state)
        assertEquals(ProjectStatus.WRAPPED, e.s().projects.getValue(DemoIds.P_CUTTING_CHAI).status)
        assertTrue(q.feedbackState(e.s(), meera, m.id).unlocked)
        assertEquals(setOf(WrapKind.PAID, WrapKind.FOOTAGE, WrapKind.CREDIT), q.wrapItems(e.s(), meera, m.id).map { it.kind }.toSet())
        assertTrue("open roles closed at start", e.s().roles.values.filter { it.projectId == DemoIds.P_CUTTING_CHAI }.none { it.status == RoleStatus.OPEN })
        assertTrue(e.s().notifications.any { it.userId == meera && it.n.kind == NotificationKind.WRAP_OVERDUE && it.n.title.contains("Iron Gate") })
    }

    @Test fun `delivery auto-confirms after 7 days and feedback reveals after 14`() {
        val (e, _, q) = fresh()
        e.startDemo(DemoPersona.DANA)
        e.respondBookingCard(dana, DemoIds.BC_DANA_SOFTLIGHT_1, true).getOrThrow()
        val delivery = q.wrapItems(e.s(), dana, DemoIds.M_DANA_SOFTLIGHT).first { it.kind == WrapKind.DELIVERY }
        e.markWrapItem(dana, delivery.id, "https://demo.calltime.app/delivery/1", null).getOrThrow()
        e.advanceDays(8.0)
        val item = e.s().wrapItems.getValue(delivery.id)
        assertEquals(WrapStatus.CONFIRMED, item.status)
        assertTrue(item.autoConfirmed)
        assertEquals(MatchState.WRAPPED, e.s().matches.getValue(DemoIds.M_DANA_SOFTLIGHT).state)

        assertFalse(q.feedbackState(e.s(), kabir, DemoIds.M_KABIR_RENT_DAY).revealed)
        e.advanceDays(12.0)
        assertTrue(q.feedbackState(e.s(), kabir, DemoIds.M_KABIR_RENT_DAY).revealed)
    }

    @Test fun `talks without a booking card nudge at 72h and close politely after 7 days`() {
        val (e, _, _) = fresh()
        e.startDemo(DemoPersona.ROHAN)
        e.advanceDays(3.0)
        assertTrue("nudge72:${DemoIds.M_ROHAN_DP}" in e.s().flags)
        assertTrue(e.s().notifications.any { it.userId == rohan && it.n.title == "Send the booking card?" })
        e.advanceDays(6.0)
        val m = e.s().matches.getValue(DemoIds.M_ROHAN_DP)
        assertEquals(MatchState.CLOSED, m.state)
        assertEquals(DemoText.MATCH_AUTO_CLOSED, e.s().messages.filter { it.matchId == m.id }.maxBy { it.createdAt }.body)
    }

    // ───────────── demo controls & account ─────────────

    @Test fun `inject flagged example for cast and crew personas`() {
        val (e, _, q) = fresh()
        e.startDemo(DemoPersona.MEERA)
        e.injectFlaggedExample().getOrThrow()
        val talks = q.inbox(e.s(), meera).conversations.filter { it.match.ownerId == DemoIds.U_SCAM_2 }
        assertEquals(1, talks.size)
        assertTrue(e.s().messages.any { it.matchId == talks.single().match.id && it.safetyExample })
        assertTrue(DemoIds.U_SCAM_2 in e.index().hidden)
        assertTrue(q.discoverCandidates(e.s(), "u_025", null).none { it.project.ownerId == DemoIds.U_SCAM_2 })

        e.startDemo(DemoPersona.KABIR)
        e.injectFlaggedExample().getOrThrow()
        val ask = q.inbox(e.s(), kabir).asks.single { it.item.project.ownerId == DemoIds.U_SCAM_2 }
        assertTrue(MessageFlag.MONEY_FROM_TALENT in MessageSafety.analyze(ask.application.note!!))
    }

    @Test fun `personas start sessions and build my own starts without a profile`() {
        val (e, _, _) = fresh()
        val s1 = e.startDemo(DemoPersona.DANA)
        assertEquals(dana, s1.userId)
        assertTrue(s1.hasProfile)
        val s2 = e.startDemo(null)
        assertFalse(s2.hasProfile)
        assertTrue(s2.userId in e.s().realUsers)
        assertEquals(s2, e.s().session)
        e.signOut()
        assertNull(e.s().session)
        assertTrue(e.verifyOtp("bad", "123456").isFailure)
        assertTrue(e.verifyOtp("meera@demo.calltime.app", "12").isFailure)
        assertEquals(meera, e.verifyOtp("meera@demo.calltime.app", "123456").getOrThrow().userId)
    }

    @Test fun `reset reseeds and keeps the persona`() {
        val (e, clock, _) = fresh()
        e.startDemo(DemoPersona.MEERA)
        e.apply(meera, "r_tw_support", null, null, null, false).getOrThrow()
        e.advanceDays(10.0)
        e.reset()
        assertEquals(0L, e.s().offsetMillis)
        assertEquals(meera, e.s().session?.userId)
        assertEquals(e.dateOf(clock.millis), e.s().anchor)
        assertTrue(e.s().applications.values.none { it.applicantId == meera && it.roleId == "r_tw_support" })
    }

    @Test fun `delete account removes the profile and reopens their slots`() {
        val (e, _, q) = fresh()
        e.startDemo(DemoPersona.MEERA)
        val export = Json.parseToJsonElement(q.exportMyData(e.s(), meera)).jsonObject
        assertEquals(meera, export["profile"]!!.jsonObject["id"]!!.toString().trim('"'))
        assertFalse("never another person's private rate", q.exportMyData(e.s(), meera).contains("\"u_002\":"))
        e.deleteAccount(meera).getOrThrow()
        assertNull(e.s().profiles[meera])
        assertNull(e.s().session)
        assertTrue(e.s().matches.values.none { it.collaboratorId == meera })
        assertEquals(RoleStatus.OPEN, e.s().roles.getValue(DemoIds.R_CC_LEAD).status)
        assertNull(q.profileCard(e.s(), DemoIds.FARIDA, meera))
    }
}
