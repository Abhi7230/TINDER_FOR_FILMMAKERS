package com.tff.app.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.domain.CardText
import com.tff.app.domain.ListingRules
import com.tff.app.domain.RuleResult
import com.tff.app.domain.Severity
import com.tff.app.model.RoleCode
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.RoleCard
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

@Composable
fun checkGroupTitle(g: CheckGroup): String = stringResource(
    when (g) {
        CheckGroup.BASICS -> R.string.ws5_chk_basics
        CheckGroup.ROLES -> R.string.ws5_chk_roles
        CheckGroup.PAY -> R.string.ws5_chk_pay
        CheckGroup.UNPAID -> R.string.ws5_chk_unpaid
        CheckGroup.LOGISTICS -> R.string.ws5_chk_logistics
        CheckGroup.DELIVERY -> R.string.ws5_chk_delivery
        CheckGroup.GEAR -> R.string.ws5_chk_gear
        CheckGroup.PICTURE_LOCK -> R.string.ws5_chk_lock
        CheckGroup.CONTENT -> R.string.ws5_chk_content
        CheckGroup.AUDITION -> R.string.ws5_chk_audition
        CheckGroup.CLEAN_TEXT -> R.string.ws5_chk_clean
    },
)

@Composable
internal fun ReviewStep(ui: WizardUi, vm: PostProjectViewModel) {
    val d = ui.draft ?: return
    val c = Ct.colors
    val prepared = vm.previewDraft(ui)
    val rows = WizardLogic.checklist(d, ui.postOnly, ui.rules)
    val roleNames = d.roles.associate { it.id to CardText.roleName(it) }
    val blockerCount = groupIssues(WizardLogic.blockers(ui.rules), roleNames).size
    var deleteAsk by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Rules checklist (§5.3): green ticks, red items that jump to the field.
        Column(
            Modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                if (blockerCount == 0) stringResource(R.string.ws5_ready) else pluralStringResource(R.plurals.ws5_fix_n_to_publish, blockerCount, blockerCount),
                style = Ct.type.titleM, color = if (blockerCount == 0) c.go else c.onDangerContainer,
            )
            Text(stringResource(R.string.ws5_rules_hint), style = Ct.type.label, color = c.textMuted)
            Spacer(Modifier.heightIn(min = 8.dp))
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.45f), thickness = 1.dp)
                CheckLine(checkGroupTitle(row.group), row.issues.isEmpty())
                groupIssues(row.issues, roleNames).forEach { (r, names) ->
                    IssueRow(r, names, onFix = { vm.jump(r) }, quickFix = if (vm.hasQuickFix(r)) ({ vm.quickFix(r) }) else null)
                }
            }
        }

        // Warnings and the AUTO relabel (shown, never blocking).
        val notes = groupIssues(WizardLogic.notes(ui.rules), roleNames)
        if (notes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepSubhead(stringResource(R.string.ws5_worth_a_look))
                notes.forEach { (r, names) -> NoteRow(r, names, onGo = { vm.jump(r) }, quickFix = if (vm.hasQuickFix(r)) ({ vm.quickFix(r) }) else null) }
            }
        }

        // One-time nudges (R22, R21).
        val r22 = ui.rules.firstOrNull { it.rule == "R22" }
        if (r22 != null && PostProjectViewModel.NUDGE_R22 !in ui.dismissed) {
            NudgeCard(
                text = r22.message, onDismiss = { vm.dismiss(PostProjectViewModel.NUDGE_R22) },
                actions = {
                    GhostButton(stringResource(R.string.ws5_add_editor), { vm.dismiss(PostProjectViewModel.NUDGE_R22); vm.startAddRole(RoleCode.EDITOR) })
                    GhostButton(stringResource(R.string.ws5_add_composer), { vm.dismiss(PostProjectViewModel.NUDGE_R22); vm.startAddRole(RoleCode.COMPOSER) })
                },
            )
        }
        ui.rules.filter { it.rule == "R21" }.distinctBy { it.roleId }.forEach { r21 ->
            val key = PostProjectViewModel.NUDGE_R21 + (r21.roleId ?: "")
            if (key !in ui.dismissed) {
                NudgeCard(
                    text = r21.message + (roleNames[r21.roleId]?.let { " ($it)" } ?: ""), onDismiss = { vm.dismiss(key) },
                    actions = { GhostButton(stringResource(R.string.ws5_change_delivery), { vm.dismiss(key); vm.jump(r21) }) },
                )
            }
        }

        // Preview carousel: each role as its real Role Card.
        if (prepared != null && prepared.roles.isNotEmpty() && ui.ownerSummary != null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepSubhead(stringResource(R.string.ws5_cards_preview))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    prepared.roles.forEach { r ->
                        RoleCard(WizardLogic.previewCard(prepared, r, ui.ownerSummary, ui.city, ui.area), Modifier.width(if (prepared.roles.size == 1) 340.dp else 300.dp))
                    }
                }
            }
        }

        // What's in it, with a way back to each step.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepSubhead(stringResource(R.string.ws5_summary))
            val p = d.project
            SummaryBlock(stringResource(R.string.ws5_step_basics), listOf(
                p.title.ifBlank { "—" },
                listOf(CardText.formatLabel(p.format), "${p.runtimeMin} min", CardText.languageNames(p.languages), CardText.scriptStatusLabel(p.scriptStatus))
                    .filter { it.isNotBlank() }.joinToString(" · "),
            ), onEdit = { vm.goTo(WizardStep.BASICS, fromReview = true) })
            val where = CardText.placeName(ui.area?.name ?: "", ui.city?.name ?: "")
            SummaryBlock(stringResource(R.string.ws5_step_when), if (ui.postOnly) listOf(where, stringResource(R.string.ws5_post_only_short)) else listOf(
                where,
                p.shootStart?.let { s -> Dates.span(s, p.shootEnd ?: s) + " · " + pluralDays(p.shootDays) + (p.hoursCap?.let { " · ≤${it}h" } ?: "") }
                    ?: stringResource(R.string.ws5_no_dates_yet),
            ), onEdit = { vm.goTo(WizardStep.WHEN_WHERE, fromReview = true) })
            SummaryBlock(stringResource(R.string.ws5_step_roles), ui.activeRoles.map { CardText.roleName(it) + " ×" + it.count + " · " + CardText.compTypeLabel(it.compType) }
                .ifEmpty { listOf(stringResource(R.string.ws5_no_roles_yet)) }, onEdit = { vm.goTo(WizardStep.ROLES, fromReview = true) })
            SummaryBlock(stringResource(R.string.ws5_step_safety), listOfNotNull(
                when {
                    p.contentFlags.isNotEmpty() -> p.contentFlags.sortedBy { it.ordinal }.joinToString(" · ") { CardText.contentFlagLabel(it) }
                    p.contentNoneConfirmed -> stringResource(R.string.ws5_none_of_these)
                    else -> stringResource(R.string.ws5_not_answered)
                },
                p.auditionMode?.takeIf { WizardLogic.hasCast(ui.activeRoles) }?.let { stringResource(R.string.ws5_auditions_by, CardText.auditionModeLabel(it)) },
            ), onEdit = { vm.goTo(WizardStep.SAFETY, fromReview = true) })
        }
        if (ListingRules.isPostOnly(ui.activeRoles) && !ui.postOnly) {
            Text(stringResource(R.string.ws5_all_remote_note), style = Ct.type.label, color = c.textMuted)
        }
        // A saved draft can go for good (it opens here from Projects, so this is where people look for it).
        if (!ui.isLive && ui.savedId != null) {
            GhostButton(stringResource(R.string.ws5_delete_draft), { deleteAsk = true }, color = c.onDangerContainer, enabled = !ui.saving && !ui.publishing)
        }
    }
    if (deleteAsk) {
        com.tff.app.ui.screens.projects.ConfirmDialog(
            title = stringResource(R.string.ws5_delete_draft_title), body = stringResource(R.string.ws5_delete_draft_body),
            confirm = stringResource(R.string.ws5_delete), danger = true,
            onConfirm = { deleteAsk = false; vm.deleteDraft() }, onDismiss = { deleteAsk = false },
        )
    }
}

