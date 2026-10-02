package com.tff.app.domain

import com.tff.app.core.Money
import com.tff.app.model.*
import java.time.LocalDate

enum class Severity { BLOCK, WARN, AUTO }

/** One rule outcome. [roleId] / [field] let the wizard link a red item back to its input. */
data class RuleResult(
    val rule: String,                // "R01".."R22"
    val severity: Severity,
    val message: String,             // user-facing fix-it text (PRD §5.3 copy)
    val roleId: String? = null,
    val field: String? = null,       // e.g. "amount", "perks", "shootDates", "contentFlags"
)

/** PRD §5.3. Pure Kotlin. Mirrored by listing_rules_ok() in Supabase. */
object ListingRules {

    // User-facing copy (PRD §5.3, Appendix D: say how to fix it).
    const val MSG_R01_AMOUNT = "Add pay, or mark it unpaid and say what people get."
    const val MSG_R01_RANGE = "The lower amount is above the higher one. Fix the range."
    const val MSG_R01_TERMS = "Say when people get paid: on wrap, half upfront, or within 7, 15 or 30 days."
    const val MSG_R02_PERKS = "Unpaid roles need at least 2 things people get: footage by a date, travel, a step-up credit, 2+ meals or a place to stay."
    const val MSG_R02_MEALS = "If you can't pay people, feed people. Unpaid shoots need at least one meal a day."
    const val MSG_R03 = "Unpaid actors work for footage. Promise their scenes by a date, within 26 weeks."
    const val MSG_R05_DATES = "Add your shoot dates."
    const val MSG_R05_PAST = "Shoot dates can't be in the past. Pick dates from today on."
    const val MSG_R05_HOURS = "Set the longest shoot day, between 2 and 16 hours."
    const val MSG_R05_CERTAINTY = "Say whether the dates are locked or tentative."
    const val MSG_R05_MEALS = "Say what food is on set, even if it's none."
    const val MSG_R05_TRANSPORT = "Say how people get to set."
    const val MSG_R05_TA = "Add the travel allowance per day."
    const val MSG_R05_LOCATION = "Say where you're shooting: studio, outdoors, a home, an office or a mix."
    const val MSG_R05_DEADLINE = "Set a delivery date for this remote role, at least a week from today."
    const val MSG_R06 = "Long days lose crew. Can you cap at 10-12?"
    const val MSG_R07 = "Tick any content that applies, or \"None of these\"."
    const val MSG_R08_IC = "Say whether an intimacy coordinator will be on set for these scenes."
    const val MSG_R08_SAFETY = "Describe how these scenes will be handled safely (at least 20 characters). No nudity at auditions is fixed."
    const val MSG_R09_COORD = "Say whether a stunt coordinator or armourer will be on set."
    const val MSG_R09_SAFETY = "Describe the safety plan for stunts or prop weapons."
    const val MSG_R10 = "Minors on set need a parent or guardian present. Cast roles are 18+ only."
    const val MSG_R11 = "Choose how auditions happen: self-tape, video call, or a studio or public space with others present."
    const val MSG_R12 = "Calltime never allows charging cast or crew."
    const val MSG_R13 = "Unpaid roles can't ask for spec work (demos, test edits, prescreens). Ask about past work instead."
    const val MSG_R14 = "Keep contact in Calltime until you're booked."
    const val MSG_R15 = "Say what people get instead."
    const val MSG_R15_DEFERRED = "Deferred pay shows as Unpaid. Say what people get now."
    const val MSG_R16 = "This reads like 3 jobs. Split it into roles?"
    const val MSG_R17 = "Own gear with no kit fee. The card will say \"Own gear, no kit fee\". Add a kit fee if you can."
    const val MSG_R18_TITLE = "Give the project a title of 2-60 characters."
    const val MSG_R18_LOGLINE = "Write a logline of 20-200 characters: who, what they want, what's in the way."
    const val MSG_R18_LANGUAGE = "Pick at least one language."
    const val MSG_R18_ROLES = "Add at least one role."
    const val MSG_R19 = "Projects with intimate scenes need an ID-verified owner. Book a 5-minute check."
    const val MSG_R20 = "Composers and sound designers need a picture-lock date to judge the job."
    const val MSG_R21 = "That's under 3 weeks after picture lock. Composers and sound designers need time."
    const val MSG_R22 = "Editors and composers do better work when they're in early. Add them now as remote roles?"
    const val MSG_R23_ZERO_BUDGET = "Some roles here are paid, so the card won't say \"Honest zero-budget\". Say it only when nobody is paid."

