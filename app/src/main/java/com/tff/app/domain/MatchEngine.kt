package com.tff.app.domain

import com.tff.app.core.Geo
import com.tff.app.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.pow

/**
 * The viewer whose Gigs deck is being built. [minDayRate] is the viewer's OWN private rate, in [currency].
 *
 * [currency] (profile_private.currency: the currency the private rate is in, i.e. the home currency) and [country]
 * default from the viewer's city via [Markets], so callers that only pass the first four values still get the
 * currency (G4) and country (G2 remote) rules. Null = unknown city: those two checks are skipped.
 */
data class Viewer(
    val profile: Profile,
    val minDayRate: Int?,
    val lat: Double,                 // viewer's area centroid
    val lng: Double,
    val currency: String? = Markets.currencyOfCity(profile.cityId),
    val country: String? = Markets.countryOfCity(profile.cityId),
)

/**
 * Country and currency markets (PRD §11.2 reference data). Every launch country has exactly one currency
 * (IN = INR, US = USD, GB = GBP), so a role's currency also tells its country when the city is unknown.
 * Keep in step with DemoCities / supabase/seed.sql (DemoSeedTest checks every listed city).
 */
object Markets {
    private val US = setOf("los_angeles", "atlanta")
    private val GB = setOf("london", "manchester")
    private val IN = setOf(
        "mumbai", "bengaluru", "delhi_ncr", "pune", "hyderabad", "chennai", "kolkata", "kochi",
        "indore", "dehradun", "nagpur", "chandigarh", "ahmedabad", "jaipur", "lucknow", "mysuru",
    )

    /** "IN" / "US" / "GB", or null for a city id this build doesn't know. */
    fun countryOfCity(cityId: String?): String? = when (cityId) {
        null -> null
        in IN -> "IN"
        in US -> "US"
        in GB -> "GB"
        else -> null
    }

    fun currencyOfCountry(country: String?): String? = when (country?.uppercase()) {
        "IN" -> "INR"; "US" -> "USD"; "GB" -> "GBP"; else -> null
    }

    fun countryOfCurrency(currency: String?): String? = when (currency?.uppercase()) {
        "INR" -> "IN"; "USD" -> "US"; "GBP" -> "GB"; else -> null
    }

    fun currencyOfCity(cityId: String?): String? = currencyOfCountry(countryOfCity(cityId))

