package com.tff.app.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.di.rememberVm
import com.tff.app.domain.BookingDiff
import com.tff.app.domain.BookingTermsFactory
import com.tff.app.domain.CardText
import com.tff.app.domain.ShareText
import com.tff.app.model.BookingCard
import com.tff.app.model.BookingStatus
import com.tff.app.model.BookingTerms
import com.tff.app.model.CompType
import com.tff.app.model.MatchState
import com.tff.app.model.MatchView
import com.tff.app.model.ReportTarget
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.BookedOverlay
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.screens.chat.ACTIVE_STATES
import com.tff.app.ui.screens.chat.BookingStatusChip
import com.tff.app.ui.screens.chat.ChatBridge
import com.tff.app.ui.screens.chat.ConfirmDialog
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.bookedCallLine
import com.tff.app.ui.screens.chat.changedFieldsText
import com.tff.app.ui.screens.chat.localDateOf
import com.tff.app.ui.screens.chat.momentSubtitle
import com.tff.app.ui.screens.chat.clock
import com.tff.app.ui.screens.chat.userMessage
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

/** S16 Booking card (route booking/{matchId}): the owner's editor, the collaborator's answer, versions and diff. */
@Composable
fun BookingCardScreen(nav: AppNavigator, matchId: String) {
    val vm = rememberVm(key = "booking_$matchId") { repo -> BookingViewModel(repo, matchId) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Load.Loading -> Frame(nav) { LoadingSkeleton(Modifier.fillMaxWidth(), kind = SkeletonKind.DETAIL, rows = 6) }
        is Load.Failed -> Frame(nav) { ErrorState(onRetry = vm::retry, modifier = Modifier.fillMaxSize(), message = s.message) }
        is Load.Ready -> if (s.data.view == null) Frame(nav) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.ws6_booking_missing_title), stringResource(R.string.ws6_booking_missing_body), icon = Icons.Rounded.Movie) {
                    PrimaryButton(stringResource(R.string.ws6_back), { nav.back() })
                }
            }
        } else BookingContent(nav, vm, s.data)
    }
}

@Composable
private fun Frame(nav: AppNavigator, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ct.colors.bg)) {
        CtTopBar(stringResource(R.string.ws6_booking_title), onBack = nav::back)
        Box(Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars)) { content() }
    }
}

/** Back to the chat this card belongs to: pop when the chat is underneath, else open it. */
private fun backToChat(nav: AppNavigator, matchId: String) {
    if (ChatBridge.isChatOpen(matchId)) nav.back() else nav.toChat(matchId)
}

@Composable
private fun BookingContent(nav: AppNavigator, vm: BookingViewModel, data: BookingData) {
    val v = data.view!!
    val m = v.match
    val cards = data.cards
    val latest = cards.lastOrNull()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val res = LocalContext.current.resources
    val generic = stringResource(R.string.ws6_generic_error)
    val active = m.state in ACTIVE_STATES

    // Two taps: "Send booking card" opens the editor straight away when there's nothing to look at first.
    LaunchedEffect(v.isOwner, latest?.id, latest?.status) {
        if (v.isOwner && editing == null && active && m.state != MatchState.BOOKED && (latest == null || latest.status == BookingStatus.DECLINED)) {
            vm.startEditing(latest?.terms) { nav.toast(it ?: generic) }
        }
    }

    val e = editing
    if (e != null && v.isOwner) {
        EditorScreen(nav, vm, v, latest, e)
        return
    }
    if (latest == null) {
        Frame(nav) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val loading by vm.loadingDraft.collectAsStateWithLifecycle()
                if (loading) LoadingSkeleton(Modifier.fillMaxWidth(), kind = SkeletonKind.DETAIL)
                else EmptyState(
                    stringResource(R.string.ws6_booking_none_title),
                    stringResource(if (v.isOwner) R.string.ws6_booking_none_owner else R.string.ws6_booking_none_collab),
                    icon = Icons.Rounded.Movie,
                ) {
                    if (v.isOwner && active) PrimaryButton(stringResource(R.string.ws6_send_booking_card), { vm.startEditing(null) { nav.toast(it ?: generic) } })
                    else SecondaryButton(stringResource(R.string.ws6_back_to_chat), { backToChat(nav, m.id) })
                }
            }
        }
        return
    }
    CardScreen(nav, vm, data, latest, res, generic)
}

