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
