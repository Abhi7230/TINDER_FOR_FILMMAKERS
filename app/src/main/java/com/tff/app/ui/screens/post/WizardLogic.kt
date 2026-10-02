package com.tff.app.ui.screens.post

import com.tff.app.domain.CardText
import com.tff.app.domain.DeckCard
import com.tff.app.domain.ListingRules
import com.tff.app.domain.RuleResult
import com.tff.app.domain.Severity
import com.tff.app.model.Area
import com.tff.app.model.City
import com.tff.app.model.CompType
import com.tff.app.model.ContentFlag
import com.tff.app.model.Format
import com.tff.app.model.OwnerSummary
import com.tff.app.model.PaymentTerms
import com.tff.app.model.Perk
import com.tff.app.model.Profile
import com.tff.app.model.Project
import com.tff.app.model.ProjectDraft
import com.tff.app.model.ProjectRole
import com.tff.app.model.ProjectStatus
import com.tff.app.model.RoleCode
import com.tff.app.model.RoleStatus
import com.tff.app.model.RoleWithProject
import com.tff.app.model.ScriptStatus
import com.tff.app.model.Stage
import com.tff.app.model.Taxonomy
import com.tff.app.model.Transport
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

// Pure Kotlin (no android.*): everything the S11 wizard decides, so it can be unit-tested.

/** S11: five steps and a review. */
enum class WizardStep { BASICS, WHEN_WHERE, ROLES, SAFETY, REVIEW }

/** "Pay for all roles" (S11 step 3): what every new role inherits. Each role can still change its own. */
data class PayDefaults(
    val compType: CompType = CompType.PAID_DAY,
    val amount: Int? = null,
    val paymentTerms: PaymentTerms = PaymentTerms.ON_WRAP,
    val perks: Set<Perk> = emptySet(),
    val footageWeeks: Int = WizardLogic.DEFAULT_FOOTAGE_WEEKS,
    val finalFilmWeeks: Int = WizardLogic.DEFAULT_FINAL_FILM_WEEKS,
)

/** Field keys. Project-level keys match ListingRules' RuleResult.field values. */
object Fields {
    const val TITLE = "title"
    const val LOGLINE = "logline"
    const val LANGUAGES = "languages"
    const val RUNTIME = "runtimeMin"
    const val AREA = "area"
    const val SHOOT_DATES = "shootDates"
    const val HOURS = "hoursCap"
    const val CERTAINTY = "dateCertainty"
    const val MEALS = "meals"
    const val TRANSPORT = "transport"
    const val TA = "travelAllowancePerDay"
    const val LOCATION = "shootLocationType"
    const val STORY = "storySetting"
    const val LOCK = "pictureLockDate"
    const val LODGING = "lodging"
    const val CONTENT = "contentFlags"
    const val IC = "intimacyCoordinator"
    const val SAFETY = "safetyMeasures"
    const val STUNT = "stuntOrArmourer"
    const val GUARDIAN = "guardianPresent"
    const val AUDITION = "auditionMode"
    const val ROLES = "roles"
    // role-scoped
    const val AMOUNT = "amount"
    const val TERMS = "paymentTerms"
    const val PERKS = "perks"
    const val FOOTAGE = "footageDueWeeks"
    const val DEADLINE = "postDeadline"
    const val SCREENING = "screeningQuestion"
    const val CREDIT = "creditTitle"
    const val SCOPE = "scopeIncludes"
    const val SCOPE_EX = "scopeExcludes"
    const val DETAILS = "roleDetails"
    const val GEAR = "gearPolicy"

    /** Role fields that live under the role editor's collapsed "More details". */
    val ROLE_MORE = setOf(CREDIT, SCOPE, SCOPE_EX, DETAILS, SCREENING)
    /** Project fields under step 2's "More details". */
    val WHEN_MORE = setOf(STORY)
}

/** Groups on the Review checklist (green tick or red items that link back to the field). */
enum class CheckGroup(val rules: Set<String>) {
    BASICS(setOf("R18", "W03")),
    ROLES(setOf("R18")),
    PAY(setOf("R01")),
    UNPAID(setOf("R02", "R03")),
    LOGISTICS(setOf("R05")),
    DELIVERY(setOf("R05")),
    GEAR(setOf("W01")),
    PICTURE_LOCK(setOf("R20")),
    CONTENT(setOf("R07", "R08", "R09", "R10", "R19")),
    AUDITION(setOf("R11")),
    CLEAN_TEXT(setOf("R12", "R13", "R14")),
}

