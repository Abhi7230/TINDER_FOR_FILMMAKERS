package com.tff.app.ui.screens.role

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.data.RepoException
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.ShareText
import com.tff.app.model.ApplicationStatus
import com.tff.app.model.Ambition
import com.tff.app.model.ContentFlag
import com.tff.app.model.DateCertainty
import com.tff.app.model.GearPolicy
import com.tff.app.model.LocationType
import com.tff.app.model.ReportTarget
import com.tff.app.model.RoleStatus
import com.tff.app.model.RoleWithProject
import com.tff.app.model.Stage
import com.tff.app.model.SwipeDir
import com.tff.app.model.Taxonomy
import com.tff.app.model.Transport
import com.tff.app.model.YesNoNa
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.DeckAction
import com.tff.app.ui.components.DeckActionBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.KeyValueRow
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PayLine
import com.tff.app.ui.components.PosterBand
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.components.RoleLabelRow
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.components.TeamMonograms
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.TopBarAction
import com.tff.app.ui.components.TrackRecordChips
import com.tff.app.ui.components.contentFlagIcon
import com.tff.app.ui.components.rememberGuestGate
import com.tff.app.ui.screens.discover.Ws4Prefs
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * S06 Role detail: header, What you get, Dates and place, The role, The project, Team attached,
 * Who's posting, Safety and content, Status — in that order. Sticky Pass · Save · Interested (or
 * "Applied ✓", or Crew up for the owner). Overflow: Share (WhatsApp-ready text) and Report.
 * The first cast role anyone opens shows the "What normal looks like" tip above Safety.
 */
@Composable
fun RoleDetailScreen(nav: AppNavigator, roleId: String) {
    val vm = rememberVm(key = "role_$roleId") { RoleDetailViewModel(it, roleId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val gate = rememberGuestGate(nav)
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf(false) }
    var applying by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberNotificationAsk()
    val shareTitle = stringResource(R.string.ws4_share_title)
    val genericError = stringResource(R.string.ws4_generic_error)
    val passedMsg = stringResource(R.string.ws4_passed_toast)
    val savedMsg = stringResource(R.string.ws4_saved_toast)
    val alreadySaved = stringResource(R.string.ws4_already_saved)
    val sentMsg = stringResource(R.string.ws4_apply_sent)
    val item = ui.item

    Scaffold(
        containerColor = Ct.colors.bg,
        topBar = {
            CtTopBar(
                title = item?.let { CardText.roleName(it.role) } ?: stringResource(R.string.ws4_role_title),
                subtitle = item?.project?.title,
                onBack = { nav.back() },
                actions = {
                    if (item != null) {
                        Box {
                            TopBarAction(Icons.Rounded.MoreVert, stringResource(R.string.ws4_more_options), { menu = true })
                            DropdownMenu(menu, { menu = false }, containerColor = Ct.colors.surfaceRaised) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ws4_share_role), color = Ct.colors.text) },
                                    leadingIcon = { Icon(Icons.Rounded.Share, null, tint = Ct.colors.textSecondary) },
                                    onClick = { menu = false; nav.share(ShareText.roleCard(item, AppContainer.shareBaseUrl), shareTitle) },
                                    colors = MenuDefaults.itemColors(),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ws4_report_role), color = Ct.colors.text) },
                                    leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = Ct.colors.textSecondary) },
                                    onClick = { menu = false; gate.require { report = true } },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (item != null) {
                BottomActions(
                    ui = ui,
                    onPass = { gate.require { scope.launch { vm.swipe(SwipeDir.LEFT).onSuccess { nav.toast(passedMsg); nav.back() }.onFailure { nav.toast((it as? RepoException)?.userMessage ?: genericError) } } } },
                    onSave = { gate.require { if (ui.saved) nav.toast(alreadySaved) else scope.launch { vm.swipe(SwipeDir.SAVE).onSuccess { nav.toast(savedMsg) }.onFailure { nav.toast((it as? RepoException)?.userMessage ?: genericError) } } } },
                    onInterested = { gate.require { applying = true } },
                    onCrewUp = { nav.toCrewUp(item.role.id) },
                    onShare = { nav.share(ShareText.roleCard(item, AppContainer.shareBaseUrl), shareTitle) },
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                ui.loading -> LoadingSkeleton(Modifier.fillMaxSize(), kind = SkeletonKind.DETAIL)
                ui.error != null -> ErrorState(onRetry = vm::load, message = ui.error!!.ifBlank { null }, modifier = Modifier.fillMaxSize())
                ui.missing || item == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(stringResource(R.string.ws4_role_missing_title), stringResource(R.string.ws4_role_missing_body), icon = Icons.Rounded.SearchOff) {
                        PrimaryButton(stringResource(R.string.ws4_back_to_discover), { nav.toMain() })
                    }
                }
                else -> RoleDetailBody(nav, item, ui, onOwner = { gate.require { nav.toPerson(item.owner.id) } })
            }
        }
    }

    if (report) ReportSheet(ReportTarget.ROLE, roleId, onDismiss = { report = false })
    if (applying && item != null) {
        ApplySheet(
            item = item,
            onCancel = { applying = false },
            onFinished = { out ->
                applying = false
                when (out) {
                    is ApplyOutcome.Sent -> { vm.applied(); nav.toast(sentMsg); askNotifications() }
                    is ApplyOutcome.Gone -> { vm.applied(); nav.toast(out.message); vm.load() }
                    is ApplyOutcome.DailyCap -> nav.toast(out.message)
                    is ApplyOutcome.Invalid -> Unit
                }
            },
        )
    }
}

