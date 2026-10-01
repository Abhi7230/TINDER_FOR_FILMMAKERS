package com.tff.app.ui.screens.projects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.domain.CardText
import com.tff.app.domain.WrapItemsFactory
import com.tff.app.model.MatchState
import com.tff.app.model.ProjectRole
import com.tff.app.model.ProjectStatus
import com.tff.app.model.RoleStatus
import com.tff.app.model.ShootDayFull
import com.tff.app.model.TeamEntry
import com.tff.app.model.WrapItem
import com.tff.app.model.WrapKind
import com.tff.app.model.WrapStatus
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.DangerButton
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.KeyValueRow
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PayLine
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.shortRoleLabel
import com.tff.app.ui.screens.post.ChoiceChips
import com.tff.app.ui.screens.post.MonoNote
import com.tff.app.ui.screens.post.ShootDatesField
import com.tff.app.ui.screens.post.pluralDays
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

// ─────────────────────────────── shared bits ───────────────────────────────

@Composable
internal fun DashCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Ct.colors
    Column(
        modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun wrapKindLabel(k: WrapKind): String = stringResource(
    when (k) {
        WrapKind.PAID -> R.string.ws5_wk_paid
        WrapKind.FOOTAGE -> R.string.ws5_wk_footage
        WrapKind.FINAL_FILM -> R.string.ws5_wk_final
        WrapKind.CREDIT -> R.string.ws5_wk_credit
        WrapKind.DELIVERY -> R.string.ws5_wk_delivery
    },
)

// ─────────────────────────────── Roles ───────────────────────────────

@Composable
internal fun RolesTab(d: DashData, vm: ProjectDashboardViewModel, nav: AppNavigator, busy: Boolean, onShare: () -> Unit) {
    var closeAsk by remember { mutableStateOf<ProjectRole?>(null) }
    val p = d.project
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (p.status == ProjectStatus.DRAFT) {
            InfoTipCard(
                stringResource(R.string.ws5_dash_draft_tip), title = stringResource(R.string.ws5_dash_draft_title),
                actionLabel = stringResource(R.string.ws5_finish_publish), onAction = { nav.toPostProject(p.id) },
            )
        }
        val order = mapOf(RoleStatus.OPEN to 0, RoleStatus.FILLED to 1, RoleStatus.CLOSED to 2)
        d.full.roles.sortedBy { order[it.status] ?: 3 }.forEach { r ->
            RoleDashCard(
                role = r, d = d,
                onCrewUp = { nav.toCrewUp(r.id) },
                onShare = onShare,
                onEdit = if (d.canEdit && r.status == RoleStatus.OPEN && r.filledCount == 0) ({ nav.toPostProject(p.id) }) else null,
                onClose = if (r.status == RoleStatus.OPEN && p.status in setOf(ProjectStatus.OPEN, ProjectStatus.CREWED)) ({ closeAsk = r }) else null,
            )
        }
        if (d.full.roles.isEmpty()) {
            EmptyState(title = stringResource(R.string.ws5_no_roles_title), body = stringResource(R.string.ws5_no_roles_body), icon = Icons.Rounded.Groups)
        }
        if (d.canEdit) {
            SecondaryButton(stringResource(R.string.ws5_add_role), { nav.toPostProject(p.id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }
    closeAsk?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.ws5_close_role_title, CardText.roleName(r)),
            body = stringResource(R.string.ws5_close_role_body),
            confirm = stringResource(R.string.ws5_close_role), danger = true,
            onConfirm = { vm.closeRole(r.id); closeAsk = null }, onDismiss = { closeAsk = null },
        )
    }
}

@Composable
private fun RoleDashCard(role: ProjectRole, d: DashData, onCrewUp: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onClose: (() -> Unit)?) {
    val c = Ct.colors
    val p = d.project
    val live = p.status == ProjectStatus.OPEN || p.status == ProjectStatus.CREWED
    DashCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CardText.roleLabel(role), style = Ct.type.slate, color = c.accent, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusChip(role.status)
        }
        PayLine(CardText.payLine(role, p), paid = role.isPaid)
        if (role.isPostRole) MonoNote(CardText.whenLine(role, p))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TimecodeText(stringResource(R.string.ws5_applied_of, role.applicationsCount, role.applicationCap), style = Ct.type.monoS, color = c.textSecondary)
            TimecodeText(
                stringResource(R.string.ws5_filled_of, minOf(role.filledCount, role.count), role.count), style = Ct.type.monoS,
                color = if (role.filledCount >= role.count) c.go else c.textSecondary,
            )
        }
        val backups = d.full.backups[role.id].orEmpty().size
        if (backups > 0) MonoNote(pluralStringResource(R.plurals.ws5_backups_n, backups, backups), color = c.info)
        ChipFlow(spacing = 4.dp, lineSpacing = 0.dp) {
            if (live && role.status == RoleStatus.OPEN) SecondaryButton(stringResource(R.string.ws5_crew_up), onCrewUp, icon = Icons.Rounded.Groups)
            if (live && role.status == RoleStatus.OPEN) GhostButton(stringResource(R.string.ws5_share), onShare)
            if (onEdit != null) GhostButton(stringResource(R.string.ws5_edit), onEdit)
            if (onClose != null) GhostButton(stringResource(R.string.ws5_close), onClose, color = c.onDangerContainer)
        }
    }
}

