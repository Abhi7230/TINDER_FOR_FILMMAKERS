package com.tff.app.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.domain.ReputationCalc
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.model.AppMode
import com.tff.app.model.CompMode
import com.tff.app.model.LinkKind
import com.tff.app.model.ProfileCard
import com.tff.app.nav.AppNavigator
import com.tff.app.nav.MainTab
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipStyle
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTooltip
import com.tff.app.ui.components.KeyValueRow
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.TopBarAction
import com.tff.app.ui.components.TrackRecordChip
import com.tff.app.ui.components.TrackRecordChips
import com.tff.app.ui.components.VerificationBadges
import com.tff.app.ui.components.shortRoleLabel
import com.tff.app.ui.screens.onboarding.availabilityLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.launch

/**
 * S19 Me: my profile laid out like S10 (header, work, roles, track record with an "i" explainer,
 * credits, where and when, gear, taste, about) with Edit entry points, plus the private "How I work"
 * section, the Keep list with [Invite to a project] (Rebook), saved roles, and Safety Center / Settings.
 * Guests get a friendly sign-in state. A tab screen: own top bar, no bottom insets.
 */
@Composable
fun MeScreen(nav: AppNavigator) {
    val vm = rememberVm { MeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Ct.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        CtTopBar(
            title = stringResource(R.string.ws7_me_title),
            actions = { TopBarAction(Icons.Rounded.Settings, stringResource(R.string.ws7_me_settings_cd), nav::toSettings) },
        )
        when (val s = ui) {
            MeUi.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp), kind = SkeletonKind.DETAIL)
            is MeUi.Error -> ErrorState(onRetry = vm::load, message = s.message ?: stringResource(R.string.ws7_load_error), modifier = Modifier.fillMaxSize())
            MeUi.Guest -> GuestMe(nav)
            MeUi.NoProfile -> Column(Modifier.fillMaxSize()) {
                EmptyState(stringResource(R.string.ws7_me_noprofile_title), stringResource(R.string.ws7_me_noprofile_body), Modifier.weight(1f)) {
                    PrimaryButton(stringResource(R.string.ws7_me_noprofile_cta), nav::toOnboarding)
                }
                EntryRows(nav, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            is MeUi.Ready -> MeContent(s, nav)
        }
    }
}