@Composable
private fun BottomActions(
    ui: RoleDetailUi,
    onPass: () -> Unit,
    onSave: () -> Unit,
    onInterested: () -> Unit,
    onCrewUp: () -> Unit,
    onShare: () -> Unit,
) {
    val c = Ct.colors
    val item = ui.item ?: return
    Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
        when {
            ui.isOwner -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(stringResource(R.string.ws4_share), onShare, Modifier.weight(1f), icon = Icons.Rounded.Share)
                PrimaryButton(stringResource(R.string.ws4_seg_crew_up), onCrewUp, Modifier.weight(1f), icon = Icons.Rounded.Groups)
            }
            ui.application != null -> AppliedBar(ui.application.status)
            item.role.status != RoleStatus.OPEN -> Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(item.role.status)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ws4_role_not_open), style = Ct.type.bodyS, color = c.textSecondary)
            }
            else -> DeckActionBar(
                listOf(DeckAction.PASS, DeckAction.SAVE, DeckAction.INTERESTED),
                onAction = { a -> when (a) { DeckAction.PASS -> onPass(); DeckAction.SAVE -> onSave(); else -> onInterested() } },
                enabled = !ui.busy,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

/** "Applied ✓" with the application's current status and the closure promise. */
@Composable
private fun AppliedBar(status: ApplicationStatus) {
    val c = Ct.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.go, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ws4_applied), style = Ct.type.titleM, color = c.text)
                Spacer(Modifier.width(10.dp))
                StatusChip(status)
            }
            Text(stringResource(R.string.ws4_applied_promise), style = Ct.type.bodyS, color = c.textSecondary)
        }
    }
}