// ─────────────────────────────── Team & backups ───────────────────────────────

@Composable
internal fun TeamTab(d: DashData, vm: ProjectDashboardViewModel, nav: AppNavigator, busy: Boolean) {
    val c = Ct.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PersonLine(d.full.owner.shortName, initials(d.full.owner.displayName), stringResource(R.string.ws5_you_payer), highlight = true)
        if (d.full.team.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.ws5_team_empty_title), body = stringResource(R.string.ws5_team_empty_body), icon = Icons.Rounded.Groups,
            ) {
                d.full.roles.firstOrNull { it.status == RoleStatus.OPEN }?.let { r -> SecondaryButton(stringResource(R.string.ws5_open_crew_up), { nav.toCrewUp(r.id) }) }
            }
        }
        d.full.team.forEach { t -> TeamRow(t, onClick = { nav.toChat(t.match.id) }) }

        val withBackups = d.full.roles.filter { d.full.backups[it.id].orEmpty().isNotEmpty() }
        SectionHeader(stringResource(R.string.ws5_backups), count = withBackups.sumOf { d.full.backups[it.id].orEmpty().size }.takeIf { it > 0 })
        if (withBackups.isEmpty()) {
            Text(stringResource(R.string.ws5_backups_empty), style = Ct.type.bodyS, color = c.textSecondary)
        }
        withBackups.forEach { r -> BackupSection(r, d, busy, onOffer = { name -> vm.offerSlot(r.id, name) }) }
    }
}

@Composable
private fun TeamRow(t: TeamEntry, onClick: () -> Unit) {
    val c = Ct.colors
    val p = t.person.profile
    DashCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(p.initials, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.shortName, style = Ct.type.bodyStrong, color = c.text)
                Text(shortRoleLabel(t.roleCode), style = Ct.type.bodyS, color = c.textSecondary)
            }
            StatusChip(t.match.state)
        }
        Row(Modifier.padding(start = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (t.nextAck) {
                true -> CtChip(stringResource(R.string.ws5_ack_got_it), tone = ChipTone.GO, small = true, icon = Icons.Rounded.CheckCircle)
                false -> CtChip(stringResource(R.string.ws5_ack_not_yet), tone = ChipTone.WARN, small = true, icon = Icons.Rounded.Schedule)
                null -> if (!t.match.isPostRole) CtChip(stringResource(R.string.ws5_ack_no_sheet), small = true)
            }
            if (t.wrapTotal > 0) CtChip(stringResource(R.string.ws5_wrap_progress, t.wrapDone, t.wrapTotal), small = true, monoNumbers = true)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = stringResource(R.string.ws5_open_chat_cd, p.shortName), tint = c.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BackupSection(r: ProjectRole, d: DashData, busy: Boolean, onOffer: (String) -> Unit) {
    val c = Ct.colors
    val list = d.full.backups[r.id].orEmpty()
    var open by rememberSaveable(r.id) { mutableStateOf(true) }
    val angle by animateFloatAsState(if (open) 180f else 0f, label = "bk")
    val slotOpen = r.status != RoleStatus.CLOSED && r.filledCount < r.count
    DashCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(CardText.roleName(r).uppercase(), style = Ct.type.slate, color = c.text, modifier = Modifier.weight(1f))
            MonoNote("${list.size}")
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.rotate(angle))
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEachIndexed { i, b ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
                        TimecodeText("#${i + 1}", style = Ct.type.mono, color = c.accent)
                        Spacer(Modifier.width(10.dp))
                        Monogram(b.card.profile.initials, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.card.profile.shortName, style = Ct.type.bodyStrong, color = c.text)
                            if (b.reasons.isNotEmpty()) Text(b.reasons.take(2).joinToString(" · "), style = Ct.type.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (slotOpen) {
                    val first = list.first().card.profile.shortName
                    PrimaryButton(stringResource(R.string.ws5_offer_slot, first), { onOffer(first) }, Modifier.fillMaxWidth(), loading = busy)
                    Text(stringResource(R.string.ws5_offer_slot_hint), style = Ct.type.label, color = c.textMuted)
                } else {
                    Text(stringResource(R.string.ws5_slot_filled), style = Ct.type.label, color = c.textMuted)
                }
            }
        }
    }
}

