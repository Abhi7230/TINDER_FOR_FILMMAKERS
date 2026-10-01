package com.tff.app.ui.screens.onboarding

import com.tff.app.core.Geo
import com.tff.app.model.AvailabilityPattern
import com.tff.app.model.City
import com.tff.app.model.CompMode
import com.tff.app.model.ContentFlag
import com.tff.app.model.Density
import com.tff.app.model.Goal
import com.tff.app.model.Level
import com.tff.app.model.LinkKind
import com.tff.app.model.Perk
import com.tff.app.model.RoleCode
import com.tff.app.model.RoleWithProject
import com.tff.app.model.SecondaryRole
import com.tff.app.model.Taxonomy
import kotlin.math.roundToInt

/**
 * Pure helpers shared by onboarding (S04) and Edit profile (S20). No android.* so they're unit-tested
 * in app/src/test (ProfileLogicTest).
 */

/** Roles I do: one main role, up to 4 others, up to 2 step-up roles (PRD §6.1, F3). */
data class RoleSelection(
    val primary: RoleCode? = null,
    val secondary: List<RoleCode> = emptyList(),
    val stepUp: List<RoleCode> = emptyList(),
) {
    val all: Set<RoleCode> get() = setOfNotNull(primary) + secondary + stepUp

    /** "MAIN" / "ALSO" / "STEP UP" tag shown on the picker row. */
    fun tagOf(code: RoleCode): RoleTag? = when (code) {
        primary -> RoleTag.MAIN
        in secondary -> RoleTag.ALSO
        in stepUp -> RoleTag.STEP_UP
        else -> null
    }

    /**
     * S04 tap rule, one cycle for every row: unselected → MAIN (when there's no main role yet, else ALSO)
     * → ALSO → unselected. Max 1 main role and 4 others. Tapping a step-up role makes it a role you do.
     */
    fun tap(code: RoleCode): RoleSelection = when (code) {
        primary -> if (secondary.size < MAX_SECONDARY) copy(primary = null, secondary = listOf(code) + secondary) else copy(primary = null)
        in secondary -> copy(secondary = secondary - code)
        else -> {
            val s = copy(stepUp = stepUp - code)
            when {
                s.primary == null -> s.copy(primary = code)
                s.secondary.size < MAX_SECONDARY -> s.copy(secondary = s.secondary + code)
                else -> this
            }
        }
    }

    /** S04 long-press rule: "step up to" (max 2). Long-press again to remove. */
    fun longPress(code: RoleCode): RoleSelection = when {
        code in stepUp -> copy(stepUp = stepUp - code)
        stepUp.size >= MAX_STEP_UP -> this
        else -> RoleSelection(primary = primary.takeIf { it != code }, secondary = secondary - code, stepUp = stepUp + code)
    }

    fun secondaryRoles(existing: List<SecondaryRole> = emptyList()): List<SecondaryRole> =
        secondary.map { code -> existing.firstOrNull { it.code == code } ?: SecondaryRole(code, 2) }

    companion object {
        const val MAX_SECONDARY = 4
        const val MAX_STEP_UP = 2
    }
}

enum class RoleTag { MAIN, ALSO, STEP_UP }

object ProfileLogic {
    /** Remote OK defaults on for editors, composers, colorists, sound designers and writers (PRD §6.1). */
    fun remoteDefault(primary: RoleCode?): Boolean = primary != null && Taxonomy.dept(primary) in Taxonomy.POST_DEPTS

    /** Work-link kind from the URL ("kind auto-detected", S04). */
    fun detectLinkKind(url: String): LinkKind {
        val u = url.trim().lowercase()
        return when {
            u.isEmpty() -> LinkKind.OTHER
            "imdb." in u -> LinkKind.IMDB
            "youtube." in u || "youtu.be" in u || "vimeo." in u -> LinkKind.REEL
            "instagram." in u || "behance." in u || "artstation." in u || "drive.google." in u || "dropbox." in u ||
                "soundcloud." in u || "bandcamp." in u -> LinkKind.PORTFOLIO
            else -> LinkKind.OTHER
        }
    }

