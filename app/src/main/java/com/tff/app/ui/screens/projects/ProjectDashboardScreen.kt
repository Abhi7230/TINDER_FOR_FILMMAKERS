package com.tff.app.ui.screens.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.di.rememberVm
import com.tff.app.model.DateCertainty
import com.tff.app.model.ProjectStatus
import com.tff.app.model.RoleStatus
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.TopBarAction
import com.tff.app.ui.screens.post.RoleShareBody
import com.tff.app.ui.screens.post.pluralDays
import com.tff.app.ui.theme.Ct

/** S13 Project dashboard: owner tabs (Roles · Team · Shoot days · Wrap · Settings) or the collaborator's view. */
@Composable
fun ProjectDashboardScreen(nav: AppNavigator, projectId: String) {
    val vm = rememberVm(key = "dash:$projectId") { repo -> ProjectDashboardViewModel(repo, projectId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var shareOpen by remember { mutableStateOf(false) }
    /** The role whose share text opens first (a role card's share icon); null = the first open role. */
    var shareRole by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is DashEvent.Toast -> nav.toast(if (e.arg != null) context.getString(e.res, e.arg) else context.getString(e.res))
                is DashEvent.Message -> nav.toast(e.text.ifBlank { context.getString(R.string.ws5_toast_failed) })
                DashEvent.Closed -> nav.back()
            }
        }
    }

    val data = (ui as? DashUi.Ready)?.data
    Scaffold(
        containerColor = Ct.colors.bg,
        topBar = {
            CtTopBar(
                title = data?.project?.title ?: stringResource(R.string.ws5_dashboard),
                onBack = { nav.back() },
                actions = {
                    if (data != null && data.full.isOwner && data.project.status in setOf(ProjectStatus.OPEN, ProjectStatus.CREWED)) {
                        TopBarAction(Icons.Rounded.Share, stringResource(R.string.ws5_share_roles_cd), { shareRole = null; shareOpen = true })
                    }
                    if (data != null && data.canEdit) {
                        TopBarAction(Icons.Rounded.Edit, stringResource(R.string.ws5_edit_listing_cd), { nav.toPostProject(projectId) })
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = ui) {
                DashUi.Loading -> LoadingSkeleton(Modifier.fillMaxSize(), kind = SkeletonKind.DETAIL, rows = 4)
                DashUi.Missing -> EmptyState(
                    title = stringResource(R.string.ws5_dash_missing_title), body = stringResource(R.string.ws5_dash_missing_body),
                    icon = Icons.Rounded.Movie, modifier = Modifier.fillMaxSize(),
                ) { SecondaryButton(stringResource(R.string.ws5_back), { nav.back() }) }
                is DashUi.Error -> ErrorState(onRetry = vm::retry, message = s.message, modifier = Modifier.fillMaxSize())
                is DashUi.Ready -> if (s.data.full.isOwner) OwnerDashboard(s.data, vm, nav, busy, onShare = { id -> shareRole = id; shareOpen = true })
                else CollaboratorDashboard(s.data, nav)
            }
        }
    }

    if (shareOpen && data != null) {
        val items = data.full.roles.filter { it.status == RoleStatus.OPEN }.mapNotNull { data.item(it.id) }
        var sel by rememberSaveable(shareRole) { mutableIntStateOf(items.indexOfFirst { it.role.id == shareRole }.coerceAtLeast(0)) }
        CtBottomSheet(onDismiss = { shareOpen = false }, title = stringResource(R.string.ws5_share_sheet_title), subtitle = stringResource(R.string.ws5_share_sheet_sub)) {
            if (items.isEmpty()) Text(stringResource(R.string.ws5_share_none_open), style = Ct.type.bodyS, color = Ct.colors.textSecondary)
            else RoleShareBody(items, sel, { sel = it }) { text ->
                vm.log("role_shared", mapOf("project" to projectId))
                nav.share(text, context.getString(R.string.ws5_share_title))
            }
        }
    }
}

/** Header under the top bar: status, what it is, when, and (owner) the pipeline. */
@Composable
internal fun DashHeader(d: DashData) {
    val c = Ct.colors
    val p = d.project
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The owner's pipeline already names the stage; the chip stays for collaborators and for a cancelled project.
        val chip = !d.full.isOwner || p.status == ProjectStatus.CANCELLED
        val badges = chip || p.rescheduleCount > 0 || p.honestZeroBudget
        if (badges) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chip) StatusChip(p.status)
            if (p.rescheduleCount > 0) CtChip(stringResource(R.string.ws5_rescheduled_n, p.rescheduleCount), tone = if (p.rescheduleCount >= 2) ChipTone.WARN else ChipTone.NEUTRAL, small = true, monoNumbers = true)
            if (p.honestZeroBudget) CtChip(stringResource(R.string.ws5_zero_budget_chip), small = true)
        }
        val place = listOf(d.full.areaName, d.full.cityName).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }
        Text(metaLine(p.format, p.runtimeMin, place), style = Ct.type.bodyS, color = c.textSecondary)
        val dates = p.shootDates
        if (dates.isNotEmpty()) {
            val s = dates.min()
            val e = dates.max()
            val bits = listOfNotNull(
                (if (s == e) Dates.shortUpper(s) else Dates.span(s, e).uppercase()), pluralDays(dates.size).uppercase(),
                p.hoursCap?.let { "≤${it}H" },
                p.dateCertainty?.let { stringResource(if (it == DateCertainty.LOCKED) R.string.ws5_locked_caps else R.string.ws5_tentative_caps) },
            )
            TimecodeText(bits.joinToString(" · "), style = Ct.type.monoS, color = c.text, maxLines = 2)
        } else if (d.postOnly) {
            TimecodeText(stringResource(R.string.ws5_post_only_caps), style = Ct.type.monoS, color = c.text)
        }
        if (d.full.isOwner) {
            val live = d.full.roles.filter { it.status != RoleStatus.CLOSED }
            val filled = live.sumOf { minOf(it.filledCount, it.count) }
            val total = live.sumOf { it.count }
            if (p.status != ProjectStatus.CANCELLED) ProjectPipeline(p.status, shootPassed = d.lastShootPassed)
            if (p.status == ProjectStatus.OPEN || p.status == ProjectStatus.CREWED) {
                val crewed = p.status == ProjectStatus.CREWED || (total > 0 && filled >= total)
                TimecodeText(stringResource(R.string.ws5_filled_caps, filled, total), style = Ct.type.mono, color = if (crewed) c.go else c.text)
            }
        }
    }
}