@Composable
private fun PersonLine(name: String, initials: String, sub: String, highlight: Boolean = false) {
    val c = Ct.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        Monogram(initials, size = 40.dp, highlight = highlight)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = Ct.type.bodyStrong, color = c.text)
            Text(sub, style = Ct.type.bodyS, color = c.textSecondary)
        }
    }
}

internal fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

// ─────────────────────────────── Shoot days ───────────────────────────────

@Composable
internal fun DaysTab(d: DashData, nav: AppNavigator) {
    val c = Ct.colors
    val p = d.project
    val booked = d.bookedOnSet.size
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (booked == 0) stringResource(R.string.ws5_days_hint_nobody) else pluralStringResource(R.plurals.ws5_days_hint, booked, booked),
            style = Ct.type.bodyS, color = c.textSecondary,
        )
        val sheets = d.days
        p.shootDates.sorted().forEach { date ->
            val sheet = sheets.firstOrNull { it.day.day == date }
            if (sheet != null) DayCard(sheet, onEdit = { nav.toShootDayEditor(p.id, sheet.day.id) }, onView = { nav.toShootDayView(p.id, sheet.day.id) })
            else MissingDayCard(date, past = date.isBefore(d.today), onAdd = { nav.toShootDayEditor(p.id, null) })
        }
        if (p.shootDates.isEmpty()) {
            EmptyState(title = stringResource(R.string.ws5_no_dates_title), body = stringResource(R.string.ws5_no_dates_body), icon = Icons.Rounded.EventBusy)
        } else if (p.shootDates.any { dt -> sheets.none { it.day.day == dt } && !dt.isBefore(d.today) }) {
            SecondaryButton(stringResource(R.string.ws5_add_day_sheet), { nav.toShootDayEditor(p.id, null) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun DayCard(s: ShootDayFull, onEdit: () -> Unit, onView: () -> Unit) {
    val c = Ct.colors
    val acked = s.calls.count { it.call.ackAt != null }
    val total = s.calls.size
    DashCard(onClick = onView) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ws5_day_n_of, s.dayIndex, s.dayCount), style = Ct.type.slate, color = c.accent)
            Spacer(Modifier.width(10.dp))
            TimecodeText(Dates.shortUpper(s.day.day), style = Ct.type.mono, color = c.text, modifier = Modifier.weight(1f))
            if (s.day.version > 1) CtChip(stringResource(R.string.ws5_updated_v, s.day.version), small = true, monoNumbers = true)
        }
        TimecodeText(stringResource(R.string.ws5_call_wrap, Dates.time(s.day.generalCall), Dates.time(s.day.wrapBy)), style = Ct.type.monoS, color = c.textSecondary)
        Text(s.day.address.lines().joinToString(", "), style = Ct.type.bodyS, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (total > 0) CtChip(
                stringResource(R.string.ws5_got_it_n, acked, total), tone = if (acked == total) ChipTone.GO else ChipTone.WARN, small = true, monoNumbers = true,
            ) else CtChip(stringResource(R.string.ws5_nobody_on_sheet), small = true)
            Spacer(Modifier.weight(1f))
            GhostButton(stringResource(R.string.ws5_edit), onEdit)
        }
    }
}

