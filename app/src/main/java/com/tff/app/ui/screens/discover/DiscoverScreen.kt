package com.tff.app.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tff.app.R
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.DeckCard
import com.tff.app.domain.ShareText
import com.tff.app.di.AppContainer
import com.tff.app.model.CompMode
import com.tff.app.model.SwipeDir
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.DeckAction
import com.tff.app.ui.components.DeckActionBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.RoleCard
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SegmentedControl
import com.tff.app.ui.components.SelectedMark
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.SwipeDeck
import com.tff.app.ui.components.SwipeDirection
import com.tff.app.ui.components.SwipeLabels
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.TopBarAction
import com.tff.app.ui.components.direction
import com.tff.app.ui.components.rememberGuestGate
import com.tff.app.ui.components.rememberSwipeDeckState
import com.tff.app.ui.screens.crewup.CrewUpPane
import com.tff.app.ui.screens.crewup.CrewUpViewModel
import com.tff.app.ui.screens.role.ApplyOutcome
import com.tff.app.ui.screens.role.ApplySheet
import com.tff.app.ui.screens.role.rememberNotificationAsk
import com.tff.app.ui.theme.Ct
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UNDO_MS = 5_000L

/**
 * S05 Discover tab. Gigs deck of Role Cards (Pass · Save · Interested), the honest density header,
 * Filters (S08), the Apply sheet (S07) on Interested, the 5 s Undo chip, all three empty states, guest
 * browsing by city, and — for people who own open roles — the Crew up deck inline (S09).
 * Sits inside the bottom-nav scaffold: draws its own top bar with status-bar insets, no bottom padding.
 */
@Composable
fun DiscoverScreen(nav: AppNavigator) {
    val vm = rememberVm { DiscoverViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val gate = rememberGuestGate(nav)
    var crewUp by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showCities by rememberSaveable { mutableStateOf(false) }
    val showCrewUp = crewUp && ui.ownsOpenRoles && !ui.isGuest
    val genericError = stringResource(R.string.ws4_generic_error)

    LaunchedEffect(vm) { vm.events.collect { nav.toast(it.ifBlank { genericError }) } }
    // LIVE: new roles land without a restart (re-fetch on return, poll while the deck is empty). No-op in demo.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refreshIfStale(fromPoll = false)
            while (true) { delay(DiscoverViewModel.POLL_MS); vm.refreshIfStale(fromPoll = true) }
        }
    }

    Column(Modifier.fillMaxSize().background(Ct.colors.bg)) {
        val (title, gigsSubtitle) = densityTitle(ui)
        // Crew up: the header talks about the selected role, not the city's head count (same VM as the pane).
        val subtitle = if (showCrewUp) crewUpStatus() else gigsSubtitle
        val activeFilters = DeckLogic.activeFilterCount(ui.filters)
        CtTopBar(
            title = title,
            subtitle = subtitle,
            titleLoading = ui.density == null && ui.loading && ui.city == null,
            actions = {
                if (!showCrewUp && !ui.needsProfile && ui.error == null) {
                    val cd = if (activeFilters > 0) pluralStringResource(R.plurals.ws4_filters_cd_active, activeFilters, activeFilters) else stringResource(R.string.ws4_filters_cd)
                    if (activeFilters > 0) {
                        BadgedBox(badge = { Badge(containerColor = Ct.colors.accent, contentColor = Ct.colors.onAccent) { Text("$activeFilters") } }) {
                            TopBarAction(Icons.Rounded.Tune, cd, { showFilters = true }, tint = Ct.colors.accent)
                        }
                    } else TopBarAction(Icons.Rounded.Tune, cd, { showFilters = true })
                }
            },
            bottom = if (ui.ownsOpenRoles && !ui.isGuest) ({
                SegmentedControl(
                    listOf(stringResource(R.string.ws4_seg_gigs), stringResource(R.string.ws4_seg_crew_up)),
                    if (showCrewUp) 1 else 0,
                    { crewUp = it == 1 },
                )
            }) else null,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (showCrewUp) CrewUpPane(nav, initialRoleId = null, inline = true)
            else GigsBody(nav, vm, ui, onGuest = { gate.prompt() }, isGuest = gate.isGuest, onPickCity = { showCities = true }, onFilters = { showFilters = true })
        }
    }

    if (showFilters) {
        FiltersSheet(
            current = ui.filters,
            languages = ui.languageOptions,
            showDistance = !ui.isGuest,
            preview = vm::previewCount,
            onApply = { vm.setFilters(it); showFilters = false },
            onDismiss = { showFilters = false },
            onEditProfile = if (ui.isGuest) null else ({ showFilters = false; nav.toEditProfile() }),
        )
    }
    if (showCities) {
        CityPickerSheet(ui, onPick = { vm.pickGuestCity(it); showCities = false }, onDismiss = { showCities = false })
    }
}

