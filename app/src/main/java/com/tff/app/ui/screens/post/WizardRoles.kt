package com.tff.app.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.domain.CardText
import com.tff.app.domain.RuleResult
import com.tff.app.domain.Severity
import com.tff.app.model.CompType
import com.tff.app.model.GearPolicy
import com.tff.app.model.Level
import com.tff.app.model.PaymentTerms
import com.tff.app.model.Perk
import com.tff.app.model.ProjectRole
import com.tff.app.model.RoleStatus
import com.tff.app.model.Scarcity
import com.tff.app.model.Taxonomy
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.DateField
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.IntStepper
import com.tff.app.ui.components.MultiChipPicker
import com.tff.app.ui.components.PayLine
import com.tff.app.ui.components.RolePicker
import com.tff.app.ui.components.RolePickerMode
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.ToggleRow
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

@Composable
private fun anchor(ui: WizardUi, vm: PostProjectViewModel, key: String): Modifier =
    Modifier.focusAnchor(key, ui.focusField, ui.focusSeq, vm::clearFocus)

@Composable
fun termsShort(t: PaymentTerms): String = stringResource(
    when (t) {
        PaymentTerms.ON_WRAP -> R.string.ws5_terms_wrap
        PaymentTerms.HALF_UPFRONT -> R.string.ws5_terms_half
        PaymentTerms.WITHIN_7_DAYS -> R.string.ws5_terms_7
        PaymentTerms.WITHIN_15_DAYS -> R.string.ws5_terms_15
        PaymentTerms.WITHIN_30_DAYS -> R.string.ws5_terms_30
    },
)

@Composable
private fun amountLabel(c: CompType, postRole: Boolean): String = stringResource(
    when (c) {
        CompType.PAID_FLAT -> R.string.ws5_amount_flat
        CompType.STIPEND -> if (postRole) R.string.ws5_amount_stipend_total else R.string.ws5_amount_stipend
        else -> R.string.ws5_amount_day
    },
)

// ─────────────────────────────── Step 3 · Roles ───────────────────────────────