@Composable
private fun MissingDayCard(date: java.time.LocalDate, past: Boolean, onAdd: () -> Unit) {
    val c = Ct.colors
    DashCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimecodeText(Dates.shortUpper(date), style = Ct.type.mono, color = c.text, modifier = Modifier.weight(1f))
            CtChip(stringResource(R.string.ws5_no_sheet_yet), small = true)
        }
        if (!past) GhostButton(stringResource(R.string.ws5_add_day_sheet), onAdd, icon = Icons.Rounded.Add)
    }
}

// ─────────────────────────────── Wrap ───────────────────────────────

@Composable
internal fun WrapTab(d: DashData, vm: ProjectDashboardViewModel, nav: AppNavigator, busy: Boolean) {
    val c = Ct.colors
    val p = d.project
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d.wrap.isEmpty()) {
            EmptyState(title = stringResource(R.string.ws5_wrap_empty_title), body = stringResource(R.string.ws5_wrap_empty_body), icon = Icons.Rounded.Inventory2)
        }
        d.wrap.forEach { g ->
            val done = g.items.count { it.status == WrapStatus.CONFIRMED }
            val overdue = g.items.count { WrapItemsFactory.isOverdue(it, d.today) }
            DashCard(onClick = { nav.toWrap(projectId = p.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Monogram(g.person.profile.initials, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.person.profile.shortName, style = Ct.type.bodyStrong, color = c.text)
                        Text(shortRoleLabel(g.roleCode), style = Ct.type.bodyS, color = c.textSecondary)
                    }
                    TimecodeText(stringResource(R.string.ws5_done_of, done, g.items.size), style = Ct.type.monoS, color = if (done == g.items.size) c.go else c.textSecondary)
                }
                ChipFlow(spacing = 6.dp, lineSpacing = 6.dp) {
                    g.items.forEach { it -> WrapItemChip(it, d.today) }
                }
                if (overdue > 0) CtChip(pluralStringResource(R.plurals.ws5_wrap_overdue, overdue, overdue), tone = ChipTone.STOP, filled = true, small = true, monoNumbers = true)
            }
        }
        if (d.wrap.isNotEmpty()) PrimaryButton(stringResource(R.string.ws5_open_wrap), { nav.toWrap(projectId = p.id) }, Modifier.fillMaxWidth())
        if (p.status in setOf(ProjectStatus.OPEN, ProjectStatus.CREWED) && d.lastShootPassed) {
            SecondaryButton(stringResource(R.string.ws5_mark_wrapped), vm::markWrapped, Modifier.fillMaxWidth(), loading = busy)
            Text(stringResource(R.string.ws5_mark_wrapped_hint), style = Ct.type.label, color = c.textMuted)
        }
    }
}

@Composable
private fun WrapItemChip(item: WrapItem, today: java.time.LocalDate) {
    val overdue = WrapItemsFactory.isOverdue(item, today)
    val tone = when {
        overdue -> ChipTone.STOP
        item.status == WrapStatus.CONFIRMED -> ChipTone.GO
        item.status == WrapStatus.NOT_RECEIVED -> ChipTone.STOP
        item.status == WrapStatus.DONE -> ChipTone.INFO
        else -> ChipTone.NEUTRAL
    }
    val due = item.dueDate?.let { " · " + Dates.dayMonth(it).uppercase() } ?: ""
    CtChip(wrapKindLabel(item.kind) + due, tone = tone, small = true, monoNumbers = true)
}

// ─────────────────────────────── Settings ───────────────────────────────

