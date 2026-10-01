package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ApplicantRankerTest {

    private val today = Fx.TODAY
    private val soundRole = Fx.item(role = Fx.role(id = "r_sound", screeningQuestion = "What's your kit?"))

    private fun card(p: Profile, km: Double? = 4.0, rep: Reputation = Fx.reputation(id = p.id, shoots = 0, replyHours = null)) =
        ProfileCard(profile = p, reputation = rep, distanceKm = km)

    private fun app(id: String, applicant: String, at: Long, answer: String? = "Sound Devices MixPre-6, two lavs, a boom.") =
        Application(id = id, roleId = "r_sound", applicantId = applicant, screeningAnswer = answer, createdAt = at)

    private val mixer = Fx.profile(id = "u_mix", name = "Dana Reyes", primaryRole = RoleCode.SOUND_MIXER, level = Level.CREDITED_INDIE,
        languages = listOf("en", "kn"), gear = listOf("MixPre-6", "Boom op (Ravi)"), phoneVerified = true,
        workLinks = listOf(Fx.reel("l1", RoleCode.SOUND_MIXER)))

    @Test fun orderingAndReasonChips() {
        val strong = ApplicantInput(app("a1", mixer.id, 2_000), card(mixer, 2.0,
            Fx.reputation(id = mixer.id, shoots = 9, showedUp = Ratio(9, 9), replyHours = 3, confirmedCredits = 3)), rateFit = 1.0)
        val weak = Fx.profile(id = "u_weak", name = "Sam Lee", primaryRole = RoleCode.BOOM_OP,
            secondaryRoles = listOf(SecondaryRole(RoleCode.SOUND_MIXER, 1)), level = Level.FIRST_PROJECT)
        val weakIn = ApplicantInput(app("a2", weak.id, 1_000, answer = null), card(weak, 20.0), rateFit = 0.5)
        val ranked = ApplicantRanker.rank(soundRole, listOf(weakIn, strong), Level.CREDITED_INDIE, today)
        assertEquals(listOf("u_mix", "u_weak"), ranked.map { it.card.profile.id })
        val top = ranked[0]
        assertTrue(top.score > 90.0)
        assertEquals(3, top.reasons.size)
        assertEquals("Showed up 9/9", top.reasons[0])
        assertTrue(top.reasons.contains("Reel for this role"))
        assertEquals(2, top.availDays)
        assertEquals(2, top.totalDays)
        // "Speaks Kannada" and "Has boom op" are reasons too when they make the cut.
        val (_, all) = ApplicantRanker.score(soundRole, strong.copy(card = strong.card.copy(
            reputation = Fx.reputation(id = mixer.id, shoots = 0, replyHours = null))), Level.CREDITED_INDIE, today)
        assertTrue(all.contains("Speaks Kannada"))
        assertTrue(all.contains("Has boom op"))
    }

    @Test fun tiesGoToTheEarlierApplication() {
        val twin = mixer.copy(id = "u_twin")
        val early = ApplicantInput(app("a_early", twin.id, 1_000), card(twin), 1.0)
        val late = ApplicantInput(app("a_late", mixer.id, 5_000), card(mixer), 1.0)
        val ranked = ApplicantRanker.rank(soundRole, listOf(late, early), Level.CREDITED_INDIE, today)
        assertEquals(ranked[0].score, ranked[1].score, 1e-9)
        assertEquals("a_early", ranked[0].application!!.id)
    }

    @Test fun nearDuplicateAnswersScoreZero() {
        val answer = "I have a Zoom F6, two Sanken lavs and a boom pole, and I've recorded 4 shorts."
        val copy = "I have a Zoom F6, two Sanken lavs and a boom pole, and I've recorded 4 shorts!"
        assertTrue(ApplicantRanker.similarity(answer, copy) >= 0.9)
        assertTrue(ApplicantRanker.similarity("Zoom F6 and lavs", "I can start on Monday") < 0.5)
        assertEquals(1.0, ApplicantRanker.similarity("", ""), 1e-9)
        val fresh = ApplicantInput(app("a1", mixer.id, 0, answer), card(mixer), 1.0, otherAnswers = listOf("Different answer about my DP work"))
        val duped = fresh.copy(otherAnswers = listOf(copy))
        assertEquals(5.0, ApplicantRanker.breakdown(soundRole, fresh, Level.CREDITED_INDIE, today).answer, 1e-9)
        assertEquals(0.0, ApplicantRanker.breakdown(soundRole, duped, Level.CREDITED_INDIE, today).answer, 1e-9)
        val empty = fresh.copy(application = fresh.application!!.copy(screeningAnswer = "  "))
        assertEquals(0.0, ApplicantRanker.breakdown(soundRole, empty, Level.CREDITED_INDIE, today).answer, 1e-9)
    }

    @Test fun beginnerFloorOnlyOnBeginnerProjects() {
        val newbie = Fx.profile(id = "u_new", primaryRole = RoleCode.SOUND_MIXER, level = Level.FIRST_PROJECT, workLinks = emptyList())
        val input = ApplicantInput(app("a1", newbie.id, 0), card(newbie), 1.0)
        val beginners = soundRole.copy(role = soundRole.role.copy(levelWanted = Level.FIRST_PROJECT))
        assertEquals(5.0, ApplicantRanker.breakdown(beginners, input, Level.CREDITED_INDIE, today).reel, 1e-9)
        assertEquals(0.0, ApplicantRanker.breakdown(soundRole, input, Level.CREDITED_INDIE, today).reel, 1e-9)
        // A first-project owner also gets the floor applied.
        assertEquals(5.0, ApplicantRanker.breakdown(soundRole, input, Level.FIRST_PROJECT, today).reel, 1e-9)
    }

    @Test fun reelTiersAndPortfolioScoresFour() {
        fun reel(links: List<WorkLink>) = ApplicantRanker.breakdown(soundRole,
            ApplicantInput(app("a", "u", 0), card(mixer.copy(workLinks = links)), 1.0), Level.CREDITED_INDIE, today).reel
        assertEquals(10.0, reel(listOf(Fx.reel("a", RoleCode.SOUND_MIXER))), 1e-9)
        assertEquals(5.0, reel(listOf(Fx.reel("a", RoleCode.DP))), 1e-9)
        assertEquals(4.0, reel(listOf(Fx.reel("a", kind = LinkKind.PORTFOLIO))), 1e-9)
        assertEquals(0.0, reel(listOf(Fx.reel("a", kind = LinkKind.HEADSHOT))), 1e-9)
        assertEquals(0.0, reel(emptyList()), 1e-9)
    }

    @Test fun rateFitDrivesTheRateComponent() {
        val p = Fx.project()
        val paid = Fx.role(amountMin = 2000, amountMax = 2500)
        assertEquals(1.0, ApplicantRanker.rateFit(paid, p, null), 1e-9)
        assertEquals(1.0, ApplicantRanker.rateFit(paid, p, 2500), 1e-9)
        assertEquals(0.5, ApplicantRanker.rateFit(paid, p, 5000), 1e-9)
        assertEquals(1.0, ApplicantRanker.rateFit(Fx.role(compType = CompType.UNPAID), p, 5000), 1e-9)
        // On-set flat fee is judged per day: 6,000 over 2 days vs a 4,000 minimum.
        assertEquals(0.75, ApplicantRanker.rateFit(Fx.role(compType = CompType.PAID_FLAT, amountMin = 6000), p, 4000), 1e-9)
        fun rate(fit: Double) = ApplicantRanker.breakdown(soundRole, ApplicantInput(app("a", "u", 0), card(mixer), fit), Level.CREDITED_INDIE, today).rate
        assertEquals(10.0, rate(1.0), 1e-9)
        assertEquals(4.0, rate(0.4), 1e-9)
        assertEquals(0.0, rate(0.0), 1e-9)
    }

    @Test fun componentsFollowTheSpec() {
        val rep = Fx.reputation(id = mixer.id, shoots = 5, showedUp = Ratio(4, 5), replyHours = 30, confirmedCredits = 6)
        val idMixer = mixer.copy(idVerified = true)
        val b = ApplicantRanker.breakdown(soundRole, ApplicantInput(app("a", "u", 0), card(idMixer, 12.5, rep), 1.0), Level.CREDITED_INDIE, today)
        assertEquals(25.0, b.roleTier, 1e-9)
        assertEquals(15.0, b.avail, 1e-9)
        assertEquals(5.0, b.distance, 1e-9)                    // 10 × (1 − 12.5/25)
        assertEquals(8.0 + 3.0, b.reliability, 1e-9)            // 10 × 0.8 + reply < 72h
        assertEquals(4.0 + 2.0 + 4.0, b.trust, 1e-9)             // id + phone + min(4, credits)
        // n < 3 → 8; step-up 18; level off by one 20.
        val fresh = ApplicantRanker.breakdown(soundRole, ApplicantInput(app("a", "u", 0), card(mixer), 1.0), Level.CREDITED_INDIE, today)
        assertEquals(8.0, fresh.reliability, 1e-9)
        val stepper = Fx.profile(id = "u_s", primaryRole = RoleCode.BOOM_OP, stepUpRoles = listOf(RoleCode.SOUND_MIXER))
        assertEquals(18.0, ApplicantRanker.breakdown(soundRole, ApplicantInput(null, card(stepper), 1.0), Level.CREDITED_INDIE, today).roleTier, 1e-9)
        val wantsPro = soundRole.copy(role = soundRole.role.copy(levelWanted = Level.WORKING_PRO))
        assertEquals(20.0, ApplicantRanker.breakdown(wantsPro, ApplicantInput(null, card(mixer), 1.0), Level.CREDITED_INDIE, today).roleTier, 1e-9)
        // Remote post roles: full availability and distance.
        val editor = Fx.item(role = Fx.role(roleCode = RoleCode.EDITOR, compType = CompType.PAID_FLAT, amountMin = 30000, remoteOk = true,
            postDeadline = today.plusDays(40)), project = Fx.postProject(id = "p_fc"))
        val ed = Fx.profile(id = "u_ed", primaryRole = RoleCode.EDITOR, remoteOk = true)
        val eb = ApplicantRanker.breakdown(editor, ApplicantInput(null, card(ed, 900.0), 1.0), Level.CREDITED_INDIE, today)
        assertEquals(15.0, eb.avail, 1e-9)
        assertEquals(10.0, eb.distance, 1e-9)
    }

    // ───────────── Reverse gates (Scout / Rebook asks) ─────────────

    private val kabir = Fx.profile(id = "u_kabir", name = "Kabir Shah", primaryRole = RoleCode.GAFFER, compMode = CompMode.PAID_ONLY,
        languages = listOf("hi", "en"))
    private val gafferPaid = Fx.item(role = Fx.role(id = "r_g", roleCode = RoleCode.GAFFER, amountMin = 3000))
    private val gafferUnpaid = Fx.item(role = Fx.role(id = "r_g2", roleCode = RoleCode.GAFFER, compType = CompType.UNPAID,
        perks = setOf(Perk.FOOTAGE_BY_DATE, Perk.STEP_UP_CREDIT), footageDueWeeks = 6))

    @Test fun reverseG4BlocksUnpaidAskToPaidOnly() {
        assertEquals("Kabir only takes paid work right now.", ApplicantRanker.askBlockReason(gafferUnpaid, kabir, null, today, false))
        assertNull(ApplicantRanker.askBlockReason(gafferPaid, kabir, null, today, false))
        assertNotNull(ApplicantRanker.askBlockReason(gafferPaid, kabir, 4000, today, false))
        val selective = kabir.copy(compMode = CompMode.PAID_AND_SELECTIVE, unpaidMustHaves = listOf(Perk.LODGING))
        assertEquals("Kabir does unpaid work only with: stay covered.", ApplicantRanker.askBlockReason(gafferUnpaid, selective, null, today, false))
    }

    @Test fun reverseG5BlocksRuledOutContentWithoutNamingIt() {
        val night = gafferPaid.copy(project = gafferPaid.project.copy(contentFlags = setOf(ContentFlag.NIGHT_SHOOT), contentNoneConfirmed = false))
        val reason = ApplicantRanker.askBlockReason(night, kabir.copy(contentWontDo = listOf(ContentFlag.NIGHT_SHOOT)), null, today, false)
        assertEquals("This project has content Kabir has ruled out.", reason)
        assertFalse(reason!!.contains("Night", ignoreCase = true))
    }

    @Test fun castRolesOnlyByRebookFromKeepList() {
        val meera = Fx.profile(languages = listOf("kn", "en"))
        val lead = Fx.item(role = Fx.role(id = "r_lead", roleCode = RoleCode.ACTOR, compType = CompType.UNPAID,
            perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 6))
        assertEquals("Actors apply to you. Calltime doesn't let anyone browse actors.",
            ApplicantRanker.askBlockReason(lead, meera, null, today, isKeepList = false))
        assertNull(ApplicantRanker.askBlockReason(lead, meera, null, today, isKeepList = true))
        // Keep-list rebooks still respect hard limits.
        val paidOnlyMeera = meera.copy(compMode = CompMode.PAID_ONLY)
        assertEquals("Meera only takes paid work right now.", ApplicantRanker.askBlockReason(lead, paidOnlyMeera, null, today, true))
    }

    @Test fun otherReverseGates() {
        val wrongRole = Fx.profile(id = "u_e", name = "Esha Iyer", primaryRole = RoleCode.EDITOR, compMode = CompMode.OPEN_TO_UNPAID)
        assertEquals("Esha doesn't list Gaffer as one of their roles.", ApplicantRanker.askBlockReason(gafferPaid, wrongRole, null, today, false))
        val busy = kabir.copy(busyDates = setOf(Fx.SAT_14_NOV, Fx.SUN_15_NOV))
        assertEquals("Kabir isn't free on the shoot dates.", ApplicantRanker.askBlockReason(gafferPaid, busy, null, today, false))
        val self = kabir.copy(id = gafferPaid.project.ownerId)
        assertNotNull(ApplicantRanker.askBlockReason(gafferPaid, self, null, today, false))
        val writer = Fx.item(role = Fx.role(roleCode = RoleCode.WRITER, compType = CompType.PAID_FLAT, amountMin = 20000, remoteOk = true,
            postDeadline = today.plusDays(30)), project = Fx.postProject(id = "p_fc", languages = listOf("kn")))
        val hindiWriter = Fx.profile(id = "u_w", name = "Ravi Nair", primaryRole = RoleCode.WRITER, languages = listOf("hi"))
        assertEquals("Ravi doesn't speak Kannada.", ApplicantRanker.askBlockReason(writer, hindiWriter, null, today, false))
        val soon = writer.copy(role = writer.role.copy(postDeadline = today.plusDays(3)))
        assertNotNull(ApplicantRanker.askBlockReason(soon, hindiWriter.copy(languages = listOf("kn")), null, today, false))
        assertNull(ApplicantRanker.askBlockReason(writer, hindiWriter.copy(languages = listOf("kn")), null, today, false))
    }

    @Test fun scoutCandidatesHaveNoApplication() {
        val view = ApplicantRanker.rank(soundRole, listOf(ApplicantInput(null, card(mixer), 1.0)), Level.CREDITED_INDIE, today).single()
        assertNull(view.application)
        // Scout candidates have no answer to give.
        assertEquals(0.0, ApplicantRanker.breakdown(soundRole, ApplicantInput(null, card(mixer), 1.0), Level.CREDITED_INDIE, today).answer, 1e-9)
        assertNull(view.headshotUrl)
        val lead = Fx.item(role = Fx.role(roleCode = RoleCode.ACTOR, compType = CompType.UNPAID, perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 6))
        val actorView = ApplicantRanker.rank(lead, listOf(ApplicantInput(null, card(Fx.profile()), 1.0, headshotUrl = "https://x/h.jpg")),
            Level.CREDITED_INDIE, LocalDate.of(2026, 10, 2)).single()
        assertEquals("https://x/h.jpg", actorView.headshotUrl)
    }
}
