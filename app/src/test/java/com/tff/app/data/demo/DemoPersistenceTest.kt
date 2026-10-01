package com.tff.app.data.demo

import com.tff.app.model.Decision
import com.tff.app.model.DemoPersona
import com.tff.app.model.SimSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DemoPersistenceTest {

    @Test fun `seed round-trips through JSON unchanged`() {
        val s = T.seed()
        val back = DemoJson.decode(DemoJson.encode(s))
        assertEquals(s, back)
    }

    @Test fun `state after real use round-trips through JSON and the file store`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.MEERA)
        e.setSpeed(SimSpeed.INSTANT)
        val app = e.apply(DemoIds.MEERA, DemoIds.R_LL_LEAD, "Note", null, null, false).getOrThrow()
        e.decide(DemoIds.LAST_LOCAL_OWNER, app.id, Decision.TALK, null).getOrThrow()
        e.advanceDays(2.0)
        val s = e.state.value
        assertEquals(s, DemoJson.decode(DemoJson.encode(s)))

        val dir = Files.createTempDirectory("ws3").toFile()
        val store = FileStateStore(File(dir, "demo_state.json"))
        assertNull(store.load())
        store.save(s)
        val loaded = store.load()!!
        assertEquals(s, loaded)
        assertEquals(SimSpeed.INSTANT, loaded.speed)
        assertEquals(DemoIds.MEERA, loaded.session?.userId)
        assertTrue(loaded.offsetMillis > 0)
        // a relaunch continues from the saved world
        val (e2, _) = T.engine(loaded)
        assertEquals(s.matches.keys, e2.state.value.matches.keys)
        store.clear()
        assertNull(store.load())
        dir.deleteRecursively()
    }

    @Test fun `a corrupt or older file is ignored so the app reseeds`() {
        val dir = Files.createTempDirectory("ws3").toFile()
        val f = File(dir, "demo_state.json")
        f.writeText("{ not json")
        assertNull(FileStateStore(f).load())
        f.writeText(DemoJson.encode(T.seed()).replaceFirst("{", "{\"schema\":0,"))
        assertNull(FileStateStore(f).load())
        dir.deleteRecursively()
    }

    @Test fun `unknown keys from a newer build don't break loading`() {
        val text = DemoJson.encode(T.seed()).replaceFirst("{", "{\"futureField\":[1,2,3],")
        assertEquals(T.seed(), DemoJson.decode(text))
    }

    @Test fun `in-memory store counts saves`() {
        val store = InMemoryStateStore()
        store.save(T.seed())
        assertEquals(1, store.saves)
        assertEquals(T.seed(), store.load())
    }

    @Test fun `delta store rebuilds the seed and replays only what changed`() {
        val (e, clock) = T.engine()
        val q = DemoQueries(e)
        e.startDemo(DemoPersona.ROHAN)
        // a spread of mutations: applications, matches, cards, day sheets (re-published), wrap, feedback, messages, reports
        val ed = q.applicants(e.state.value, DemoIds.ROHAN, DemoIds.R_FC_SOUND).first()
        val m = e.decide(DemoIds.ROHAN, ed.application!!.id, Decision.TALK, null).getOrThrow()!!
        e.sendMessage(DemoIds.ROHAN, m.id, "Can you bring two lavs?").getOrThrow()
        e.sendBookingCard(DemoIds.ROHAN, m.id, e.draftBookingTerms(DemoIds.ROHAN, m.id)).getOrThrow()
        e.respondBookingCard(m.collaboratorId, e.state.value.bookingCards.values.single { it.matchId == m.id }.id, true).getOrThrow()
        val fc = e.state.value.projects.getValue(DemoIds.P_FILTER_COFFEE)
        val draft = com.tff.app.model.ShootDayDraft(fc.id, null, fc.shootDates.first(), java.time.LocalTime.of(7, 0), java.time.LocalTime.of(17, 0), "Jayanagar 4th Block, Bengaluru")
        e.publishShootDay(DemoIds.ROHAN, draft).getOrThrow()
        val dayId = e.state.value.shootDays.values.single { it.projectId == fc.id }.id
        e.publishShootDay(DemoIds.ROHAN, draft.copy(shootDayId = dayId, generalCall = java.time.LocalTime.of(6, 30))).getOrThrow()
        e.ackShootDay(m.collaboratorId, dayId).getOrThrow()
        e.report(DemoIds.ROHAN, com.tff.app.model.ReportTarget.PROFILE, "u_049", com.tff.app.model.ReportReason.SPAM, null).getOrThrow()
        e.injectFlaggedExample().getOrThrow()
        e.submitFeedback(DemoIds.KABIR, DemoIds.M_KABIR_RENT_DAY, com.tff.app.model.FeedbackAnswers(workAgain = true)).getOrThrow()
        e.block(DemoIds.ROHAN, "u_046").getOrThrow()
        e.advanceDays(3.0)
        val s = e.state.value

        val dir = Files.createTempDirectory("ws3").toFile()
        val f = File(dir, "demo_state_v2.json")
        DeltaStateStore(f).save(s)
        assertTrue("delta is small: ${f.length()} bytes", f.length() < 120_000)
        val back = DeltaStateStore(f).load()!!
        assertEquals(s, back)

        // a seed from another build (version or fingerprint mismatch) is never mixed with this delta
        f.writeText(f.readText().replace("\"fingerprint\":\"", "\"fingerprint\":\"x"))
        assertNull(DeltaStateStore(f).load())
        f.writeText("garbage")
        assertNull(DeltaStateStore(f).load())
        dir.deleteRecursively()
    }

    @Test fun `delta of an untouched seed is tiny and reset can reuse the cached seed`() {
        val s = T.seed()
        val dir = Files.createTempDirectory("ws3").toFile()
        val f = File(dir, "demo_state_v2.json")
        val store = DeltaStateStore(f)
        store.prime(s)
        store.save(s)
        assertTrue(f.length() < 25_000)
        assertEquals(s, DeltaStateStore(f).load())
        val (e, _) = T.engine(s)
        e.startDemo(DemoPersona.MEERA)
        e.apply(DemoIds.MEERA, "r_tw_support", null, null, null, false).getOrThrow()
        e.reset { store.cachedBase() }
        assertTrue("same day: the cached seed is reused", e.state.value.profiles === s.profiles)
        assertEquals(DemoIds.MEERA, e.state.value.session?.userId)
        dir.deleteRecursively()
    }
}