@Composable
internal fun SettingsTab(d: DashData, vm: ProjectDashboardViewModel, nav: AppNavigator, busy: Boolean, onShare: () -> Unit) {
    val c = Ct.colors
    val p = d.project
    var reschedule by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    val live = p.status == ProjectStatus.OPEN || p.status == ProjectStatus.CREWED
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.ws5_listing))
        if (d.canEdit) SecondaryButton(stringResource(R.string.ws5_edit_listing), { nav.toPostProject(p.id) }, Modifier.fillMaxWidth())
        if (live) SecondaryButton(stringResource(R.string.ws5_share_roles), onShare, Modifier.fillMaxWidth())
        if (!live && p.status != ProjectStatus.DRAFT) Text(stringResource(R.string.ws5_settings_closed), style = Ct.type.bodyS, color = c.textSecondary)

        if (live && !d.postOnly && vm.features.reschedule && p.shootDates.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SectionHeader(stringResource(R.string.ws5_dates))
            KeyValueRow(stringResource(R.string.ws5_shoot_dates), datesLine(p.shootDates), mono = true)
            KeyValueRow(stringResource(R.string.ws5_rescheduled), "${p.rescheduleCount}×", mono = true)
            SecondaryButton(stringResource(R.string.ws5_reschedule), { reschedule = true }, Modifier.fillMaxWidth())
            Text(stringResource(R.string.ws5_reschedule_hint), style = Ct.type.label, color = c.textMuted)
        }
        if (live) {
            Spacer(Modifier.height(4.dp))
            SectionHeader(stringResource(R.string.ws5_cancel_section))
            Text(stringResource(R.string.ws5_cancel_hint), style = Ct.type.bodyS, color = c.textSecondary)
            DangerButton(stringResource(R.string.ws5_cancel_project), { cancel = true }, Modifier.fillMaxWidth())
        }
        if (p.status == ProjectStatus.DRAFT) {
            Spacer(Modifier.height(4.dp))
            SectionHeader(stringResource(R.string.ws5_draft_section))
            DangerButton(stringResource(R.string.ws5_delete_draft), { deleteAsk = true }, Modifier.fillMaxWidth(), loading = busy)
        }
    }
    if (reschedule) RescheduleSheet(d, busy, onConfirm = { vm.reschedule(it); reschedule = false }, onDismiss = { reschedule = false })
    if (cancel) CancelSheet(d, busy, onConfirm = { vm.cancelProject(it); cancel = false }, onDismiss = { cancel = false })
    if (deleteAsk) {
        ConfirmDialog(
            title = stringResource(R.string.ws5_delete_draft_title), body = stringResource(R.string.ws5_delete_draft_body),
            confirm = stringResource(R.string.ws5_delete), danger = true,
            onConfirm = { vm.deleteDraft(); deleteAsk = false }, onDismiss = { deleteAsk = false },
        )
    }
}

@Composable
private fun datesLine(dates: List<java.time.LocalDate>): String {
    val s = dates.min()
    val e = dates.max()
    return (if (s == e) Dates.shortUpper(s) else Dates.span(s, e).uppercase()) + " · " + pluralDays(dates.size).uppercase()
}

/** F12: new dates → every booked person gets a new booking-card version; everyone waiting hears too. */
@Composable
private fun RescheduleSheet(d: DashData, busy: Boolean, onConfirm: (List<java.time.LocalDate>) -> Unit, onDismiss: () -> Unit) {
    val p = d.project
    var dates by remember { mutableStateOf(p.shootDates.filter { !it.isBefore(d.today) }.ifEmpty { emptyList() }) }
    val booked = d.full.team.count { it.match.state == MatchState.BOOKED && !it.match.isPostRole }
    CtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ws5_reschedule_title), subtitle = stringResource(R.string.ws5_reschedule_sub)) {
        ShootDatesField(dates, { dates = it }, today = d.today)
        Spacer(Modifier.height(16.dp))
        InfoTipCard(
            if (booked > 0) pluralStringResource(R.plurals.ws5_reschedule_booked, booked, booked) else stringResource(R.string.ws5_reschedule_nobody),
        )
        if (p.rescheduleCount >= 1) {
            Spacer(Modifier.height(10.dp))
            InfoTipCard(stringResource(R.string.ws5_reschedule_twice), icon = Icons.Rounded.Schedule)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            stringResource(R.string.ws5_send_new_dates), { onConfirm(dates) }, Modifier.fillMaxWidth(),
            enabled = dates.isNotEmpty() && dates.sorted() != p.shootDates.sorted(), loading = busy,
        )
    }
}

