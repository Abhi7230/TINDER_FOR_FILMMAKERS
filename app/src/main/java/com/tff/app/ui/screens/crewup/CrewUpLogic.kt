package com.tff.app.ui.screens.crewup

import com.tff.app.model.ApplicantView
import com.tff.app.model.ProjectRole
import com.tff.app.model.ProjectStatus
import com.tff.app.model.ProjectSummary
import com.tff.app.model.RoleStatus
import com.tff.app.model.Scarcity
import com.tff.app.model.Taxonomy

/** S09 sub-decks. */
enum class CrewTab { APPLICANTS, SCOUT }

/** Pure Crew up rules (no android.*), unit-tested in CrewUpLogicTest. */
object CrewUpLogic {
    /** Cast roles are Applicants-only: Calltime never lets anyone browse actors (PRD §3.1). */
    fun hasScout(role: ProjectRole): Boolean = !role.isCast

    /**
     * Default sub-deck (PRD §3.5): scarce crew roles open on Scout, because few people apply to them.
     * When people already applied, their answers come first: every applicant gets an answer.
     */
    fun defaultTab(role: ProjectRole, pendingApplicants: Int): CrewTab =
        if (hasScout(role) && Taxonomy.info(role.roleCode).scarcity == Scarcity.SCARCE && pendingApplicants == 0) CrewTab.SCOUT
        else CrewTab.APPLICANTS

    /** The viewer's open roles on open projects, in project then role order. */
    fun openRoleIds(projects: List<ProjectSummary>): List<String> =
        projects.filter { it.project.status == ProjectStatus.OPEN }
            .flatMap { p -> p.roles.filter { it.status == RoleStatus.OPEN }.sortedBy { it.id }.map { it.id } }

    /** Stable key for a person in a deck: their application, or the profile for Scout. */
    fun key(v: ApplicantView): String = v.application?.id ?: ("p:" + v.card.profile.id)

    /**
     * Keep the deck order stable while you swipe: people already shown keep their place, new arrivals
     * (ranked) go to the back, and people who left (answered, withdrew) drop out.
     */
    fun stableOrder(previous: List<String>, fresh: List<ApplicantView>): List<ApplicantView> {
        val byKey = fresh.associateBy(::key)
        val kept = previous.mapNotNull { byKey[it] }
        val keptKeys = kept.map(::key).toSet()
        return kept + fresh.filter { key(it) !in keptKeys }
    }
}
