package com.tff.app.ui.screens.projects

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.model.ProjectStatus
import com.tff.app.model.ProjectSummary
import com.tff.app.nav.AppNavigator
import com.tff.app.nav.MainTab
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SegmentedControl
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.shortRoleLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

/** S12 Projects tab: Mine | Joined. Tab screen: draws its own top bar with status-bar insets, no bottom padding. */
@Composable
fun ProjectsScreen(nav: AppNavigator) {
    val vm = rememberVm { repo -> ProjectsViewModel(repo) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var autoPicked by rememberSaveable { mutableStateOf(false) }
    // People who only join shoots (an actor, say) land on Joined the first time.
    LaunchedEffect(ui) {
        val r = ui as? ProjectsUi.Ready ?: return@LaunchedEffect
        if (!autoPicked) { autoPicked = true; if (r.mine.isEmpty() && r.joined.isNotEmpty()) tab = 1 }
    }
    val c = Ct.colors
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            val ready = ui as? ProjectsUi.Ready
            CtTopBar(
                title = stringResource(R.string.ws5_projects_title),
                subtitle = ready?.let { subtitle(it) },
                bottom = if (ui is ProjectsUi.Guest) null else ({
                    SegmentedControl(
                        listOf(stringResource(R.string.ws5_tab_mine), stringResource(R.string.ws5_tab_joined)), tab, { tab = it },
                    )
                }),
            )
            when (val s = ui) {
                ProjectsUi.Loading -> LoadingSkeleton(Modifier.fillMaxSize(), kind = SkeletonKind.LIST, rows = 4)
                ProjectsUi.Guest -> EmptyState(
                    title = stringResource(R.string.ws5_projects_guest_title), body = stringResource(R.string.ws5_projects_guest_body),
                    icon = Icons.Rounded.Movie, modifier = Modifier.fillMaxSize(),
                ) { PrimaryButton(stringResource(R.string.ws5_sign_in), { nav.toWelcome() }) }
                is ProjectsUi.Error -> ErrorState(onRetry = vm::retry, message = s.message, modifier = Modifier.fillMaxSize())
                is ProjectsUi.Ready -> if (tab == 0) MineList(s, nav) else JoinedList(s, nav)
            }
        }
        val s = ui
        if (s is ProjectsUi.Ready && tab == 0 && s.mine.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { nav.toPostProject() },
                containerColor = c.accent, contentColor = c.onAccent, shape = CtShapes.pill,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.ws5_post_a_project), style = Ct.type.bodyStrong) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun subtitle(r: ProjectsUi.Ready): String? {
    val crewing = r.mine.count { it.project.status == ProjectStatus.OPEN }
    val drafts = r.mine.count { it.project.status == ProjectStatus.DRAFT }
    val booked = r.joined.count { it.summary.project.status != ProjectStatus.WRAPPED && it.summary.project.status != ProjectStatus.CANCELLED }
    val parts = buildList {
        if (crewing > 0) add(pluralStringResource(R.plurals.ws5_sub_crewing, crewing, crewing))
        if (drafts > 0) add(pluralStringResource(R.plurals.ws5_sub_drafts, drafts, drafts))
        if (booked > 0) add(pluralStringResource(R.plurals.ws5_sub_booked, booked, booked))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun MineList(s: ProjectsUi.Ready, nav: AppNavigator) {
    if (s.mine.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.ws5_mine_empty_title), body = stringResource(R.string.ws5_mine_empty_body),
            icon = Icons.Rounded.Movie, modifier = Modifier.fillMaxSize(),
        ) { PrimaryButton(stringResource(R.string.ws5_post_a_project), { nav.toPostProject() }, icon = Icons.Rounded.Add) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(s.mine, key = { it.project.id }) { p ->
            MineCard(p, s.places[p.project.areaId], onOpen = {
                if (p.project.status == ProjectStatus.DRAFT) nav.toPostProject(p.project.id) else nav.toProjectDashboard(p.project.id)
            }, onDashboard = { nav.toProjectDashboard(p.project.id) })
        }
    }
}

@Composable
private fun JoinedList(s: ProjectsUi.Ready, nav: AppNavigator) {
    if (s.joined.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.ws5_joined_empty_title), body = stringResource(R.string.ws5_joined_empty_body),
            icon = Icons.Rounded.EventAvailable, modifier = Modifier.fillMaxSize(),
        ) { PrimaryButton(stringResource(R.string.ws5_find_gigs), { nav.toMain(MainTab.DISCOVER) }) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(s.joined, key = { it.summary.project.id }) { j -> JoinedCard(j, s.places[j.summary.project.areaId], onOpen = { nav.toProjectDashboard(j.summary.project.id) }) }
    }
}

