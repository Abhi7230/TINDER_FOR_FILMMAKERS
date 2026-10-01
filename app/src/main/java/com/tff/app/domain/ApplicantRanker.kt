package com.tff.app.domain

import com.tff.app.model.*
import java.time.LocalDate
import kotlin.math.min

data class ApplicantInput(
    val application: Application?,          // null for Scout candidates
    val card: ProfileCard,
    val rateFit: Double,                     // computed server-side / by DemoRepo, never from the owner's device
    val otherAnswers: List<String> = emptyList(),   // this person's answers on other roles (near-duplicate check)
    val headshotUrl: String? = null,
)

/** Applicant score components (PRD §7.4). Max: 25 + 15 + 10 + 10 + 10 + 15 + 5 + 10 = 100. */
data class ApplicantBreakdown(
    val roleTier: Double, val avail: Double, val distance: Double, val rate: Double,
    val reel: Double, val reliability: Double, val answer: Double, val trust: Double,
) { val total: Double get() = roleTier + avail + distance + rate + reel + reliability + answer + trust }

/** PRD §7.4. Pure Kotlin. */
object ApplicantRanker {
    const val NEAR_DUPLICATE = 0.9
    private val BEGINNER_LEVELS = setOf(Level.FIRST_PROJECT, Level.BUILDING_REEL)
    private val WORK_LINK_KINDS = setOf(LinkKind.PORTFOLIO, LinkKind.CREDIT, LinkKind.IMDB, LinkKind.OTHER)

    /** rate_fit = min(1, amount_max / min_day_rate); 1 if no private rate or unpaid role. Runs where the rate lives. */
    fun rateFit(role: ProjectRole, project: Project, minDayRate: Int?): Double {
        if (minDayRate == null || minDayRate <= 0 || role.compType == CompType.UNPAID) return 1.0
        val amount = MatchEngine.dayRate(role, project) ?: role.amountMax ?: role.amountMin ?: return 1.0
        return min(1.0, amount.toDouble() / minDayRate).coerceAtLeast(0.0)
    }

    /** The eight components, for the score and the reason chips. */
    fun breakdown(item: RoleWithProject, input: ApplicantInput, ownerLevel: Level, today: LocalDate): ApplicantBreakdown {
        val r = item.role
        val p = item.project
        val person = input.card.profile
        val rep = input.card.reputation

        val roleTier = when {
            r.roleCode == person.primaryRole -> {
                val wanted = r.levelWanted
                when {
                    wanted == null || wanted == person.level -> 25.0
                    kotlin.math.abs(wanted.ordinal - person.level.ordinal) == 1 -> 20.0
                    else -> 15.0
                }
            }
            r.roleCode in person.stepUpRoles -> 18.0
            person.secondaryRoles.any { it.code == r.roleCode } -> 15.0
            else -> 0.0
        }

        val avail = if (r.isPostRole || p.shootDays == 0) 15.0
        else 15.0 * MatchEngine.availableDays(person, p.shootDates, p.hoursCap) / p.shootDays

        val d = input.card.distanceKm
        val distance = when {
            r.remoteOk -> 10.0
            d == null -> 5.0
            else -> 10.0 * (1.0 - min(d / 25.0, 1.0))
        }

        val rate = 10.0 * input.rateFit.coerceIn(0.0, 1.0)

        val links = person.workLinks
        var reel = when {
            links.any { it.kind == LinkKind.REEL && it.roleCode == r.roleCode } -> 10.0
            links.any { it.kind == LinkKind.REEL } -> 5.0
            links.any { it.kind in WORK_LINK_KINDS } -> 4.0
            else -> 0.0
        }
        if (r.levelWanted in BEGINNER_LEVELS || ownerLevel == Level.FIRST_PROJECT) reel = maxOf(reel, 5.0)

        val ratio = reliabilityRatio(r, rep)
        val reliability = if (ratio == null) 8.0 else {
            val h = rep.replyHoursMedian
            val resp = when { h == null -> 0.0; h < 24 -> 5.0; h < 72 -> 3.0; else -> 0.0 }
            10.0 * ratio.value + resp
        }

        val answer = answerScore(r, input)

        val credits = maxOf(rep.confirmedCredits, input.card.confirmedCredits.size)
        val trust = (if (person.idVerified) 4.0 else 0.0) + (if (person.phoneVerified) 2.0 else 0.0) + min(4, credits).toDouble()

        return ApplicantBreakdown(roleTier, avail, distance, rate, reel, reliability, answer, trust)
    }