    fun r04Message(floor: Int, currency: String) =
        "Below ${Money.format(floor, currency)}/day here, so the card shows it as Stipend, not Paid."

    private val INTIMATE = setOf(ContentFlag.NUDITY, ContentFlag.SIMULATED_SEX, ContentFlag.INTIMACY_SCENE)
    private val RISKY = setOf(ContentFlag.STUNTS_FIGHTS, ContentFlag.FIREARMS_PROPS)
    private val R02_PERKS = setOf(Perk.TRAVEL_COVERED, Perk.LODGING, Perk.FOOTAGE_BY_DATE, Perk.FINAL_FILM_BY_DATE, Perk.STEP_UP_CREDIT, Perk.MEALS)

    private val I = setOf(RegexOption.IGNORE_CASE)
    private val SPEC_WORK = Regex("""\b(?:demos?|test\s*(?:edit|cut|shoot|mix|score|grade)s?|pre-?screens?|custom|sample\s*(?:cut|edit|mix|score)s?|spec\s+(?:work|edit|piece))\b""", I)
    private val EUPHEMISM = Regex("""\b(?:collabs?|collaborations?|collaborative|exposure|passion\s*projects?|great\s*opportunit(?:y|ies)|deferred|deferral|profit[\s-]*shar(?:e|ing))\b""", I)
    private val EUPHEMISM_FIELDS = setOf("title", "logline", "storySetting", "creditTitle", "screeningQuestion", "scopeIncludes", "scopeExcludes", "roleDetails")
    private val DEFERRED = Regex("""\b(?:deferred|deferral|profit[\s-]*shar(?:e|ing))\b""", I)

    /** An on-set role is anything that isn't a remote POST / MUSIC / WRITING role. */
    fun isOnSet(r: ProjectRole): Boolean = !(r.remoteOk && Taxonomy.dept(r.roleCode) in Taxonomy.POST_DEPTS)