@Composable
private fun GuestMe(nav: AppNavigator) {
    Column(Modifier.fillMaxSize()) {
        EmptyState(stringResource(R.string.ws7_me_guest_title), stringResource(R.string.ws7_me_guest_body), Modifier.weight(1f), icon = Icons.Rounded.Lock) {
            PrimaryButton(stringResource(R.string.ws7_me_guest_cta), nav::toWelcome)
        }
        EntryRows(nav, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun EntryRows(nav: AppNavigator, modifier: Modifier = Modifier) {
    val demo = runCatching { AppContainer.repo.mode == AppMode.DEMO }.getOrDefault(false)
    Column(modifier.fillMaxWidth()) {
        NavRow(Icons.Rounded.Shield, stringResource(R.string.ws7_me_safety), stringResource(R.string.ws7_me_safety_body), nav::toSafetyCenter)
        NavRow(
            Icons.Rounded.Settings, stringResource(R.string.ws7_me_settings),
            stringResource(if (demo) R.string.ws7_me_settings_body else R.string.ws7_me_settings_body_live), nav::toSettings,
        )
    }
}

@Composable
private fun MeContent(s: MeUi.Ready, nav: AppNavigator) {
    val c = Ct.colors
    val p = s.profile
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var trackInfo by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf<ProfileCard?>(null) }
    var inviteCount by remember { mutableIntStateOf(0) }
    val edit = stringResource(R.string.ws7_edit)
    val selfListed = p.workLinks.filter { it.kind == LinkKind.CREDIT || it.kind == LinkKind.IMDB }
    val confirmed = s.card?.confirmedCredits.orEmpty()
    val showCredits = confirmed.isNotEmpty() || selfListed.isNotEmpty()
    val showGearBlock = p.gear.isNotEmpty() || p.tools.isNotEmpty() || !com.tff.app.model.Taxonomy.isCast(p.primaryRole)
    // header, stats, work, roles, track, [credits], where, how, [gear], about → keep
    val keepIndex = 8 + (if (showCredits) 1 else 0) + (if (showGearBlock) 1 else 0)

    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item("header") { Header(s, nav) }
        item("stats") {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${s.saved.size}", stringResource(R.string.ws7_saved_title), nav::toSaved, Modifier.weight(1f))
                StatTile("${s.keep.size}", stringResource(R.string.ws7_me_keep).lowercase().replaceFirstChar { it.uppercase() }, {
                    scope.launch { list.animateScrollToItem(keepIndex) }
                }, Modifier.weight(1f))
            }
        }

        // ── Work ──
        item("work") {
            Section(stringResource(R.string.ws7_me_work), edit, nav::toEditProfile)
            val links = p.workLinks
            if (links.isEmpty()) {
                Text(stringResource(R.string.ws7_me_no_links), style = Ct.type.bodyS, color = c.textSecondary)
                GhostButton(stringResource(R.string.ws7_me_add_link), nav::toEditProfile)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(links, key = { it.id.ifBlank { it.url } }) { l -> WorkLinkTile(l, onOpen = { nav.openUrl(l.url) }) }
                }
            }
        }

        // ── Roles ──
        item("roles") {
            Section(stringResource(R.string.ws7_me_roles), edit, nav::toEditProfile)
            RoleLine(stringResource(R.string.ws7_me_role_main), listOf(p.primaryRole))
            if (p.secondaryRoles.isNotEmpty()) RoleLine(stringResource(R.string.ws7_me_role_also), p.secondaryRoles.map { it.code })
            if (p.stepUpRoles.isNotEmpty()) RoleLine(stringResource(R.string.ws7_me_role_step), p.stepUpRoles, step = true)
        }

        // ── Track record ──
        item("track") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.ws7_me_track)) }
                IconButton(onClick = { trackInfo = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.ws7_me_track_info_cd), tint = c.info)
                }
            }
            Text(stringResource(R.string.ws7_me_track_as_crew), style = Ct.type.label, color = c.textMuted)
            Spacer(Modifier.height(6.dp))
            TrackRecordChips(s.reputation, asOwner = false)
            if (s.ownsProjects || s.reputation.keptBookings != null || s.reputation.paidOnTime != null) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ws7_me_track_as_owner), style = Ct.type.label, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                TrackRecordChips(s.reputation, asOwner = true)
            }
        }

        // ── Credits ──
        if (showCredits) item("credits") {
            Section(stringResource(R.string.ws7_me_credits), edit, nav::toEditProfile)
            confirmed.forEach { cr ->
                CreditRow("${cr.projectTitle} · ${cr.creditTitle}", cr.year.toString(), stringResource(R.string.ws1_badge_confirmed_credit), ChipTone.GO, stringResource(R.string.ws1_tip_confirmed_credit))
            }
            selfListed.forEach { l ->
                CreditRow(l.title ?: l.url.substringAfter("://").take(40), l.year?.toString() ?: "", stringResource(R.string.ws1_badge_self_listed), ChipTone.NEUTRAL, null)
            }
        }

        // ── Where and when ──
        item("where") {
            Section(stringResource(R.string.ws7_me_where), edit, nav::toEditProfile)
            val place = listOfNotNull(s.card?.areaName, s.card?.cityName).joinToString(", ").ifEmpty { p.areaId }
            KeyValueRow(stringResource(R.string.ws7_me_area), place, divider = true)
            KeyValueRow(
                stringResource(R.string.ws7_me_radius),
                if (p.travelIfCovered) stringResource(R.string.ws7_me_radius_covered, p.travelRadiusKm) else stringResource(R.string.ws7_me_radius_value, p.travelRadiusKm),
                divider = true,
            )
            KeyValueRow(stringResource(R.string.ws7_me_remote), stringResource(if (p.remoteOk) R.string.ws7_me_remote_yes else R.string.ws7_me_remote_no), divider = true)
            KeyValueRow(stringResource(R.string.ws7_me_languages), p.languages.joinToString(", ") { CardText.languageName(it) }, divider = true)
            KeyValueRow(stringResource(R.string.ws7_me_availability), availabilityLabel(p.availabilityPattern), divider = p.busyDates.isNotEmpty())
            if (p.busyDates.isNotEmpty()) KeyValueRow(stringResource(R.string.ws7_me_busy), stringResource(R.string.ws7_me_busy_value, p.busyDates.size), mono = true)
        }

        // ── How I work (private) ──
        item("how") {
            Section(stringResource(R.string.ws7_me_how), edit, nav::toEditProfile)
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textMuted, modifier = Modifier.padding(top = 2.dp).width(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ws7_me_private_note), style = Ct.type.label, color = c.textMuted)
            }
            KeyValueRow(stringResource(R.string.ws7_me_comp), compLabel(p.compMode), divider = true)
            if (p.compMode != CompMode.PAID_ONLY) {
                KeyValueRow(
                    stringResource(R.string.ws7_me_unpaid_if),
                    p.unpaidMustHaves.joinToString(", ") { CardText.perkLabel(it) }.ifEmpty { stringResource(R.string.ws7_me_unpaid_any) },
                    divider = true,
                )
            }
            val currency = s.currency
            KeyValueRow(
                stringResource(R.string.ws7_me_min_rate),
                s.minRate?.let { stringResource(R.string.ws7_me_min_rate_set, Money.format(it, currency)) } ?: stringResource(R.string.ws7_me_min_rate_none),
                mono = s.minRate != null, divider = true,
            )
            p.kitFeeDay?.let { KeyValueRow(stringResource(R.string.ws7_me_kit_fee), stringResource(R.string.ws7_me_kit_fee_value, Money.format(it, currency)), mono = true, divider = true) }
            KeyValueRow(
                stringResource(R.string.ws7_me_wont_do),
                p.contentWontDo.joinToString(", ") { CardText.contentFlagLabel(it) }.ifEmpty { stringResource(R.string.ws7_me_wont_do_none) },
            )
        }

        // ── Gear and tools ──
        // cast with nothing listed: skip the empty gear block (crew always see it, so they know to fill it in)
        if (showGearBlock) item("gear") {
            Section(stringResource(R.string.ws7_me_gear), edit, nav::toEditProfile)
            if (p.gear.isEmpty() && p.tools.isEmpty()) Text(stringResource(R.string.ws7_me_gear_none), style = Ct.type.bodyS, color = c.textSecondary)
            p.gear.forEach { g -> Text("•  $g", style = Ct.type.bodyS, color = c.text, modifier = Modifier.padding(vertical = 2.dp)) }
            if (p.tools.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ChipFlow(spacing = 6.dp, lineSpacing = 6.dp) { p.tools.forEach { CtChip(it, small = true) } }
            }
        }

        // ── Taste + about ──
        item("about") {
            Section(stringResource(R.string.ws7_me_about), edit, nav::toEditProfile)
            if (p.pronouns != null) Text(p.pronouns, style = Ct.type.label, color = c.textMuted)
            Text(p.bio ?: stringResource(R.string.ws7_me_bio_none), style = Ct.type.body, color = if (p.bio != null) c.text else c.textSecondary)
            if (p.affiliations.isNotEmpty()) KeyValueRow(stringResource(R.string.ws7_me_affiliations), p.affiliations.joinToString(", "))
            if (p.tasteFilms.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.ws7_me_taste_films), style = Ct.type.label, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                ChipFlow(spacing = 6.dp, lineSpacing = 6.dp) { p.tasteFilms.forEach { CtChip(it, small = true) } }
            }
        }

        // ── Keep list (keepIndex: keep in sync with the items above) ──
        item("keep") {
            SectionHeader(stringResource(R.string.ws7_me_keep), Modifier.padding(top = 24.dp), count = s.keep.size)
            Text(stringResource(R.string.ws7_me_keep_body), style = Ct.type.bodyS, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            if (s.keep.isEmpty()) Text(stringResource(R.string.ws7_me_keep_empty), style = Ct.type.bodyS, color = c.textMuted)
        }
        items(s.keep, key = { "keep_" + it.profile.id }) { person ->
            KeepRow(person, onInvite = { inviteCount++; inviting = person }, Modifier.padding(top = 8.dp))
        }

        // ── Saved ──
        item("saved") {
            Section(stringResource(R.string.ws7_me_saved), if (s.saved.size > 3) stringResource(R.string.ws7_see_all) else null, nav::toSaved, count = s.saved.size)
            if (s.saved.isEmpty()) Text(stringResource(R.string.ws7_me_saved_empty), style = Ct.type.bodyS, color = c.textMuted)
        }
        items(s.saved.take(3), key = { "saved_" + it.role.id }) { r ->
            SavedRoleRow(r, onClick = { nav.toRoleDetail(r.role.id) }, Modifier.padding(top = 8.dp))
        }

        item("entries") {
            Spacer(Modifier.height(20.dp))
            EntryRows(nav)
        }
    }

    if (trackInfo) {
        CtBottomSheet({ trackInfo = false }, title = stringResource(R.string.ws7_me_track_sheet_title)) {
            listOf(R.string.ws7_me_track_sheet_1, R.string.ws7_me_track_sheet_2, R.string.ws7_me_track_sheet_3, R.string.ws7_me_track_sheet_4).forEach {
                Text(stringResource(it), style = Ct.type.body, color = c.text, modifier = Modifier.padding(bottom = 12.dp))
            }
            PrimaryButton(stringResource(R.string.ws7_done), { trackInfo = false }, Modifier.fillMaxWidth())
        }
    }
    inviting?.let { person -> InviteSheet(person, nav, onDismiss = { inviting = null }, openCount = inviteCount) }
}


