package com.tff.app.ui.screens.person

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.data.RepoException
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.model.ApplicantView
import com.tff.app.model.AvailabilityPattern
import com.tff.app.model.CompMode
import com.tff.app.model.CompType
import com.tff.app.model.Decision
import com.tff.app.model.LinkKind
import com.tff.app.model.ProfileCard
import com.tff.app.model.ReportTarget
import com.tff.app.model.RoleWithProject
import com.tff.app.model.WorkLink
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipStyle
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.DangerButton
import com.tff.app.ui.components.DeckAction
import com.tff.app.ui.components.DeckActionBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GeneratedPosterArt
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.KeyValueRow
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.components.SCOPE_RATIO
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.components.TopBarAction
import com.tff.app.ui.components.TrackRecordChip
import com.tff.app.ui.components.TrackRecordChips
import com.tff.app.ui.components.VerificationBadges
import com.tff.app.ui.components.posterTexture
import com.tff.app.ui.components.youTubeThumbnail
import com.tff.app.ui.screens.crewup.AskComposerSheet
import com.tff.app.ui.screens.crewup.InTalksOverlay
import com.tff.app.ui.screens.crewup.firstName
import com.tff.app.ui.screens.crewup.inTalksLine
import com.tff.app.ui.screens.role.linkHost
import com.tff.app.ui.screens.role.linkKindLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * S10 Person detail: header (name, role, level, area, distance, badges, Active today), reels, roles
 * with step-up, track record with an explainer, confirmed then self-listed credits, gear and tools,
 * languages, availability, taste, bio, affiliations. An owner looking at an applicant (or a Scout
 * candidate) also sees the application and the S09 actions. Overflow: Report, Block.
 */