/** One row of the Review checklist. [issues] empty = green tick. */
data class CheckRow(val group: CheckGroup, val issues: List<RuleResult>)

object WizardLogic {
    const val DEFAULT_RUNTIME = 10
    const val DEFAULT_HOURS = 10
    const val DEFAULT_FOOTAGE_WEEKS = 8
    const val DEFAULT_FINAL_FILM_WEEKS = 12
    const val MAX_RANGE_DAYS = 45L
    const val PROBE_ROLE_ID = "__probe"

    const val W01 = "W01"
    const val W03 = "W03"
    const val MSG_W01 = "Say who brings the gear: the production, or their own kit with or without a kit fee."
    const val MSG_W03 = "Set a runtime between 1 and 240 minutes."

    private val INTIMATE = setOf(ContentFlag.NUDITY, ContentFlag.SIMULATED_SEX, ContentFlag.INTIMACY_SCENE)
    private val RISKY = setOf(ContentFlag.STUNTS_FIGHTS, ContentFlag.FIREARMS_PROPS)

    /** Perks an owner can promise on a role (the derived ones come from the project). */
    val ROLE_PERKS = listOf(Perk.FOOTAGE_BY_DATE, Perk.FINAL_FILM_BY_DATE, Perk.STEP_UP_CREDIT, Perk.TRAVEL_COVERED, Perk.LODGING)

    /** Languages offered in the picker, the owner's own first. */
    fun languageOptions(owner: Profile?, current: List<String>): List<String> {
        val base = listOf("en", "hi", "mr", "kn", "ta", "te", "ml", "bn", "gu", "pa", "ur", "es", "fr")
        return (owner?.languages.orEmpty() + current + base).map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
    }

    /** Fresh draft with the research defaults (PRD §6.2 / S11): stage PRE, ambition PORTFOLIO, hours cap 10, the owner's city/area/languages. */
    fun newDraft(id: String, owner: Profile, city: City?, nowMillis: Long): ProjectDraft {
        val areaId = city?.areas?.firstOrNull { it.id == owner.areaId }?.id ?: city?.areas?.firstOrNull()?.id ?: owner.areaId
        return ProjectDraft(
            project = Project(
                id = id, ownerId = owner.id, payerId = owner.id, title = "", logline = "",
                format = Format.SHORT, runtimeMin = DEFAULT_RUNTIME, languages = owner.languages.take(2).ifEmpty { listOf("en") },
                stage = Stage.PRE, scriptStatus = ScriptStatus.DRAFT,
                cityId = city?.id ?: owner.cityId, areaId = areaId, hoursCap = DEFAULT_HOURS,
                status = ProjectStatus.DRAFT, createdAt = nowMillis, updatedAt = nowMillis,
            ),
            roles = emptyList(),
        )
    }

    /** A new role that inherits "Pay for all roles". Post-only projects make post roles remote. */
    fun newRole(id: String, code: RoleCode, project: Project, pay: PayDefaults, currency: String, postOnly: Boolean): ProjectRole {
        val info = Taxonomy.info(code)
        val remote = if (postOnly) true else info.remoteDefault && info.dept in Taxonomy.POST_DEPTS
        return withPay(
            ProjectRole(
                id = id, projectId = project.id, roleCode = code, count = 1, creditTitle = info.defaultCredit,
                compType = pay.compType, currency = currency, remoteOk = remote,
                applicationCap = Taxonomy.defaultCap(code), status = RoleStatus.OPEN,
            ),
            pay,
        )
    }