/**
 * The same message on several roles reads once, with the roles listed ("LEAD ACTOR · GAFFER"). Fixes that live on the
 * project (meals, dates) come first, since one of them often clears the role items too.
 */
internal fun groupIssues(results: List<RuleResult>, roleNames: Map<String, String>): List<Pair<RuleResult, String?>> =
    results.groupBy { Triple(it.rule, it.field, it.message) }.values
        .map { g -> g.first() to g.mapNotNull { r -> r.roleId?.let(roleNames::get) }.distinct().joinToString(" · ").ifBlank { null } }
        .sortedWith(compareBy({ WizardLogic.stepOf(it.first) == WizardStep.ROLES }, { it.first.rule }))

@Composable
private fun IssueRow(r: RuleResult, roleName: String?, onFix: () -> Unit, quickFix: (() -> Unit)?) {
    val c = Ct.colors
    Column(Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CtShapes.field).clickable(role = Role.Button, onClick = onFix)
                .semantics(mergeDescendants = true) { }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (roleName != null) MonoNote(roleName.uppercase(), color = c.textMuted)
                Text(r.message, style = Ct.type.bodyS, color = c.onDangerContainer)
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ws5_fix), style = Ct.type.bodyStrong, color = c.accent)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
        if (quickFix != null) {
            SecondaryButton(quickFixLabel(r), quickFix, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun quickFixLabel(r: RuleResult): String = stringResource(if (r.rule == "R06") R.string.ws5_qf_hours else R.string.ws5_qf_meals)

@Composable
private fun NoteRow(r: RuleResult, roleName: String?, onGo: () -> Unit, quickFix: (() -> Unit)?) {
    val c = Ct.colors
    val auto = r.severity == Severity.AUTO
    val bg = if (auto) c.infoContainer else c.warnContainer
    val fg = if (auto) c.onInfoContainer else c.onWarnContainer
    Column(Modifier.fillMaxWidth().clip(CtShapes.field).background(bg)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onGo).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 10.dp)
                .semantics(mergeDescendants = true) { },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (auto) Icons.Rounded.Info else Icons.Rounded.WarningAmber, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (roleName != null) MonoNote(roleName.uppercase(), color = fg.copy(alpha = 0.8f))
                Text(r.message, style = Ct.type.bodyS, color = fg)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.ws5_go_fix), tint = fg, modifier = Modifier.size(18.dp))
        }
        if (quickFix != null) {
            GhostButton(quickFixLabel(r), quickFix, Modifier.padding(start = 30.dp), color = fg)
        }
    }
}

@Composable
private fun NudgeCard(text: String, onDismiss: () -> Unit, actions: @Composable () -> Unit) {
    val c = Ct.colors
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(c.infoContainer).border(1.dp, c.info.copy(alpha = 0.22f), CtShapes.field)
            .padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = c.info, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = Ct.type.bodyS, color = c.onInfoContainer, modifier = Modifier.weight(1f).padding(top = 2.dp))
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.ws5_not_now), tint = c.onInfoContainer, modifier = Modifier.size(18.dp)) }
        }
        Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) { actions() }
    }
}

@Composable
private fun SummaryBlock(title: String, lines: List<String>, onEdit: () -> Unit) {
    val c = Ct.colors
    Row(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.6f), CtShapes.field)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.ws5_edit), onClick = onEdit)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = Ct.type.slate.copy(fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.textMuted)
            lines.forEach { Text(it, style = Ct.type.bodyS, color = c.text) }
        }
        GhostButton(stringResource(R.string.ws5_edit), onEdit)
    }
}