@Composable
fun PersonDetailScreen(nav: AppNavigator, profileId: String, roleId: String?, applicationId: String?) {
    val vm = rememberVm(key = "person_${profileId}_${roleId}_$applicationId") { PersonDetailViewModel(it, profileId, roleId, applicationId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var talk by remember { mutableStateOf<Pair<String, String>?>(null) }
    val genericError = stringResource(R.string.ws4_generic_error)
    val declinedMsg = stringResource(R.string.ws4_declined_toast)
    val backupMsg = stringResource(R.string.ws4_backup_toast)
    val askSentMsg = stringResource(R.string.ws4_ask_sent)
    val blockedMsg = stringResource(R.string.ws4_blocked_toast)
    val skippedMsg = stringResource(R.string.ws4_skipped_toast)
    val card = ui.card
    val signedIn = ui.me != null

    fun fail(e: Throwable) = nav.toast((e as? RepoException)?.userMessage ?: genericError)
    fun decide(d: Decision) {
        val v = ui.view ?: return
        val item = ui.item ?: return
        scope.launch {
            vm.decide(d).onSuccess { m ->
                when (d) {
                    Decision.TALK -> m?.let { talk = it.id to inTalksLine(v, item) }
                    Decision.DECLINE -> { nav.toast(declinedMsg.format(firstName(v))); nav.back() }
                    Decision.BACKUP -> { nav.toast(backupMsg.format(firstName(v))); nav.back() }
                }
            }.onFailure(::fail)
        }
    }

    Scaffold(
        containerColor = Ct.colors.bg,
        topBar = {
            CtTopBar(
                title = card?.profile?.shortName ?: stringResource(R.string.ws4_person_title),
                subtitle = ui.item?.let { stringResource(R.string.ws4_person_for_role, CardText.roleName(it.role), it.project.title) },
                onBack = { nav.back() },
                actions = {
                    if (card != null && signedIn && !ui.isSelf) {
                        Box {
                            TopBarAction(Icons.Rounded.MoreVert, stringResource(R.string.ws4_more_options), { menu = true })
                            DropdownMenu(menu, { menu = false }, containerColor = Ct.colors.surfaceRaised) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ws4_report_person), color = Ct.colors.text) },
                                    leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = Ct.colors.textSecondary) },
                                    onClick = { menu = false; report = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ws4_block_person), color = Ct.colors.onDangerContainer) },
                                    leadingIcon = { Icon(Icons.Rounded.Block, null, tint = Ct.colors.stop) },
                                    onClick = { menu = false; confirmBlock = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            val v = ui.view
            if (card != null && v != null && ui.item != null && (ui.canDecide || ui.canAsk)) {
                Column(Modifier.fillMaxWidth().background(Ct.colors.surface).navigationBarsPadding()) {
                    HorizontalDivider(color = Ct.colors.outline.copy(alpha = 0.6f))
                    DeckActionBar(
                        if (ui.canAsk) listOf(DeckAction.SKIP, DeckAction.SEND_ASK) else listOf(DeckAction.NOT_THIS_TIME, DeckAction.BACKUP, DeckAction.LETS_TALK),
                        onAction = { a ->
                            when (a) {
                                DeckAction.LETS_TALK -> decide(Decision.TALK)
                                DeckAction.BACKUP -> decide(Decision.BACKUP)
                                DeckAction.NOT_THIS_TIME -> decide(Decision.DECLINE)
                                DeckAction.SEND_ASK -> asking = true
                                DeckAction.SKIP -> { vm.skip(); nav.toast(skippedMsg.format(firstName(v))); nav.back() }
                                else -> Unit
                            }
                        },
                        enabled = !ui.busy,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                ui.loading -> LoadingSkeleton(Modifier.fillMaxSize().padding(top = 16.dp), kind = SkeletonKind.LIST, rows = 6)
                ui.error != null -> ErrorState(onRetry = vm::load, message = ui.error!!.ifBlank { null }, modifier = Modifier.fillMaxSize())
                ui.unavailable || card == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(stringResource(R.string.ws4_person_unavailable), stringResource(R.string.ws4_person_unavailable_body), icon = Icons.Rounded.PersonOff) {
                        PrimaryButton(stringResource(R.string.ws1_back), { nav.back() })
                    }
                }
                else -> PersonBody(nav, card, ui)
            }
        }
    }

    if (report) ReportSheet(ReportTarget.PROFILE, profileId, onDismiss = { report = false })
    if (confirmBlock && card != null) {
        val c = Ct.colors
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            containerColor = c.surfaceRaised,
            title = { Text(stringResource(R.string.ws4_block_title, card.profile.shortName), style = Ct.type.titleM, color = c.text) },
            text = { Text(stringResource(R.string.ws4_block_body), style = Ct.type.bodyS, color = c.textSecondary) },
            confirmButton = {
                DangerButton(stringResource(R.string.ws4_block_confirm), {
                    confirmBlock = false
                    scope.launch { vm.block().onSuccess { nav.toast(blockedMsg.format(card.profile.shortName.removeSuffix("."))); nav.back() }.onFailure(::fail) }
                })
            },
            dismissButton = { GhostButton(stringResource(R.string.ws1_cancel), { confirmBlock = false }, color = c.textSecondary) },
        )
    }
    val v = ui.view
    val item = ui.item
    if (asking && v != null && item != null) {
        AskComposerSheet(
            view = v, item = item, asksLeft = ui.asksLeft,
            check = { vm.askCheck() },
            send = { note -> vm.sendAsk(note) },
            onCancel = { asking = false },
            onSent = { asking = false; nav.toast(askSentMsg.format(firstName(v))); nav.back() },
            onSkip = { asking = false; vm.skip(); nav.toast(skippedMsg.format(firstName(v))); nav.back() },
        )
    }
    talk?.let { (matchId, subtitle) ->
        InTalksOverlay(
            subtitle = subtitle,
            onOpenChat = { talk = null; nav.toChat(matchId) },
            onKeepSwiping = { talk = null; nav.back() },
        )
    }
}