    /** Applies "Pay for all roles" to one role (comp type, amount, terms, unpaid perks and weeks). */
    fun withPay(r: ProjectRole, pay: PayDefaults): ProjectRole {
        val unpaid = pay.compType == CompType.UNPAID
        val perksApply = pay.compType == CompType.UNPAID || pay.compType == CompType.STIPEND
        val perks = if (perksApply) pay.perks else r.perks.intersect(setOf(Perk.STEP_UP_CREDIT))
        return r.copy(
            compType = pay.compType,
            amountMin = if (unpaid) null else pay.amount,
            amountMax = if (unpaid) null else pay.amount,
            paymentTerms = if (unpaid) null else pay.paymentTerms,
            perks = perks,
            footageDueWeeks = if (Perk.FOOTAGE_BY_DATE in perks) pay.footageWeeks else null,
            finalFilmDueWeeks = if (Perk.FINAL_FILM_BY_DATE in perks) pay.finalFilmWeeks else null,
        )
    }

    /** True when any editable role's pay differs from the defaults (shows "Apply to all roles"). */
    fun payDiffers(roles: List<ProjectRole>, pay: PayDefaults): Boolean = roles.filter { it.status == RoleStatus.OPEN }.any { r ->
        val w = withPay(r, pay)
        w.compType != r.compType || w.amountMin != r.amountMin || w.paymentTerms != r.paymentTerms || w.perks != r.perks ||
            w.footageDueWeeks != r.footageDueWeeks
    }

    /** "Pay for all roles" read back from an existing draft (its first role), so editing a draft starts where it was. */
    fun payFrom(roles: List<ProjectRole>): PayDefaults {
        val r = roles.firstOrNull() ?: return PayDefaults()
        return PayDefaults(
            compType = r.compType,
            amount = r.amountMin ?: r.amountMax,
            paymentTerms = r.paymentTerms ?: PaymentTerms.ON_WRAP,
            perks = r.perks.intersect(ROLE_PERKS.toSet()),
            footageWeeks = r.footageDueWeeks ?: DEFAULT_FOOTAGE_WEEKS,
            finalFilmWeeks = r.finalFilmDueWeeks ?: DEFAULT_FINAL_FILM_WEEKS,
        )
    }

    /** [Duplicate]: same terms, new id, nothing counted yet. */
    fun duplicate(r: ProjectRole, newId: String): ProjectRole =
        r.copy(id = newId, applicationsCount = 0, filledCount = 0, status = RoleStatus.OPEN)

    /**
     * Switch a role to another code (after Duplicate, say). Credit title, cap, remote default and gear policy follow the
     * new role when they were still the old role's defaults; anything the owner typed is kept.
     */
    fun changeCode(r: ProjectRole, code: RoleCode, postOnly: Boolean): ProjectRole {
        if (r.roleCode == code) return r
        val old = Taxonomy.info(r.roleCode)
        val new = Taxonomy.info(code)
        val credit = if (r.creditTitle.isBlank() || r.creditTitle == old.defaultCredit) new.defaultCredit else r.creditTitle
        val cap = if (r.applicationCap == Taxonomy.defaultCap(r.roleCode)) Taxonomy.defaultCap(code) else r.applicationCap
        val remote = if (postOnly) true else new.remoteDefault && new.dept in Taxonomy.POST_DEPTS
        val gear = if (new.dept in Taxonomy.GEAR_DEPTS) r.gearPolicy else null
        val scope = r.scopeIncludes.filter { it in new.scopeSuggestions || it !in old.scopeSuggestions }
        return r.copy(
            roleCode = code, creditTitle = credit, applicationCap = cap, remoteOk = remote, gearPolicy = gear,
            scopeIncludes = scope, roleDetails = if (old.detailsFamily == new.detailsFamily) r.roleDetails else emptyMap(),
            postDeadline = if (remote && new.dept in Taxonomy.POST_DEPTS) r.postDeadline else null,
        )
    }

    /** Roles a post-only project can take: remote post work (edit, colour, VFX, sound design, music, writing). */
    fun postOnlyRoleCodes(): Set<RoleCode> = Taxonomy.all.filter { it.dept in Taxonomy.POST_DEPTS }.map { it.code }.toSet()

    fun isGearRole(code: RoleCode): Boolean = Taxonomy.dept(code) in Taxonomy.GEAR_DEPTS
    fun isPostDept(code: RoleCode): Boolean = Taxonomy.dept(code) in Taxonomy.POST_DEPTS
    fun needsLock(roles: List<ProjectRole>): Boolean = roles.any { it.status != RoleStatus.CLOSED && CardText.needsPictureLock(it.roleCode) }
    fun hasCast(roles: List<ProjectRole>): Boolean = roles.any { it.status != RoleStatus.CLOSED && it.isCast }

