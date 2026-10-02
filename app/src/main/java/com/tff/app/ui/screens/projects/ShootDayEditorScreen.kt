package com.tff.app.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Dates
import kotlinx.coroutines.delay
import com.tff.app.di.rememberVm
import com.tff.app.domain.ShareText
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.TimeField
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.shortRoleLabel
import com.tff.app.ui.screens.post.FieldLabel
import com.tff.app.ui.screens.post.StepSubhead
import com.tff.app.ui.theme.Ct

/** S16E Shoot day editor (owner). */
@Composable
fun ShootDayEditorScreen(nav: AppNavigator, projectId: String, shootDayId: String?) {
    val vm = rememberVm(key = "sde:$projectId:${shootDayId ?: "new"}") { repo -> ShootDayEditorViewModel(repo, projectId, shootDayId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var cancelAsk by remember { mutableStateOf(false) }
    var leaveAsk by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is EditorEvent.Published -> nav.toast(
                    if (e.count == 0) context.getString(R.string.ws5_toast_sheet_nobody)
                    else context.resources.getQuantityString(if (e.updated) R.plurals.ws5_toast_sheet_updated else R.plurals.ws5_toast_sheet_sent, e.count, e.count),
                )
                is EditorEvent.Nudged -> nav.toast(context.getString(R.string.ws5_toast_nudged, e.name.removeSuffix(".")))
                is EditorEvent.Failed -> nav.toast(e.message ?: context.getString(R.string.ws5_toast_failed))
                EditorEvent.DayCancelled -> { nav.toast(context.getString(R.string.ws5_toast_day_cancelled)); nav.back() }
            }
        }
    }
    BackHandler(enabled = ui.dirty) { leaveAsk = true }

    val current = ui.current
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Ct.colors.bg,
        topBar = {
            CtTopBar(
                title = stringResource(if (current != null) R.string.ws5_sde_title_edit else R.string.ws5_sde_title_new),
                subtitle = ui.full?.project?.title,
                onBack = { if (ui.dirty) leaveAsk = true else nav.back() },
                divider = true,
            )
        },
        bottomBar = {
            if (ui.phase == EditorPhase.READY) {
                Column(Modifier.fillMaxWidth().background(Ct.colors.bg).navigationBarsPadding()) {
                    HorizontalDivider(color = Ct.colors.outline.copy(alpha = 0.6f), thickness = 1.dp)
                    PrimaryButton(
                        stringResource(if (current != null) R.string.ws5_sde_update else R.string.ws5_sde_publish),
                        { if (vm.canPublish()) confirm = true else vm.publish() },
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), loading = ui.publishing,
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (ui.phase) {
                EditorPhase.LOADING -> LoadingSkeleton(Modifier.fillMaxSize(), kind = SkeletonKind.LIST, rows = 6)
                EditorPhase.ERROR -> ErrorState(onRetry = vm::retry, message = ui.error, modifier = Modifier.fillMaxSize())
                EditorPhase.NOT_OWNER -> EmptyState(
                    title = stringResource(R.string.ws5_sde_not_owner_title), body = stringResource(R.string.ws5_sde_not_owner_body),
                    icon = Icons.Rounded.EventBusy, modifier = Modifier.fillMaxSize(),
                ) { SecondaryButton(stringResource(R.string.ws5_back), { nav.back() }) }
                EditorPhase.POST_ONLY -> EmptyState(
                    title = stringResource(R.string.ws5_sde_post_title), body = stringResource(R.string.ws5_sde_post_body),
                    icon = Icons.Rounded.EventBusy, modifier = Modifier.fillMaxSize(),
                ) { SecondaryButton(stringResource(R.string.ws5_back), { nav.back() }) }
                EditorPhase.NO_DATES -> EmptyState(
                    title = stringResource(R.string.ws5_no_dates_title), body = stringResource(R.string.ws5_no_dates_body),
                    icon = Icons.Rounded.EventBusy, modifier = Modifier.fillMaxSize(),
                ) { SecondaryButton(stringResource(R.string.ws5_edit_listing), { nav.toPostProject(projectId) }) }
                EditorPhase.READY -> EditorForm(ui, vm, nav, onCancelDay = { cancelAsk = true })
            }
        }
    }

    if (confirm) {
        val n = ui.crew.size
        // Empty recommended fields are said out loud before anything is sent (an empty field can look filled in).
        val empty = listOfNotNull(
            stringResource(R.string.ws5_sde_empty_parking).takeIf { ui.form.parking.isBlank() },
            stringResource(R.string.ws5_sde_empty_hospital).takeIf { ui.form.hospital.isBlank() },
            stringResource(R.string.ws5_sde_empty_meals).takeIf { ui.form.meals.isBlank() },
        )
        val emptyLine = when (empty.size) {
            0 -> null
            1 -> stringResource(R.string.ws5_sde_empty_one, empty[0].replaceFirstChar { it.uppercase() })
            2 -> stringResource(R.string.ws5_sde_empty_two, empty[0].replaceFirstChar { it.uppercase() }, empty[1])
            else -> stringResource(R.string.ws5_sde_empty_three, empty[0].replaceFirstChar { it.uppercase() }, empty[1], empty[2])
        }
        ConfirmDialog(
            title = when {
                n == 0 -> stringResource(R.string.ws5_sde_confirm_nobody_title)
                current != null -> pluralStringResource(R.plurals.ws5_sde_confirm_update, n, n)
                else -> pluralStringResource(R.plurals.ws5_sde_confirm_notify, n, n)
            },
            body = listOfNotNull(emptyLine, stringResource(
                when {
                    n == 0 -> R.string.ws5_sde_confirm_nobody_body
                    current != null -> R.string.ws5_sde_confirm_update_body
                    else -> R.string.ws5_sde_confirm_body
                },
            )).joinToString("\n\n"),
            confirm = stringResource(if (n == 0) R.string.ws5_sde_publish_anyway else R.string.ws5_sde_notify),
            danger = false, dismiss = stringResource(if (emptyLine != null) R.string.ws5_sde_fill_in else R.string.ws5_not_yet),
            onConfirm = { confirm = false; vm.publish() }, onDismiss = { confirm = false },
        )
    }
    if (cancelAsk) {
        ConfirmDialog(
            title = stringResource(R.string.ws5_sde_cancel_title, ui.form.day?.let { Dates.short(it) } ?: ""),
            body = stringResource(R.string.ws5_sde_cancel_body),
            confirm = stringResource(R.string.ws5_sde_cancel_confirm), danger = true,
            onConfirm = { cancelAsk = false; vm.cancelDay() }, onDismiss = { cancelAsk = false },
        )
    }
    if (leaveAsk) {
        ConfirmDialog(
            title = stringResource(R.string.ws5_sde_leave_title), body = stringResource(R.string.ws5_sde_leave_body),
            confirm = stringResource(R.string.ws5_discard), danger = true,
            onConfirm = { leaveAsk = false; nav.back() }, onDismiss = { leaveAsk = false },
        )
    }
}

