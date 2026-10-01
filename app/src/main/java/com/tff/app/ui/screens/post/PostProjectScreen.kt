package com.tff.app.ui.screens.post

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.ListingRules
import com.tff.app.domain.ShareText
import com.tff.app.model.ProjectRole
import com.tff.app.model.RoleWithProject
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.RoleCard
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.StepHeader
import com.tff.app.ui.components.TopBarAction
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

/** S11 Post a project (5 steps + review). Also opens an existing draft or a live listing to edit. */
@Composable
fun PostProjectScreen(nav: AppNavigator, projectId: String?) {
    val vm = rememberVm(key = "post:${projectId ?: "new"}") { repo -> PostProjectViewModel(repo, projectId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmExit by remember { mutableStateOf(false) }
    var removeAsk by remember { mutableStateOf<ProjectRole?>(null) }
    var preview by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                WizardEvent.DraftSaved -> nav.toast(context.getString(R.string.ws5_toast_draft_saved))
                WizardEvent.ListingUpdated -> nav.toast(context.getString(R.string.ws5_toast_listing_updated))
                is WizardEvent.FixFirst -> nav.toast(context.resources.getQuantityString(R.plurals.ws5_toast_fix_first, e.count, e.count))
                is WizardEvent.Failed -> nav.toast(e.message ?: context.getString(R.string.ws5_toast_failed))
                WizardEvent.Exit -> nav.back()
            }
        }
    }
    val exit = { if (ui.dirty && ui.phase == WizardPhase.READY) confirmExit = true else nav.back() }
    BackHandler(enabled = ui.published == null) { if (!vm.back()) exit() }

    val editor = ui.editingRoleId != null
    val picking = ui.editingRoleId == PostProjectViewModel.NEW_ROLE || ui.changingCode

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Ct.colors.bg,
        topBar = {
            CtTopBar(
                title = when {
                    ui.isLive -> stringResource(R.string.ws5_wizard_title_live)
                    projectId != null -> stringResource(R.string.ws5_wizard_title_draft)
                    else -> stringResource(R.string.ws5_wizard_title_new)
                },
                onBack = { if (!vm.back()) exit() },
                divider = true,
                actions = {
                    if (ui.phase == WizardPhase.READY) {
                        TopBarAction(Icons.Rounded.Visibility, stringResource(R.string.ws5_preview_cd), { preview = true })
                        if (!ui.isLive) {
                            GhostButton(
                                stringResource(if (ui.saving) R.string.ws5_saving else R.string.ws5_save_draft), vm::saveDraft,
                                enabled = !ui.saving && !ui.publishing,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (ui.phase == WizardPhase.READY && ui.published == null) {
                WizardBottomBar(ui, vm, editor, picking, onRemove = { r -> removeAsk = r })
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).background(Ct.colors.bg)) {
            when (ui.phase) {
                WizardPhase.LOADING -> LoadingSkeleton(Modifier.fillMaxSize().padding(top = 8.dp), kind = SkeletonKind.DETAIL, rows = 4)
                WizardPhase.GUEST -> EmptyState(
                    title = stringResource(R.string.ws5_guest_post_title), body = stringResource(R.string.ws5_guest_post_body),
                    icon = Icons.Rounded.Movie, modifier = Modifier.fillMaxSize(),
                ) { PrimaryButton(stringResource(R.string.ws5_sign_in), { nav.toWelcome() }) }
                WizardPhase.ERROR -> ErrorState(onRetry = vm::load, message = ui.error, modifier = Modifier.fillMaxSize())
                WizardPhase.READY -> WizardContent(ui, vm, editor, picking, onRemove = { r -> removeAsk = r })
            }
        }
    }

    if (preview && ui.phase == WizardPhase.READY) PreviewSheet(ui, vm, onDismiss = { preview = false })

    ui.published?.let { info ->
        PublishedSheet(
            info = info,
            onShare = { text -> nav.share(text, context.getString(R.string.ws5_share_title)) },
            onDone = { nav.back(); nav.toProjectDashboard(info.projectId) },
            onCrewUp = { roleId -> nav.back(); nav.toCrewUp(roleId) },
        )
    }

    removeAsk?.let { r ->
        val n = r.applicationsCount
        AlertDialog(
            onDismissRequest = { removeAsk = null },
            containerColor = Ct.colors.surface,
            title = { Text(stringResource(R.string.ws5_remove_title, CardText.roleName(r)), style = Ct.type.titleM, color = Ct.colors.text) },
            text = {
                Text(
                    if (n > 0) context.resources.getQuantityString(R.plurals.ws5_remove_body_apps, n, n) else stringResource(R.string.ws5_remove_body),
                    style = Ct.type.bodyS, color = Ct.colors.textSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { vm.removeRole(r.id); removeAsk = null }) { Text(stringResource(R.string.ws5_remove), color = Ct.colors.stop, style = Ct.type.bodyStrong) } },
            dismissButton = { TextButton(onClick = { removeAsk = null }) { Text(stringResource(R.string.ws5_keep), color = Ct.colors.textSecondary, style = Ct.type.bodyStrong) } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = Ct.colors.surface,
            title = { Text(stringResource(if (ui.isLive) R.string.ws5_leave_live_title else R.string.ws5_leave_title), style = Ct.type.titleM, color = Ct.colors.text) },
            text = { Text(stringResource(if (ui.isLive) R.string.ws5_leave_live_body else R.string.ws5_leave_body), style = Ct.type.bodyS, color = Ct.colors.textSecondary) },
            confirmButton = {
                if (ui.isLive) TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.ws5_keep_editing), color = Ct.colors.accent, style = Ct.type.bodyStrong) }
                else TextButton(onClick = { confirmExit = false; vm.saveAndExit() }) { Text(stringResource(R.string.ws5_save_draft), color = Ct.colors.accent, style = Ct.type.bodyStrong) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; nav.back() }) { Text(stringResource(R.string.ws5_discard), color = Ct.colors.textSecondary, style = Ct.type.bodyStrong) }
            },
        )
    }
}

