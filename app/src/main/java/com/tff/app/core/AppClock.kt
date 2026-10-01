package com.tff.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The one clock the app reads. Demo mode shifts it with [offsetMillis] (Settings → demo controls),
 * so "Jump past next shoot" moves every screen, ranking rule and wrap due-date together.
 * Domain code never reads this directly: it takes `today` / `nowMillis` as parameters.
 */
object AppClock {
    @Volatile var offsetMillis: Long = 0L
    val zone: ZoneId get() = ZoneId.systemDefault()
    fun nowMillis(): Long = System.currentTimeMillis() + offsetMillis
    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()
    fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), zone)
}