@Composable
private fun Header(s: MeUi.Ready, nav: AppNavigator) {
    val c = Ct.colors
    val p = s.profile
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(p.initials, size = 60.dp, highlight = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.displayName, style = Ct.type.titleL, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(CardText.personRoleLine(p), style = Ct.type.slate.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.bodyS.lineHeight), color = c.accent)
                val place = listOfNotNull(s.card?.areaName, s.card?.cityName).joinToString(", ")
                if (place.isNotEmpty()) Text(place, style = Ct.type.bodyS, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChipFlow(spacing = 6.dp, lineSpacing = 6.dp) {
            CardText.stepUpChip(p)?.let { CtChip(it, tone = ChipTone.INFO, filled = true, style = ChipStyle.CAPS) }
            if (p.remoteOk) CtChip(stringResource(R.string.ws7_me_remote_yes), tone = ChipTone.NEUTRAL)
            // non-interactive here so every chip in the row is the same height; the Track record section explains itself
            TrackRecordChip(ReputationCalc.headlineChip(s.reputation, asOwner = false), interactive = false)
        }
        Spacer(Modifier.height(10.dp))
        VerificationBadges(p)
        if (s.email != null) {
            Spacer(Modifier.height(8.dp))
            TimecodeText(s.email, style = Ct.type.monoS, color = c.textMuted)
        }
        Spacer(Modifier.height(14.dp))
        SecondaryButton(stringResource(R.string.ws7_me_edit_profile), nav::toEditProfile, Modifier.fillMaxWidth(), icon = Icons.Rounded.Edit)
    }
}