/**
 * "Mumbai" + "28 people · 9 open projects" (PRD S05; CardText.densityHeader split after the city). No
 * "· 6 actors" segment: a head count of people in the viewer's own role reads as competition or as open roles.
 * Early cities: "Indore · early city" + "3 people · 1 open project · + remote roles".
 */
@Composable
private fun densityTitle(ui: DiscoverUi): Pair<String, String?> {
    val d = ui.density ?: return (ui.city?.name ?: stringResource(R.string.ws4_discover_title)) to null
    val parts = CardText.densityHeader(d).split(" · ")
    val early = d.isEarly && !d.isLive && parts.size > 1
    val head = if (early) parts.take(2).joinToString(" · ") else parts.first()
    val rest = parts.drop(if (early) 2 else 1)
    return head to rest.joinToString(" · ").ifBlank { null }
}

/** Crew up header: "Lead Actor · 10 to review · 10/20 applied" for the role selected in the inline pane. */
@Composable
private fun crewUpStatus(): String? {
    val cvm = rememberVm(key = "crewup_inline") { CrewUpViewModel(it, null) }
    val cu by cvm.ui.collectAsStateWithLifecycle()
    val sel = cu.selected ?: return null
    val r = sel.item.role
    return stringResource(R.string.ws4_crewup_status, CardText.roleName(r), sel.pending, r.applicationsCount, r.applicationCap)
}