    fun check(d: ProjectDraft, owner: Profile, city: City, today: LocalDate): List<RuleResult> {
        val p = d.project
        val roles = d.roles
        val out = mutableListOf<RuleResult>()
        val onSetRoles = roles.filter { isOnSet(it) }
        val hasOnSet = onSetRoles.isNotEmpty()

        // R01 pay is structured
        for (r in roles) {
            if (r.compType == CompType.UNPAID) continue
            val max = r.amountMax ?: r.amountMin
            if (max == null || max <= 0) out += RuleResult("R01", Severity.BLOCK, MSG_R01_AMOUNT, r.id, "amount")
            else if (r.amountMin != null && r.amountMax != null && r.amountMin > r.amountMax) out += RuleResult("R01", Severity.BLOCK, MSG_R01_RANGE, r.id, "amount")
            if (r.paymentTerms == null) out += RuleResult("R01", Severity.BLOCK, MSG_R01_TERMS, r.id, "paymentTerms")
        }

        // R02 unpaid on-set: ≥2 perks + ≥1 meal
        for (r in onSetRoles.filter { it.compType == CompType.UNPAID }) {
            val perks = MatchEngine.perks(r, p).intersect(R02_PERKS)
            if (perks.size < 2) out += RuleResult("R02", Severity.BLOCK, MSG_R02_PERKS, r.id, "perks")
            val meals = p.meals
            if (meals == null || meals.ordinal < Meals.ONE_MEAL.ordinal) out += RuleResult("R02", Severity.BLOCK, MSG_R02_MEALS, r.id, "meals")
        }

        // R03 unpaid actor: footage by a date within 26 weeks
        for (r in roles.filter { it.roleCode == RoleCode.ACTOR && it.compType == CompType.UNPAID }) {
            val w = r.footageDueWeeks
            if (Perk.FOOTAGE_BY_DATE !in r.perks || w == null || w < 1 || w > 26) {
                out += RuleResult("R03", Severity.BLOCK, MSG_R03, r.id, "footageDueWeeks")
            }
        }

        // R23 "Honest zero-budget" only when no role is paid (auto: the label is dropped)
        if (p.honestZeroBudget && !zeroBudgetAllowed(roles)) out += RuleResult("R23", Severity.AUTO, MSG_R23_ZERO_BUDGET, field = "honestZeroBudget")

        // R04 below the paid floor → Stipend (auto)
        for (r in roles) {
            if (belowFloor(r, p, city)) out += RuleResult("R04", Severity.AUTO, r04Message(city.paidFloorDay, r.currency), r.id, "amount")
        }

        // R05 logistics (on-set) / delivery dates (post roles)
        if (hasOnSet) {
            val dates = p.shootDates
            if (dates.isEmpty()) out += RuleResult("R05", Severity.BLOCK, MSG_R05_DATES, field = "shootDates")
            else if (dates.min() < today) out += RuleResult("R05", Severity.BLOCK, MSG_R05_PAST, field = "shootDates")
            val h = p.hoursCap
            if (h == null || h < 2 || h > 16) out += RuleResult("R05", Severity.BLOCK, MSG_R05_HOURS, field = "hoursCap")
            if (p.dateCertainty == null) out += RuleResult("R05", Severity.BLOCK, MSG_R05_CERTAINTY, field = "dateCertainty")
            if (p.meals == null) out += RuleResult("R05", Severity.BLOCK, MSG_R05_MEALS, field = "meals")
            if (p.transport == null) out += RuleResult("R05", Severity.BLOCK, MSG_R05_TRANSPORT, field = "transport")
            else if (p.transport == Transport.MILEAGE_OR_TA_PAID && (p.travelAllowancePerDay ?: 0) <= 0) {
                out += RuleResult("R05", Severity.BLOCK, MSG_R05_TA, field = "travelAllowancePerDay")
            }
            if (p.shootLocationType == null) out += RuleResult("R05", Severity.BLOCK, MSG_R05_LOCATION, field = "shootLocationType")
        }
        for (r in roles.filterNot { isOnSet(it) }) {
            val dl = r.postDeadline
            if (dl == null || dl < today.plusDays(7)) out += RuleResult("R05", Severity.BLOCK, MSG_R05_DEADLINE, r.id, "postDeadline")
        }

        // R06 long days
        if (hasOnSet && (p.hoursCap ?: 0) > 12) out += RuleResult("R06", Severity.WARN, MSG_R06, field = "hoursCap")

        // R07 content answered
        if (p.contentFlags.isEmpty() && !p.contentNoneConfirmed) out += RuleResult("R07", Severity.BLOCK, MSG_R07, field = "contentFlags")

        // R08 intimacy
        if (p.contentFlags.any { it in INTIMATE }) {
            if (p.intimacyCoordinator == null) out += RuleResult("R08", Severity.BLOCK, MSG_R08_IC, field = "intimacyCoordinator")
            if ((p.safetyMeasures?.trim()?.length ?: 0) < 20) out += RuleResult("R08", Severity.BLOCK, MSG_R08_SAFETY, field = "safetyMeasures")
        }

        // R09 stunts / firearms
        if (p.contentFlags.any { it in RISKY }) {
            if (p.stuntOrArmourer == null) out += RuleResult("R09", Severity.BLOCK, MSG_R09_COORD, field = "stuntOrArmourer")
            if (p.safetyMeasures.isNullOrBlank()) out += RuleResult("R09", Severity.BLOCK, MSG_R09_SAFETY, field = "safetyMeasures")
        }

        // R10 minors
        if (ContentFlag.MINORS_ON_SET in p.contentFlags && p.guardianPresent != true) {
            out += RuleResult("R10", Severity.BLOCK, MSG_R10, field = "guardianPresent")
        }

        // R11 audition mode for cast roles
        if (roles.any { it.isCast } && p.auditionMode == null) out += RuleResult("R11", Severity.BLOCK, MSG_R11, field = "auditionMode")

        // R12 money asks / R14 contact info in any public text
        val texts = publicTexts(d)
        texts.firstOrNull { MessageSafety.isMoney(it.text) }?.let { out += RuleResult("R12", Severity.BLOCK, MSG_R12, it.roleId, it.field) }
        texts.firstOrNull { MessageSafety.hasContact(it.text) }?.let { out += RuleResult("R14", Severity.BLOCK, MSG_R14, it.roleId, it.field) }

        // R13 spec work on unpaid roles
        for (r in roles.filter { it.compType == CompType.UNPAID }) {
            val q = r.screeningQuestion
            if (!q.isNullOrBlank() && SPEC_WORK.containsMatchIn(q)) out += RuleResult("R13", Severity.BLOCK, MSG_R13, r.id, "screeningQuestion")
        }

        // R15 euphemisms on unpaid roles
        val unpaid = roles.filter { it.compType == CompType.UNPAID }
        if (unpaid.isNotEmpty()) {
            val projectHits = texts.filter { it.roleId == null && it.field in EUPHEMISM_FIELDS && EUPHEMISM.containsMatchIn(it.text) }
            if (projectHits.isNotEmpty()) {
                val msg = if (projectHits.any { DEFERRED.containsMatchIn(it.text) }) MSG_R15_DEFERRED else MSG_R15
                out += RuleResult("R15", Severity.WARN, msg, null, projectHits.first().field)
            }
            for (r in unpaid) {
                val hits = texts.filter { it.roleId == r.id && it.field in EUPHEMISM_FIELDS && EUPHEMISM.containsMatchIn(it.text) }
                if (hits.isNotEmpty()) {
                    val msg = if (hits.any { DEFERRED.containsMatchIn(it.text) }) MSG_R15_DEFERRED else MSG_R15
                    out += RuleResult("R15", Severity.WARN, msg, r.id, hits.first().field)
                }
            }
        }

        // R16 scope spans ≥3 departments
        for (r in roles) if (scopeDepts(r).size >= 3) out += RuleResult("R16", Severity.WARN, MSG_R16, r.id, "scopeIncludes")

        // R17 BYO gear with no kit fee
        for (r in roles) {
            if (Taxonomy.dept(r.roleCode) in Taxonomy.GEAR_DEPTS && r.gearPolicy == GearPolicy.BYO_NO_FEE) {
                out += RuleResult("R17", Severity.WARN, MSG_R17, r.id, "gearPolicy")
            }
        }

        // R18 basics
        val title = p.title.trim()
        if (title.length !in 2..60) out += RuleResult("R18", Severity.BLOCK, MSG_R18_TITLE, field = "title")
        if (p.logline.trim().length !in 20..200) out += RuleResult("R18", Severity.BLOCK, MSG_R18_LOGLINE, field = "logline")
        if (p.languages.none { it.isNotBlank() }) out += RuleResult("R18", Severity.BLOCK, MSG_R18_LANGUAGE, field = "languages")
        if (roles.isEmpty()) out += RuleResult("R18", Severity.BLOCK, MSG_R18_ROLES, field = "roles")

        // R19 intimate scenes with cast need an ID-verified owner
        if (roles.any { it.isCast } && p.contentFlags.any { it in INTIMATE } && !owner.idVerified) {
            out += RuleResult("R19", Severity.BLOCK, MSG_R19, field = "contentFlags")
        }

        // R20 / R21 picture lock for music and sound design
        val lockRoles = roles.filter { CardText.needsPictureLock(it.roleCode) }
        for (r in lockRoles) {
            val lock = p.pictureLockDate
            if (lock == null) {
                out += RuleResult("R20", Severity.BLOCK, MSG_R20, r.id, "pictureLockDate")
            } else {
                val dl = r.postDeadline
                if (dl != null && com.tff.app.core.Dates.daysBetween(lock, dl) < 21) out += RuleResult("R21", Severity.WARN, MSG_R21, r.id, "postDeadline")
            }
        }

        // R22 bring post in early
        if ((p.stage == Stage.DEVELOPMENT || p.stage == Stage.PRE) && hasOnSet &&
            roles.none { Taxonomy.dept(it.roleCode) == Dept.POST || Taxonomy.dept(it.roleCode) == Dept.MUSIC }) {
            out += RuleResult("R22", Severity.WARN, MSG_R22, field = "roles")
        }

        return out.sortedBy { it.rule }
    }