@Composable
private fun EditorForm(ui: EditorUi, vm: ShootDayEditorViewModel, nav: AppNavigator, onCancelDay: () -> Unit) {
    val c = Ct.colors
    val f = ui.form
    val show = ui.attempted
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Date, from the booking dates
        Column(Modifier.fillMaxWidth()) {
            FieldLabel(stringResource(R.string.ws5_sde_date), error = show && ui.dayError, hint = stringResource(R.string.ws5_sde_date_hint))
            ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
                ui.dates.forEach { d ->
                    val hasSheet = ui.days.any { it.day.day == d }
                    CtFilterChip(
                        Dates.shortUpper(d), selected = f.day == d, onClick = { vm.pickDay(d) },
                        enabled = !d.isBefore(ui.today) || hasSheet, icon = if (hasSheet) Icons.Rounded.Description else null,
                    )
                }
            }
            if (show && ui.dayError) Text(stringResource(R.string.ws5_sde_date_error), style = Ct.type.label, color = c.onDangerContainer)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField(stringResource(R.string.ws5_sde_general_call), f.generalCall, vm::setGeneralCall, Modifier.weight(1f))
            TimeField(stringResource(R.string.ws5_sde_wrap_by), f.wrapBy, { t -> vm.edit { it.copy(wrapBy = t) } }, Modifier.weight(1f))
        }
        if (f.overnight) InfoTipCard(stringResource(R.string.ws5_sde_overnight), icon = Icons.Rounded.NightsStay)
        // Safety: a late wrap has to say how people get home (it shows as its own row on the day sheet).
        if (f.lateWrap || f.gettingHome.isNotBlank()) {
            CtTextField(
                f.gettingHome, { t -> vm.edit { it.copy(gettingHome = t.replace('\n', ' ')) } }, label = stringResource(R.string.ws5_sde_home),
                placeholder = stringResource(R.string.ws5_sde_home_ph), maxLength = DayForm.HOME_MAX, singleLine = true,
                supportingText = if (f.lateWrap) stringResource(R.string.ws5_sde_home_hint, Dates.time(f.wrapBy)) else null,
                errorText = if (show && ui.gettingHomeError) stringResource(R.string.ws5_sde_home_error, Dates.time(f.wrapBy)) else null,
                modifier = Modifier.bringIntoViewOnError(show && ui.gettingHomeError),
            )
        }

        // Per-person calls
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepSubhead(stringResource(R.string.ws5_sde_calls))
            if (ui.crew.isEmpty()) Text(stringResource(R.string.ws5_sde_no_crew), style = Ct.type.bodyS, color = c.textSecondary)
            else Text(stringResource(R.string.ws5_sde_calls_hint), style = Ct.type.label, color = c.textMuted)
            ui.crew.forEach { t ->
                val pid = t.match.collaboratorId
                val staggered = f.perPerson.containsKey(pid)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Monogram(t.person.profile.initials, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.person.profile.shortName, style = Ct.type.bodyStrong, color = c.text)
                        Text(shortRoleLabel(t.roleCode), style = Ct.type.label, color = if (staggered) c.accent else c.textSecondary)
                    }
                    TimeField(null, f.callFor(pid), { tt -> vm.setCall(pid, tt) }, Modifier.width(132.dp))
                }
            }
        }

        // Where
        Column(Modifier.fillMaxWidth()) {
            CtTextField(
                f.address, { t -> vm.edit { it.copy(address = t) } }, label = stringResource(R.string.ws5_sde_address),
                placeholder = stringResource(R.string.ws5_sde_address_ph), minLines = 2, maxLength = 300, showCounter = false,
                supportingText = stringResource(R.string.ws5_sde_address_hint),
                errorText = if (show && ui.addressError) stringResource(R.string.ws5_sde_address_error) else null,
            )
            if (f.address.isNotBlank()) {
                GhostButton(stringResource(R.string.ws5_sde_test_maps), { nav.openUrl(ShareText.mapsUrl(f.address)) }, icon = Icons.Rounded.Map)
            }
        }
        CtTextField(f.parking, { t -> vm.edit { it.copy(parking = t) } }, label = stringResource(R.string.ws5_sde_parking),
            placeholder = stringResource(R.string.ws5_sde_parking_ph), maxLength = 200, showCounter = false)
        CtTextField(f.hospital, { t -> vm.edit { it.copy(hospital = t) } }, label = stringResource(R.string.ws5_sde_hospital),
            placeholder = stringResource(R.string.ws5_sde_hospital_ph), maxLength = 200, showCounter = false)
        CtTextField(f.meals, { t -> vm.edit { it.copy(meals = t) } }, label = stringResource(R.string.ws5_sde_meals),
            placeholder = stringResource(R.string.ws5_sde_meals_ph), maxLength = 200, showCounter = false)
        CtTextField(f.notes, { t -> vm.edit { it.copy(notes = t) } }, label = stringResource(R.string.ws5_sde_notes),
            placeholder = stringResource(R.string.ws5_sde_notes_ph), maxLength = DayForm.notesBudget(f.gettingHome), minLines = 3)

        // After publishing: who has tapped Got it, with Nudge for the rest.
        val cur = ui.current
        if (cur != null) {
            val acked = cur.calls.count { it.call.ackAt != null }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepSubhead(stringResource(R.string.ws5_sde_acks), Modifier.weight(1f))
                    if (cur.calls.isNotEmpty()) CtChip(stringResource(R.string.ws5_got_it_n, acked, cur.calls.size), tone = if (acked == cur.calls.size) ChipTone.GO else ChipTone.WARN, small = true, monoNumbers = true)
                }
                if (cur.calls.isEmpty()) Text(stringResource(R.string.ws5_nobody_on_sheet), style = Ct.type.bodyS, color = c.textSecondary)
                cur.calls.forEach { cv ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cv.shortName, style = Ct.type.bodyStrong, color = c.text)
                            Text(shortRoleLabel(cv.roleCode), style = Ct.type.label, color = c.textSecondary)
                        }
                        TimecodeText(Dates.time(cv.call.callTime), style = Ct.type.mono, color = c.text)
                        Spacer(Modifier.width(12.dp))
                        if (cv.call.ackAt != null) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.ws5_ack_got_it), tint = c.go, modifier = Modifier.size(22.dp))
                        } else {
                            SecondaryButton(stringResource(R.string.ws5_nudge), { vm.nudge(cv.call.profileId, cv.shortName) })
                        }
                    }
                }
                GhostButton(stringResource(R.string.ws5_sde_view_sheet), { nav.toShootDayView(cur.day.projectId, cur.day.id) })
            }
        }
        val day = f.day
        if (cur != null && day != null && ui.dates.size > 1 && !day.isBefore(ui.today) && vm.featuresReschedule()) {
            GhostButton(stringResource(R.string.ws5_sde_cancel_day), onCancelDay, color = c.onDangerContainer)
        }
    }
}

/** Scrolls a field into view the moment it turns red (the late-wrap "Getting home" line sits above the fold edge). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewOnError(error: Boolean): Modifier {
    val req = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error) { delay(150); runCatching { req.bringIntoView() } } }
    return this.bringIntoViewRequester(req)
}