@Composable
private fun CancelSheet(d: DashData, busy: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val reasons = listOf(R.string.ws5_cr_location, R.string.ws5_cr_dates, R.string.ws5_cr_money, R.string.ws5_cr_people, R.string.ws5_cr_other).map { stringResource(it) }
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    val booked = d.full.team.count { it.match.state == MatchState.BOOKED }
    CtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ws5_cancel_title), subtitle = stringResource(R.string.ws5_cancel_sub)) {
        ChoiceChips(reasons, reason, { it }, { reason = it })
        Spacer(Modifier.height(12.dp))
        CtTextField(details, { details = it }, label = stringResource(R.string.ws5_cancel_details), maxLength = 200, minLines = 2,
            supportingText = stringResource(R.string.ws5_cancel_details_hint))
        Spacer(Modifier.height(12.dp))
        InfoTipCard(if (booked > 0) pluralStringResource(R.plurals.ws5_cancel_booked, booked, booked) else stringResource(R.string.ws5_cancel_nobody))
        Spacer(Modifier.height(16.dp))
        val text = listOfNotNull(reason, details.trim().ifBlank { null }).joinToString(". ")
        DangerButton(stringResource(R.string.ws5_cancel_confirm), { onConfirm(text) }, Modifier.fillMaxWidth(), enabled = reason != null, loading = busy)
    }
}

@Composable
internal fun ConfirmDialog(title: String, body: String, confirm: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Ct.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = Ct.type.titleM, color = c.text) },
        text = { Text(body, style = Ct.type.bodyS, color = c.textSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) c.stop else c.accent, style = Ct.type.bodyStrong) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ws5_keep), color = c.textSecondary, style = Ct.type.bodyStrong) } },
    )
}

// ─────────────────────────────── Collaborator view ───────────────────────────────