    /** On-set roles use "Showed up"; post roles use "Delivered on time". */
    private fun reliabilityRatio(r: ProjectRole, rep: Reputation): Ratio? =
        if (r.isPostRole) rep.deliveredOnTime ?: rep.showedUp else rep.showedUp ?: rep.deliveredOnTime

    /** 5 for a real answer; 0 for none or a near-copy of their other answers. Roles without a question score 5 for everyone. */
    private fun answerScore(r: ProjectRole, input: ApplicantInput): Double {
        if (r.screeningQuestion.isNullOrBlank()) return 5.0
        val a = input.application?.screeningAnswer?.trim().orEmpty()
        if (a.isEmpty()) return 0.0
        return if (isNearDuplicate(a, input.otherAnswers)) 0.0 else 5.0
    }

    fun isNearDuplicate(answer: String, others: List<String>): Boolean =
        others.any { it.isNotBlank() && similarity(answer, it) >= NEAR_DUPLICATE }

    /** Score 0..100 + top-3 reason chips ("Showed up 9/9", "Speaks Kannada", "Has kit"). */
    fun score(item: RoleWithProject, input: ApplicantInput, ownerLevel: Level, today: LocalDate): Pair<Double, List<String>> {
        val b = breakdown(item, input, ownerLevel, today)
        return b.total to reasons(item, input, b)
    }

    private data class Reason(val text: String, val strength: Double, val priority: Int)

    /**
     * Facts that set this person apart. Days, distance, kit fee and rate fit are left out: the Person Card's
     * fit line already shows them (PRD §6.5).
     */
    private fun reasons(item: RoleWithProject, input: ApplicantInput, b: ApplicantBreakdown): List<String> {
        val r = item.role
        val p = item.project
        val person = input.card.profile
        val rep = input.card.reputation
        val out = mutableListOf<Reason>()

        // A strong record is a reason to pick someone; a weak one stays on their profile, not in the chips.
        reliabilityRatio(r, rep)?.takeIf { it.value >= 0.8 }?.let { ratio ->
            val label = if (r.isPostRole && rep.deliveredOnTime != null) "Delivered on time" else if (rep.showedUp != null) "Showed up" else "Delivered on time"
            out += Reason("$label $ratio", ratio.value, 0)
        }
        // Language match on a non-English project language ("Speaks Kannada").
        val shared = p.languages.map { it.lowercase() }.filter { it != "en" && it in person.languages.map { l -> l.lowercase() } }
        shared.firstOrNull()?.let { out += Reason("Speaks ${CardText.languageName(it)}", 0.85, 1) }
        // Gear for gear departments.
        if (Taxonomy.dept(r.roleCode) in Taxonomy.GEAR_DEPTS && person.gear.isNotEmpty()) {
            val boom = person.gear.any { it.contains("boom op", ignoreCase = true) || it.contains("boom operator", ignoreCase = true) }
            out += Reason(if (boom) "Has boom op" else "Has kit", 0.8, 2)
        }
        if (b.reel >= 10.0) out += Reason("Reel for this role", 1.0, 3)
        else if (b.reel >= 5.0 && person.workLinks.any { it.kind == LinkKind.REEL }) out += Reason("Has a reel", 0.5, 3)
        if (r.roleCode in person.stepUpRoles && r.roleCode != person.primaryRole) {
            out += Reason("Stepping up to ${CardText.roleName(r.roleCode)}", 0.75, 6)
        }
        if (person.idVerified) out += Reason("ID verified", 0.7, 7)
        val credits = maxOf(rep.confirmedCredits, input.card.confirmedCredits.size)
        if (credits > 0) out += Reason(if (credits == 1) "1 confirmed credit" else "$credits confirmed credits", min(1.0, credits / 4.0) * 0.8, 8)
        if (!r.screeningQuestion.isNullOrBlank() && b.answer >= 5.0) out += Reason("Answered the question", 0.55, 10)

        return out.sortedWith(compareByDescending<Reason> { it.strength }.thenBy { it.priority })
            .map { it.text }.distinct().take(3)
    }

