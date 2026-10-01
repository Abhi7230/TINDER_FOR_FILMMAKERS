package com.tff.app.domain

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
    fun check(d: ProjectDraft, owner: Profile, city: City, today: LocalDate): List<RuleResult> = TODO()
    fun canPublish(results: List<RuleResult>): Boolean = results.none { it.severity == Severity.BLOCK }
    /** R04: relabel paid roles below the city's paid floor as STIPEND. Returns the adjusted roles. */
    fun applyAuto(d: ProjectDraft, city: City): ProjectDraft = TODO()
    /** Every role is remote and in POST / MUSIC / WRITING. */
    fun isPostOnly(roles: List<ProjectRole>): Boolean = TODO()
}
