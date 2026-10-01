package com.tff.app.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formatting helpers. Mono-type strings (dates, call times) are UPPERCASE per the design spec. */
object Dates {
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)     // Sat 14 Nov
    private val dayNum = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)           // 14 Nov
    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun short(d: LocalDate): String = dayMonth.format(d)                   // "Sat 14 Nov"
    fun shortUpper(d: LocalDate): String = short(d).uppercase()            // "SAT 14 NOV"
    fun dayMonth(d: LocalDate): String = dayNum.format(d)                  // "14 Nov"
    fun time(t: LocalTime): String = hm.format(t)                          // "07:30"

    /** "Sat 14 – Sun 15 Nov", or "Sat 14 Nov" for one day, or "Sat 28 Nov – Sun 6 Dec" across months. */
    fun span(start: LocalDate, end: LocalDate): String {
        if (start == end) return short(start)
        val sameMonth = start.month == end.month && start.year == end.year
        val left = if (sameMonth) DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH).format(start) else short(start)
        return "$left – ${short(end)}"
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    /** "2h ago", "3d ago", "just now" for footers. */
    fun ago(thenMillis: Long, nowMillis: Long): String {
        val mins = (nowMillis - thenMillis) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            else -> "${mins / (60 * 24)}d ago"
        }
    }
}
