package com.tff.app.ui.screens.discover

import com.tff.app.domain.CardText
import com.tff.app.domain.DeckCard
import com.tff.app.domain.Gate
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.Viewer
import com.tff.app.model.City
import com.tff.app.model.DateWindow
import com.tff.app.model.DeckFilters
import com.tff.app.model.Profile
import com.tff.app.model.RoleWithProject
import java.time.LocalDate
import kotlin.math.roundToInt

/** Which of the S05 empty states the Gigs deck shows. */
enum class DeckEmpty {
    /** (a) Nothing passes the gates. */
    NOTHING_IN_RANGE,
    /** (b) 25 applications sent today. */
    DAILY_CAP,
    /** (c) Everything in the deck has been swiped. */
    EXHAUSTED,
    /** The deck has roles, but the S08 filters hide all of them. */
    FILTERED,
}

/**
 * Honest numbers for the "Nothing in range yet" state: how many open roles are near the viewer, and
 * which single thing keeps the near-misses out of the deck. Counts only roles that fail exactly one gate,
 * so every number points at one fix (widen radius, include remote, edit unpaid terms).
 */
data class DeckBlockers(
    val nearby: Int = 0,
    val justOutside: Int = 0,
    val remoteOnly: Int = 0,
    val unpaidTerms: Int = 0,
    val otherRoles: Int = 0,
    val busyDates: Int = 0,
)

/**
 * Pure deck logic for S05 (no android.*): build the deck with [MatchEngine], classify empty states,
 * and work out the near-miss numbers. Unit-tested in DiscoverDeckLogicTest.
 */
object DeckLogic {
    /** "Widen radius" multiplies the saved travel radius by this (PRD S05 empty state a). */
    const val WIDEN_FACTOR = 1.5
    const val MAX_RADIUS_KM = 150
    const val MIN_RADIUS_KM = 2
    /** The S08 distance slider: its top stop means "any distance". */
    const val DISTANCE_SLIDER_MAX = 100

    /** The viewer for MatchEngine: the profile, its own private min rate, and its area centroid. Null if the area is unknown. */
    fun viewer(profile: Profile, minRate: Int?, cities: List<City>): Viewer? {
        val area = cities.firstOrNull { it.id == profile.cityId }?.areas?.firstOrNull { it.id == profile.areaId }
            ?: cities.asSequence().flatMap { it.areas.asSequence() }.firstOrNull { it.id == profile.areaId }
        if (area != null) return Viewer(profile, minRate, area.lat, area.lng)
        val city = cities.firstOrNull { it.id == profile.cityId } ?: return null
        return Viewer(profile, minRate, city.lat, city.lng)
    }

    /** Signed in: gates, fit score, diversity, exploration, small-market fallback. Guest ([viewer] null): status gates only. */
    fun build(viewer: Viewer?, candidates: List<RoleWithProject>, today: LocalDate, nowMillis: Long): List<DeckCard> =
        if (viewer == null) MatchEngine.guestDeck(candidates, today, nowMillis)
        else MatchEngine.buildDeck(viewer, candidates, today, nowMillis)

    fun filter(cards: List<DeckCard>, filters: DeckFilters, today: LocalDate): List<DeckCard> =
        if (filters == DeckFilters()) cards else MatchEngine.applyFilters(cards, filters, today)

    /** Number of S08 filters that differ from the defaults (for the badge on the Filters icon). */
    fun activeFilterCount(f: DeckFilters): Int = listOf(
        f.paidOnly, f.maxDistanceKm != null, f.remoteOnly, f.dates != DateWindow.ANY,
        f.idVerifiedOwnersOnly, f.footageByDate, f.languages.isNotEmpty(),
    ).count { it }