@Composable
private fun RoleDetailBody(nav: AppNavigator, item: RoleWithProject, ui: RoleDetailUi, onOwner: () -> Unit) {
    val c = Ct.colors
    val role = item.role
    val p = item.project
    val info = Taxonomy.info(role.roleCode)
    // The first cast role anyone opens on this device shows the "what normal looks like" tip.
    val showCastTip = remember(role.id) { role.isCast && !Ws4Prefs.castTipSeen }
    var castTip by rememberSaveable(role.id) { mutableStateOf(showCastTip) }
    LaunchedEffect(showCastTip) { if (showCastTip) Ws4Prefs.castTipSeen = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // 1 · Header: poster, role, title, pay line
        val topRight = when {
            role.remoteOk -> stringResource(R.string.ws1_badge_remote)
            // Beyond the viewer's range the city says more than "858 KM".
            ui.outOfRange && item.cityName.isNotBlank() -> item.cityName.uppercase(Locale.ENGLISH)
            ui.distanceKm != null -> "${ui.distanceKm.roundToInt().coerceAtLeast(1)} KM"
            else -> null
        }
        PosterBand(p, topRight = topRight)
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (ui.notYourRole || ui.outOfRange) {
                Spacer(Modifier.height(12.dp))
                OutsideGatesBanner(ui)
            }
            Spacer(Modifier.height(16.dp))
            RoleLabelRow(role)
            Text(p.title, style = Ct.type.titleL, color = c.text, modifier = Modifier.padding(top = 2.dp))
            PayLine(CardText.payLine(role, p), paid = role.isPaid, modifier = Modifier.padding(top = 8.dp), maxLines = 3)
            Spacer(Modifier.height(10.dp))
            TimecodeText(CardText.whenLine(role, p), style = Ct.type.monoS, color = c.text, maxLines = 2)
            // Places and languages are words (Inter); mono is for dates, times and numbers.
            Text(CardText.whereLine(item), style = Ct.type.bodyS, color = c.textSecondary, maxLines = 2)
        }

        // 2 · What you get
        Section(stringResource(R.string.ws4_sec_get)) {
            KeyValueRow(stringResource(R.string.ws4_kv_pay), payAmount(role), mono = true, valueColor = if (role.isPaid) c.go else c.text, divider = true)
            role.kitFeeDay?.takeIf { it > 0 }?.let { KeyValueRow(stringResource(R.string.ws4_kv_kit), Money.format(it, role.currency) + "/day", mono = true, divider = true) }
            role.paymentTerms?.takeIf { role.compType != com.tff.app.model.CompType.UNPAID }?.let {
                KeyValueRow(stringResource(R.string.ws4_kv_paid_when), CardText.termsLabel(it, role.isPostRole).replaceFirstChar { ch -> ch.uppercase() }, divider = true)
            }
            val perks = CardText.perksDetailed(role, p)
            if (perks.isEmpty()) Bullet(stringResource(R.string.ws4_no_perks))
            perks.forEach { Bullet(it) }
            Text(stringResource(R.string.ws4_credit_note), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
        }

        // 3 · Dates and place
        Section(stringResource(R.string.ws4_sec_dates)) {
            if (role.isPostRole) {
                KeyValueRow(stringResource(R.string.ws4_kv_where), stringResource(R.string.ws4_remote_anywhere), divider = true)
                if (CardText.needsPictureLock(role.roleCode)) p.pictureLockDate?.let { lock ->
                    KeyValueRow(stringResource(R.string.ws4_kv_lock), Dates.short(lock).uppercase(Locale.ENGLISH), mono = true, divider = true)
                    role.postDeadline?.let { KeyValueRow(stringResource(R.string.ws4_kv_lock_gap), CardText.lockToDue(lock, it), mono = true, divider = true) }
                }
                role.postDeadline?.let { KeyValueRow(stringResource(R.string.ws4_kv_deliver), Dates.short(it).uppercase(Locale.ENGLISH), mono = true, divider = true) }
                p.shootStart?.let { KeyValueRow(stringResource(R.string.ws4_kv_shoot_ref), CardText.dateList(p.shootDates), divider = true) }
            } else {
                if (p.shootDates.isNotEmpty()) {
                    KeyValueRow(stringResource(R.string.ws4_kv_shoot_days), CardText.dateList(p.shootDates).uppercase(Locale.ENGLISH), mono = true, divider = true)
                    KeyValueRow(stringResource(R.string.ws4_kv_days), if (p.shootDays == 1) stringResource(R.string.ws4_one_day) else stringResource(R.string.ws4_n_days, p.shootDays), mono = true, divider = true)
                }
                p.hoursCap?.let { KeyValueRow(stringResource(R.string.ws4_kv_hours), stringResource(R.string.ws4_hours_cap, it), mono = true, divider = true) }
                p.dateCertainty?.let {
                    KeyValueRow(stringResource(R.string.ws4_kv_certainty), stringResource(if (it == DateCertainty.LOCKED) R.string.ws4_dates_locked else R.string.ws4_dates_tentative), divider = true)
                }
                if (role.remoteOk) KeyValueRow(stringResource(R.string.ws4_kv_remote), stringResource(R.string.ws4_remote_ok_role), divider = true)
                val shotIn = CardText.placeName(item.areaName, item.cityName) + (ui.distanceKm?.let { " · " + stringResource(R.string.ws4_km_from_you, it.roundToInt().coerceAtLeast(1)) } ?: "")
                KeyValueRow(stringResource(R.string.ws4_kv_shot_in), shotIn, divider = true)
                p.storySetting?.takeIf { it.isNotBlank() }?.let { KeyValueRow(stringResource(R.string.ws4_kv_story), it, divider = true) }
                p.shootLocationType?.let { KeyValueRow(stringResource(R.string.ws4_kv_location), locationLabel(it), divider = true) }
                p.transport?.let { t ->
                    val ta = p.travelAllowancePerDay?.takeIf { t == Transport.MILEAGE_OR_TA_PAID && it > 0 }?.let { " · " + Money.format(it, role.currency) + "/day" } ?: ""
                    KeyValueRow(stringResource(R.string.ws4_kv_transport), CardText.transportLabel(t) + ta, divider = true)
                }
                p.meals?.let { KeyValueRow(stringResource(R.string.ws4_kv_meals), CardText.mealsLabel(it), divider = true) }
                if (p.shootDays > 1) p.lodging?.let { KeyValueRow(stringResource(R.string.ws4_kv_stay), stringResource(if (it) R.string.ws4_stay_yes else R.string.ws4_stay_no), divider = true) }
                KeyValueRow(stringResource(R.string.ws4_kv_address), p.exactAddress?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ws4_address_hidden))
            }
        }

        // 4 · The role
        Section(stringResource(R.string.ws4_sec_role)) {
            Text(info.duties, style = Ct.type.body, color = c.text)
            Spacer(Modifier.height(8.dp))
            KeyValueRow(stringResource(R.string.ws4_kv_credit), role.creditTitle, divider = true)
            if (role.count > 1) KeyValueRow(stringResource(R.string.ws4_kv_count), "×${role.count}", mono = true, divider = true)
            KeyValueRow(stringResource(R.string.ws4_kv_level), role.levelWanted?.let { CardText.levelLabel(it) } ?: stringResource(R.string.ws4_any_level), divider = true)
            if (role.stepUpFriendly) KeyValueRow(stringResource(R.string.ws4_kv_step_up), stringResource(R.string.ws4_step_up_yes), divider = true)
            role.gearPolicy?.takeIf { it != GearPolicy.NA }?.let { KeyValueRow(stringResource(R.string.ws4_kv_gear), CardText.gearPolicyLabel(it), divider = true) }
            Taxonomy.detailFields(info.detailsFamily).forEach { (key, label) ->
                role.roleDetails[key]?.takeIf { it.isNotBlank() }?.let { KeyValueRow(detailLabel(label), detailValue(it, yesNo(true), yesNo(false)), divider = true) }
            }
            if (role.scopeIncludes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.ws4_includes), style = Ct.type.label, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                ChipFlow { role.scopeIncludes.forEach { CtChip(it, tone = ChipTone.GO) } }
            }
            if (role.scopeExcludes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.ws4_excludes), style = Ct.type.label, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                ChipFlow { role.scopeExcludes.forEach { CtChip(it) } }
            }
        }

        // 5 · The project
        Section(stringResource(R.string.ws4_sec_project)) {
            Text(p.logline, style = Ct.type.body, color = c.text)
            Spacer(Modifier.height(8.dp))
            KeyValueRow(stringResource(R.string.ws4_kv_format), "${CardText.formatLabel(p.format)} · ${p.runtimeMin} min", divider = true)
            if (p.genres.isNotEmpty()) KeyValueRow(stringResource(R.string.ws4_kv_genre), p.genres.joinToString(", "), divider = true)
            KeyValueRow(stringResource(R.string.ws4_kv_languages), CardText.languageNames(p.languages).replace("/", ", "), divider = true)
            KeyValueRow(stringResource(R.string.ws4_kv_script), CardText.scriptStatusLabel(p.scriptStatus) + if (p.scriptReadable) " · " + stringResource(R.string.ws4_script_readable) else "", divider = true)
            p.pageCount?.let { KeyValueRow(stringResource(R.string.ws4_kv_pages), "$it", mono = true, divider = true) }
            KeyValueRow(stringResource(R.string.ws4_kv_stage), stageLabel(p.stage), divider = true)
            KeyValueRow(stringResource(R.string.ws4_kv_ambition), ambitionLabel(p.ambition), divider = true)
            KeyValueRow(stringResource(R.string.ws4_kv_roles_paid), CardText.rolesPaidChip(item), mono = true, divider = p.honestZeroBudget || p.lookbookUrl != null)
            if (p.honestZeroBudget) {
                Spacer(Modifier.height(8.dp))
                CtChip(stringResource(R.string.ws1_honest_zero), filled = true)
            }
            p.lookbookUrl?.takeIf { it.isNotBlank() }?.let { url ->
                GhostButton(stringResource(R.string.ws4_watch_lookbook), { nav.openUrl(url) }, icon = Icons.Rounded.PlayCircle, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // 6 · Team attached
        Section(stringResource(R.string.ws4_sec_team)) {
            val attached = item.team.filterNot { it.isOwner }
            if (attached.isEmpty()) Text(stringResource(R.string.ws4_team_none), style = Ct.type.bodyS, color = c.textSecondary)
            else TeamMonograms(attached)
        }

        // 7 · Who's posting
        Section(stringResource(R.string.ws4_sec_owner)) {
            val o = item.owner
            Row(
                Modifier.fillMaxWidth().clip(CtShapes.field).clickable(role = Role.Button, onClick = onOwner).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Monogram(o.displayName.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase() }, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.shortName, style = Ct.type.titleM, color = c.text)
                    Text(CardText.levelLabel(o.level) + " · " + stringResource(R.string.ws4_payer_line, o.shortName), style = Ct.type.bodyS, color = c.textSecondary)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.ws4_view_profile), tint = c.textMuted)
            }
            Spacer(Modifier.height(8.dp))
            if (o.idVerified) {
                CtChip(stringResource(R.string.ws1_badge_id), tone = ChipTone.INFO, filled = true, icon = Icons.Rounded.Shield,
                    contentDescription = stringResource(R.string.ws1_badge_id) + ". " + stringResource(R.string.ws1_tip_id))
                Spacer(Modifier.height(8.dp))
            }
            TrackRecordChips(o.reputation, asOwner = true)
        }

        // 8 · Safety and content (cast tip first time)
        if (castTip) {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                InfoTipCard(
                    stringResource(R.string.ws4_cast_tip),
                    title = stringResource(R.string.ws4_cast_tip_title),
                    actionLabel = stringResource(R.string.ws4_safety_center),
                    onAction = { nav.toSafetyCenter() },
                    onDismiss = { castTip = false },
                )
            }
        }
        Section(stringResource(R.string.ws4_sec_safety)) {
            if (p.contentFlags.isEmpty()) Text(stringResource(R.string.ws4_no_flags), style = Ct.type.bodyS, color = c.textSecondary)
            else ChipFlow { ContentFlag.entries.filter { it in p.contentFlags }.forEach { f -> CtChip(CardText.contentFlagLabel(f), tone = ChipTone.WARN, icon = contentFlagIcon(f)) } }
            Spacer(Modifier.height(8.dp))
            p.intimacyCoordinator?.let { KeyValueRow(stringResource(R.string.ws4_kv_ic), yesNo(it), divider = true) }
            p.stuntOrArmourer?.let { KeyValueRow(stringResource(R.string.ws4_kv_stunt), yesNo(it), divider = true) }
            p.guardianPresent?.let { KeyValueRow(stringResource(R.string.ws4_kv_guardian), yesNo(it), divider = true) }
            p.safetyMeasures?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.ws4_kv_measures), style = Ct.type.bodyS, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                Text(it, style = Ct.type.body.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.body.lineHeight), color = c.text, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
            }
            if (role.isCast) p.auditionMode?.let { m ->
                KeyValueRow(stringResource(R.string.ws4_kv_audition), CardText.auditionModeLabel(m), icon = Icons.Rounded.Videocam, divider = true)
                Text(stringResource(R.string.ws4_audition_fixed), style = Ct.type.label, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
            }
            if (p.bringFriendWelcome || p.shootLocationType == LocationType.PRIVATE_HOME) {
                KeyValueRow(stringResource(R.string.ws4_kv_friend), stringResource(R.string.ws4_friend_yes), icon = Icons.Rounded.Home, divider = true)
            }
            p.insurance?.takeIf { it != YesNoNa.NA }?.let { KeyValueRow(stringResource(R.string.ws4_kv_insurance), yesNoNa(it), divider = true) }
            p.permits?.takeIf { it != YesNoNa.NA }?.let { KeyValueRow(stringResource(R.string.ws4_kv_permits), yesNoNa(it), divider = true) }
            if (p.shootLocationType == LocationType.PRIVATE_HOME) {
                Spacer(Modifier.height(10.dp))
                InfoTipCard(stringResource(R.string.ws4_private_home_tip), icon = Icons.Rounded.Home)
            }
        }

        // 9 · Status
        Section(stringResource(R.string.ws4_sec_status)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(role.status)
                Spacer(Modifier.width(10.dp))
                // The chip already says Open / Filled / Closed; the rest of the footer is the facts.
                TimecodeText(CardText.footer(item).split(" · ").drop(1).joinToString(" · "), style = Ct.type.monoS, color = c.textSecondary, maxLines = 2)
            }
            CardText.postedAgo(p, AppClock.nowMillis())?.let {
                Text(it, style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
            }
            if (p.rescheduleCount >= 2) {
                Spacer(Modifier.height(8.dp))
                CtChip(stringResource(R.string.ws1_rescheduled, p.rescheduleCount), tone = ChipTone.STOP, monoNumbers = true)
            }
            Text(stringResource(R.string.ws4_closure_promise), style = Ct.type.bodyS, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** "Not one of your roles · 858 km from Jayanagar. You can still share it." (info container). */
@Composable
private fun OutsideGatesBanner(ui: RoleDetailUi) {
    val c = Ct.colors
    val bits = buildList {
        if (ui.notYourRole) add(stringResource(R.string.ws4_outside_not_yours))
        val km = ui.distanceKm
        if (ui.outOfRange && km != null) {
            val from = ui.myAreaName?.takeIf { it.isNotBlank() }
            add(if (from != null) stringResource(R.string.ws4_outside_far, km.roundToInt(), from) else stringResource(R.string.ws4_km_from_you, km.roundToInt()))
        }
    }
    val text = bits.joinToString(" · ") + ". " + stringResource(R.string.ws4_outside_share)
    Row(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(c.infoContainer).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = c.info, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(com.tff.app.ui.components.monoNumbers(text, Ct.type.mono), style = Ct.type.bodyS, color = c.onInfoContainer)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp).semantics(mergeDescendants = false) { }) {
        SectionHeader(title)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Ct.colors.go, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Ct.type.body.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.body.lineHeight), color = Ct.colors.text)
    }
}