@Composable
private fun stepTitle(s: WizardStep): String = stringResource(
    when (s) {
        WizardStep.BASICS -> R.string.ws5_step_basics
        WizardStep.WHEN_WHERE -> R.string.ws5_step_when
        WizardStep.ROLES -> R.string.ws5_step_roles
        WizardStep.SAFETY -> R.string.ws5_step_safety
        WizardStep.REVIEW -> R.string.ws5_step_review
    },
)

@Composable
private fun stepIntro(s: WizardStep, postOnly: Boolean): String = stringResource(
    when (s) {
        WizardStep.BASICS -> R.string.ws5_intro_basics
        WizardStep.WHEN_WHERE -> if (postOnly) R.string.ws5_intro_when_post else R.string.ws5_intro_when
        WizardStep.ROLES -> R.string.ws5_intro_roles
        WizardStep.SAFETY -> R.string.ws5_intro_safety
        WizardStep.REVIEW -> R.string.ws5_intro_review
    },
)

@Composable
private fun WizardContent(ui: WizardUi, vm: PostProjectViewModel, editor: Boolean, picking: Boolean, onRemove: (ProjectRole) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(ui.step, ui.editingRoleId, ui.changingCode) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)) {
        when {
            picking -> RolePickerPane(ui, vm)
            editor -> {
                Text(stringResource(R.string.ws5_role_editor_kicker).uppercase(), style = Ct.type.slate.copy(fontSize = 14.sp), color = Ct.colors.accent)
                Spacer(Modifier.height(4.dp))
                RoleEditor(ui, vm)
            }
            else -> {
                StepHeader(current = ui.step.ordinal + 1, total = WizardStep.entries.size, title = stepTitle(ui.step))
                Spacer(Modifier.height(6.dp))
                Text(stepIntro(ui.step, ui.postOnly), style = Ct.type.bodyS, color = Ct.colors.textSecondary)
                Spacer(Modifier.height(22.dp))
                when (ui.step) {
                    WizardStep.BASICS -> BasicsStep(ui, vm)
                    WizardStep.WHEN_WHERE -> WhenWhereStep(ui, vm)
                    WizardStep.ROLES -> RolesStep(ui, vm, onRemove)
                    WizardStep.SAFETY -> SafetyStep(ui, vm)
                    WizardStep.REVIEW -> ReviewStep(ui, vm)
                }
            }
        }
    }
}