@Composable
private fun Section(title: String, action: String?, onAction: () -> Unit, count: Int? = null) {
    SectionHeader(title, Modifier.padding(top = 20.dp), count = count, action = action, onAction = if (action != null) onAction else null)
}

@Composable
private fun RoleLine(label: String, codes: List<com.tff.app.model.RoleCode>, step: Boolean = false) {
    val c = Ct.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = Ct.type.label, color = c.textMuted)
        codes.forEach { code ->
            val info = com.tff.app.model.Taxonomy.info(code)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (step) "→ ${shortRoleLabel(code)}" else info.label, style = Ct.type.bodyStrong, color = if (step) c.info else c.text)
            }
            Text(info.duties, style = Ct.type.bodyS, color = c.textSecondary)
        }
    }
}

@Composable
private fun CreditRow(title: String, year: String, badge: String, tone: ChipTone, tip: String?) {
    val c = Ct.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (year.isNotEmpty()) TimecodeText(year, style = Ct.type.monoS, color = c.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        if (tip != null) InfoTooltip(tip) { show -> CtChip(badge, tone = tone, small = true, onClick = show, contentDescription = "$badge. $tip") }
        else CtChip(badge, tone = tone, small = true)
    }
}

@Composable
private fun KeepRow(person: ProfileCard, onInvite: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val p = person.profile
    Column(modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(p.initials, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.shortName, style = Ct.type.titleM, color = c.text)
                Text(CardText.personRoleLine(p), style = Ct.type.label, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val place = listOfNotNull(person.areaName, person.cityName).joinToString(", ")
                if (place.isNotEmpty()) Text(place, style = Ct.type.label, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        TrackRecordChips(person.reputation, asOwner = person.reputation.keptBookings != null || person.reputation.paidOnTime != null || person.reputation.footageDelivered != null, singleLine = true)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(stringResource(R.string.ws7_me_invite), onInvite, Modifier.fillMaxWidth(), icon = Icons.Rounded.GroupAdd)
    }
}

@Composable
private fun compLabel(m: CompMode): String = stringResource(
    when (m) {
        CompMode.PAID_ONLY -> R.string.ws7_onb_comp_paid
        CompMode.PAID_AND_SELECTIVE -> R.string.ws7_onb_comp_selective
        CompMode.OPEN_TO_UNPAID -> R.string.ws7_onb_comp_open
    }
)

