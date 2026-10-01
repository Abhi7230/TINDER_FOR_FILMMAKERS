package com.tff.app.data.demo

import kotlinx.serialization.json.Json
import java.io.File

/** Where DemoState lives between launches. JVM-testable; DemoRepo uses [FileStateStore] in filesDir. */
interface StateStore {
    fun load(): DemoState?
    fun save(state: DemoState)
    fun clear()
}

object DemoJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
        allowStructuredMapKeys = true
    }

    fun encode(s: DemoState): String = json.encodeToString(DemoState.serializer(), s)
    fun decode(text: String): DemoState = json.decodeFromString(DemoState.serializer(), text)
}

class InMemoryStateStore(private var value: DemoState? = null) : StateStore {
    var saves = 0
        private set
    override fun load(): DemoState? = value
    override fun save(state: DemoState) { value = state; saves++ }
    override fun clear() { value = null }
}

/** JSON file, written atomically (temp file + rename) so a crash mid-write never corrupts the demo. */
class FileStateStore(private val file: File) : StateStore {
    override fun load(): DemoState? {
        if (!file.exists()) return null
        return runCatching { DemoJson.decode(file.readText()) }
            .getOrNull()
            ?.takeIf { it.schema == DemoState.SCHEMA }
    }

    @Synchronized
    override fun save(state: DemoState) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(DemoJson.encode(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    override fun clear() { file.delete() }
}

/** Changed / added entries and removed keys of one keyed collection, relative to the seed. */
@kotlinx.serialization.Serializable
data class MapDelta<V>(val put: Map<String, V> = emptyMap(), val removed: List<String> = emptyList())

/** What DeltaStateStore writes: the seed's identity, the small fields in full, and deltas for the big collections. */
@kotlinx.serialization.Serializable
data class DemoSnapshot(
    val schema: Int = DemoState.SCHEMA,
    val seedVersion: Int = DemoSeed.VERSION,
    val anchor: String,
    val seedNowMillis: Long,
    val seedZone: String,
    val fingerprint: String,
    val offsetMillis: Long = 0,
    val speed: com.tff.app.model.SimSpeed = com.tff.app.model.SimSpeed.NORMAL,
    val session: com.tff.app.model.Session? = null,
    val realUsers: Set<String> = emptySet(),
    val privateRates: Map<String, Int> = emptyMap(),
    val emails: Map<String, String> = emptyMap(),
    val readMarks: Map<String, Long> = emptyMap(),
    val flags: Set<String> = emptySet(),
    val seq: Long = 1,
    val reports: List<ReportRow> = emptyList(),
    val blocks: List<BlockRow> = emptyList(),
    val swipes: List<SwipeRow> = emptyList(),
    val notifications: List<NotificationRow> = emptyList(),
    val events: List<LogEvent> = emptyList(),
    val profiles: MapDelta<com.tff.app.model.Profile> = MapDelta(),
    val projects: MapDelta<com.tff.app.model.Project> = MapDelta(),
    val roles: MapDelta<com.tff.app.model.ProjectRole> = MapDelta(),
    val applications: MapDelta<com.tff.app.model.Application> = MapDelta(),
    val matches: MapDelta<com.tff.app.model.Match> = MapDelta(),
    val bookingCards: MapDelta<com.tff.app.model.BookingCard> = MapDelta(),
    val shootDays: MapDelta<com.tff.app.model.ShootDay> = MapDelta(),
    val wrapItems: MapDelta<com.tff.app.model.WrapItem> = MapDelta(),
    val messages: MapDelta<com.tff.app.model.Message> = MapDelta(),
    val callTimes: MapDelta<com.tff.app.model.CallTime> = MapDelta(),
    val feedback: MapDelta<com.tff.app.model.Feedback> = MapDelta(),
)

/**
 * Persists DemoState as a delta against its deterministic seed. Decoding the full world on a slow device
 * costs far more than rebuilding the seed, so a relaunch = DemoSeed.build(anchor, Random(42), zone, seedNow)
 * plus a small delta. A seed version or fingerprint mismatch discards the file (the app reseeds).
 */
class DeltaStateStore(private val file: java.io.File) : StateStore {
    @Volatile private var base: DemoState? = null

    /** The seed the current file is relative to (also lets "Reset demo" skip a rebuild on the same day). */
    fun cachedBase(): DemoState? = base

    private fun baseFor(s: DemoState): DemoState {
        base?.let { b -> if (b.anchor == s.anchor && b.seedNowMillis == s.seedNowMillis && b.seedZone == s.seedZone) return b }
        val zone = runCatching { java.time.ZoneId.of(s.seedZone) }.getOrDefault(java.time.ZoneId.systemDefault())
        return DemoSeed.build(s.anchor, kotlin.random.Random(42), zone, s.seedNowMillis).also { base = it }
    }

    /** Remember [seed] as the base when the world was just seeded, so the first save needs no rebuild. */
    fun prime(seed: DemoState) { base = seed }

    override fun load(): DemoState? {
        if (!file.exists()) return null
        return runCatching {
            val snap = DemoJson.json.decodeFromString(DemoSnapshot.serializer(), file.readText())
            if (snap.schema != DemoState.SCHEMA || snap.seedVersion != DemoSeed.VERSION) return null
            val anchor = java.time.LocalDate.parse(snap.anchor)
            val b = baseFor(DemoState(anchor = anchor, seedNowMillis = snap.seedNowMillis, seedZone = snap.seedZone))
            if (fingerprint(b) != snap.fingerprint) return null
            restore(b, snap)
        }.getOrNull()
    }

    override fun save(state: DemoState) {
        val snap = snapshot(baseFor(state), state)
        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        tmp.writeText(DemoJson.json.encodeToString(DemoSnapshot.serializer(), snap))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    override fun clear() { file.delete() }

    companion object {
        fun fingerprint(s: DemoState): String = listOf(s.profiles.size, s.projects.size, s.roles.size, s.applications.size, s.matches.size,
            s.messages.size, s.wrapItems.size, s.feedback.size, s.callTimes.size, s.seq).joinToString("/")

        private fun <V> diff(base: Map<String, V>, cur: Map<String, V>) =
            MapDelta(cur.filter { (k, v) -> base[k] != v }, base.keys.filter { it !in cur })

        private fun <V> apply(base: Map<String, V>, d: MapDelta<V>): Map<String, V> {
            val out = LinkedHashMap(base)
            d.removed.forEach { out.remove(it) }
            d.put.forEach { (k, v) -> out[k] = v }
            return out
        }

        private fun <T> diffList(base: List<T>, cur: List<T>, key: (T) -> String) =
            diff(base.associateBy(key), cur.associateBy(key))

        /** Base order for base keys, then new entries in saved order (the engine only ever appends). */
        private fun <T> applyList(base: List<T>, d: MapDelta<T>, key: (T) -> String): List<T> {
            val removed = d.removed.toSet()
            val baseKeys = HashSet<String>()
            val out = ArrayList<T>(base.size + d.put.size)
            for (item in base) {
                val k = key(item)
                baseKeys += k
                if (k in removed) continue
                out += d.put[k] ?: item
            }
            d.put.forEach { (k, v) -> if (k !in baseKeys) out += v }
            return out
        }

        private val callKey: (com.tff.app.model.CallTime) -> String = { "${it.shootDayId}|${it.profileId}" }

        fun snapshot(b: DemoState, s: DemoState) = DemoSnapshot(
            anchor = s.anchor.toString(), seedNowMillis = s.seedNowMillis, seedZone = s.seedZone, fingerprint = fingerprint(b),
            offsetMillis = s.offsetMillis, speed = s.speed, session = s.session, realUsers = s.realUsers, privateRates = s.privateRates,
            emails = s.emails, readMarks = s.readMarks, flags = s.flags, seq = s.seq, reports = s.reports, blocks = s.blocks,
            swipes = s.swipes, notifications = s.notifications, events = s.events,
            profiles = diff(b.profiles, s.profiles), projects = diff(b.projects, s.projects), roles = diff(b.roles, s.roles),
            applications = diff(b.applications, s.applications), matches = diff(b.matches, s.matches),
            bookingCards = diff(b.bookingCards, s.bookingCards), shootDays = diff(b.shootDays, s.shootDays),
            wrapItems = diff(b.wrapItems, s.wrapItems),
            messages = diffList(b.messages, s.messages) { it.id },
            callTimes = diffList(b.callTimes, s.callTimes, callKey),
            feedback = diffList(b.feedback, s.feedback) { it.id },
        )

        fun restore(b: DemoState, d: DemoSnapshot) = b.copy(
            offsetMillis = d.offsetMillis, speed = d.speed, session = d.session, realUsers = d.realUsers, privateRates = d.privateRates,
            emails = d.emails, readMarks = d.readMarks, flags = d.flags, seq = d.seq, reports = d.reports, blocks = d.blocks,
            swipes = d.swipes, notifications = d.notifications, events = d.events,
            profiles = apply(b.profiles, d.profiles), projects = apply(b.projects, d.projects), roles = apply(b.roles, d.roles),
            applications = apply(b.applications, d.applications), matches = apply(b.matches, d.matches),
            bookingCards = apply(b.bookingCards, d.bookingCards), shootDays = apply(b.shootDays, d.shootDays),
            wrapItems = apply(b.wrapItems, d.wrapItems),
            messages = applyList(b.messages, d.messages) { it.id },
            callTimes = applyList(b.callTimes, d.callTimes, callKey),
            feedback = applyList(b.feedback, d.feedback) { it.id },
        )
    }
}