    fun canPublish(results: List<RuleResult>): Boolean = results.none { it.severity == Severity.BLOCK }

    /**
     * R04: relabel paid roles below the city's paid floor as STIPEND. Returns the adjusted roles.
     * An on-set flat fee is turned into its per-day amount so the Stipend line reads per day. Also forces
     * "bring a friend" on for shoots at a private home (PRD §5.4).
     */
    fun applyAuto(d: ProjectDraft, city: City): ProjectDraft {
        val p = d.project
        val roles = d.roles.map { r ->
            if (!belowFloor(r, p, city)) r
            else if (r.compType == CompType.PAID_FLAT) {
                val days = p.shootDays.coerceAtLeast(1)
                r.copy(compType = CompType.STIPEND, amountMin = r.amountMin?.let { it / days }, amountMax = r.amountMax?.let { it / days })
            } else r.copy(compType = CompType.STIPEND)
        }
        var project = if (p.shootLocationType == LocationType.PRIVATE_HOME && !p.bringFriendWelcome) p.copy(bringFriendWelcome = true) else p
        if (project.honestZeroBudget && !zeroBudgetAllowed(roles)) project = project.copy(honestZeroBudget = false)
        return d.copy(project = project, roles = roles)
    }

    /** "Honest zero-budget" is true only when no role pays a day rate or a flat fee (stipends and unpaid are fine). */
    fun zeroBudgetAllowed(roles: List<ProjectRole>): Boolean = roles.none { it.isPaid }

