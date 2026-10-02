package com.tff.app.ui.screens.post

/**
 * One-shot hint for where the wizard opens on an existing listing. The post route only carries the project id
 * (nav is frozen), so the dashboard's [Edit role] / [Add role] leave the role here just before navigating and the
 * wizard's ViewModel takes it once. Without a hint an existing listing opens on Review, as before.
 */
object WizardLaunch {
    private var pending: Pair<String, String>? = null

    /** Open [projectId] on the Roles step with [roleId]'s editor expanded. */
    fun editRole(projectId: String, roleId: String) { pending = projectId to roleId }

    /** Open [projectId] on the Roles step with the role picker open. */
    fun addRole(projectId: String) { pending = projectId to PostProjectViewModel.NEW_ROLE }

    /** The role to open for [projectId] (a role id or [PostProjectViewModel.NEW_ROLE]); clears the hint either way. */
    fun take(projectId: String?): String? {
        val p = pending
        pending = null
        return p?.takeIf { projectId != null && it.first == projectId }?.second
    }
}