    /** Sorted descending; ties → earlier application. */
    fun rank(item: RoleWithProject, inputs: List<ApplicantInput>, ownerLevel: Level, today: LocalDate): List<ApplicantView> {
        val p = item.project
        val total = if (item.role.isPostRole) 0 else p.shootDays
        return inputs.map { input ->
            val (s, reasons) = score(item, input, ownerLevel, today)
            val availDays = if (item.role.isPostRole) 0 else MatchEngine.availableDays(input.card.profile, p.shootDates, p.hoursCap)
            ApplicantView(
                application = input.application, card = input.card, score = s, reasons = reasons,
                rateFit = input.rateFit, availDays = availDays, totalDays = total,
                headshotUrl = if (item.role.isCast) input.headshotUrl else null,
            )
        }.sortedWith(
            compareByDescending<ApplicantView> { it.score }
                .thenBy { it.application?.createdAt ?: Long.MAX_VALUE }
                .thenBy { it.card.profile.id }
        )
    }

    /**
     * Reverse gates for Scout / Rebook / backup asks: null if [candidate] may be asked onto [item], else a
     * human reason ("Kabir only takes paid work right now"). Cast roles: only allowed when [isKeepList].
     */
    fun askBlockReason(item: RoleWithProject, candidate: Profile, candidateMinRate: Int?, today: LocalDate, isKeepList: Boolean): String? {
        val r = item.role
        val p = item.project
        val name = firstName(candidate)
        if (r.isCast && !isKeepList) return "Actors apply to you. Calltime doesn't let anyone browse actors."
        if (candidate.id == p.ownerId) return "That's you. Pick someone else."
        if (r.status != RoleStatus.OPEN) return "This role isn't open any more."
        if (!r.isPostRole && p.shootStart?.isBefore(today) == true) return "This shoot has already started."

        // Reverse G4 (money)
        if (!MatchEngine.moneyOk(candidate.compMode, candidate.unpaidMustHaves, candidateMinRate, r, p)) {
            return when (candidate.compMode) {
                CompMode.PAID_ONLY -> if (!r.isPaid) "$name only takes paid work right now." else "This rate doesn't work for $name right now."
                CompMode.PAID_AND_SELECTIVE -> {
                    val missing = candidate.unpaidMustHaves.filter { it !in MatchEngine.perks(r, p) }
                    val list = missing.joinToString(", ") { CardText.perkLabel(it).lowercase() }
                    "$name does unpaid work only with: $list."
                }
                CompMode.OPEN_TO_UNPAID -> null
            }
        }
        // Reverse G5 (content). Never say which flag: content_wont_do is private.
        if (p.contentFlags.any { it in candidate.contentWontDo }) return "This project has content $name has ruled out."

        // Reverse G1 (role)
        if (MatchEngine.roleMatch(candidate, r) == MatchEngine.RoleMatch.NONE) {
            return "$name doesn't list ${CardText.roleName(r)} as one of their roles."
        }
        // Reverse G3 (time)
        if (r.isPostRole) {
            if (r.postDeadline!!.isBefore(today.plusDays(7))) return "The delivery date is less than a week away."
        } else if (p.shootDates.isNotEmpty()) {
            val avail = MatchEngine.availableDays(candidate, p.shootDates, p.hoursCap)
            val need = if (r.isCast) p.shootDates.distinct().size else 1
            if (avail < need) return "$name isn't free on the shoot dates."
        }
        // Reverse G7 (language)
        if (Taxonomy.dept(r.roleCode) in setOf(Dept.CAST, Dept.DIRECTION, Dept.WRITING) && p.languages.isNotEmpty()) {
            val ul = candidate.languages.map { it.lowercase() }.toSet()
            if (p.languages.none { it.lowercase() in ul }) return "$name doesn't speak ${CardText.languageNames(p.languages)}."
        }
        return null
    }

    private fun firstName(p: Profile): String = p.displayName.trim().split(Regex("\\s+")).firstOrNull()?.ifBlank { null } ?: "This person"

    /** Normalised Levenshtein similarity in 0..1. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() && y.isEmpty()) return 1.0
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val dist = levenshtein(x, y)
        return 1.0 - dist.toDouble() / maxOf(x.length, y.length)
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), "").replace(Regex("\\s+"), " ").trim()

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