@Composable
internal fun CollaboratorDashboard(d: DashData, nav: AppNavigator) {
    val c = Ct.colors
    val m = d.full.myMatch
    val booked = m != null && m.state in setOf(MatchState.BOOKED, MatchState.WRAPPED)
    val roleCode = m?.let { mm -> d.full.roles.firstOrNull { it.id == mm.roleId }?.roleCode }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        DashHeader(d)
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (booked && roleCode != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ws5_booked_as, shortRoleLabel(roleCode)).uppercase(), style = Ct.type.slate, color = c.go, modifier = Modifier.weight(1f))
                    StatusChip(m!!.state)
                }
            }
            if (!booked) {
                NonMemberView(d, nav)
                return@Column
            }
            val mid = m!!.id
            // My booking card (current version)
            val view = d.myView
            val card = view?.acceptedCard ?: view?.currentCard
            SectionHeader(stringResource(R.string.ws5_my_booking), action = stringResource(R.string.ws5_open), onAction = { nav.toBookingCard(mid) })
            DashCard(onClick = { nav.toBookingCard(mid) }) {
                if (card != null) {
                    TimecodeText(stringResource(R.string.ws5_card_version, card.version), style = Ct.type.monoS, color = c.textMuted)
                    Text(CardText.bookingSummary(card.terms), style = Ct.type.bodyS, color = c.text)
                } else {
                    Text(stringResource(R.string.ws5_booking_loading), style = Ct.type.bodyS, color = c.textSecondary)
                }
                val pending = view?.currentCard?.takeIf { it.status == com.tff.app.model.BookingStatus.SENT && it.id != view.acceptedCard?.id }
                if (pending != null) CtChip(stringResource(R.string.ws5_new_version_waiting, pending.version), tone = ChipTone.ACCENT, filled = true, monoNumbers = true)
            }
            // Day sheets (on-set roles)
            if (!m.isPostRole && !d.postOnly) {
                SectionHeader(stringResource(R.string.ws5_day_sheets))
                if (d.days.isEmpty()) Text(stringResource(R.string.ws5_no_sheet_collab), style = Ct.type.bodyS, color = c.textSecondary)
                d.days.forEach { s ->
                    val my = s.myCall
                    DashCard(onClick = { nav.toShootDayView(d.project.id, s.day.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ws5_day_n_of, s.dayIndex, s.dayCount), style = Ct.type.slate, color = c.accent)
                            Spacer(Modifier.width(10.dp))
                            TimecodeText(Dates.shortUpper(s.day.day), style = Ct.type.mono, color = c.text, modifier = Modifier.weight(1f))
                            if (my?.ackAt != null) CtChip(stringResource(R.string.ws5_ack_got_it), tone = ChipTone.GO, small = true, icon = Icons.Rounded.CheckCircle)
                            else if (my != null) CtChip(stringResource(R.string.ws5_tap_got_it), tone = ChipTone.WARN, small = true)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(stringResource(R.string.ws5_your_call).uppercase(), style = Ct.type.slate, color = c.textSecondary)
                            Spacer(Modifier.width(10.dp))
                            TimecodeText(Dates.time(my?.callTime ?: s.day.generalCall), style = Ct.type.monoL.copy(fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.text)
                        }
                        Text(s.day.address.lines().joinToString(", "), style = Ct.type.bodyS, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // My wrap items
            if (d.myItems.isNotEmpty()) {
                SectionHeader(stringResource(R.string.ws5_my_wrap), action = stringResource(R.string.ws5_open), onAction = { nav.toWrap(matchId = mid) })
                DashCard(onClick = { nav.toWrap(matchId = mid) }) {
                    d.myItems.forEachIndexed { i, it ->
                        if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.45f), thickness = 1.dp)
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(wrapKindLabel(it.kind), style = Ct.type.body, color = c.text)
                                it.dueDate?.let { dd -> TimecodeText(stringResource(R.string.ws5_due, Dates.shortUpper(dd)), style = Ct.type.monoS, color = c.textMuted) }
                            }
                            StatusChip(it.status, WrapItemsFactory.isOverdue(it, d.today))
                        }
                    }
                }
            }
            // Team (names and roles only)
            SectionHeader(stringResource(R.string.ws5_team))
            PersonLine(d.full.owner.shortName, initials(d.full.owner.displayName), stringResource(R.string.ws5_production_payer))
            d.full.team.filter { it.match.collaboratorId != m.collaboratorId }.forEach { t ->
                PersonLine(t.person.profile.shortName, t.person.profile.initials, shortRoleLabel(t.roleCode))
            }
            Spacer(Modifier.height(4.dp))
            SecondaryButton(stringResource(R.string.ws5_message_production), { nav.toChat(mid) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Chat)
            if (m.state == MatchState.BOOKED && d.project.status != ProjectStatus.WRAPPED) {
                DangerButton(stringResource(R.string.ws5_withdraw), { nav.toChat(mid) }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.ws5_withdraw_hint), style = Ct.type.label, color = c.textMuted)
            }
        }
    }
}

/** Someone who isn't booked (an applicant, or a link): what the project is and its open roles. */
@Composable
private fun NonMemberView(d: DashData, nav: AppNavigator) {
    val c = Ct.colors
    val m = d.full.myMatch
    Text(d.project.logline, style = Ct.type.body, color = c.text)
    if (m != null && m.state in setOf(MatchState.TALKING, MatchState.OFFERED)) {
        DashCard(onClick = { nav.toChat(m.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ws5_in_talks_here), style = Ct.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                StatusChip(m.state)
            }
            GhostButton(stringResource(R.string.ws5_open_chat), { nav.toChat(m.id) })
        }
    }
    SectionHeader(stringResource(R.string.ws5_open_roles))
    val open = d.full.roles.filter { it.status == RoleStatus.OPEN }
    if (open.isEmpty()) Text(stringResource(R.string.ws5_no_open_roles), style = Ct.type.bodyS, color = c.textSecondary)
    open.forEach { r ->
        DashCard(onClick = { nav.toRoleDetail(r.id) }) {
            Text(CardText.roleLabel(r), style = Ct.type.slate, color = c.accent)
            PayLine(CardText.payLine(r, d.project), paid = r.isPaid)
        }
    }
}
