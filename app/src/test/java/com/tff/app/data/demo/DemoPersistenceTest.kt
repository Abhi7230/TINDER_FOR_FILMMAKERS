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
}