/** Mine card: title, status, the pipeline, "Crewing · 3/5 filled", next shoot date, new applicants, overdue wrap items. */
@Composable
private fun MineCard(s: ProjectSummary, place: String?, onOpen: () -> Unit, onDashboard: () -> Unit) {
    val c = Ct.colors
    val p = s.project
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
            .clickable(role = Role.Button, onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(p.title.ifBlank { stringResource(R.string.ws5_untitled) }, style = Ct.type.titleL, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(metaLine(p.format, p.runtimeMin, place), style = Ct.type.bodyS, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusChip(p.status)
        }
        ProjectPipeline(p.status, shootPassed = s.nextShootDate == null && p.shootDates.isNotEmpty())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pipelineText(s), style = Ct.type.mono, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            s.nextShootDate?.let { TimecodeText(stringResource(R.string.ws5_next_shoot, Dates.shortUpper(it)), style = Ct.type.monoS, color = c.accent) }
        }
        val chips = buildList<@Composable () -> Unit> {
            if (s.newApplicants > 0) add { CtChip(pluralStringResource(R.plurals.ws5_new_applicants, s.newApplicants, s.newApplicants), tone = ChipTone.ACCENT, filled = true, monoNumbers = true) }
            if (s.overdueWrapItems > 0) add { CtChip(pluralStringResource(R.plurals.ws5_wrap_overdue, s.overdueWrapItems, s.overdueWrapItems), tone = ChipTone.STOP, filled = true, monoNumbers = true) }
            if (p.rescheduleCount >= 2) add { CtChip(stringResource(R.string.ws5_rescheduled_n, p.rescheduleCount), tone = ChipTone.WARN, monoNumbers = true) }
        }
        if (chips.isNotEmpty()) ChipFlow(spacing = 8.dp, lineSpacing = 6.dp) { chips.forEach { it() } }
        if (p.status == ProjectStatus.DRAFT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ws5_draft_note), style = Ct.type.label, color = c.textMuted, modifier = Modifier.weight(1f))
                GhostButton(stringResource(R.string.ws5_finish_publish), onOpen)
            }
        }
    }
}

@Composable
private fun JoinedCard(j: JoinedRow, place: String?, onOpen: () -> Unit) {
    val c = Ct.colors
    val s = j.summary
    val p = s.project
    val wrapped = p.status == ProjectStatus.WRAPPED
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
            .clickable(role = Role.Button, onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (s.myRoleCode?.let { shortRoleLabel(it) } ?: stringResource(R.string.ws5_crew)).uppercase(),
                style = Ct.type.slate, color = c.accent, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (wrapped || p.status == ProjectStatus.CANCELLED) StatusChip(p.status) else StatusChip(stringResource(R.string.ws5_booked), ChipTone.GO)
        }
        Text(p.title, style = Ct.type.titleL, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(metaLine(p.format, p.runtimeMin, place), style = Ct.type.bodyS, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            s.myNextCall != null -> CallLine(stringResource(R.string.ws5_your_call), s.myNextCall)
            s.myDeliveryDate != null && !wrapped -> CallLine(stringResource(R.string.ws5_deliver_by_caps), Dates.shortUpper(s.myDeliveryDate))
            wrapped -> Text(stringResource(R.string.ws5_joined_wrapped), style = Ct.type.label, color = c.textMuted)
        }
        if (j.myOverdue > 0) {
            CtChip(pluralStringResource(R.plurals.ws5_my_wrap_overdue, j.myOverdue, j.myOverdue), tone = ChipTone.STOP, filled = true, monoNumbers = true)
        }
    }
}

@Composable
private fun CallLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
        Text(label.uppercase(), style = Ct.type.slate, color = Ct.colors.textSecondary)
        Spacer(Modifier.width(10.dp))
        TimecodeText(value, style = Ct.type.mono.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Ct.colors.text)
    }
}

@Composable
internal fun metaLine(format: com.tff.app.model.Format, runtime: Int, place: String?): String =
    listOfNotNull(CardText.formatLabel(format), "$runtime min", place).joinToString(" · ")

/** "Crewing · 3/5 filled" (S12). */
@Composable
internal fun pipelineText(s: ProjectSummary): String = when (s.project.status) {
    ProjectStatus.DRAFT -> pluralStringResource(R.plurals.ws5_pipe_draft, s.roles.size, s.roles.size)
    ProjectStatus.OPEN -> stringResource(R.string.ws5_pipe_crewing, s.filledSlots, s.totalSlots)
    ProjectStatus.CREWED -> stringResource(R.string.ws5_pipe_crewed, s.filledSlots, s.totalSlots)
    ProjectStatus.WRAPPED -> stringResource(R.string.ws5_pipe_wrapped)
    ProjectStatus.CANCELLED -> stringResource(R.string.ws5_pipe_cancelled)
}

/** Draft → Crewing → Crewed → Wrapped as four ticks; the current stage is Tungsten (S12 status pipeline). */
@Composable
internal fun ProjectPipeline(status: ProjectStatus, shootPassed: Boolean, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val stages = listOf(R.string.ws5_stage_draft, R.string.ws5_stage_crewing, R.string.ws5_stage_crewed, R.string.ws5_stage_wrapped).map { stringResource(it) }
    val current = when (status) {
        ProjectStatus.DRAFT -> 0
        ProjectStatus.OPEN -> if (shootPassed) 2 else 1
        ProjectStatus.CREWED -> 2
        ProjectStatus.WRAPPED -> 3
        ProjectStatus.CANCELLED -> -1
    }
    val cd = if (current >= 0) stringResource(R.string.ws5_pipeline_cd, stages[current]) else stringResource(R.string.ws5_pipe_cancelled)
    Column(modifier.fillMaxWidth().semantics { contentDescription = cd }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            stages.indices.forEach { i ->
                val col by animateColorAsState(
                    when { current < 0 -> c.outline; i < current -> c.accent.copy(alpha = 0.55f); i == current -> c.accent; else -> c.outline }, label = "pipe$i",
                )
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(col))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stages.forEachIndexed { i, label ->
                Text(
                    label.uppercase(), style = Ct.type.monoS.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = if (i == current) c.accent else c.textMuted, modifier = Modifier.weight(1f), maxLines = 1,
                )
            }
        }
    }
}
