package com.tff.app.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tff.app.R
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.model.ProfileCard
import com.tff.app.model.ProjectRole
import com.tff.app.model.ProjectStatus
import com.tff.app.model.ProjectSummary
import com.tff.app.model.RoleStatus
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.FormSection
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteUi(
    val loading: Boolean = true,
    val error: Boolean = false,
    val projects: List<ProjectSummary> = emptyList(),
    val projectId: String? = null,
    val roleId: String? = null,
    val checking: Boolean = false,
    val block: String? = null,
    val checked: Boolean = false,
    val note: String = "",
    val sending: Boolean = false,
    val sendError: String? = null,
    val sent: Boolean = false,
) {
    val project: ProjectSummary? get() = projects.firstOrNull { it.project.id == projectId }
    val openRoles: List<ProjectRole> get() = project?.roles.orEmpty().filter { it.status == RoleStatus.OPEN && it.filledCount < it.count }
}

/** Rebook (S19): choose one of my open projects and a role, run the reverse G4/G5 check, then send an Ask card. */
class InviteViewModel(private val repo: Repo, private val profileId: String) : ViewModel() {
    private val _ui = MutableStateFlow(InviteUi())
    val ui: StateFlow<InviteUi> = _ui

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            repo.myProjects()
                .catch { _ui.update { it.copy(loading = false, error = true) } }
                .collect { list ->
                    val open = list.filter { p -> p.project.status == ProjectStatus.OPEN && p.roles.any { it.status == RoleStatus.OPEN && it.filledCount < it.count } }
                    val first = _ui.value.projectId?.takeIf { id -> open.any { it.project.id == id } } ?: open.firstOrNull()?.project?.id
                    _ui.update { it.copy(loading = false, projects = open, projectId = first) }
                }
        }
    }

    fun pickProject(id: String) = _ui.update { it.copy(projectId = id, roleId = null, block = null, checked = false, sendError = null) }

    fun pickRole(id: String) {
        _ui.update { it.copy(roleId = id, checking = true, block = null, checked = false, sendError = null) }
        viewModelScope.launch {
            val reason = runCatching { repo.askCheck(id, profileId) }.getOrElse { (it as? RepoException)?.userMessage ?: it.message }
            if (_ui.value.roleId == id) _ui.update { it.copy(checking = false, block = reason, checked = true) }
        }
    }

    fun setNote(v: String) = _ui.update { it.copy(note = v.take(140)) }

    fun send(fallback: String) {
        val s = _ui.value
        val roleId = s.roleId ?: return
        if (s.sending || s.block != null || !s.checked) return
        _ui.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            val r = runCatching { repo.sendAsk(roleId, profileId, s.note.trim().ifBlank { null }) }.getOrElse { Result.failure(it) }
            r.onSuccess {
                runCatching { repo.log("rebook_invite", mapOf("role" to roleId)) }
                _ui.update { it.copy(sending = false, sent = true) }
            }.onFailure { e -> _ui.update { it.copy(sending = false, sendError = (e as? RepoException)?.userMessage ?: fallback) } }
        }
    }
}

@Composable
fun InviteSheet(person: ProfileCard, nav: AppNavigator, onDismiss: () -> Unit, openCount: Int = 0) {
    val p = person.profile
    // a fresh VM per opening (openCount), so a finished invite never carries over to the next one
    val vm = rememberVm(key = "invite_${p.id}_$openCount") { InviteViewModel(it, p.id) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Ct.colors
    val first = p.displayName.trim().substringBefore(' ')
    val fallback = stringResource(R.string.ws7_generic_error)
    val sentMsg = stringResource(R.string.ws7_inv_sent, first)
    LaunchedEffect(ui.sent) { if (ui.sent) { nav.toast(sentMsg); onDismiss() } }

    CtBottomSheet(onDismiss, title = stringResource(R.string.ws7_inv_title, p.shortName), subtitle = stringResource(R.string.ws7_inv_subtitle)) {
        when {
            ui.loading -> LoadingSkeleton(rows = 3)
            ui.error -> ErrorState(onRetry = vm::load)
            ui.projects.isEmpty() -> {
                Text(stringResource(R.string.ws7_inv_no_projects, first), style = Ct.type.body, color = c.textSecondary)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.ws7_inv_post), { onDismiss(); nav.toPostProject() }, Modifier.fillMaxWidth())
            }
            else -> {
                FormSection(stringResource(R.string.ws7_inv_project)) {
                    ui.projects.forEach { s ->
                        OptionRow(s.project.title, CardText.formatLabel(s.project.format), s.project.id == ui.projectId) { vm.pickProject(s.project.id) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                FormSection(stringResource(R.string.ws7_inv_role)) {
                    val proj = ui.project
                    if (ui.openRoles.isEmpty() || proj == null) {
                        Text(stringResource(R.string.ws7_inv_no_roles), style = Ct.type.bodyS, color = c.textMuted)
                    } else ui.openRoles.forEach { r ->
                        OptionRow(CardText.roleName(r), CardText.payLine(r, proj.project), r.id == ui.roleId) { vm.pickRole(r.id) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    ui.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.ws7_inv_checking, first), style = Ct.type.bodyS, color = c.textSecondary)
                    }
                    ui.block != null -> CheckLine(ui.block!!, ok = false)
                    ui.checked -> CheckLine(stringResource(R.string.ws7_inv_ok, first), ok = true)
                }
                if (ui.checked && ui.block == null) {
                    Spacer(Modifier.height(12.dp))
                    CtTextField(ui.note, vm::setNote, label = stringResource(R.string.ws7_inv_note_label), placeholder = stringResource(R.string.ws7_inv_note_hint), maxLength = 140, minLines = 2)
                }
                if (ui.sendError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(ui.sendError!!, style = Ct.type.bodyS, color = c.onDangerContainer)
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    stringResource(R.string.ws7_inv_send), { vm.send(fallback) }, Modifier.fillMaxWidth(),
                    enabled = ui.checked && ui.block == null && ui.roleId != null, loading = ui.sending,
                )
                Spacer(Modifier.height(4.dp))
                SecondaryButton(stringResource(R.string.ws7_cancel), onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ct.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CtShapes.field)
            .background(if (selected) c.accent.copy(alpha = 0.08f) else c.surfaceRaised)
            .border(1.dp, if (selected) c.accent.copy(alpha = 0.6f) else c.outline, CtShapes.field)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.textMuted))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.bodyStrong, color = if (selected) c.accent else c.text)
            Text(body, style = Ct.type.bodyS, color = c.textSecondary, maxLines = 2)
        }
    }
}

@Composable
private fun CheckLine(text: String, ok: Boolean) {
    val c = Ct.colors
    Row(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(if (ok) c.go.copy(alpha = 0.10f) else c.warnContainer)
            .padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Block, contentDescription = null, tint = if (ok) c.go else c.onWarnContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Ct.type.bodyS, color = if (ok) c.text else c.onWarnContainer)
    }
}