    /** Every role is remote and in POST / MUSIC / WRITING. */
    fun isPostOnly(roles: List<ProjectRole>): Boolean = roles.isNotEmpty() && roles.none { isOnSet(it) }

    private fun belowFloor(r: ProjectRole, p: Project, city: City): Boolean {
        if (!r.isPaid || city.paidFloorDay <= 0) return false
        if (r.currency.isNotBlank() && city.currency.isNotBlank() && !r.currency.equals(city.currency, ignoreCase = true)) return false
        val rate = MatchEngine.dayRate(r, p) ?: return false
        return rate in 1 until city.paidFloorDay
    }

    private data class Txt(val text: String, val roleId: String?, val field: String)

    /** Text people will read on the card, detail page or apply sheet. Lookbook links are excluded (channel URLs carry @handles). */
    private fun publicTexts(d: ProjectDraft): List<Txt> {
        val p = d.project
        val out = mutableListOf(Txt(p.title, null, "title"), Txt(p.logline, null, "logline"))
        p.storySetting?.let { out += Txt(it, null, "storySetting") }
        p.safetyMeasures?.let { out += Txt(it, null, "safetyMeasures") }
        for (r in d.roles) {
            out += Txt(r.creditTitle, r.id, "creditTitle")
            r.screeningQuestion?.let { out += Txt(it, r.id, "screeningQuestion") }
            r.scopeIncludes.forEach { out += Txt(it, r.id, "scopeIncludes") }
            r.scopeExcludes.forEach { out += Txt(it, r.id, "scopeExcludes") }
            r.roleDetails.values.forEach { out += Txt(it, r.id, "roleDetails") }
        }
        return out.filter { it.text.isNotBlank() }
    }

    // ── R16: map scope chips to departments ──
    private val suggestionDepts: Map<String, List<Dept>> = Taxonomy.all
        .flatMap { info -> info.scopeSuggestions.map { it.lowercase() to info.dept } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, v) -> v.distinct() }

    private val keywordDepts: List<Pair<Regex, Dept>> = listOf(
        "sound\\s*design|final\\s*mix|re-?recording|foley|dialogue\\s*edit" to Dept.POST,
        "edit|editing|assembly|picture\\s*cut|rough\\s*cut|colou?r|grade|grading|vfx|composit|clean-?up|motion\\s*graphics|titles|subtitl" to Dept.POST,
        "music|score|compos|song" to Dept.MUSIC,
        "sound|audio|boom|lav|record" to Dept.SOUND,
        "camera|cinematograph|operat|focus|gimbal|drone|footage\\s*shoot" to Dept.CAMERA,
        "light|grip|rigging|dolly|gaffer|power" to Dept.GE,
        "make-?up|hair|costume|wardrobe|props?|set\\s*dress|art\\s*direct|production\\s*design" to Dept.ART,
        "acting|actor|perform|voice-?over|dubbing" to Dept.CAST,
        "script|screenplay|writ|dialogue\\s*polish|rewrite|treatment" to Dept.WRITING,
        "schedul|budget|permit|location|catering|craft|driv|runs?\\b|logistics|transport" to Dept.PRODUCTION,
        "direct|casting|shot\\s*list|rehears|blocking" to Dept.DIRECTION,
        "bts|stills|photograph|social\\s*media|poster" to Dept.OTHER,
    ).map { (rx, dept) -> Regex(rx, I) to dept }

    /** Departments a role's scope touches, including the role's own. */
    fun scopeDepts(r: ProjectRole): Set<Dept> {
        val own = Taxonomy.dept(r.roleCode)
        val out = mutableSetOf(own)
        for (chip in r.scopeIncludes) {
            val c = chip.trim().lowercase()
            if (c.isEmpty()) continue
            val candidates = (suggestionDepts[c] ?: emptyList()).ifEmpty {
                keywordDepts.firstOrNull { it.first.containsMatchIn(c) }?.let { listOf(it.second) } ?: emptyList()
            }
            if (candidates.isEmpty()) continue
            out += if (own in candidates) own else candidates.first()
        }
        return out
    }
}