@Composable
private fun PersonBody(nav: AppNavigator, card: ProfileCard, ui: PersonUi) {
    val c = Ct.colors
    val p = card.profile
    val item = ui.item
    var explain by rememberSaveable { mutableStateOf(false) }
    val activeToday = AppClock.nowMillis() - p.lastActiveAt in 0 until 24 * 3_600_000L

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Monogram(p.initials, size = 72.dp, highlight = p.idVerified)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.shortName + (p.pronouns?.takeIf { it.isNotBlank() }?.let { "  ($it)" } ?: ""),
                    style = Ct.type.titleL, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(CardText.personRoleLine(p), style = Ct.type.slate, color = c.accent, maxLines = 2)
                val place = listOfNotNull(card.areaName, card.cityName).distinct().joinToString(", ")
                // A far-away person (another city) shows the city, not "858 km".
                val farKm = (ui.me?.travelRadiusKm ?: 50) * 3
                val dist = card.distanceKm?.takeIf { it <= farKm }?.let { if (it < 1) "<1 km" else "${it.roundToInt()} km" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        com.tff.app.ui.components.monoNumbers(listOfNotNull(place.ifBlank { null }, dist).joinToString(" · "), Ct.type.mono),
                        style = Ct.type.bodyS, color = c.textSecondary, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (activeToday) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(c.go))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.ws1_active_today), style = Ct.type.label, color = c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
        CardText.stepUpChip(p)?.let {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CtChip(it, tone = ChipTone.GO, style = ChipStyle.CAPS, contentDescription = stringResource(R.string.ws1_step_up_cd, it.removePrefix("→ ")))
            }
        }
        VerificationBadges(p, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))

        // Owner in context: the application and how they fit this role
        val v = ui.view
        if (item != null && v != null) ApplicationCard(item, v, ui)

        // Reels
        val reels = p.workLinks.filter { it.kind == LinkKind.REEL || (it.kind == LinkKind.HEADSHOT && item?.role?.isCast == true) }
        val other = p.workLinks.filter { it.kind == LinkKind.PORTFOLIO || it.kind == LinkKind.OTHER }
        Section(stringResource(R.string.ws4_sec_reels)) {
            if (reels.isEmpty() && other.isEmpty()) Text(stringResource(R.string.ws4_no_reels), style = Ct.type.bodyS, color = c.textSecondary)
            reels.forEach { ReelTile(it, p.displayName) { nav.openUrl(it.url) } }
            other.forEach { LinkLine(it) { nav.openUrl(it.url) } }
        }

        // Roles including step-up
        Section(stringResource(R.string.ws4_sec_roles)) {
            KeyValueRow(stringResource(R.string.ws4_kv_primary), CardText.roleName(p.primaryRole) + " · " + CardText.levelLabel(p.level), divider = p.secondaryRoles.isNotEmpty() || p.stepUpRoles.isNotEmpty())
            if (p.secondaryRoles.isNotEmpty()) {
                KeyValueRow(
                    stringResource(R.string.ws4_kv_also),
                    // Proficiency as words ("Director · experienced"), never rating-like dots.
                    p.secondaryRoles.zip(p.secondaryRoles.map { proficiencyLabel(it.proficiency) })
                        .joinToString(", ") { (r, level) -> CardText.roleName(r.code) + " · " + level },
                    divider = p.stepUpRoles.isNotEmpty(),
                )
            }
            if (p.stepUpRoles.isNotEmpty()) KeyValueRow(stringResource(R.string.ws4_kv_stepping_up), p.stepUpRoles.joinToString(", ") { "→ " + CardText.roleName(it) })
        }

        // Track record (facts only) with an explainer
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.ws4_sec_track)) }
                IconButton(onClick = { explain = !explain }) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.ws4_track_explain_cd), tint = c.info)
                }
            }
            if (explain) {
                InfoTipCard(stringResource(R.string.ws4_track_explain), onDismiss = { explain = false })
                Spacer(Modifier.height(8.dp))
            }
            TrackRecordChips(card.reputation, asOwner = false)
            val r = card.reputation
            if (r.paidOnTime != null || r.footageDelivered != null || r.keptBookings != null) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.ws4_as_production), style = Ct.type.label, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                TrackRecordChips(r, asOwner = true)
            }
        }

        // Credits: confirmed first, then self-listed
        val selfListed = p.workLinks.filter { it.kind == LinkKind.CREDIT || it.kind == LinkKind.IMDB }
        Section(stringResource(R.string.ws4_sec_credits)) {
            if (card.confirmedCredits.isEmpty() && selfListed.isEmpty()) Text(stringResource(R.string.ws4_no_credits), style = Ct.type.bodyS, color = c.textSecondary)
            card.confirmedCredits.forEach { cc ->
                CreditRow("${cc.projectTitle} · ${cc.creditTitle}", "${cc.year}", confirmed = true)
            }
            selfListed.forEach { l ->
                CreditRow(
                    (l.title ?: linkHost(l.url)) + (l.roleCode?.let { " · " + CardText.roleName(it) } ?: ""),
                    l.year?.toString(), confirmed = false, onClick = { nav.openUrl(l.url) },
                )
            }
        }

        // Gear and tools
        if (p.gear.isNotEmpty() || p.tools.isNotEmpty()) Section(stringResource(R.string.ws4_sec_gear)) {
            if (p.gear.isNotEmpty()) ChipFlow { p.gear.forEach { CtChip(it, filled = true) } }
            if (p.gear.isNotEmpty() && p.tools.isNotEmpty()) Spacer(Modifier.height(8.dp))
            if (p.tools.isNotEmpty()) ChipFlow { p.tools.forEach { CtChip(it) } }
        }

        // Languages
        Section(stringResource(R.string.ws4_sec_languages)) {
            Text(p.languages.joinToString(", ") { CardText.languageName(it) }, style = Ct.type.body, color = c.text)
        }

        // Availability and how they work
        Section(stringResource(R.string.ws4_sec_availability)) {
            KeyValueRow(stringResource(R.string.ws4_kv_when), availabilityLabel(p.availabilityPattern), divider = true)
            KeyValueRow(stringResource(R.string.ws4_kv_works), compModeLabel(p.compMode), divider = true)
            if (p.compMode != CompMode.PAID_ONLY && p.unpaidMustHaves.isNotEmpty()) {
                KeyValueRow(stringResource(R.string.ws4_kv_unpaid_if), p.unpaidMustHaves.joinToString(", ") { CardText.perkLabel(it).lowercase() }, divider = true)
            }
            if (p.gear.isNotEmpty()) p.kitFeeDay?.takeIf { it > 0 }?.let { fee ->
                val currency = item?.role?.currency ?: ui.currency
                KeyValueRow(stringResource(R.string.ws4_kv_kit_fee), Money.format(fee, currency) + "/day", mono = true, divider = true)
            }
            KeyValueRow(stringResource(R.string.ws4_kv_remote), stringResource(if (p.remoteOk) R.string.ws4_yes else R.string.ws4_no))
        }

        if (p.tasteFilms.isNotEmpty()) Section(stringResource(R.string.ws4_sec_taste)) {
            p.tasteFilms.take(5).forEach { Text("· $it", style = Ct.type.body, color = c.text, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        p.bio?.takeIf { it.isNotBlank() }?.let { bio -> Section(stringResource(R.string.ws4_sec_bio)) { Text(bio, style = Ct.type.body, color = c.text) } }
        if (p.affiliations.isNotEmpty()) Section(stringResource(R.string.ws4_sec_affiliations)) {
            ChipFlow { p.affiliations.forEach { CtChip(it) } }
        }
    }
}

/** The owner's view of this person's application (note, screening answer, fit) or Scout fit. */
@Composable
private fun ApplicationCard(item: RoleWithProject, v: ApplicantView, ui: PersonUi) {
    val c = Ct.colors
    val app = v.application
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp).clip(CtShapes.card)
            .background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (app == null) R.string.ws4_scout_fit else R.string.ws4_their_application).uppercase(),
                style = Ct.type.slate, color = c.accent, modifier = Modifier.weight(1f),
            )
            if (app != null) StatusChip(app.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(com.tff.app.ui.components.monoNumbers(CardText.fitLine(item, v), Ct.type.mono), style = Ct.type.bodyS, color = c.text)
        if (v.reasons.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ChipFlow(spacing = 6.dp, lineSpacing = 6.dp) { v.reasons.take(3).forEach { TrackRecordChip(it, interactive = false, small = true) } }
        }
        app?.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text("“$it”", style = Ct.type.body, color = c.text)
        }
        val q = item.role.screeningQuestion
        app?.screeningAnswer?.takeIf { it.isNotBlank() }?.let { ans ->
            Spacer(Modifier.height(12.dp))
            if (!q.isNullOrBlank()) Text(q, style = Ct.type.label, color = c.textSecondary)
            Text(ans, style = Ct.type.bodyS, color = c.text, modifier = Modifier.padding(top = 2.dp))
        }
        if (app?.kitOffered == true) {
            Spacer(Modifier.height(10.dp))
            CtChip(stringResource(R.string.ws4_brings_kit), tone = ChipTone.GO, filled = true)
        }
        Spacer(Modifier.height(10.dp))
        val footer = when {
            app == null -> stringResource(R.string.ws1_scout_not_applied)
            else -> stringResource(R.string.ws1_applied_ago, Dates.ago(app.createdAt, AppClock.nowMillis()))
        }
        Text(footer, style = Ct.type.label, color = c.textMuted)
        if (item.role.compType == CompType.UNPAID && app == null) {
            Text(stringResource(R.string.ws4_scout_unpaid_note), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 4.dp))
        }
        if (!ui.canDecide && app != null && ui.context != PersonContext.SCOUT) {
            Text(stringResource(R.string.ws4_already_answered), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ReelTile(link: WorkLink, seed: String, onOpen: () -> Unit) {
    val c = Ct.colors
    val thumb = remember(link.url) { youTubeThumbnail(link.url) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(CtShapes.field).clickable(role = Role.Button, onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(SCOPE_RATIO).clip(CtShapes.field)) {
            GeneratedPosterArt(seed = seed + link.id, Modifier.fillMaxSize())
            if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().posterTexture())
            Box(
                Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.6f)).border(1.dp, c.text.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.text, modifier = Modifier.size(28.dp)) }
        }
        // Inset the caption so the tile's rounded clip (14dp corners) doesn't shave its first and last letters.
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (link.title ?: linkHost(link.url)) + (link.roleCode?.let { " · " + CardText.roleName(it) } ?: ""),
                style = Ct.type.bodyS, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(linkKindLabel(link.kind) + (link.year?.let { " · $it" } ?: ""), style = Ct.type.monoS, color = c.textMuted)
        }
    }
}