@Composable
private fun WizardBottomBar(ui: WizardUi, vm: PostProjectViewModel, editor: Boolean, picking: Boolean, onRemove: (ProjectRole) -> Unit) {
    val c = Ct.colors
    Column(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding()) {
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                picking -> SecondaryButton(stringResource(R.string.ws5_cancel), { vm.back() }, Modifier.fillMaxWidth())
                editor -> {
                    val r = ui.editingRole
                    if (r != null && r.filledCount == 0) {
                        GhostButton(stringResource(R.string.ws5_remove_role), { onRemove(r) }, color = c.onDangerContainer)
                    }
                    PrimaryButton(stringResource(R.string.ws5_done), vm::closeEditor, Modifier.weight(1f))
                }
                ui.step == WizardStep.REVIEW -> {
                    PrimaryButton(
                        stringResource(if (ui.isLive) R.string.ws5_update_listing else R.string.ws5_publish), vm::publish,
                        Modifier.fillMaxWidth(), loading = ui.publishing, enabled = !ui.saving,
                    )
                }
                else -> {
                    if (ui.step.ordinal > 0 && !ui.returnToReview) SecondaryButton(stringResource(R.string.ws5_back), { vm.back() })
                    PrimaryButton(
                        stringResource(if (ui.returnToReview) R.string.ws5_back_to_review else R.string.ws5_next), vm::next, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Live card preview (S11): the real RoleCard built from the draft, exactly as it will appear in the deck. */
@Composable
private fun PreviewSheet(ui: WizardUi, vm: PostProjectViewModel, onDismiss: () -> Unit) {
    val prepared = vm.previewDraft(ui)
    val roles = prepared?.roles.orEmpty()
    var sel by rememberSaveable { mutableIntStateOf(0) }
    CtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ws5_preview_title), subtitle = stringResource(R.string.ws5_preview_sub)) {
        if (prepared == null || roles.isEmpty() || ui.ownerSummary == null) {
            InfoTipCard(stringResource(R.string.ws5_preview_empty))
            return@CtBottomSheet
        }
        val i = sel.coerceIn(0, roles.lastIndex)
        if (roles.size > 1) {
            ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
                roles.forEachIndexed { idx, r -> CtFilterChip(CardText.roleName(r), selected = idx == i, onClick = { sel = idx }) }
            }
            Spacer(Modifier.height(12.dp))
        }
        val role = roles[i]
        RoleCard(WizardLogic.previewCard(prepared, role, ui.ownerSummary, ui.city, ui.area), Modifier.fillMaxWidth())
        val original = ui.draft?.roles?.firstOrNull { it.id == role.id }
        if (original != null && relabelled(original, role)) {
            Spacer(Modifier.height(12.dp))
            val city = ui.city
            if (city != null) InfoTipCard(ListingRules.r04Message(city.paidFloorDay, city.currency))
        }
    }
}

/** After Publish (F17): the exact share template per role, the Android share sheet, and where to go next. */
@Composable
private fun PublishedSheet(info: PublishedInfo, onShare: (String) -> Unit, onDone: () -> Unit, onCrewUp: (String) -> Unit) {
    val c = Ct.colors
    var sel by rememberSaveable { mutableIntStateOf(0) }
    CtBottomSheet(onDismiss = onDone, title = stringResource(R.string.ws5_published_title), subtitle = stringResource(R.string.ws5_published_sub)) {
        val items: List<RoleWithProject> = info.items
        if (items.isEmpty()) {
            PrimaryButton(stringResource(R.string.ws5_go_dashboard), onDone, Modifier.fillMaxWidth())
            return@CtBottomSheet
        }
        val i = sel.coerceIn(0, items.lastIndex)
        RoleShareBody(items, i, { sel = it }, onShare)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(stringResource(R.string.ws5_go_dashboard), onDone, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        GhostButton(stringResource(R.string.ws5_open_crew_up), { onCrewUp(items[i].role.id) }, Modifier.fillMaxWidth())
    }
}

/**
 * The F17 share template for one role (picked with chips when there are several), shown exactly as it will be sent,
 * plus [Share this role] → the Android share sheet. Used after Publish and from the dashboard.
 */
@Composable
fun RoleShareBody(items: List<RoleWithProject>, selected: Int, onSelect: (Int) -> Unit, onShare: (String) -> Unit) {
    val c = Ct.colors
    if (items.isEmpty()) return
    val i = selected.coerceIn(0, items.lastIndex)
    if (items.size > 1) {
        ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
            items.forEachIndexed { idx, it -> CtFilterChip(CardText.roleName(it.role), selected = idx == i, onClick = { onSelect(idx) }) }
        }
        Spacer(Modifier.height(12.dp))
    }
    val text = ShareText.roleCard(items[i], AppContainer.shareBaseUrl)
    Text(
        text, style = Ct.type.monoS.copy(fontSize = 13.sp, lineHeight = 19.sp), color = c.text,
        modifier = Modifier.fillMaxWidth().clip(CtShapes.field).background(c.surfaceRaised).padding(14.dp),
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(stringResource(R.string.ws5_share_role), { onShare(text) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
}