    /**
     * Which empty state to show, or null when there are cards to swipe.
     * [unfiltered] = deck size before filters, [filtered] = after filters, [visible] = after local swipes.
     * [hasHistory] = the person has applied, saved or swiped before, so an empty deck means "seen it all".
     */
    fun emptyKind(isGuest: Boolean, likesLeft: Int, unfiltered: Int, filtered: Int, visible: Int, hasHistory: Boolean): DeckEmpty? = when {
        !isGuest && likesLeft <= 0 -> DeckEmpty.DAILY_CAP
        visible > 0 -> null
        filtered == 0 && unfiltered > 0 -> DeckEmpty.FILTERED
        unfiltered > 0 -> DeckEmpty.EXHAUSTED
        !isGuest && hasHistory -> DeckEmpty.EXHAUSTED
        else -> DeckEmpty.NOTHING_IN_RANGE
    }

    /** Near misses: candidates that fail exactly one gate, grouped by the fix that would let them in. */
    fun blockers(viewer: Viewer?, candidates: List<RoleWithProject>, today: LocalDate): DeckBlockers {
        val unique = candidates.distinctBy { it.role.id }
        if (viewer == null) return DeckBlockers(nearby = unique.size)
        var outside = 0
        var remote = 0
        var unpaid = 0
        var roles = 0
        var dates = 0
        val widened = WIDEN_FACTOR * MatchEngine.FALLBACK_RADIUS
        val remoteViewer = viewer.copy(profile = viewer.profile.copy(remoteOk = true))
        for (c in unique) {
            val failed = MatchEngine.failedGates(viewer, c, today, MatchEngine.FALLBACK_RADIUS, includeAllRemote = false)
            if (failed.size != 1) continue
            when (failed.first()) {
                Gate.G2_PLACE -> when {
                    !viewer.profile.remoteOk && c.role.remoteOk &&
                        MatchEngine.failedGates(remoteViewer, c, today, 1.0, includeAllRemote = false).isEmpty() -> remote++
                    MatchEngine.failedGates(viewer, c, today, widened, includeAllRemote = false).isEmpty() -> outside++
                    else -> {}
                }
                Gate.G4_MONEY -> unpaid++
                Gate.G1_ROLE -> roles++
                Gate.G3_TIME -> dates++
                else -> {}
            }
        }
        return DeckBlockers(unique.size, outside, remote, unpaid, roles, dates)
    }

    /**
     * How many roles a wider radius would actually add: candidates not already in the deck ([deckIds],
     * including ones swiped this session) that pass every gate at [newRadiusKm]. The empty states only offer
     * "Widen radius" when this is above 0, labelled with it ("Widen to 23 km · +2 roles").
     */
    fun widenGain(viewer: Viewer?, candidates: List<RoleWithProject>, deckIds: Set<String>, today: LocalDate, newRadiusKm: Int?): Int {
        if (viewer == null || newRadiusKm == null) return 0
        val current = viewer.profile.travelRadiusKm.coerceAtLeast(1)
        if (newRadiusKm <= current) return 0
        val multiplier = newRadiusKm.toDouble() / current
        return candidates.distinctBy { it.role.id }.count { c ->
            c.role.id !in deckIds && MatchEngine.failedGates(viewer, c, today, multiplier, includeAllRemote = false).isEmpty()
        }
    }

    /** "Widen radius" target: radius × 1.5, rounded, within 2–150 km. Null when already at the maximum. */
    fun widenedRadius(current: Int): Int? {
        if (current >= MAX_RADIUS_KM) return null
        return (current * WIDEN_FACTOR).roundToInt().coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM).takeIf { it > current }
    }

    /** Language filter options: every project language in the candidates plus the viewer's own, by name. */
    fun languageOptions(candidates: List<RoleWithProject>, me: Profile?): List<String> =
        (candidates.flatMap { it.project.languages } + me?.languages.orEmpty())
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
            .sortedBy { CardText.languageName(it) }

    /** A rebuild is only worth it when something MatchEngine reads has changed (not lastActiveAt). */
    fun gatingKey(p: Profile?): Profile? = p?.copy(lastActiveAt = 0, createdAt = 0)
}