@Composable
private fun LinkLine(link: WorkLink, onOpen: () -> Unit) {
    val c = Ct.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CtShapes.field).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = c.info, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(link.title ?: linkHost(link.url), style = Ct.type.bodyS, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(linkKindLabel(link.kind), style = Ct.type.label, color = c.textMuted)
    }
}

@Composable
private fun CreditRow(title: String, year: String?, confirmed: Boolean, onClick: (() -> Unit)? = null) {
    val c = Ct.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clip(CtShapes.field).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.bodyS, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (year != null) Text(year, style = Ct.type.monoS, color = c.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        if (confirmed) CtChip(stringResource(R.string.ws1_badge_confirmed_credit), tone = ChipTone.GO, filled = true, icon = Icons.Rounded.Verified, small = true,
            contentDescription = stringResource(R.string.ws1_badge_confirmed_credit) + ". " + stringResource(R.string.ws1_tip_confirmed_credit))
        else CtChip(stringResource(R.string.ws1_badge_self_listed), small = true)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        SectionHeader(title)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun availabilityLabel(a: AvailabilityPattern) = stringResource(
    when (a) {
        AvailabilityPattern.ANY_DAY -> R.string.ws4_avail_any
        AvailabilityPattern.WEEKENDS_ONLY -> R.string.ws4_avail_weekends
        AvailabilityPattern.EVENINGS_AND_WEEKENDS -> R.string.ws4_avail_evenings
        AvailabilityPattern.WEEKDAYS_ONLY -> R.string.ws4_avail_weekdays
    }
)

@Composable
private fun compModeLabel(m: CompMode) = stringResource(
    when (m) {
        CompMode.PAID_ONLY -> R.string.ws4_comp_paid_only
        CompMode.PAID_AND_SELECTIVE -> R.string.ws4_comp_selective
        CompMode.OPEN_TO_UNPAID -> R.string.ws4_comp_open
    }
)

/** Secondary-role proficiency 1..3 as plain words. */
@Composable
private fun proficiencyLabel(level: Int): String = stringResource(
    when (level.coerceIn(1, 3)) { 1 -> R.string.ws4_prof_1; 2 -> R.string.ws4_prof_2; else -> R.string.ws4_prof_3 }
)