@Composable
private fun GigsBody(
    nav: AppNavigator,
    vm: DiscoverViewModel,
    ui: DiscoverUi,
    isGuest: Boolean,
    onGuest: () -> Unit,
    onPickCity: () -> Unit,
    onFilters: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deck = rememberSwipeDeckState<DeckCard>()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val applyFor by vm.applyFor.collectAsStateWithLifecycle()
    val askNotifications = rememberNotificationAsk()
    val sentMsg = stringResource(R.string.ws4_apply_sent)
    val capMsg = stringResource(R.string.ws4_cap_title) + " " + stringResource(R.string.ws4_cap_body)

    // A rebuilt deck starts clean (the Repo already leaves out what was swiped).
    LaunchedEffect(ui.deckVersion) { deck.reset() }
    // The Undo chip lives for 5 s.
    LaunchedEffect(undo) { undo?.let { delay(UNDO_MS); vm.expireUndo(it) } }

    when {
        ui.loading -> LoadingBody()
        ui.error != null -> ErrorState(onRetry = vm::retry, message = ui.error.ifBlank { null }, modifier = Modifier.fillMaxSize())
        ui.needsProfile -> ScrollCentered {
            EmptyState(
                title = stringResource(R.string.ws4_needs_profile_title),
                body = stringResource(R.string.ws4_needs_profile_body),
                icon = Icons.Rounded.PersonOutline,
            ) { PrimaryButton(stringResource(R.string.ws4_needs_profile_cta), { nav.toOnboarding() }) }
        }
        ui.empty == DeckEmpty.DAILY_CAP -> ScrollCentered { EmptyBody(nav, vm, ui, DeckEmpty.DAILY_CAP, isGuest, onGuest, onPickCity, onFilters) }
        else -> Column(Modifier.fillMaxSize()) {
            SwipeDeck(
                items = ui.cards,
                key = { it.item.role.id },
                state = deck,
                labels = SwipeLabels.gigs(),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 4.dp),
                canSwipe = { _, dir ->
                    when {
                        isGuest -> { onGuest(); false }
                        dir == SwipeDirection.RIGHT && ui.likesLeft <= 0 -> { nav.toast(capMsg); false }
                        else -> true
                    }
                },
                onSwiped = { card, dir ->
                    when (dir) {
                        SwipeDirection.RIGHT -> vm.startApply(card)
                        SwipeDirection.LEFT -> vm.swiped(card, SwipeDir.LEFT)
                        SwipeDirection.UP -> vm.swiped(card, SwipeDir.SAVE)
                    }
                },
                onTap = { nav.toRoleDetail(it.item.role.id) },
                // Interested parks the card at the commit point under the Apply sheet (S07) until it's sent.
                holdOn = setOf(SwipeDirection.RIGHT),
                empty = { ScrollCentered { EmptyBody(nav, vm, ui, ui.empty ?: DeckEmpty.EXHAUSTED, isGuest, onGuest, onPickCity, onFilters) } },
            ) { card -> RoleCard(card, Modifier.fillMaxSize()) }

            UndoRow(ui, undo, isGuest, onGuest, onPickCity, onUndo = { offer ->
                scope.launch {
                    deck.undo()
                    vm.undo(offer)
                }
            })
            if (ui.cards.isNotEmpty()) {
                DeckActionBar(
                    listOf(DeckAction.PASS, DeckAction.SAVE, DeckAction.INTERESTED),
                    onAction = { a -> if (isGuest) onGuest() else deck.swipeAsync(a.direction) },
                    state = deck,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else Spacer(Modifier.height(8.dp))
        }
    }

    applyFor?.let { card ->
        ApplySheet(
            item = card.item,
            onCancel = {
                vm.cancelApply()
                deck.releaseHeldAsync()
            },
            onFinished = { out ->
                val id = card.item.role.id
                when (out) {
                    is ApplyOutcome.Sent -> {
                        // Close the sheet now; the card flies out, then leaves the deck for good.
                        vm.cancelApply()
                        deck.commitHeldAsync { vm.applyFinished(id, keepGone = true); deck.clearUndo() }
                        nav.toast(sentMsg)
                        askNotifications()
                    }
                    is ApplyOutcome.Gone -> {
                        vm.cancelApply()
                        deck.commitHeldAsync { vm.applyFinished(id, keepGone = true); deck.clearUndo() }
                        nav.toast(out.message)
                    }
                    is ApplyOutcome.DailyCap -> {
                        vm.applyFinished(id, keepGone = false)
                        deck.releaseHeldAsync()
                        nav.toast(out.message)
                    }
                    is ApplyOutcome.Invalid -> Unit
                }
            },
        )
    }
}

/** "18 left today" (mono) on the left; the 5 s Undo chip on the right. Guests get a sign-in nudge instead. */
@Composable
private fun UndoRow(ui: DiscoverUi, undo: UndoOffer?, isGuest: Boolean, onGuest: () -> Unit, onPickCity: () -> Unit, onUndo: (UndoOffer) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isGuest) {
            // Guests browse one city's deck (default Mumbai); the chip says which and changes it.
            val city = ui.city?.name ?: stringResource(R.string.ws4_change_city)
            CtChip(city, icon = Icons.Rounded.LocationOn, onClick = onPickCity, contentDescription = stringResource(R.string.ws4_change_city_cd, city))
            Spacer(Modifier.weight(1f))
            GhostButton(stringResource(R.string.ws4_guest_sign_in), onGuest)
        } else {
            TimecodeText(
                pluralStringResource(R.plurals.ws4_likes_left, ui.likesLeft, ui.likesLeft),
                style = Ct.type.monoS, color = if (ui.likesLeft <= 5) Ct.colors.accent else Ct.colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (undo != null) {
                val label = if (undo.dir == SwipeDir.SAVE) stringResource(R.string.ws4_undo_saved) else stringResource(R.string.ws4_undo_passed)
                CtChip(label, icon = Icons.AutoMirrored.Rounded.Undo, onClick = { onUndo(undo) }, contentDescription = label)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Column(Modifier.fillMaxSize()) {
        LoadingSkeleton(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp), kind = SkeletonKind.CARD)
        Spacer(Modifier.height(48.dp))
        DeckActionBar(listOf(DeckAction.PASS, DeckAction.SAVE, DeckAction.INTERESTED), onAction = {}, enabled = false, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun ScrollCentered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 480.dp)) { content() }
    }
}

/** The S05 empty states, each with buttons that actually fix something. */
@Composable
private fun EmptyBody(
    nav: AppNavigator,
    vm: DiscoverViewModel,
    ui: DiscoverUi,
    kind: DeckEmpty,
    isGuest: Boolean,
    onGuest: () -> Unit,
    onPickCity: () -> Unit,
    onFilters: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val me = ui.me
    val cityName = ui.city?.name ?: ""
    val early = ui.city?.isEarly == true && ui.city.isLive.not()
    val widened = me?.let { DeckLogic.widenedRadius(it.travelRadiusKm) }
    val radiusMsg = widened?.let { stringResource(R.string.ws4_radius_now, it) }
    val remoteMsg = stringResource(R.string.ws4_remote_on)
    // Only offer a wider radius when it actually adds roles, and say how many.
    val widen: (() -> Unit)? = if (widened != null && ui.widenGain > 0) ({ scope.launch { if (vm.widenRadius() != null) radiusMsg?.let(nav::toast) } }) else null
    val widenLabel = if (widened != null && ui.widenGain > 0) pluralStringResource(R.plurals.ws4_widen_gain, ui.widenGain, widened, ui.widenGain) else ""
    val remote: (() -> Unit)? = if (me != null && !me.remoteOk) ({ scope.launch { if (vm.includeRemote()) nav.toast(remoteMsg) } }) else null
    val inviteTitle = stringResource(R.string.ws4_invite_title)
    val invite = { nav.share(ShareText.cityInvite(cityName, AppContainer.shareBaseUrl), inviteTitle) }

    when (kind) {
        DeckEmpty.DAILY_CAP -> EmptyState(
            title = stringResource(R.string.ws4_cap_title),
            body = stringResource(R.string.ws4_cap_body),
            icon = Icons.Rounded.Bookmarks,
        ) { PrimaryButton(stringResource(R.string.ws4_see_saved), { nav.toSaved() }) }

        DeckEmpty.FILTERED -> EmptyState(
            title = stringResource(R.string.ws4_filtered_title),
            body = pluralStringResource(R.plurals.ws4_filtered_body, ui.unfilteredCount, ui.unfilteredCount),
            icon = Icons.Rounded.FilterAltOff,
        ) {
            PrimaryButton(stringResource(R.string.ws4_filters_clear), { vm.setFilters(com.tff.app.model.DeckFilters()) })
            SecondaryButton(stringResource(R.string.ws4_filters_change), onFilters)
        }

        // Early city: the loop continues by posting and inviting, not by a radius that adds nothing (PRD S05).
        DeckEmpty.EXHAUSTED -> if (early && !isGuest && me != null) EmptyState(
            title = stringResource(R.string.ws4_exhausted_title),
            body = stringResource(R.string.ws4_exhausted_early_body, cityName),
            icon = Icons.Rounded.Groups,
        ) {
            PrimaryButton(stringResource(R.string.ws4_post_own), { nav.toPostProject() }, icon = Icons.Rounded.Add)
            SecondaryButton(stringResource(R.string.ws4_invite_group, cityName), invite, icon = Icons.Rounded.Share)
            if (widen != null) SecondaryButton(widenLabel, widen, enabled = !ui.busy)
            if (remote != null) SecondaryButton(stringResource(R.string.ws4_include_remote), remote, enabled = !ui.busy)
            GhostButton(stringResource(R.string.ws4_see_saved), { nav.toSaved() })
        } else EmptyState(
            title = stringResource(R.string.ws4_exhausted_title),
            body = stringResource(R.string.ws4_exhausted_body),
            icon = Icons.Rounded.Bookmarks,
        ) {
            PrimaryButton(stringResource(R.string.ws4_see_saved), { if (isGuest) onGuest() else nav.toSaved() })
            if (!isGuest) {
                if (widen != null) SecondaryButton(widenLabel, widen, enabled = !ui.busy)
                if (remote != null) SecondaryButton(stringResource(R.string.ws4_include_remote), remote, enabled = !ui.busy)
            }
        }

        DeckEmpty.NOTHING_IN_RANGE -> if (isGuest || me == null) EmptyState(
            title = stringResource(R.string.ws4_guest_empty_title, cityName),
            body = stringResource(R.string.ws4_guest_empty_body),
            icon = Icons.Rounded.NearMe,
        ) {
            PrimaryButton(stringResource(R.string.ws4_pick_city), onPickCity)
            SecondaryButton(stringResource(R.string.ws1_signin_cta), onGuest)
        } else EmptyState(
            title = stringResource(R.string.ws4_empty_title),
            body = honestNumbers(ui.blockers, cityName, me.travelRadiusKm, early),
            icon = Icons.Rounded.NearMe,
        ) {
            if (early) {
                PrimaryButton(stringResource(R.string.ws4_post_own), { nav.toPostProject() }, icon = Icons.Rounded.Add)
                SecondaryButton(stringResource(R.string.ws4_invite_group, cityName), invite, icon = Icons.Rounded.Share)
                if (widen != null) SecondaryButton(widenLabel, widen, enabled = !ui.busy)
            } else if (widen != null) PrimaryButton(widenLabel, widen, enabled = !ui.busy)
            if (remote != null) SecondaryButton(stringResource(R.string.ws4_include_remote), remote, enabled = !ui.busy)
            SecondaryButton(
                stringResource(if (me.compMode == CompMode.PAID_ONLY) R.string.ws4_edit_pay else R.string.ws4_edit_unpaid),
                { nav.toEditProfile() },
            )
            if (!early) GhostButton(stringResource(R.string.ws4_post_own), { nav.toPostProject() })
        }
    }
}

/** "9 open roles near Mumbai right now. 2 are just past your 15 km radius. 3 are unpaid without what you need." */
@Composable
private fun honestNumbers(b: DeckBlockers, city: String, radius: Int, early: Boolean): String {
    val lines = mutableListOf<String>()
    if (early) lines += stringResource(R.string.ws4_honest_early, city)
    lines += if (b.nearby == 0) stringResource(R.string.ws4_honest_none, city)
    else pluralStringResource(R.plurals.ws4_honest_nearby, b.nearby, b.nearby, city)
    if (b.justOutside > 0) lines += pluralStringResource(R.plurals.ws4_honest_outside, b.justOutside, b.justOutside, radius)
    if (b.remoteOnly > 0) lines += pluralStringResource(R.plurals.ws4_honest_remote, b.remoteOnly, b.remoteOnly)
    if (b.unpaidTerms > 0) lines += pluralStringResource(R.plurals.ws4_honest_unpaid, b.unpaidTerms, b.unpaidTerms)
    if (b.busyDates > 0) lines += pluralStringResource(R.plurals.ws4_honest_dates, b.busyDates, b.busyDates)
    if (b.nearby > 0 && lines.size == 1 + (if (early) 1 else 0)) lines += stringResource(R.string.ws4_honest_fit)
    return lines.joinToString(" ")
}

/** Guests choose which city's deck to browse (default Mumbai). Live cities first, early cities labelled. */
@Composable
private fun CityPickerSheet(ui: DiscoverUi, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Ct.colors
    CtBottomSheet(onDismiss, title = stringResource(R.string.ws4_city_title), subtitle = stringResource(R.string.ws4_city_sub)) {
        ui.cities.forEach { city ->
            val selected = city.id == ui.city?.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onPick(city.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(city.name, style = Ct.type.body, color = c.text)
                    val note = when {
                        city.isLive -> stringResource(R.string.ws4_city_live)
                        city.isEarly -> stringResource(R.string.ws4_city_early)
                        else -> null
                    }
                    if (note != null) Text(note, style = Ct.type.label, color = if (city.isLive) c.go else c.textMuted)
                }
                if (selected) SelectedMark()
            }
        }
    }
}