    /** Roles the owner works on (closed ones stay in the draft untouched so saving never reopens or deletes them). */
    fun activeRoles(d: ProjectDraft): List<ProjectRole> = d.roles.filter { it.status != RoleStatus.CLOSED }

    /**
     * The draft as it is saved and checked. Post-only projects drop the shoot logistics; answers that no longer
     * apply (follow-ups for unticked content, pay on unpaid roles, deadlines on on-set roles) are cleared.
     */
    fun forSave(d: ProjectDraft, postOnly: Boolean): ProjectDraft {
        var p = d.project
        if (postOnly) {
            p = p.copy(
                shootDates = emptyList(), shootLocationType = null, dateCertainty = null, hoursCap = null,
                meals = null, transport = null, travelAllowancePerDay = null, lodging = null,
            )
        } else {
            if (p.transport != Transport.MILEAGE_OR_TA_PAID) p = p.copy(travelAllowancePerDay = null)
            if (p.shootDates.size > 1 && p.lodging == null) p = p.copy(lodging = false)
        }
        val flags = p.contentFlags
        if (flags.isNotEmpty()) p = p.copy(contentNoneConfirmed = false)
        if (flags.none { it in INTIMATE }) p = p.copy(intimacyCoordinator = null)
        if (flags.none { it in RISKY }) p = p.copy(stuntOrArmourer = null)
        if (ContentFlag.MINORS_ON_SET !in flags) p = p.copy(guardianPresent = null)
        p = p.copy(
            title = p.title.trim(), logline = p.logline.trim(),
            storySetting = p.storySetting?.trim()?.ifBlank { null }, safetyMeasures = p.safetyMeasures?.trim()?.ifBlank { null },
            lookbookUrl = p.lookbookUrl?.trim()?.ifBlank { null }, shootDates = p.shootDates.distinct().sorted(),
            languages = p.languages.filter { it.isNotBlank() }.distinct(),
        )
        return d.copy(project = p, roles = d.roles.map { if (it.status == RoleStatus.CLOSED) it else normalizeRole(it) })
    }

    fun normalizeRole(r: ProjectRole): ProjectRole {
        var x = r
        if (x.compType == CompType.UNPAID) x = x.copy(amountMin = null, amountMax = null, paymentTerms = null)
        else {
            val min = x.amountMin ?: x.amountMax
            val max = x.amountMax ?: min
            x = x.copy(amountMin = min, amountMax = if (max != null && min != null && max < min) min else max)
        }
        if (Perk.FOOTAGE_BY_DATE !in x.perks) x = x.copy(footageDueWeeks = null)
        if (Perk.FINAL_FILM_BY_DATE !in x.perks) x = x.copy(finalFilmDueWeeks = null)
        if (!isGearRole(x.roleCode)) x = x.copy(gearPolicy = null)
        if (!(x.remoteOk && isPostDept(x.roleCode))) x = x.copy(postDeadline = null)
        x = x.copy(
            creditTitle = x.creditTitle.trim().ifBlank { Taxonomy.info(x.roleCode).defaultCredit },
            scopeIncludes = x.scopeIncludes.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            scopeExcludes = x.scopeExcludes.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            roleDetails = x.roleDetails.mapValues { it.value.trim() }.filterValues { it.isNotBlank() },
            screeningQuestion = x.screeningQuestion?.trim()?.ifBlank { null },
            count = x.count.coerceIn(1, 10),
            applicationCap = x.applicationCap.coerceIn(5, 40),
        )
        return x
    }