// ───────────────────────── View / answer ─────────────────────────

@Composable
private fun CardScreen(nav: AppNavigator, vm: BookingViewModel, data: BookingData, latest: BookingCard, res: android.content.res.Resources, generic: String) {
    val v = data.view!!
    val m = v.match
    val c = Ct.colors
    val cards = data.cards
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val shown = cards.firstOrNull { it.version == selected } ?: latest
    val prev = cards.lastOrNull { it.version < shown.version }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var withdraw by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf(false) }
    var decline by remember { mutableStateOf(false) }
    var showBooked by remember { mutableStateOf(false) }
    var bookedTerms by remember { mutableStateOf<BookingTerms?>(null) }
    val cpFirst = v.counterpart.profile.displayName.trim().split(Regex("\\s+")).firstOrNull() ?: v.counterpart.profile.shortName
    val canAnswer = !v.isOwner && latest.status == BookingStatus.SENT && shown.id == latest.id && m.state in ACTIVE_STATES
    val features = vm.features

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Scaffold(
            containerColor = c.bg,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CtTopBar(
                    title = stringResource(R.string.ws6_booking_title),
                    subtitle = "${CardText.roleName(v.item.role)} · ${v.item.project.title}",
                    onBack = nav::back,
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.ws6_more_options), tint = c.text) }
                            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = c.surfaceRaised) {
                                MenuItem(stringResource(R.string.ws6_back_to_chat)) { menu = false; backToChat(nav, m.id) }
                                MenuItem(stringResource(R.string.ws6_report_card)) { menu = false; report = true }
                                if (features.withdrawRelease && m.state in ACTIVE_STATES) {
                                    if (v.isOwner) MenuItem(stringResource(if (m.state == MatchState.BOOKED) R.string.ws6_release_cta else R.string.ws6_end_talks_cta), danger = true) { menu = false; release = true }
                                    else MenuItem(stringResource(R.string.ws6_withdraw_cta), danger = true) { menu = false; withdraw = true }
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                when {
                    canAnswer -> AnswerBar(
                        terms = latest.terms, busy = busy,
                        onAccept = {
                            val firstBooking = m.state != MatchState.BOOKED
                            vm.respond(latest.id, true) { r ->
                                r.onSuccess {
                                    if (firstBooking) { bookedTerms = latest.terms; showBooked = true }
                                    else nav.toast(res.getString(R.string.ws6_booking_reaccepted, latest.version))
                                }.onFailure { nav.toast(it.userMessage() ?: generic) }
                            }
                        },
                        onAskChange = {
                            vm.respond(latest.id, false) { r ->
                                r.onSuccess {
                                    ChatBridge.setPrefill(m.id, res.getString(R.string.ws6_ask_change_prefill))
                                    backToChat(nav, m.id)
                                }.onFailure { nav.toast(it.userMessage() ?: generic) }
                            }
                        },
                        onDecline = { decline = true },
                    )
                    v.isOwner && m.state in ACTIVE_STATES && shown.id == latest.id -> OwnerBar(
                        latest = latest, cpFirst = cpFirst, booked = m.state == MatchState.BOOKED,
                        onEdit = { vm.startEditing(latest.terms) { nav.toast(it ?: generic) } },
                    )
                    else -> {}
                }
            },
        ) { inner ->
            Column(
                Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ws6_chat_booking_card_v, shown.version), style = Ct.type.slate, color = c.text, modifier = Modifier.weight(1f))
                    BookingStatusChip(shown.status)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ws6_booking_sent_meta, shown.terms.payerName, Dates.shortUpper(localDateOf(shown.sentAt)), clock(shown.sentAt)),
                    style = Ct.type.monoS, color = c.textMuted,
                )
                if (prev != null && shown.changedFields.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    CtChip(stringResource(R.string.ws6_booking_changed_chip, shown.version, changedFieldsText(shown.changedFields)), tone = ChipTone.ACCENT, filled = true)
                }
                if (shown.id != latest.id) {
                    Spacer(Modifier.height(10.dp))
                    InfoTipCard(stringResource(R.string.ws6_booking_old_version, latest.version), actionLabel = stringResource(R.string.ws6_booking_see_latest),
                        onAction = { selected = null })
                }
                Spacer(Modifier.height(14.dp))
                SummaryCard(shown.terms)
                StatusNote(v, latest, shown, cpFirst)
                if (!v.isOwner && m.state == MatchState.BOOKED && !m.isPostRole && shown.status == BookingStatus.ACCEPTED) {
                    Spacer(Modifier.height(12.dp))
                    InfoTipCard(
                        title = stringResource(R.string.ws6_trusted_tip_title),
                        text = stringResource(R.string.ws6_trusted_tip_body),
                        actionLabel = stringResource(R.string.ws6_share_trusted),
                        onAction = { nav.share(trustedText(v, shown.terms), res.getString(R.string.ws6_share_trusted)) },
                    )
                }
                Spacer(Modifier.height(16.dp))
                TermsSections(shown.terms, prev?.terms, shown.changedFields)
                Spacer(Modifier.height(12.dp))
                SectionHeader(stringResource(R.string.ws6_booking_pledge_header))
                Text(shown.terms.noticePledge, style = Ct.type.bodyS, color = c.textSecondary)
                if (cards.size > 1) {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(stringResource(R.string.ws6_booking_versions), count = cards.size)
                    cards.asReversed().forEach { card ->
                        VersionRow(card, isShown = card.id == shown.id) { selected = card.version }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        BookedOverlay(
            visible = showBooked,
            onDismiss = { showBooked = false },
            callLine = bookedCallLine(v, bookedTerms),
            subtitle = momentSubtitle(v),
            actions = {
                val day = v.nextShootDay
                if (!m.isPostRole && day != null && features.shootDays) {
                    SecondaryButton(stringResource(R.string.ws6_back_to_chat), { showBooked = false; backToChat(nav, m.id) }, Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.ws6_open_day_sheet), { showBooked = false; nav.toShootDayView(v.item.project.id, day.id) }, Modifier.weight(1f))
                } else {
                    PrimaryButton(stringResource(R.string.ws6_back_to_chat), { showBooked = false; backToChat(nav, m.id) }, Modifier.weight(1f))
                }
            },
        )
    }

    if (report) ReportSheet(ReportTarget.BOOKING, shown.id, onDismiss = { report = false })
    if (withdraw) WithdrawSheet(v, nav, onDismiss = { withdraw = false }, onDone = { withdraw = false })
    if (release) ReleaseSheet(v, nav, onDismiss = { release = false }, onDone = { release = false })
    if (decline) ConfirmDialog(
        title = stringResource(R.string.ws6_decline_title, latest.version),
        body = stringResource(R.string.ws6_decline_body, v.counterpart.profile.shortName),
        confirm = stringResource(R.string.ws6_decline_cta),
        danger = true,
        onDismiss = { decline = false },
        onConfirm = {
            decline = false
            vm.respond(latest.id, false) { r ->
                r.onSuccess { nav.toast(res.getString(R.string.ws6_declined_toast)); backToChat(nav, m.id) }
                    .onFailure { nav.toast(it.userMessage() ?: generic) }
            }
        },
    )
}