@Composable
internal fun RolesStep(ui: WizardUi, vm: PostProjectViewModel, onRemove: (ProjectRole) -> Unit) {
    val d = ui.draft ?: return
    val show = ui.showErrors(WizardStep.ROLES)
    val prepared = vm.previewDraft(ui)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PayForAll(ui, vm)
        Column(anchor(ui, vm, Fields.ROLES).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepSubhead(stringResource(R.string.ws5_roles_n, ui.activeRoles.size))
            if (ui.activeRoles.isEmpty()) {
                Text(stringResource(R.string.ws5_roles_empty), style = Ct.type.bodyS, color = Ct.colors.textSecondary)
                RuleMessages(ui.issues(Fields.ROLES), show)
            }
            ui.activeRoles.forEach { r ->
                val shown = prepared?.roles?.firstOrNull { it.id == r.id } ?: r
                RoleRowCard(
                    role = shown, payLine = CardText.payLine(shown, prepared?.project ?: d.project),
                    whenLine = if (shown.remoteOk && WizardLogic.isPostDept(shown.roleCode) && shown.postDeadline != null) CardText.whenLine(shown, prepared?.project ?: d.project) else null,
                    issues = WizardLogic.blockers(ui.roleIssues(r.id)).size, showIssues = show,
                    locked = r.filledCount > 0 || r.status == RoleStatus.FILLED,
                    onEdit = { vm.openRole(r.id) }, onDuplicate = { vm.duplicateRole(r.id) }, onRemove = { onRemove(r) },
                )
            }
        }
        SecondaryButton(stringResource(R.string.ws5_add_role), { vm.startAddRole() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        if (ui.postOnly) {
            Text(stringResource(R.string.ws5_post_only_roles_note), style = Ct.type.label, color = Ct.colors.textMuted)
        }
    }
}

/** "Pay for all roles": comp type, amount, terms, unpaid perks and footage weeks that every new role inherits. */
@Composable
private fun PayForAll(ui: WizardUi, vm: PostProjectViewModel) {
    val c = Ct.colors
    val pay = ui.pay
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Payments, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ws5_pay_all), style = Ct.type.slate, color = c.text)
                Text(stringResource(R.string.ws5_pay_all_hint), style = Ct.type.label, color = c.textMuted)
            }
        }
        ChoiceChips(CompType.entries, pay.compType, { CardText.compTypeLabel(it) }, { t -> vm.setPay(pay.copy(compType = t)) })
        if (pay.compType != CompType.UNPAID) {
            MoneyField(
                pay.amount, { v -> vm.setPay(pay.copy(amount = v)) }, label = amountLabel(pay.compType, false), currency = ui.currency,
                supportingText = stringResource(R.string.ws5_pay_all_amount_hint),
            )
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_terms))
                ChoiceChips(PaymentTerms.entries, pay.paymentTerms, { termsShort(it) }, { t -> vm.setPay(pay.copy(paymentTerms = t)) })
            }
        }
        if (pay.compType == CompType.UNPAID || pay.compType == CompType.STIPEND) {
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_what_people_get), hint = stringResource(if (pay.compType == CompType.UNPAID) R.string.ws5_unpaid_perks_hint else R.string.ws5_stipend_perks_hint))
                MultiChipPicker(
                    WizardLogic.ROLE_PERKS, pay.perks,
                    { pk -> vm.setPay(pay.copy(perks = if (pk in pay.perks) pay.perks - pk else pay.perks + pk)) }, label = CardText::perkLabel,
                )
            }
            if (Perk.FOOTAGE_BY_DATE in pay.perks) {
                IntStepper(pay.footageWeeks, { w -> vm.setPay(pay.copy(footageWeeks = w)) }, 1..26, label = stringResource(R.string.ws5_footage_weeks), suffix = stringResource(R.string.ws5_wks))
            }
            if (Perk.FINAL_FILM_BY_DATE in pay.perks) {
                IntStepper(pay.finalFilmWeeks, { w -> vm.setPay(pay.copy(finalFilmWeeks = w)) }, 1..52, label = stringResource(R.string.ws5_final_film_weeks), suffix = stringResource(R.string.ws5_wks))
            }
        }
        val editable = ui.activeRoles.count { it.filledCount == 0 && it.status == RoleStatus.OPEN }
        if (editable > 0 && WizardLogic.payDiffers(ui.activeRoles.filter { it.filledCount == 0 }, pay)) {
            GhostButton(stringResource(R.string.ws5_apply_to_all, editable), vm::applyPayToAll, icon = Icons.Rounded.CheckCircle)
        }
    }
}

@Composable
private fun RoleRowCard(
    role: ProjectRole,
    payLine: String,
    whenLine: String?,
    issues: Int,
    showIssues: Boolean,
    locked: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = Ct.colors
    val bad = showIssues && issues > 0
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(c.surface)
            .border(1.dp, if (bad) c.stop.copy(alpha = 0.6f) else c.outline.copy(alpha = 0.8f), CtShapes.field)
            .clickable(enabled = !locked, role = Role.Button, onClickLabel = stringResource(R.string.ws5_edit_role), onClick = onEdit)
            .padding(start = 16.dp, top = 12.dp, bottom = 6.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(CardText.roleLabel(role), style = Ct.type.slate, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                PayLine(payLine, paid = role.isPaid)
                if (whenLine != null) {
                    Spacer(Modifier.height(4.dp))
                    MonoNote(whenLine)
                }
            }
            if (!locked) {
                IconButton(onClick = onDuplicate) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.ws5_duplicate_cd, CardText.roleName(role)), tint = c.textSecondary) }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.ws5_remove_cd, CardText.roleName(role)), tint = c.textSecondary) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                locked -> {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ws5_role_locked), style = Ct.type.label, color = c.textMuted)
                }
                bad -> {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.stop, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pluralStringResource(R.plurals.ws5_role_fix_n, issues, issues), style = Ct.type.label, color = c.onDangerContainer, modifier = Modifier.weight(1f))
                }
                else -> {
                    Text(stringResource(R.string.ws5_tap_to_edit), style = Ct.type.label, color = c.textMuted, modifier = Modifier.weight(1f))
                }
            }
            if (!locked) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────── Role editor ───────────────────────────────

