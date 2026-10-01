package com.tff.app.domain

import com.tff.app.model.*
import java.time.LocalDate

data class ApplicantInput(
    val application: Application?,          // null for Scout candidates
    val card: ProfileCard,
    val rateFit: Double,                     // computed server-side / by DemoRepo, never from the owner's device
    val otherAnswers: List<String> = emptyList(),   // this person's answers on other roles (near-duplicate check)
    val headshotUrl: String? = null,
)

/** PRD §7.4. Pure Kotlin. */
object ApplicantRanker {
    /** rate_fit = min(1, amount_max / min_day_rate); 1 if no private rate or unpaid role. Runs where the rate lives. */
    fun rateFit(role: ProjectRole, project: Project, minDayRate: Int?): Double = TODO()
    /** Score 0..100 + top-3 reason chips ("Showed up 9/9", "Speaks Kannada", "Has kit"). */
    fun score(item: RoleWithProject, input: ApplicantInput, ownerLevel: Level, today: LocalDate): Pair<Double, List<String>> = TODO()
    /** Sorted descending; ties → earlier application. */
    fun rank(item: RoleWithProject, inputs: List<ApplicantInput>, ownerLevel: Level, today: LocalDate): List<ApplicantView> = TODO()
    /**
     * Reverse gates for Scout / Rebook / backup asks: null if [candidate] may be asked onto [item], else a
     * human reason ("Kabir only takes paid work right now"). Cast roles: only allowed when [isKeepList].
     */
    fun askBlockReason(item: RoleWithProject, candidate: Profile, candidateMinRate: Int?, today: LocalDate, isKeepList: Boolean): String? = TODO()
    /** Normalised Levenshtein similarity in 0..1. */
    fun similarity(a: String, b: String): Double = TODO()
}