private fun trustedText(v: MatchView, t: BookingTerms): String {
    val day = v.nextShootDay
    if (day != null) return ShareText.trustedContact(v.item.project, day, v.myCallTime)
    val first = t.dates.minOrNull()
    return listOfNotNull(
        "I'm booked on a shoot: \"${v.item.project.title}\"" + (first?.let { " · ${Dates.short(it)}" } ?: ""),
        "Area: ${CardText.placeName(v.item.areaName, v.item.cityName)}",
        "Production: ${t.payerName}",
        "Sent from Calltime so someone I trust knows where I'll be.",
    ).joinToString("\n")
}

@Composable
private fun MenuItem(text: String, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = Ct.type.body, color = if (danger) Ct.colors.stop else Ct.colors.text) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** One line under the summary that says where things stand, from the viewer's side. */
@Composable
private fun StatusNote(v: MatchView, latest: BookingCard, shown: BookingCard, cpFirst: String) {
    val c = Ct.colors
    val text = when {
        shown.id != latest.id -> null
        shown.status == BookingStatus.ACCEPTED -> stringResource(R.string.ws6_booking_note_accepted)
        shown.status == BookingStatus.DECLINED && v.isOwner -> stringResource(R.string.ws6_booking_note_declined_owner, cpFirst)
        shown.status == BookingStatus.DECLINED -> stringResource(R.string.ws6_booking_note_declined_collab)
        shown.status == BookingStatus.SENT && v.isOwner -> stringResource(R.string.ws6_pinned_waiting_for, cpFirst)
        shown.status == BookingStatus.SENT && v.match.state == MatchState.BOOKED -> stringResource(R.string.ws6_booking_note_change)
        shown.status == BookingStatus.SENT -> stringResource(R.string.ws6_booking_note_offer)
        else -> null
    } ?: return
    Spacer(Modifier.height(10.dp))
    Text(text, style = Ct.type.bodyS, color = if (shown.status == BookingStatus.ACCEPTED) c.go else c.textSecondary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun VersionRow(card: BookingCard, isShown: Boolean, onClick: () -> Unit) {
    val c = Ct.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CtShapes.field)
            .background(if (isShown) c.surfaceHigh else c.bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("v${card.version}", style = Ct.type.mono, color = c.text, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (card.changedFields.isEmpty()) stringResource(R.string.ws6_booking_first_version)
                else stringResource(R.string.ws6_changed_list, changedFieldsText(card.changedFields)),
                style = Ct.type.bodyS, color = c.text,
            )
            Text("${Dates.shortUpper(localDateOf(card.sentAt))} · ${clock(card.sentAt)}", style = Ct.type.monoS, color = c.textMuted)
        }
        BookingStatusChip(card.status)
    }
}