/** The role picker (new role, or [Change role] after Duplicate). Post-only projects list remote post roles only. */
@Composable
internal fun RolePickerPane(ui: WizardUi, vm: PostProjectViewModel) {
    val current = ui.editingRole?.takeIf { ui.changingCode }?.roleCode
    val roles = if (ui.postOnly) Taxonomy.all.filter { it.code in WizardLogic.postOnlyRoleCodes() } else Taxonomy.all
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (ui.changingCode) R.string.ws5_pick_role_change else R.string.ws5_pick_role), style = Ct.type.titleL, color = Ct.colors.text)
        Text(
            stringResource(if (ui.postOnly) R.string.ws5_pick_role_hint_post else R.string.ws5_pick_role_hint),
            style = Ct.type.bodyS, color = Ct.colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        RolePicker(selected = setOfNotNull(current), onToggle = vm::pickCode, mode = RolePickerMode.SINGLE, roles = roles)
    }
}

@Composable
internal fun RoleEditor(ui: WizardUi, vm: PostProjectViewModel) {
    val r = ui.editingRole ?: return
    val d = ui.draft ?: return
    val c = Ct.colors
    val info = Taxonomy.info(r.roleCode)
    val issues = ui.roleIssues(r.id)
    val show = ui.showErrors(WizardStep.ROLES)
    var more by rememberSaveable(r.id) { mutableStateOf(false) }
    val postDept = WizardLogic.isPostDept(r.roleCode)
    val postRole = r.remoteOk && postDept
    fun field(key: String) = issues.filter { it.field == key }
    val update: ((ProjectRole) -> ProjectRole) -> Unit = { f -> vm.updateRole(r.id, f) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Header: the role and its plain duties line (F3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(info.label, style = Ct.type.titleL, color = c.text)
                Text(info.duties, style = Ct.type.bodyS, color = c.textSecondary)
            }
            GhostButton(stringResource(R.string.ws5_change_role), vm::startChangeCode)
        }
        IntStepper(r.count, { n -> update { it.copy(count = n) } }, 1..10, label = stringResource(R.string.ws5_how_many))

        // Pay
        Column(anchor(ui, vm, Fields.AMOUNT).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepSubhead(stringResource(R.string.ws5_pay))
            ChoiceChips(CompType.entries, r.compType, { CardText.compTypeLabel(it) }, { t ->
                update { it.copy(compType = t, paymentTerms = if (t == CompType.UNPAID) null else it.paymentTerms ?: ui.pay.paymentTerms) }
            })
            if (r.compType != CompType.UNPAID) {
                MoneyField(
                    r.amountMin ?: r.amountMax, { v -> update { it.copy(amountMin = v, amountMax = v) } }, label = amountLabel(r.compType, postRole),
                    currency = r.currency, errorText = blockText(field(Fields.AMOUNT), show),
                )
                RuleMessages(field(Fields.AMOUNT).filter { it.severity != Severity.BLOCK }, show)
                Column(anchor(ui, vm, Fields.TERMS).fillMaxWidth()) {
                    FieldLabel(stringResource(R.string.ws5_terms), error = show && field(Fields.TERMS).isNotEmpty())
                    ChoiceChips(PaymentTerms.entries, r.paymentTerms, { termsShort(it) }, { t -> update { it.copy(paymentTerms = t) } })
                    RuleMessages(field(Fields.TERMS), show)
                }
            }
            if (r.compType == CompType.UNPAID || r.compType == CompType.STIPEND) {
                Column(anchor(ui, vm, Fields.PERKS).fillMaxWidth()) {
                    FieldLabel(
                        stringResource(R.string.ws5_what_people_get), error = show && field(Fields.PERKS).any { it.severity == Severity.BLOCK },
                        hint = stringResource(if (r.compType == CompType.UNPAID) R.string.ws5_unpaid_perks_hint else R.string.ws5_stipend_perks_hint),
                    )
                    MultiChipPicker(WizardLogic.ROLE_PERKS, r.perks, { pk ->
                        update { it.copy(perks = if (pk in it.perks) it.perks - pk else it.perks + pk,
                            footageDueWeeks = if (pk == Perk.FOOTAGE_BY_DATE && pk !in it.perks) it.footageDueWeeks ?: ui.pay.footageWeeks else it.footageDueWeeks,
                            finalFilmDueWeeks = if (pk == Perk.FINAL_FILM_BY_DATE && pk !in it.perks) it.finalFilmDueWeeks ?: ui.pay.finalFilmWeeks else it.finalFilmDueWeeks) }
                    }, label = CardText::perkLabel)
                    RuleMessages(field(Fields.PERKS), show)
                }
                if (Perk.FOOTAGE_BY_DATE in r.perks) {
                    Column(anchor(ui, vm, Fields.FOOTAGE).fillMaxWidth()) {
                        IntStepper(r.footageDueWeeks ?: WizardLogic.DEFAULT_FOOTAGE_WEEKS, { w -> update { it.copy(footageDueWeeks = w) } }, 1..26,
                            label = stringResource(R.string.ws5_footage_weeks), suffix = stringResource(R.string.ws5_wks))
                        RuleMessages(field(Fields.FOOTAGE), show)
                    }
                } else if (field(Fields.FOOTAGE).isNotEmpty()) {
                    RuleMessages(field(Fields.FOOTAGE), show, anchor(ui, vm, Fields.FOOTAGE))
                }
                if (Perk.FINAL_FILM_BY_DATE in r.perks) {
                    IntStepper(r.finalFilmDueWeeks ?: WizardLogic.DEFAULT_FINAL_FILM_WEEKS, { w -> update { it.copy(finalFilmDueWeeks = w) } }, 1..52,
                        label = stringResource(R.string.ws5_final_film_weeks), suffix = stringResource(R.string.ws5_wks))
                }
            }
            // Scarcity nudge (§3.5): scarce roles posted unpaid.
            if (info.scarcity == Scarcity.SCARCE && r.compType == CompType.UNPAID) {
                InfoTipCard(
                    text = stringResource(if (Taxonomy.dept(r.roleCode) == com.tff.app.model.Dept.SOUND) R.string.ws5_nudge_sound else R.string.ws5_nudge_scarce),
                    actionLabel = stringResource(R.string.ws5_make_stipend),
                    onAction = { update { it.copy(compType = CompType.STIPEND, paymentTerms = it.paymentTerms ?: ui.pay.paymentTerms) } },
                    icon = Icons.Rounded.Payments,
                )
            }
            // Issues on this role whose field lives elsewhere (R02 meals → step 2), with a link there.
            issues.filter { it.field == Fields.MEALS || it.field == Fields.LOCK && !postDept }.distinctBy { it.message }.forEach { res ->
                ElsewhereIssue(res, show, onGo = { vm.jump(res) })
            }
        }

        // Gear (camera, sound, G&E): who brings it
        if (WizardLogic.isGearRole(r.roleCode)) {
            Column(anchor(ui, vm, Fields.GEAR).fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_gear), error = show && field(Fields.GEAR).any { it.severity == Severity.BLOCK })
                ChoiceChips(GearPolicy.entries, r.gearPolicy, { CardText.gearPolicyLabel(it) }, { g -> update { it.copy(gearPolicy = g) } })
                RuleMessages(field(Fields.GEAR), show)
            }
            if (r.gearPolicy == GearPolicy.BYO_WITH_KIT_FEE) {
                MoneyField(r.kitFeeDay, { v -> update { it.copy(kitFeeDay = v) } }, label = stringResource(R.string.ws5_kit_fee), currency = r.currency)
            }
        }

        // Remote post roles: delivery date (+ picture lock for music and sound design)
        if (postDept) {
            ToggleRow(
                stringResource(R.string.ws5_remote), r.remoteOk, { on -> update { it.copy(remoteOk = on, postDeadline = if (on) it.postDeadline else null) } },
                subtitle = stringResource(R.string.ws5_remote_hint), enabled = !ui.postOnly,
            )
            if (r.remoteOk) {
                DateField(
                    label = stringResource(R.string.ws5_deliver_by), value = r.postDeadline, onValueChange = { dl -> update { it.copy(postDeadline = dl) } },
                    minDate = ui.today.plusDays(7), supportingText = stringResource(R.string.ws5_deliver_by_hint),
                    errorText = blockText(field(Fields.DEADLINE), show), modifier = anchor(ui, vm, Fields.DEADLINE),
                )
                RuleMessages(field(Fields.DEADLINE).filter { it.severity != Severity.BLOCK }, show)
            }
            if (CardText.needsPictureLock(r.roleCode)) PictureLockField(ui, vm, show)
        }

        // Oversupplied roles: suggest a screening question (§3.5)
        if (info.scarcity == Scarcity.OVERSUPPLIED && r.screeningQuestion.isNullOrBlank()) {
            val suggestion = stringResource(if (r.isCast) R.string.ws5_screening_suggest_cast else R.string.ws5_screening_suggest)
            InfoTipCard(
                text = stringResource(R.string.ws5_nudge_screening), icon = Icons.Rounded.QuestionAnswer,
                actionLabel = stringResource(R.string.ws5_add_question), onAction = { update { it.copy(screeningQuestion = suggestion) }; more = true },
            )
        }

        MoreDetails(more || ui.focusField in Fields.ROLE_MORE, { more = !more }, summary = stringResource(R.string.ws5_role_more_summary)) {
            CtTextField(
                r.creditTitle, { t -> update { it.copy(creditTitle = t) } }, label = stringResource(R.string.ws5_credit_title),
                supportingText = stringResource(R.string.ws5_credit_title_hint), maxLength = 40, singleLine = true,
                errorText = blockText(field(Fields.CREDIT), true), modifier = anchor(ui, vm, Fields.CREDIT),
            )
            ScopeEditor(
                label = stringResource(R.string.ws5_scope), suggestions = info.scopeSuggestions, values = r.scopeIncludes,
                onChange = { v -> update { it.copy(scopeIncludes = v) } }, modifier = anchor(ui, vm, Fields.SCOPE),
            )
            RuleMessages(field(Fields.SCOPE), true)
            ScopeEditor(
                label = stringResource(R.string.ws5_scope_ex), suggestions = emptyList(), values = r.scopeExcludes,
                onChange = { v -> update { it.copy(scopeExcludes = v) } }, modifier = anchor(ui, vm, Fields.SCOPE_EX),
            )
            ToggleRow(stringResource(R.string.ws5_step_up), r.stepUpFriendly, { on -> update { it.copy(stepUpFriendly = on) } }, subtitle = stringResource(R.string.ws5_step_up_hint))
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_level_wanted), hint = stringResource(R.string.ws5_level_wanted_hint))
                val anyone = stringResource(R.string.ws5_anyone)
                ChoiceChips(listOf<Level?>(null) + Level.entries, r.levelWanted, { l -> l?.let(CardText::levelLabel) ?: anyone }, { l -> update { it.copy(levelWanted = l) } })
            }
            if (!(WizardLogic.isGearRole(r.roleCode) && r.gearPolicy == GearPolicy.BYO_WITH_KIT_FEE)) {
                MoneyField(r.kitFeeDay, { v -> update { it.copy(kitFeeDay = v) } }, label = stringResource(R.string.ws5_kit_fee), currency = r.currency,
                    supportingText = stringResource(R.string.ws5_kit_fee_hint))
            }
            val details = Taxonomy.detailFields(info.detailsFamily)
            if (details.isNotEmpty()) {
                Column(anchor(ui, vm, Fields.DETAILS).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepSubhead(stringResource(R.string.ws5_role_details))
                    details.forEach { (key, label) ->
                        CtTextField(
                            r.roleDetails[key] ?: "", { t -> update { it.copy(roleDetails = it.roleDetails + (key to t)) } }, label = label,
                            maxLength = 60, showCounter = false, singleLine = true,
                        )
                    }
                    RuleMessages(field(Fields.DETAILS), true)
                }
            }
            CtTextField(
                r.screeningQuestion ?: "", { t -> update { it.copy(screeningQuestion = t) } }, label = stringResource(R.string.ws5_screening),
                supportingText = stringResource(if (r.compType == CompType.UNPAID) R.string.ws5_screening_hint_unpaid else R.string.ws5_screening_hint),
                maxLength = 140, minLines = 2, errorText = blockText(field(Fields.SCREENING), true), modifier = anchor(ui, vm, Fields.SCREENING),
            )
            RuleMessages(field(Fields.SCREENING).filter { it.severity != Severity.BLOCK }, true)
            IntStepper(r.applicationCap, { n -> update { it.copy(applicationCap = n) } }, 5..40, label = stringResource(R.string.ws5_cap))
            Text(stringResource(R.string.ws5_cap_hint, Taxonomy.defaultCap(r.roleCode)), style = Ct.type.label, color = c.textMuted)
        }
        // Warnings on public text (R12/R14/R15/R16) that aren't tied to a visible field above.
        val other = issues.filter { it.field !in setOf(Fields.AMOUNT, Fields.TERMS, Fields.PERKS, Fields.FOOTAGE, Fields.GEAR, Fields.DEADLINE, Fields.MEALS, Fields.LOCK, Fields.CREDIT, Fields.SCOPE, Fields.SCREENING, Fields.DETAILS) }
        RuleMessages(other, show)
        if (d.roles.size > 0) Spacer(Modifier.height(8.dp))
    }
}