/** "₹2,500/day", "₹12,000 flat", "Stipend ₹800/day", "Unpaid". */
private fun payAmount(role: com.tff.app.model.ProjectRole): String {
    val money = Money.range(role.amountMin, role.amountMax, role.currency)
    return when (role.compType) {
        com.tff.app.model.CompType.UNPAID -> CardText.compTypeLabel(role.compType)
        com.tff.app.model.CompType.PAID_FLAT -> if (money.isBlank()) CardText.compTypeLabel(role.compType) else "$money flat"
        com.tff.app.model.CompType.PAID_DAY -> if (money.isBlank()) CardText.compTypeLabel(role.compType) else "$money/day"
        com.tff.app.model.CompType.STIPEND -> if (money.isBlank()) "Stipend" else if (role.isPostRole) "Stipend $money" else "Stipend $money/day"
    }
}

/** Role-details form labels read as questions ("Sides available (Y/N)"); on a listing drop the hint. */
internal fun detailLabel(label: String): String = label.substringBefore(" (").trim()

/** "Y" → Yes, "N" → No, "DAY_PLAYER" → "Day player"; anything else as typed. */
internal fun detailValue(v: String, yes: String, no: String): String {
    val t = v.trim()
    return when {
        t.equals("y", true) || t.equals("yes", true) -> yes
        t.equals("n", true) || t.equals("no", true) -> no
        t.length > 1 && t.all { it.isUpperCase() || it == '_' } -> t.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        else -> t
    }
}

