package com.tff.app.data.demo

import com.tff.app.domain.MatchEngine
import com.tff.app.domain.Viewer
import com.tff.app.model.Profile
import com.tff.app.model.RoleWithProject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/** A clock the test moves by hand. Zone = the JVM default, the same one the domain code reads. */
class FixedClock(var millis: Long, override val zone: ZoneId = ZoneId.systemDefault()) : DemoClock {
    override fun baseMillis(): Long = millis
}

object T {
    val zone: ZoneId = ZoneId.systemDefault()
    /** Friday 2 Oct 2026, the PRD date. */
    val anchor: LocalDate = LocalDate.of(2026, 10, 2)
    val now: Long = SeedKit.millisAt(anchor, LocalTime.of(9, 30), zone)

    fun seed(anchor: LocalDate = T.anchor): DemoState =
        DemoSeed.build(anchor, Random(42), zone, SeedKit.millisAt(anchor, LocalTime.of(9, 30), zone))

    fun engine(state: DemoState = seed()): Pair<DemoEngine, FixedClock> {
        val clock = FixedClock(now)
        return DemoEngine(state, clock) { println("engine: $it") } to clock
    }

    fun viewer(ix: DemoIndex, p: Profile): Viewer {
        val a = ix.area(p.areaId)!!
        return Viewer(p, ix.s.privateRates[p.id], a.lat, a.lng)
    }

    /** Roles that pass every gate for [userId] (what their Gigs deck can show). */
    fun passing(engine: DemoEngine, userId: String): List<RoleWithProject> {
        val ix = engine.index()
        val q = DemoQueries(engine)
        val v = viewer(ix, ix.profile(userId)!!)
        return q.discoverCandidates(ix.s, userId, null).filter { MatchEngine.failedGates(v, it, ix.today).isEmpty() }
    }
}