@Composable
private fun AnswerBar(terms: BookingTerms, busy: Boolean, onAccept: () -> Unit, onAskChange: () -> Unit, onDecline: () -> Unit) {
    val c = Ct.colors
    var pledged by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        HorizontalDivider(color = c.outline.copy(alpha = 0.0f))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CtShapes.field)
                .toggleable(pledged, role = Role.Checkbox, onValueChange = { pledged = it })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = pledged, onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
                modifier = Modifier.padding(12.dp),
            )
            Text(
                if (terms.isPostRole) BookingTermsFactory.PLEDGE_POST else BookingTermsFactory.PLEDGE_ON_SET,
                style = Ct.type.bodyS, color = c.text, modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(stringResource(R.string.ws6_accept_book), onAccept, Modifier.fillMaxWidth(), enabled = pledged, loading = busy)
        if (!pledged) {
            Text(stringResource(R.string.ws6_accept_needs_pledge), style = Ct.type.label, color = c.textMuted,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.ws6_ask_to_change), onAskChange, Modifier.weight(1f), enabled = !busy)
            GhostButton(stringResource(R.string.ws6_decline_cta), onDecline, Modifier.weight(1f), enabled = !busy, color = c.stop)
        }
    }
}

@Composable
private fun OwnerBar(latest: BookingCard, cpFirst: String, booked: Boolean, onEdit: () -> Unit) {
    val c = Ct.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val note = when {
            latest.status == BookingStatus.SENT -> stringResource(R.string.ws6_owner_bar_sent, cpFirst)
            booked -> stringResource(R.string.ws6_owner_bar_booked, cpFirst)
            else -> null
        }
        if (note != null) {
            Text(note, style = Ct.type.label, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
        }
        SecondaryButton(
            stringResource(if (booked && latest.status != BookingStatus.SENT) R.string.ws6_change_terms else R.string.ws6_edit_terms),
            onEdit, Modifier.fillMaxWidth(),
        )
    }
}