    /** The country a candidate role is in: its project's city, else its currency. */
    fun countryOf(c: RoleWithProject): String? = countryOfCity(c.project.cityId) ?: countryOfCurrency(c.role.currency)
}

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
    const val DECK_MAX = 60
    const val FALLBACK_BELOW = 10
    const val FALLBACK_RADIUS = 1.5
    const val DIVERSITY_WINDOW = 10
    const val DIVERSITY_PER_PROJECT = 2
    const val EXPLORATION_EVERY = 7
    private const val DAY_MS = 24L * 3600 * 1000

    private val GATE_LANGUAGE_DEPTS = setOf(Dept.CAST, Dept.DIRECTION, Dept.WRITING)
    private val UNPAID_COMP_PERKS = setOf(Perk.MEALS, Perk.TRAVEL_COVERED, Perk.LODGING, Perk.FOOTAGE_BY_DATE,
        Perk.STEP_UP_CREDIT, Perk.SHORT_COMMITMENT, Perk.SCRIPT_READY)

    // ───────────────────────── Stage 0: gates ─────────────────────────

    /** Gates that fail for this candidate (empty = eligible). radiusMultiplier is 1.5 in the small-market fallback. */
    fun failedGates(v: Viewer, c: RoleWithProject, today: LocalDate, radiusMultiplier: Double = 1.0): Set<Gate> =
        failedGates(v, c, today, radiusMultiplier, includeAllRemote = radiusMultiplier > 1.0)

    /** [includeAllRemote]: the fallback also shows remote roles to people who didn't tick "Remote OK". */
    fun failedGates(v: Viewer, c: RoleWithProject, today: LocalDate, radiusMultiplier: Double, includeAllRemote: Boolean): Set<Gate> {
        val u = v.profile
        val r = c.role
        val p = c.project
        val out = mutableSetOf<Gate>()

        // G1 Role
        if (roleMatch(u, r) == RoleMatch.NONE) out += Gate.G1_ROLE

        // G2 Place
        if (!placeOk(v, c, radiusMultiplier, includeAllRemote)) out += Gate.G2_PLACE

        // G3 Time
        if (!timeOk(u, r, p, today)) out += Gate.G3_TIME

        // G4 Money (a min rate is only ever compared within one currency, see moneyOk)
        if (!moneyOk(u.compMode, u.unpaidMustHaves, v.minDayRate, r, p, v.currency)) out += Gate.G4_MONEY

        // G5 Content
        if (p.contentFlags.any { it in u.contentWontDo }) out += Gate.G5_CONTENT

        // G6 Status
        if (!statusOk(r, p, c.owner, today) || p.ownerId == u.id || c.owner.id == u.id) out += Gate.G6_STATUS

        // G7 Language
        if (!languageOk(u.languages, r, p)) out += Gate.G7_LANGUAGE

        return out
    }

    internal enum class RoleMatch { PRIMARY, STEP_UP, SECONDARY, NONE }

    internal fun roleMatch(u: Profile, r: ProjectRole): RoleMatch = when {
        r.roleCode == u.primaryRole -> RoleMatch.PRIMARY
        r.roleCode in u.stepUpRoles && r.stepUpFriendly -> RoleMatch.STEP_UP
        u.secondaryRoles.any { it.code == r.roleCode } -> RoleMatch.SECONDARY
        else -> RoleMatch.NONE
    }

    /** remote(R) = R.remote_ok && U.remote_ok. */
    fun isRemoteFor(u: Profile, r: ProjectRole): Boolean = r.remoteOk && u.remoteOk

    /**
     * Remote work stays inside the viewer's own country (India-wide remote roles for Indian users, US-wide for US
     * users...): pay, tax and contracts are local, and a ₹ budget can't hire in $. Unknown country on either side → true.
     */
    fun sameCountry(v: Viewer, c: RoleWithProject): Boolean {
        val mine = v.country ?: return true
        val theirs = Markets.countryOf(c) ?: return true
        return mine.equals(theirs, ignoreCase = true)
    }

    private fun placeOk(v: Viewer, c: RoleWithProject, mult: Double, includeAllRemote: Boolean): Boolean {
        val u = v.profile
        val r = c.role
        val p = c.project
        // G2 remote: only within the viewer's country (the small-market fallback too).
        if ((isRemoteFor(u, r) || (includeAllRemote && r.remoteOk)) && sameCountry(v, c)) return true
        val d = distanceKm(v, c)
        val radius = u.travelRadiusKm * mult
        if (d <= radius) return true
        val transport = p.transport ?: Transport.NONE
        return transport != Transport.NONE && u.travelIfCovered && (p.shootDays == 1 || p.lodging == true) && d <= 3 * radius
    }

    /** Post roles and remote roles with a delivery date are judged by their deadline, not shoot days. */
    private fun isDeadlineRole(r: ProjectRole): Boolean = r.isPostRole || (r.remoteOk && r.postDeadline != null)

    private fun timeOk(u: Profile, r: ProjectRole, p: Project, today: LocalDate): Boolean {
        if (isDeadlineRole(r)) return !r.postDeadline!!.isBefore(today.plusDays(7))
        val days = p.shootDates
        if (days.isEmpty()) return r.remoteOk
        val avail = availableDays(u, days, p.hoursCap)
        return if (r.isCast) avail == days.distinct().size else avail >= 1
    }

    /** G4 without a known rate currency (reverse gates in ApplicantRanker). Same rule as below with no currency check. */
    internal fun moneyOk(mode: CompMode, mustHaves: List<Perk>, minDayRate: Int?, r: ProjectRole, p: Project): Boolean =
        moneyOk(mode, mustHaves, minDayRate, r, p, rateCurrency = null)

    /**
     * G4 Money (PRD §7.1).
     *  - PAID_ONLY: comp ∈ {PAID_DAY, PAID_FLAT}. If a min day rate is set, the role must be paid in the SAME currency
     *    as that rate ([rateCurrency]) and dayRate(R) ≥ min. A min rate is never compared across currencies: a role in a
     *    different currency fails the gate when a min rate is set (₹3,000 says nothing about $300). Post roles paid
     *    flat have no day rate, so only the currency part applies to them. No min rate → any currency passes.
     *  - PAID_AND_SELECTIVE: paid, or every unpaid must-have is in perks(R, P). The rate is never looked at.
     *  - OPEN_TO_UNPAID: pass.
     * [rateCurrency] null (unknown) or a role with a blank currency skips the currency comparison.
     */
    fun moneyOk(mode: CompMode, mustHaves: List<Perk>, minDayRate: Int?, r: ProjectRole, p: Project, rateCurrency: String?): Boolean = when (mode) {
        CompMode.PAID_ONLY -> r.isPaid && run {
            if (minDayRate == null) return@run true
            if (!sameCurrency(rateCurrency, r.currency)) return@run false
            val rate = dayRate(r, p)
            rate == null || rate >= minDayRate
        }
        CompMode.PAID_AND_SELECTIVE -> r.isPaid || perks(r, p).containsAll(mustHaves)
        CompMode.OPEN_TO_UNPAID -> true
    }

    /** True when either side is unknown/blank, else a case-insensitive match. */
    fun sameCurrency(a: String?, b: String?): Boolean =
        a.isNullOrBlank() || b.isNullOrBlank() || a.trim().equals(b.trim(), ignoreCase = true)

    private fun statusOk(r: ProjectRole, p: Project, owner: OwnerSummary, today: LocalDate): Boolean {
        if (r.status != RoleStatus.OPEN) return false
        if (r.applicationsCount >= r.applicationCap) return false
        if (p.status == ProjectStatus.DRAFT || p.status == ProjectStatus.CANCELLED || p.status == ProjectStatus.WRAPPED) return false
        if (owner.lastActiveAt > 0) {
            val lastActive = Instant.ofEpochMilli(owner.lastActiveAt).atZone(ZoneId.systemDefault()).toLocalDate()
            if (lastActive.isBefore(today.minusDays(14))) return false
        }
        // On-set roles: start date not passed (post roles are covered by G3).
        if (!isDeadlineRole(r)) {
            val start = p.shootStart
            if (start != null && start.isBefore(today)) return false
        }
        return true
    }

    private fun languageOk(userLangs: List<String>, r: ProjectRole, p: Project): Boolean {
        if (Taxonomy.dept(r.roleCode) !in GATE_LANGUAGE_DEPTS) return true
        val pl = p.languages.map { it.lowercase() }.filter { it.isNotBlank() }
        if (pl.isEmpty()) return true
        val ul = userLangs.map { it.lowercase() }.toSet()
        return pl.any { it in ul }
    }

    // ───────────────────────── Helpers ─────────────────────────

    /** perks(R, P): role perks + derived MEALS / TRAVEL_COVERED / LODGING / SHORT_COMMITMENT / SCRIPT_READY. */
    fun perks(role: ProjectRole, project: Project): Set<Perk> {
        val out = role.perks.toMutableSet()
        // Meals, travel, lodging and a short shoot are on-set benefits; a remote post role doesn't get them.
        if (!role.isPostRole) {
            project.meals?.let { if (it.ordinal >= Meals.TWO_PLUS_MEALS.ordinal) out += Perk.MEALS }
            if (project.transport == Transport.MILEAGE_OR_TA_PAID || project.transport == Transport.PRODUCTION_VAN) out += Perk.TRAVEL_COVERED
            if (project.lodging == true) out += Perk.LODGING
            if (project.shootDays in 1..3) out += Perk.SHORT_COMMITMENT
        }
        if (project.scriptStatus == ScriptStatus.LOCKED) out += Perk.SCRIPT_READY
        return out
    }

    /** dayRate(R): amount_max for PAID_DAY / STIPEND; amount_max / shoot_days for on-set PAID_FLAT; null otherwise. */
    fun dayRate(role: ProjectRole, project: Project): Int? {
        val amount = role.amountMax ?: role.amountMin ?: return null
        return when (role.compType) {
            CompType.PAID_DAY, CompType.STIPEND -> amount
            CompType.PAID_FLAT -> if (!role.isPostRole && project.shootDays > 0) amount / project.shootDays else null
            CompType.UNPAID -> null
        }
    }

    /** avail(U, days): shoot days not busy and matching the availability pattern (hoursCap ≤ 5 lets evenings count). */
    fun availableDays(p: Profile, days: List<LocalDate>, hoursCap: Int?): Int = days.distinct().count { d ->
        if (d in p.busyDates) return@count false
        val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        when (p.availabilityPattern) {
            AvailabilityPattern.ANY_DAY -> true
            AvailabilityPattern.WEEKENDS_ONLY -> weekend
            AvailabilityPattern.EVENINGS_AND_WEEKENDS -> weekend || (hoursCap != null && hoursCap <= 5)
            AvailabilityPattern.WEEKDAYS_ONLY -> !weekend
        }
    }

    fun distanceKm(v: Viewer, c: RoleWithProject): Double = Geo.haversineKm(v.lat, v.lng, c.areaLat, c.areaLng)

    // ───────────────────────── Stage 1: fit ─────────────────────────

    fun fit(v: Viewer, c: RoleWithProject, today: LocalDate): FitBreakdown {
        val u = v.profile
        val r = c.role
        val p = c.project

        val role = when (roleMatch(u, r)) {
            RoleMatch.PRIMARY -> 25.0
            RoleMatch.STEP_UP -> if (Goal.STEP_UP in u.goals) 22.0 else 15.0
            RoleMatch.SECONDARY -> when (u.secondaryRoles.first { it.code == r.roleCode }.proficiency) {
                3 -> 18.0; 2 -> 12.0; else -> 6.0
            }
            RoleMatch.NONE -> 0.0
        }

        val perks = perks(r, p)
        var comp = when (r.compType) {
            CompType.PAID_DAY, CompType.PAID_FLAT -> {
                val rate = dayRate(r, p)
                // A min rate is only compared within its own currency; otherwise the no-min-rate base applies.
                val min = v.minDayRate?.takeIf { sameCurrency(v.currency, r.currency) }
                val base = if (min != null && min > 0 && rate != null) 20.0 * min(1.0, rate.toDouble() / min) else 16.0
                val bonus = if (r.paymentTerms == PaymentTerms.ON_WRAP || r.paymentTerms == PaymentTerms.HALF_UPFRONT) 2.0 else 0.0
                min(20.0, base + bonus)
            }
            CompType.STIPEND -> min(16.0, 8.0 + 2.0 * perks.size)
            CompType.UNPAID -> {
                val counted = perks.intersect(UNPAID_COMP_PERKS).count { pk ->
                    pk != Perk.FOOTAGE_BY_DATE || (r.footageDueWeeks != null && r.footageDueWeeks <= 8)
                }
                min(15.0, 3.0 * counted)
            }
        }
        if (r.compType == CompType.UNPAID && c.paidRoles >= 1) comp -= 5.0
        comp = comp.coerceIn(0.0, 20.0)

        val logistics = if (r.remoteOk && (isRemoteFor(u, r) || r.isPostRole)) 12.0 else {
            val d = distanceKm(v, c)
            val radius = u.travelRadiusKm.coerceAtLeast(1).toDouble()
            var l = 15.0 * (1.0 - min(d / radius, 1.0)).pow(0.7)
            if ((p.transport ?: Transport.NONE) != Transport.NONE) l += 2.0
            if ((p.hoursCap ?: 0) > 12) l -= 3.0
            l.coerceIn(0.0, 15.0)
        }

        val schedule = if (isDeadlineRole(r)) {
            if (!r.postDeadline!!.isBefore(today.plusDays(21))) 10.0 else 6.0
        } else if (p.shootDays == 0) 6.0 else {
            val avail = availableDays(u, p.shootDates, p.hoursCap)
            var s = 10.0 * avail / p.shootDays
            if (p.dateCertainty == DateCertainty.LOCKED) s = min(10.0, s + 2.0)
            val start = p.shootStart
            if (p.dateCertainty == DateCertainty.TENTATIVE && start != null && start.isBefore(today.plusDays(7))) s -= 3.0
            s.coerceIn(0.0, 10.0)
        }

        val cred = credScore(c)

        val genres = p.genres.map { it.lowercase().trim() }.toSet()
        val prefs = u.genresPref.map { it.lowercase().trim() }.toSet()
        val genreScore = if (prefs.isEmpty()) 2.5 else 5.0 * jaccard(genres, prefs)
        val ambitionFits = when (p.ambition) {
            Ambition.FESTIVAL -> Goal.FESTIVAL_CREDIT in u.goals
            Ambition.PORTFOLIO -> Goal.REEL_FOOTAGE in u.goals
            Ambition.CLIENT_PAID -> Goal.PAID_WORK in u.goals
            Ambition.FOR_FUN -> Goal.FUN_COMMUNITY in u.goals
            Ambition.DISTRIBUTION -> false
        }
        val formatScore = if (u.formatsPref.isEmpty()) 1.0 else if (p.format in u.formatsPref) 2.0 else 0.0
        val creative = (genreScore + (if (ambitionFits) 3.0 else 0.0) + formatScore).coerceIn(0.0, 10.0)

        val level = levelScore(r.levelWanted, u.level, Goal.STEP_UP in u.goals)

        return FitBreakdown(role, comp, logistics, schedule, cred, creative, level)
    }

    /** cred (0-15): ID 3 + min(5, shoots) + 4 × deliverable rate (2 if n<3) + 2 script locked + 1 if ≥2 people attached. */
    fun credScore(c: RoleWithProject): Double {
        val o = c.owner
        val rate = o.reputation.ownerDeliverableRate
        var s = (if (o.idVerified) 3.0 else 0.0) +
            min(5, o.reputation.shootsCompleted.coerceAtLeast(0)).toDouble() +
            (if (rate == null) 2.0 else 4.0 * rate) +
            (if (c.project.scriptStatus == ScriptStatus.LOCKED) 2.0 else 0.0) +
            (if (c.team.count { !it.isOwner } >= 2) 1.0 else 0.0)
        s = s.coerceIn(0.0, 15.0)
        return s
    }

    internal fun levelScore(wanted: Level?, have: Level, wantsStepUp: Boolean): Double {
        if (wanted == null || wanted == have) return 5.0
        val diff = wanted.ordinal - have.ordinal
        return when (diff) {
            1 -> if (wantsStepUp) 4.0 else 2.0
            -1 -> 3.0
            else -> 0.0
        }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        val inter = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    // ───────────────────────── Stage 2: multipliers ─────────────────────────

    /** m_resp: closure rate (n≥5, else null): <0.5 → 0.70; ≥0.9 → 1.05; else 1.0. */
    fun mResp(o: OwnerSummary): Double {
        val r = o.closureRate14d ?: return 1.0
        return when { r < 0.5 -> 0.70; r >= 0.9 -> 1.05; else -> 1.0 }
    }

    /** m_fresh: published ≤72h → 1.05; owner inactive >7d → 0.80; else 1.0. */
    fun mFresh(c: RoleWithProject, nowMillis: Long): Double {
        val pub = c.project.publishedAt
        if (pub != null && pub > 0 && nowMillis - pub <= 72 * 3600_000L) return 1.05
        val last = c.owner.lastActiveAt
        if (last > 0 && nowMillis - last > 7 * DAY_MS) return 0.80
        return 1.0
    }

    /** m_urgent: start ≤ today+10 and every shoot day free → 1.10. */
    fun mUrgent(v: Viewer, c: RoleWithProject, today: LocalDate): Double {
        val p = c.project
        if (isDeadlineRole(c.role)) return 1.0
        val start = p.shootStart ?: return 1.0
        if (start.isAfter(today.plusDays(10))) return 1.0
        return if (p.shootDays > 0 && availableDays(v.profile, p.shootDates, p.hoursCap) == p.shootDays) 1.10 else 1.0
    }

    /** fit × m_resp × m_fresh × m_urgent. */
    fun score(v: Viewer, c: RoleWithProject, today: LocalDate, nowMillis: Long): Double =
        fit(v, c, today).total * mResp(c.owner) * mFresh(c, nowMillis) * mUrgent(v, c, today)

    // ───────────────────────── Deck assembly ─────────────────────────

    /** Gates → score → sort → top 60 → diversity (≤2 per project in first 10) → exploration every 7th → fallback (<10 cards). */
    fun buildDeck(v: Viewer, candidates: List<RoleWithProject>, today: LocalDate, nowMillis: Long): List<DeckCard> {
        val unique = candidates.distinctBy { it.role.id }
        fun card(c: RoleWithProject, outside: Boolean): DeckCard {
            val f = fit(v, c, today)
            val s = f.total * mResp(c.owner) * mFresh(c, nowMillis) * mUrgent(v, c, today)
            return DeckCard(c, s, f, if (c.role.remoteOk) null else distanceKm(v, c), outsideRadius = outside)
        }

        val passing = unique.filter { failedGates(v, it, today).isEmpty() }
        var cards = passing.map { card(it, false) }.sortedWith(cardOrder)
        if (passing.size < FALLBACK_BELOW) {
            val ids = passing.map { it.role.id }.toSet()
            val extra = unique.filter { it.role.id !in ids && failedGates(v, it, today, FALLBACK_RADIUS, includeAllRemote = true).isEmpty() }
            cards = cards + extra.map { card(it, true) }.sortedWith(cardOrder)
        }
        cards = cards.take(DECK_MAX)
        return explore(diversify(cards))
    }

    private val cardOrder: Comparator<DeckCard> = compareByDescending<DeckCard> { it.score }
        .thenBy { it.item.project.shootStart ?: it.item.role.postDeadline ?: LocalDate.MAX }
        .thenBy { it.item.role.id }

    /** At most 2 roles from the same project within the first 10 cards; the rest keep their order. */
    internal fun diversify(cards: List<DeckCard>): List<DeckCard> {
        val remaining = cards.toMutableList()
        val out = ArrayList<DeckCard>(cards.size)
        while (remaining.isNotEmpty()) {
            val pick = if (out.size < DIVERSITY_WINDOW) {
                remaining.firstOrNull { c -> out.count { it.item.project.id == c.item.project.id } < DIVERSITY_PER_PROJECT } ?: remaining.first()
            } else remaining.first()
            remaining.remove(pick)
            out += pick
        }
        return out
    }

    /** Every 7th slot goes to the best remaining role from an owner with no completed shoots. */
    internal fun explore(cards: List<DeckCard>): List<DeckCard> {
        val out = cards.toMutableList()
        var slot = EXPLORATION_EVERY - 1
        while (slot < out.size) {
            val atSlot = out[slot]
            if (!isNewOwner(atSlot)) {
                val idx = (slot + 1 until out.size).firstOrNull { i ->
                    val c = out[i]
                    isNewOwner(c) && (slot >= DIVERSITY_WINDOW ||
                        out.subList(0, slot).count { it.item.project.id == c.item.project.id } < DIVERSITY_PER_PROJECT)
                }
                if (idx != null) {
                    val c = out.removeAt(idx)
                    out.add(slot, c.copy(exploration = true))
                }
            }
            slot += EXPLORATION_EVERY
        }
        return out
    }

    private fun isNewOwner(c: DeckCard) = c.item.owner.reputation.shootsCompleted == 0

    /** Guests: status/date gates only, ranked by freshness + owner credibility. */
    fun guestDeck(candidates: List<RoleWithProject>, today: LocalDate, nowMillis: Long): List<DeckCard> {
        val cards = candidates.distinctBy { it.role.id }
            .filter { statusOk(it.role, it.project, it.owner, today) && (!isDeadlineRole(it.role) || !it.role.postDeadline!!.isBefore(today.plusDays(7))) }
            .map { c ->
                val pub = c.project.publishedAt
                val age = if (pub == null || pub <= 0) Long.MAX_VALUE else nowMillis - pub
                val fresh = when {
                    age <= 72 * 3600_000L -> 5.0
                    age <= 7 * DAY_MS -> 3.0
                    age <= 14 * DAY_MS -> 1.0
                    else -> 0.0
                }
                DeckCard(c, (credScore(c) + fresh) * mResp(c.owner), null, null)
            }
            .sortedWith(cardOrder)
            .take(DECK_MAX)
        return diversify(cards)
    }

    /** Apply S08 filters on top of a built deck. */
    fun applyFilters(cards: List<DeckCard>, f: DeckFilters, today: LocalDate): List<DeckCard> = cards.filter { c ->
        val r = c.item.role
        val p = c.item.project
        if (f.paidOnly && !r.isPaid) return@filter false
        if (f.remoteOnly && !r.remoteOk) return@filter false
        f.maxDistanceKm?.let { max -> val d = c.distanceKm; if (d != null && d > max) return@filter false }
        if (f.idVerifiedOwnersOnly && !c.item.owner.idVerified) return@filter false
        if (f.footageByDate && Perk.FOOTAGE_BY_DATE !in r.perks) return@filter false
        if (f.languages.isNotEmpty()) {
            val want = f.languages.map { it.lowercase() }.toSet()
            if (p.languages.none { it.lowercase() in want }) return@filter false
        }
        // Date windows are about shoot days; remote post roles (no shoot days) only show under "Any".
        if (f.dates != DateWindow.ANY && isDeadlineRole(r)) return@filter false
        when (f.dates) {
            DateWindow.ANY -> true
            DateWindow.THIS_WEEK -> p.shootDates.any { !it.isBefore(today) && !it.isAfter(today.plusDays(6)) }
            DateWindow.NEXT_30_DAYS -> p.shootDates.any { !it.isBefore(today) && !it.isAfter(today.plusDays(30)) }
        }
    }
}