/** Darkroom tab strip (scrollable; Tungsten underline). 48dp touch targets, tab semantics. */
@Composable
internal fun DashTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Ct.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                Column(
                    Modifier.heightIn(min = 48.dp).selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(t, style = Ct.type.bodyStrong.copy(fontSize = 15.sp), color = if (on) c.accent else c.textSecondary, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(if (on) 28.dp else 0.dp).height(3.dp).background(if (on) c.accent else c.bg))
                }
            }
        }
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
    }
}

@Composable
private fun OwnerDashboard(d: DashData, vm: ProjectDashboardViewModel, nav: AppNavigator, busy: Boolean, onShare: (String?) -> Unit) {
    val features = vm.features
    val tabs = buildList {
        add(DashTab.ROLES); add(DashTab.TEAM)
        if (!d.postOnly && features.shootDays && d.project.status != ProjectStatus.DRAFT) add(DashTab.DAYS)
        if (d.wrapVisible && features.wrap) add(DashTab.WRAP)
        add(DashTab.SETTINGS)
    }
    var tabName by rememberSaveable { mutableStateOf(DashTab.ROLES.name) }
    val current = tabs.firstOrNull { it.name == tabName } ?: DashTab.ROLES
    val scroll = rememberScrollState()
    LaunchedEffect(current) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            DashHeader(d)
            DashTabs(tabs.map { tabLabel(it) }, tabs.indexOf(current)) { tabName = tabs[it].name }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp).navigationBarsPadding()) {
                when (current) {
                    DashTab.ROLES -> RolesTab(d, vm, nav, busy, onShare)
                    DashTab.TEAM -> TeamTab(d, vm, nav, busy)
                    DashTab.DAYS -> DaysTab(d, nav)
                    DashTab.WRAP -> WrapTab(d, vm, nav, busy)
                    DashTab.SETTINGS -> SettingsTab(d, vm, nav, busy, onShare)
                }
            }
        }
    }
}

@Composable
private fun tabLabel(t: DashTab): String = stringResource(
    when (t) {
        DashTab.ROLES -> R.string.ws5_tab_roles
        DashTab.TEAM -> R.string.ws5_tab_team
        DashTab.DAYS -> R.string.ws5_tab_days
        DashTab.WRAP -> R.string.ws5_tab_wrap
        DashTab.SETTINGS -> R.string.ws5_tab_settings
    },
)