// ───────────────────────── Owner editor ─────────────────────────

@Composable
private fun EditorScreen(nav: AppNavigator, vm: BookingViewModel, v: MatchView, latest: BookingCard?, t: BookingTerms) {
    val c = Ct.colors
    val res = LocalContext.current.resources
    val generic = stringResource(R.string.ws6_generic_error)
    val busy by vm.busy.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val nextVersion = (latest?.version ?: 0) + 1
    val base = vm.editBase
    val changed = base?.let { BookingDiff.changedFields(it, t) }.orEmpty()
    val needsChange = base != null && latest != null && latest.status in setOf(BookingStatus.SENT, BookingStatus.ACCEPTED) && changed.isEmpty()
    val cpFirst = v.counterpart.profile.displayName.trim().split(Regex("\\s+")).firstOrNull() ?: v.counterpart.profile.shortName
    val leave = { if (latest == null || latest.status == BookingStatus.DECLINED) { vm.stopEditing(); nav.back() } else vm.stopEditing() }
    androidx.activity.compose.BackHandler { leave() }

    val errAmount = stringResource(R.string.ws6_editor_amount_error)
    val errDays = stringResource(R.string.ws6_editor_days_error)
    val errDelivery = stringResource(R.string.ws6_editor_delivery_error)

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CtTopBar(
                title = stringResource(R.string.ws6_editor_title, nextVersion),
                subtitle = if (latest == null) stringResource(R.string.ws6_editor_subtitle_new) else stringResource(R.string.ws6_editor_subtitle_from, latest.version),
                onBack = leave,
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                when {
                    needsChange -> Text(stringResource(R.string.ws6_editor_no_change), style = Ct.type.label, color = c.textMuted)
                    changed.isNotEmpty() -> Text(stringResource(R.string.ws6_editor_changes, changedFieldsText(changed)), style = Ct.type.label, color = c.accent)
                    latest?.status == BookingStatus.ACCEPTED || v.match.state == MatchState.BOOKED -> {}
                    else -> Text(stringResource(R.string.ws6_editor_send_note, cpFirst), style = Ct.type.label, color = c.textSecondary)
                }
                if (v.match.state == MatchState.BOOKED && changed.isNotEmpty()) {
                    Text(stringResource(R.string.ws6_editor_reaccept_note, cpFirst), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 4.dp))
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = Ct.type.bodyS, color = c.stop, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    stringResource(if (nextVersion == 1) R.string.ws6_send_booking_card else R.string.ws6_editor_send_v, nextVersion),
                    {
                        error = when {
                            t.compType != CompType.UNPAID && (t.amount ?: 0) <= 0 -> errAmount
                            !t.isPostRole && t.dates.isEmpty() -> errDays
                            t.isPostRole && t.deliveryDate == null -> errDelivery
                            else -> null
                        }
                        if (error == null) vm.send { r ->
                            r.onSuccess {
                                nav.toast(res.getString(R.string.ws6_card_sent_toast, nextVersion, cpFirst))
                                backToChat(nav, v.match.id)
                            }.onFailure { error = it.userMessage() ?: generic }
                        }
                    },
                    Modifier.fillMaxWidth(), enabled = !needsChange, loading = busy,
                )
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            // Live plain-language preview, pinned above the fields.
            Column(Modifier.fillMaxWidth().background(c.bg).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ws6_editor_preview, cpFirst.uppercase()), style = Ct.type.slate.copy(fontSize = Ct.type.label.fontSize), color = c.accent)
                }
                Spacer(Modifier.height(6.dp))
                SummaryCard(t, maxLines = 6)
            }
            HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BookingEditor(t, v.item.project.shootDates, onUpdate = { f -> error = null; vm.update(f) })
            }
        }
    }
}