@Composable
private fun yesNo(b: Boolean) = stringResource(if (b) R.string.ws4_yes else R.string.ws4_no)

@Composable
private fun yesNoNa(v: YesNoNa) = stringResource(
    when (v) { YesNoNa.YES -> R.string.ws4_yes; YesNoNa.NO -> R.string.ws4_no; YesNoNa.NOT_YET -> R.string.ws4_not_yet; YesNoNa.NA -> R.string.ws4_na }
)

@Composable
internal fun locationLabel(t: LocationType) = stringResource(
    when (t) {
        LocationType.STUDIO -> R.string.ws4_loc_studio
        LocationType.OUTDOOR_PUBLIC -> R.string.ws4_loc_outdoor
        LocationType.PRIVATE_HOME -> R.string.ws4_loc_home
        LocationType.OFFICE_COMMERCIAL -> R.string.ws4_loc_office
        LocationType.MIXED -> R.string.ws4_loc_mixed
    }
)

@Composable
private fun stageLabel(s: Stage) = stringResource(
    when (s) { Stage.DEVELOPMENT -> R.string.ws4_stage_dev; Stage.PRE -> R.string.ws4_stage_pre; Stage.PRODUCTION -> R.string.ws4_stage_prod; Stage.POST -> R.string.ws4_stage_post }
)

@Composable
private fun ambitionLabel(a: Ambition) = stringResource(
    when (a) {
        Ambition.FOR_FUN -> R.string.ws4_amb_fun
        Ambition.PORTFOLIO -> R.string.ws4_amb_portfolio
        Ambition.FESTIVAL -> R.string.ws4_amb_festival
        Ambition.DISTRIBUTION -> R.string.ws4_amb_distribution
        Ambition.CLIENT_PAID -> R.string.ws4_amb_client
    }
)
