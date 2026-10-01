package com.tff.app.domain

import com.tff.app.model.*
import java.time.LocalDate

/** The viewer whose Gigs deck is being built. [minDayRate] is the viewer's OWN private rate. */
data class Viewer(
    val profile: Profile,
    val minDayRate: Int?,
    val lat: Double,                 // viewer's area centroid
    val lng: Double,
)

enum class Gate { G1_ROLE, G2_PLACE, G3_TIME, G4_MONEY, G5_CONTENT, G6_STATUS, G7_LANGUAGE }

data class FitBreakdown(
    val role: Double, val comp: Double, val logistics: Double, val schedule: Double,
    val cred: Double, val creative: Double, val level: Double,
) { val total: Double get() = role + comp + logistics + schedule + cred + creative + level }

data class DeckCard(
    val item: RoleWithProject,
    val score: Double,
    val fit: FitBreakdown?,
    val distanceKm: Double?,         // null for remote roles
    val outsideRadius: Boolean = false,
    val exploration: Boolean = false,
)

/** PRD §7.1–7.3. Pure Kotlin, no android.* imports. */
object MatchEngine {
    /** Gates that fail for this candidate (empty = eligible). radiusMultiplier is 1.5 in the small-market fallback. */
    fun failedGates(v: Viewer, c: RoleWithProject, today: LocalDate, radiusMultiplier: Double = 1.0): Set<Gate> = TODO()
    /** perks(R, P): role perks + derived MEALS / TRAVEL_COVERED / LODGING / SHORT_COMMITMENT / SCRIPT_READY. */
    fun perks(role: ProjectRole, project: Project): Set<Perk> = TODO()
    /** dayRate(R): amount_max for PAID_DAY / STIPEND; amount_max / shoot_days for on-set PAID_FLAT; null otherwise. */
    fun dayRate(role: ProjectRole, project: Project): Int? = TODO()
    /** avail(U, days): shoot days not busy and matching the availability pattern (hoursCap ≤ 5 lets evenings count). */
    fun availableDays(p: Profile, days: List<LocalDate>, hoursCap: Int?): Int = TODO()
    fun distanceKm(v: Viewer, c: RoleWithProject): Double = TODO()
    fun fit(v: Viewer, c: RoleWithProject, today: LocalDate): FitBreakdown = TODO()
    /** fit × m_resp × m_fresh × m_urgent. */
    fun score(v: Viewer, c: RoleWithProject, today: LocalDate, nowMillis: Long): Double = TODO()
    /** Gates → score → sort → top 60 → diversity (≤2 per project in first 10) → exploration every 7th → fallback (<10 cards). */
    fun buildDeck(v: Viewer, candidates: List<RoleWithProject>, today: LocalDate, nowMillis: Long): List<DeckCard> = TODO()
    /** Guests: status/date gates only, ranked by freshness + owner credibility. */
    fun guestDeck(candidates: List<RoleWithProject>, today: LocalDate, nowMillis: Long): List<DeckCard> = TODO()
    /** Apply S08 filters on top of a built deck. */
    fun applyFilters(cards: List<DeckCard>, f: DeckFilters, today: LocalDate): List<DeckCard> = TODO()
}