    /**
     * Live rules for the wizard: ListingRules (the same blockers publish finds, plus the AUTO relabel note) and two wizard
     * checks (gear policy on camera/sound/G&E roles, runtime). Before any on-set role exists, an on-set project is
     * checked as if it had one, so step 2's logistics are asked for up front.
     */
    fun check(d: ProjectDraft, postOnly: Boolean, owner: Profile, city: City, today: LocalDate): List<RuleResult> {
        val saved = forSave(d, postOnly)
        val active = saved.copy(roles = activeRoles(saved))
        // Checked as typed (not after applyAuto), so the R04 AUTO relabel shows; blockers are the same either way.
        val prepared = active
        val probe = !postOnly && prepared.roles.none { ListingRules.isOnSet(it) }
        val checked = if (probe) prepared.copy(roles = prepared.roles + probeRole(prepared.project, city.currency)) else prepared
        var out = ListingRules.check(checked, owner, city, today).filter { it.roleId != PROBE_ROLE_ID }
        if (probe) {
            out = out.filter { it.rule != "R22" }
            if (prepared.roles.isEmpty()) out = out + RuleResult("R18", Severity.BLOCK, ListingRules.MSG_R18_ROLES, field = Fields.ROLES)
        }
        val extra = mutableListOf<RuleResult>()
        prepared.roles.filter { isGearRole(it.roleCode) && it.gearPolicy == null }
            .forEach { extra += RuleResult(W01, Severity.BLOCK, MSG_W01, it.id, Fields.GEAR) }
        if (d.project.runtimeMin !in 1..240) extra += RuleResult(W03, Severity.BLOCK, MSG_W03, field = Fields.RUNTIME)
        return (out + extra).sortedBy { it.rule }
    }

    private fun probeRole(p: Project, currency: String) = ProjectRole(
        id = PROBE_ROLE_ID, projectId = p.id, roleCode = RoleCode.PA, creditTitle = "Production Assistant",
        compType = CompType.PAID_DAY, amountMin = 1_000_000, amountMax = 1_000_000, currency = currency,
        paymentTerms = PaymentTerms.ON_WRAP,
    )

    fun blockers(rules: List<RuleResult>): List<RuleResult> = rules.filter { it.severity == Severity.BLOCK }

    /** Which step a rule's field lives on (red Review items jump there). */
    fun stepOf(r: RuleResult): WizardStep = when (r.field) {
        Fields.TITLE, Fields.LOGLINE, Fields.LANGUAGES, Fields.RUNTIME -> WizardStep.BASICS
        Fields.SHOOT_DATES, Fields.HOURS, Fields.CERTAINTY, Fields.MEALS, Fields.TRANSPORT, Fields.TA,
        Fields.LOCATION, Fields.STORY, Fields.LOCK, Fields.LODGING, Fields.AREA -> WizardStep.WHEN_WHERE
        Fields.CONTENT, Fields.IC, Fields.SAFETY, Fields.STUNT, Fields.GUARDIAN, Fields.AUDITION -> WizardStep.SAFETY
        Fields.ROLES -> WizardStep.ROLES
        else -> if (r.roleId != null) WizardStep.ROLES else WizardStep.BASICS
    }

    /** Red items on a role's own fields open that role's editor. */
    fun opensRoleEditor(r: RuleResult): Boolean = r.roleId != null && stepOf(r) == WizardStep.ROLES

    /** BLOCK results a step must clear before [Next]. Role-scoped results block step 3 (Roles). */
    fun stepBlockers(step: WizardStep, rules: List<RuleResult>): List<RuleResult> =
        blockers(rules).filter { stepOf(it) == step && !(step == WizardStep.WHEN_WHERE && it.rule == "R20") }

    /**
     * How many answers a step still needs before [Next] (one per field, per role): the "3 TO ANSWER" counter and the
     * "Fix the 3 items in red" toast count the same things.
     */
    fun toAnswer(step: WizardStep, rules: List<RuleResult>): Int = stepBlockers(step, rules).distinctBy { it.field to it.roleId }.size