    /** Adds https:// when someone types "youtu.be/abc"; null when it can't be a link. */
    fun normaliseUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        val withScheme = if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
        return if (host.contains('.') && host.length >= 4 && !host.startsWith('.') && !host.endsWith('.')) withScheme else null
    }

    /** Whole positive number or null; anything else is invalid (returns -1). */
    fun parseRate(raw: String): Int? {
        val t = raw.trim().replace(",", "")
        if (t.isEmpty()) return null
        return t.toIntOrNull()?.takeIf { it > 0 } ?: -1
    }

    /** Ranking goals derived from what onboarding asked (they can be edited later in Edit profile). */
    fun defaultGoals(level: Level?, compMode: CompMode?, stepUp: List<RoleCode>): List<Goal> = buildList {
        if (level == Level.FIRST_PROJECT || level == Level.BUILDING_REEL) add(Goal.REEL_FOOTAGE)
        if (stepUp.isNotEmpty()) add(Goal.STEP_UP)
        if (compMode == CompMode.PAID_ONLY || level == Level.WORKING_PRO) add(Goal.PAID_WORK)
        if (level == Level.FIRST_PROJECT) add(Goal.BUILD_TEAM)
    }

    /** Step 3 skipped: newcomers see unpaid roles (they still have to say what you get), pros stay selective. */
    fun defaultCompMode(level: Level?): CompMode = when (level) {
        Level.FIRST_PROJECT, Level.BUILDING_REEL -> CompMode.OPEN_TO_UNPAID
        else -> CompMode.PAID_AND_SELECTIVE
    }

    /** Languages offered as chips, by the city's country. */
    fun languageOptions(country: String?): List<String> = when (country) {
        "IN", null -> listOf("en", "hi", "mr", "kn", "ta", "te", "ml", "bn", "gu", "pa", "ur")
        else -> listOf("en", "es", "fr", "de", "pt", "it", "ar", "hi", "ur", "zh", "ko")
    }

    /** Perks someone can require before doing unpaid work (G4). */
    val unpaidPerkOptions: List<Perk> = listOf(
        Perk.FOOTAGE_BY_DATE, Perk.MEALS, Perk.TRAVEL_COVERED, Perk.FINAL_FILM_BY_DATE,
        Perk.STEP_UP_CREDIT, Perk.LODGING, Perk.SHORT_COMMITMENT, Perk.SCRIPT_READY,
    )

    val contentOptions: List<ContentFlag> = ContentFlag.entries

    val availabilityOptions: List<AvailabilityPattern> = listOf(
        AvailabilityPattern.ANY_DAY, AvailabilityPattern.WEEKENDS_ONLY, AvailabilityPattern.EVENINGS_AND_WEEKENDS, AvailabilityPattern.WEEKDAYS_ONLY,
    )

    /** The name as the card shows it ("Meera K."). */
    fun shortName(full: String): String {
        val parts = full.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts.first()
            else -> "${parts.first()} ${parts.last().first().uppercaseChar()}."
        }
    }

    /**
     * The live radius line (S04): "≈ N people and M projects within X km". Projects are counted exactly
     * from the city's open on-set listings (distance from the chosen area); people are estimated from the
     * city's head count, weighted by the share of the city's areas inside the radius (we never fetch
     * other people's locations). Remote roles are not "within X km" and are left out.
     */
    fun nearby(city: City, areaId: String?, radiusKm: Int, density: Density?, candidates: List<RoleWithProject>): Nearby {
        val area = city.areas.firstOrNull { it.id == areaId } ?: city.areas.firstOrNull()
        val lat = area?.lat ?: city.lat
        val lng = area?.lng ?: city.lng
        val projects = candidates.asSequence()
            .filter { !it.role.isPostRole && it.project.shootDates.isNotEmpty() }
            .filter { Geo.haversineKm(lat, lng, it.areaLat, it.areaLng) <= radiusKm }
            .map { it.project.id }.distinct().count()
        val people = density?.let { d ->
            val total = city.areas.size.coerceAtLeast(1)
            val inside = city.areas.count { a -> Geo.haversineKm(lat, lng, a.lat, a.lng) <= radiusKm }.coerceAtLeast(1)
            (d.people * inside.toDouble() / total).roundToInt()
        }
        return Nearby(people, projects)
    }

    data class Nearby(val people: Int?, val projects: Int)
}