/** A rule on this role that is fixed on another step ("If you can't pay people, feed people" → meals on step 2). */
@Composable
private fun ElsewhereIssue(r: RuleResult, show: Boolean, onGo: () -> Unit) {
    val c = Ct.colors
    val danger = r.severity == Severity.BLOCK
    if (danger && !show) return
    Row(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(if (danger) c.dangerContainer else c.warnContainer)
            .clickable(role = Role.Button, onClick = onGo).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = if (danger) c.stop else c.onWarnContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(r.message, style = Ct.type.bodyS, color = if (danger) c.onDangerContainer else c.onWarnContainer, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.ws5_go_fix), tint = if (danger) c.onDangerContainer else c.onWarnContainer)
    }
}

/** Scope chips (§6.3): taxonomy suggestions plus custom chips of up to 30 characters. */
@Composable
private fun ScopeEditor(label: String, suggestions: List<String>, values: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        val t = draft.trim()
        if (t.isNotEmpty() && values.none { it.equals(t, ignoreCase = true) }) onChange(values + t)
        draft = ""
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        val all = (suggestions + values).distinct()
        if (all.isNotEmpty()) {
            ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
                all.forEach { s ->
                    val on = s in values
                    CtFilterChip(s, selected = on, onClick = { onChange(if (on) values - s else values + s) })
                }
            }
        }
        CtTextField(
            draft, { draft = it.take(30) }, label = null, placeholder = stringResource(R.string.ws5_scope_add_ph), singleLine = true,
            maxLength = 30, showCounter = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
            trailing = if (draft.isNotBlank()) ({ IconButton(onClick = add) { Icon(Icons.Rounded.Add, stringResource(R.string.ws5_scope_add_cd), tint = Ct.colors.accent) } }) else null,
        )
    }
}

/** "R04" note for the preview: paid below the floor shows as Stipend. */
internal fun relabelled(original: ProjectRole, prepared: ProjectRole?): Boolean =
    prepared != null && original.isPaid && prepared.compType == CompType.STIPEND

/** Roles shown by the remove dialog: how many people would get a closure. */
internal fun closureCount(r: ProjectRole): Int = r.applicationsCount