    /** Review checklist rows that apply to this draft, in order. */
    fun checklist(d: ProjectDraft, postOnly: Boolean, rules: List<RuleResult>): List<CheckRow> {
        val roles = activeRoles(d)
        val blocks = blockers(rules)
        fun rows(g: CheckGroup, pick: (RuleResult) -> Boolean = { it.rule in g.rules }) = CheckRow(g, blocks.filter(pick))
        val out = mutableListOf(
            rows(CheckGroup.BASICS) { it.rule in CheckGroup.BASICS.rules && it.field != Fields.ROLES },
            rows(CheckGroup.ROLES) { it.rule == "R18" && it.field == Fields.ROLES },
        )
        if (roles.any { it.compType != CompType.UNPAID }) out += rows(CheckGroup.PAY)
        if (roles.any { it.compType == CompType.UNPAID }) out += rows(CheckGroup.UNPAID)
        if (!postOnly) out += rows(CheckGroup.LOGISTICS) { it.rule == "R05" && it.field != Fields.DEADLINE }
        if (roles.any { !ListingRules.isOnSet(it) }) out += rows(CheckGroup.DELIVERY) { it.rule == "R05" && it.field == Fields.DEADLINE }
        if (roles.any { isGearRole(it.roleCode) }) out += rows(CheckGroup.GEAR)
        if (needsLock(roles)) out += rows(CheckGroup.PICTURE_LOCK)
        out += rows(CheckGroup.CONTENT)
        if (hasCast(roles)) out += rows(CheckGroup.AUDITION)
        out += rows(CheckGroup.CLEAN_TEXT)
        // A blocker that didn't land in any shown row still shows (on the basics row) so nothing is ever hidden.
        val shown = out.flatMap { it.issues }.toSet()
        val orphans = blocks.filter { it !in shown }
        if (orphans.isNotEmpty()) out[0] = out[0].copy(issues = out[0].issues + orphans)
        return out
    }

    /** Warnings and the AUTO relabel, shown on Review (R21/R22 are one-time nudges, handled separately). */
    fun notes(rules: List<RuleResult>): List<RuleResult> =
        rules.filter { it.severity != Severity.BLOCK && it.rule != "R21" && it.rule != "R22" }

    // ───────────── Card preview ─────────────

    /** The draft exactly as publish will store it (AUTO R04 relabel applied), for the live Role Card preview. */
    fun previewDraft(d: ProjectDraft, postOnly: Boolean, city: City?): ProjectDraft {
        val saved = forSave(d, postOnly)
        val active = saved.copy(roles = activeRoles(saved))
        return if (city == null) active else ListingRules.applyAuto(active, city)
    }

    /** One preview card for the real RoleCard component. */
    fun previewCard(prepared: ProjectDraft, role: ProjectRole, owner: OwnerSummary, city: City?, area: Area?): DeckCard {
        val roles = prepared.roles
        val item = RoleWithProject(
            role = role, project = prepared.project.copy(status = ProjectStatus.OPEN), owner = owner,
            cityName = city?.name ?: "", areaName = area?.name ?: "", areaLat = area?.lat ?: 0.0, areaLng = area?.lng ?: 0.0,
            team = emptyList(), paidRoles = roles.count { it.isPaid }, totalRoles = roles.size.coerceAtLeast(1),
        )
        return DeckCard(item = item, score = 0.0, fit = null, distanceKm = null)
    }

    // ───────────── Dates ─────────────

    /** Every date from [start] to [end] inclusive (capped at [MAX_RANGE_DAYS]). */
    fun range(start: LocalDate, end: LocalDate): List<LocalDate> {
        val e = if (end.isBefore(start)) start else minOf(end, start.plusDays(MAX_RANGE_DAYS - 1))
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(e) }.toList()
    }

    /** Next weekends that leave at least 5 days to crew up: (Sat, Sun) pairs. */
    fun upcomingWeekends(today: LocalDate, count: Int = 3): List<Pair<LocalDate, LocalDate>> {
        var sat = today.plusDays(5)
        while (sat.dayOfWeek != DayOfWeek.SATURDAY) sat = sat.plusDays(1)
        return (0 until count).map { i -> sat.plusWeeks(i.toLong()) to sat.plusWeeks(i.toLong()).plusDays(1) }
    }

    // ───────────── Ids ─────────────

    private var lastMs = 0L
    private var seq = 0

    /**
     * Time-ordered UUID (v7 layout): valid for Supabase's uuid columns, and in demo mode it sorts in creation order, so
     * a reloaded draft lists its roles in the order they were added.
     */
    @Synchronized
    fun newId(nowMillis: Long = System.currentTimeMillis()): String {
        val ms = maxOf(nowMillis, lastMs)
        seq = if (ms == lastMs) seq + 1 else 0
        lastMs = ms
        val msb = (ms shl 16) or 0x7000L or (seq.toLong() and 0x0FFF)
        val lsb = (Random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }
}
