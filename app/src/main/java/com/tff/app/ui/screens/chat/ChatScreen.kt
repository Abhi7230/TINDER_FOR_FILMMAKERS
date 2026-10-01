package com.tff.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.MessageSafety
import com.tff.app.model.AppMode
import com.tff.app.model.AuditionResponse
import com.tff.app.model.BookingStatus
import com.tff.app.model.MatchState
import com.tff.app.model.Message
import com.tff.app.model.MessageFlag
import com.tff.app.model.MessageKind
import com.tff.app.model.ReportReason
import com.tff.app.model.ReportStatus
import com.tff.app.model.ReportTarget
import com.tff.app.nav.AppNavigator
import com.tff.app.nav.MainTab
import com.tff.app.ui.components.BookedOverlay
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.DemoTag
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.screens.booking.ReleaseSheet
import com.tff.app.ui.screens.booking.WithdrawSheet
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

private sealed interface ChatSheet {
    data object Audition : ChatSheet
    data class Tape(val messageId: String) : ChatSheet
    data class Report(val target: ReportTarget, val id: String, val prefill: ReportReason?) : ChatSheet
    data object Block : ChatSheet
    data object Withdraw : ChatSheet
    data object Release : ChatSheet
}

private sealed interface ChatItem {
    val key: String
    data class Separator(val date: java.time.LocalDate) : ChatItem { override val key = "sep_$date" }
    data class Msg(val m: Message, val showTime: Boolean) : ChatItem { override val key = m.id }
}

/** Day separators plus "show the time only on the last bubble of a run" (same sender, within 10 minutes). */
private fun buildItems(messages: List<Message>): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    var lastDate: java.time.LocalDate? = null
    messages.forEachIndexed { i, m ->
        val d = localDateOf(m.createdAt)
        if (d != lastDate) { out += ChatItem.Separator(d); lastDate = d }
        val next = messages.getOrNull(i + 1)
        val runContinues = next != null && next.senderId == m.senderId && next.kind == MessageKind.TEXT &&
            next.createdAt - m.createdAt < 10 * 60_000 && localDateOf(next.createdAt) == d
        out += ChatItem.Msg(m, showTime = !runContinues)
    }
    return out
}

/** S15 Chat. Full-screen destination with its own top bar, pinned booking bar and composer. */
@Composable
fun ChatScreen(nav: AppNavigator, matchId: String) {
    val vm = rememberVm(key = "chat_$matchId") { repo -> ChatViewModel(repo, matchId) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Load.Loading -> ChatFrame(nav, title = null) { LoadingSkeleton(Modifier.fillMaxWidth(), kind = SkeletonKind.LIST, rows = 5) }
        is Load.Failed -> ChatFrame(nav, title = null) { ErrorState(onRetry = vm::retry, modifier = Modifier.fillMaxSize(), message = s.message) }
        is Load.Ready -> if (s.data.view == null) {
            ChatFrame(nav, title = null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.ws6_chat_missing_title),
                        body = stringResource(R.string.ws6_chat_missing_body),
                        icon = Icons.Rounded.ChatBubbleOutline,
                    ) { PrimaryButton(stringResource(R.string.ws6_back_to_inbox), { nav.toMain(MainTab.INBOX) }) }
                }
            }
        } else ChatContent(nav, vm, s.data)
    }
}

/** Plain frame for the loading / error / missing states. */
@Composable
private fun ChatFrame(nav: AppNavigator, title: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ct.colors.bg)) {
        com.tff.app.ui.components.CtTopBar(title = title ?: stringResource(R.string.ws6_chat_title), onBack = nav::back)
        Box(Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatContent(nav: AppNavigator, vm: ChatViewModel, data: ChatData) {
    val v = data.view!!
    val c = Ct.colors
    val m = v.match
    val me = data.myId
    val isOwner = v.isOwner
    val bookedLike = m.state == MatchState.BOOKED || m.state == MatchState.WRAPPED
    val ended = m.state in ENDED_STATES
    val active = m.state in ACTIVE_STATES
    val cp = v.counterpart.profile
    val cpFirst = cp.displayName.trim().split(Regex("\\s+")).firstOrNull() ?: cp.shortName
    val res = LocalContext.current.resources
    val generic = stringResource(R.string.ws6_generic_error)
    val sending by vm.sending.collectAsStateWithLifecycle()
    val features = vm.features

    var text by rememberSaveable(vm.matchId) { mutableStateOf("") }
    val prefill by ChatBridge.prefill.collectAsStateWithLifecycle()
    LaunchedEffect(prefill[vm.matchId]) { ChatBridge.consumePrefill(vm.matchId)?.let { text = it } }
    LaunchedEffect(data.messages.size) { vm.markRead() }

    var sheet by remember { mutableStateOf<ChatSheet?>(null) }
    var busySheet by remember { mutableStateOf(false) }

    // BOOKED moment when the state flips while this chat is open (e.g. the seeded person accepts your card).
    var showBooked by remember { mutableStateOf(false) }
    var lastState by remember { mutableStateOf<MatchState?>(null) }
    LaunchedEffect(m.state) {
        val prev = lastState
        if (prev != null && prev != MatchState.BOOKED && prev != MatchState.WRAPPED && m.state == MatchState.BOOKED) showBooked = true
        lastState = m.state
    }

    val openBooking = { nav.toBookingCard(m.id) }
    val reportFor = { msg: Message ->
        val prefillReason = when {
            MessageFlag.MONEY_FROM_TALENT in msg.flags -> ReportReason.MONEY_REQUEST
            MessageFlag.BODY_REQUEST in msg.flags -> ReportReason.SEXUAL_INAPPROPRIATE
            MessageFlag.PRIVATE_MEETING in msg.flags -> ReportReason.UNSAFE_SET
            else -> null
        }
        sheet = ChatSheet.Report(ReportTarget.MESSAGE, msg.id, prefillReason)
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Scaffold(
            containerColor = c.bg,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                ChatTopBar(
                    name = cp.shortName, initials = cp.initials,
                    roleLine = "${CardText.roleName(v.item.role)} · ${v.item.project.title}",
                    state = m.state,
                    onBack = nav::back,
                    onProfile = { nav.toPerson(cp.id, v.item.role.id, m.applicationId) },
                ) { close ->
                    val item = @Composable { label: String, danger: Boolean, onClick: () -> Unit ->
                        DropdownMenuItem(
                            text = { Text(label, style = Ct.type.body, color = if (danger) c.stop else c.text) },
                            onClick = { close(); onClick() },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                    item(stringResource(R.string.ws6_menu_view_project), false) {
                        if (isOwner) nav.toProjectDashboard(v.item.project.id) else nav.toRoleDetail(v.item.role.id)
                    }
                    item(stringResource(R.string.ws6_menu_view_profile), false) { nav.toPerson(cp.id, v.item.role.id, m.applicationId) }
                    if (data.cards.isNotEmpty()) item(stringResource(R.string.ws6_chat_open_booking_card), false) { openBooking() }
                    item(stringResource(R.string.ws1_safety_report), false) { sheet = ChatSheet.Report(ReportTarget.PROFILE, cp.id, null) }
                    item(stringResource(R.string.ws1_safety_block), true) { sheet = ChatSheet.Block }
                    if (features.withdrawRelease && active) {
                        if (isOwner) item(stringResource(if (m.state == MatchState.BOOKED) R.string.ws6_release_cta else R.string.ws6_end_talks_cta), true) { sheet = ChatSheet.Release }
                        else item(stringResource(R.string.ws6_withdraw_cta), true) { sheet = ChatSheet.Withdraw }
                    }
                }
            },
            bottomBar = {
                if (ended) EndedBar(null)
                else Composer(
                    text = text,
                    onText = { text = it },
                    hint = stringResource(R.string.ws6_chat_hint),
                    warning = MessageSafety.draftWarning(text, booked = bookedLike, senderIsOwnerSide = isOwner),
                    quickReplies = quickReplies(isOwner, m.state, v.item.role.isPostRole),
                    sending = sending,
                    onSend = {
                        val t = text
                        vm.send(t) { r ->
                            r.onSuccess { if (text == t) text = "" }.onFailure { nav.toast(it.userMessage() ?: generic) }
                        }
                    },
                )
            },
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
                PinnedBar(
                    data = data, nav = nav, cpFirst = cpFirst,
                    onSendCard = openBooking,
                    onAudition = { sheet = ChatSheet.Audition },
                )
                if (bookedLike && !(if (isOwner) m.contactsSharedByOwner else m.contactsSharedByCollab)) {
                    SurfaceRow(
                        icon = Icons.Rounded.ContactPhone, tint = c.go,
                        title = stringResource(R.string.ws6_share_contacts_title),
                        body = stringResource(R.string.ws6_share_contacts_body, cpFirst),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        trailing = {
                            GhostButton(stringResource(R.string.ws6_share_contacts_cta), {
                                vm.shareContacts { r ->
                                    r.onSuccess { nav.toast(res.getString(R.string.ws6_share_contacts_done, cpFirst)) }
                                        .onFailure { nav.toast(it.userMessage() ?: generic) }
                                }
                            }, color = c.go)
                        },
                    )
                }
                val msgIds = data.messages.map { it.id }.toSet()
                val myReport = data.reports.firstOrNull { it.targetId == cp.id || it.targetId == m.id || it.targetId in msgIds }
                if (myReport != null) {
                    SurfaceRow(
                        icon = Icons.Rounded.Shield, tint = c.info,
                        title = stringResource(R.string.ws6_reported_title),
                        body = stringResource(R.string.ws6_reported_body, reportStatusLabel(myReport.status)),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        onClick = { nav.toSafetyCenter() },
                        trailing = { Text(stringResource(R.string.ws6_safety_center), style = Ct.type.label, color = c.info) },
                    )
                }
                MessageList(
                    data = data,
                    modifier = Modifier.weight(1f),
                    me = me,
                    bookedLike = bookedLike,
                    active = active,
                    cpFirst = cpFirst,
                    nav = nav,
                    onReport = reportFor,
                    onBlock = { sheet = ChatSheet.Block },
                    onAudition = { msg, resp ->
                        vm.respondAudition(msg.id, resp, null) { r -> r.onFailure { nav.toast(it.userMessage() ?: generic) } }
                    },
                    onSendTape = { msg -> sheet = ChatSheet.Tape(msg.id) },
                    onAuditionReport = { msg -> sheet = ChatSheet.Report(ReportTarget.MESSAGE, msg.id, ReportReason.UNSAFE_SET) },
                )
            }
        }

        BookedOverlay(
            visible = showBooked,
            onDismiss = { showBooked = false },
            callLine = bookedCallLine(v, null),
            subtitle = momentSubtitle(v),
        )
    }

    when (val s = sheet) {
        null -> {}
        ChatSheet.Audition -> v.item.project.auditionMode?.let { mode ->
            AuditionRequestSheet(mode, cpFirst, busySheet, onDismiss = { sheet = null }) { req, err ->
                busySheet = true
                vm.sendAudition(req) { r ->
                    busySheet = false
                    r.onSuccess { sheet = null; nav.toast(res.getString(R.string.ws6_audition_sent_toast)) }
                        .onFailure { err(it.userMessage() ?: generic) }
                }
            }
        }
        is ChatSheet.Tape -> SendTapeSheet(busySheet, onDismiss = { sheet = null }) { url, err ->
            busySheet = true
            vm.respondAudition(s.messageId, AuditionResponse.TAPE_SENT, url) { r ->
                busySheet = false
                r.onSuccess { sheet = null; nav.toast(res.getString(R.string.ws6_tape_sent_toast)) }
                    .onFailure { err(it.userMessage() ?: generic) }
            }
        }
        is ChatSheet.Report -> ReportSheet(s.target, s.id, onDismiss = { sheet = null }, prefill = s.prefill)
        ChatSheet.Block -> BlockDialog(cp.shortName, cp.id, nav, onDone = { sheet = null; nav.back() }, onDismiss = { sheet = null })
        ChatSheet.Withdraw -> WithdrawSheet(v, nav, onDismiss = { sheet = null }, onDone = { sheet = null })
        ChatSheet.Release -> ReleaseSheet(v, nav, onDismiss = { sheet = null }, onDone = { sheet = null })
    }
}

@Composable
private fun reportStatusLabel(s: ReportStatus): String = stringResource(
    when (s) {
        ReportStatus.RECEIVED -> R.string.ws6_report_received
        ReportStatus.REVIEWING -> R.string.ws6_report_reviewing
        ReportStatus.ACTION_TAKEN -> R.string.ws6_report_action
        ReportStatus.NO_ACTION -> R.string.ws6_report_no_action
    }
)

// ───────────────────────── Top bar ─────────────────────────

@Composable
private fun ChatTopBar(
    name: String,
    initials: String,
    roleLine: String,
    state: MatchState,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    val c = Ct.colors
    val demo = !LocalInspectionMode.current && runCatching { AppContainer.repo.mode == AppMode.DEMO }.getOrDefault(false)
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ws1_back), tint = c.text)
            }
            Row(
                Modifier.weight(1f).clip(CtShapes.field).clickable(onClick = onProfile).padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Monogram(initials, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = Ct.type.titleM, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(state)
                        Spacer(Modifier.width(8.dp))
                        Text(roleLine, style = Ct.type.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.ws6_more_options), tint = c.text)
                }
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = c.surfaceRaised) { menu { open = false } }
            }
            if (demo) DemoTag(Modifier.padding(end = 4.dp))
        }
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
    }
}

// ───────────────────────── Pinned bar ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinnedBar(
    data: ChatData,
    nav: AppNavigator,
    cpFirst: String,
    onSendCard: () -> Unit,
    onAudition: () -> Unit,
) {
    val v = data.view!!
    val m = v.match
    val c = Ct.colors
    val isOwner = v.isOwner
    val latest = data.cards.lastOrNull() ?: v.currentCard
    val features = AppContainer.repo.features
    val post = m.isPostRole || v.item.role.isPostRole

    var icon = Icons.Rounded.Movie
    var tint: Color = c.accent
    var title: String
    var body: String? = null
    var onTap: (() -> Unit)? = null
    val buttons = mutableListOf<@Composable () -> Unit>()

    when {
        m.state in ENDED_STATES -> {
            icon = Icons.Rounded.EventBusy; tint = c.textMuted
            title = stringResource(R.string.ws6_pinned_ended)
            body = m.withdrawReason
        }
        m.state == MatchState.WRAPPED -> {
            icon = Icons.Rounded.TaskAlt; tint = c.go
            title = stringResource(R.string.ws6_pinned_wrapped, v.item.project.title)
            body = stringResource(R.string.ws6_pinned_wrapped_body)
            if (features.wrap) {
                onTap = { nav.toWrap(matchId = m.id) }
                buttons += { SecondaryButton(stringResource(R.string.ws6_wrap_checklist), { nav.toWrap(matchId = m.id) }) }
            }
        }
        m.state == MatchState.BOOKED && latest?.status == BookingStatus.SENT -> {
            // a changed version is waiting for re-acceptance
            icon = Icons.Rounded.HourglassTop
            title = stringResource(R.string.ws6_pinned_change_v, latest.version)
            body = if (isOwner) stringResource(R.string.ws6_pinned_change_owner, cpFirst) else stringResource(R.string.ws6_pinned_change_collab, changedFieldsText(latest.changedFields).ifBlank { "terms" })
            onTap = onSendCard
            buttons += {
                if (isOwner) SecondaryButton(stringResource(R.string.ws6_edit_terms), onSendCard)
                else PrimaryButton(stringResource(R.string.ws6_review_changes), onSendCard)
            }
        }
        m.state == MatchState.BOOKED && post -> {
            icon = Icons.Rounded.Inventory2; tint = c.go
            val due = (v.acceptedCard?.terms?.deliveryDate ?: v.item.role.postDeadline)
            title = if (due != null) stringResource(R.string.ws6_pinned_booked_deliver, Dates.dayMonth(due)) else stringResource(R.string.ws6_pinned_booked)
            body = stringResource(if (isOwner) R.string.ws6_pinned_post_owner else R.string.ws6_pinned_post_collab)
            if (features.wrap) onTap = { nav.toWrap(matchId = m.id) }
        }
        m.state == MatchState.BOOKED -> {
            icon = Icons.Rounded.Schedule; tint = c.go
            val day = v.nextShootDay
            val call = v.myCallTime
            if (day != null && call != null && features.shootDays) {
                title = stringResource(if (isOwner) R.string.ws6_pinned_next_shoot else R.string.ws6_pinned_next_call, nextCallText(day.day, call))
                body = stringResource(R.string.ws6_pinned_open_day_sheet)
                onTap = { nav.toShootDayView(v.item.project.id, day.id) }
            } else {
                val first = v.acceptedCard?.terms?.dates?.minOrNull() ?: v.item.project.shootStart
                title = if (first != null) stringResource(R.string.ws6_pinned_booked_first, Dates.shortUpper(first)) else stringResource(R.string.ws6_pinned_booked)
                body = stringResource(if (isOwner) R.string.ws6_pinned_no_sheet_owner else R.string.ws6_pinned_no_sheet_collab)
                onTap = if (isOwner && features.shootDays) ({ nav.toShootDayEditor(v.item.project.id) }) else onSendCard
            }
        }
        latest?.status == BookingStatus.SENT -> {
            if (isOwner) {
                icon = Icons.Rounded.HourglassTop
                title = stringResource(R.string.ws6_pinned_offer_out, latest.version)
                body = stringResource(R.string.ws6_pinned_waiting_for, cpFirst)
                onTap = onSendCard
                buttons += { SecondaryButton(stringResource(R.string.ws6_edit_terms), onSendCard) }
            } else {
                title = stringResource(R.string.ws6_pinned_card_in, latest.version)
                body = CardText.bookingSummary(latest.terms).substringAfter(" · ", "").substringBefore(" · Payer").replaceFirstChar { it.uppercase() }
                onTap = onSendCard
                buttons += { PrimaryButton(stringResource(R.string.ws6_review_offer), onSendCard) }
            }
        }
        else -> {
            val declined = latest?.status == BookingStatus.DECLINED
            if (isOwner) {
                title = stringResource(if (declined) R.string.ws6_pinned_declined_owner else R.string.ws6_pinned_send_title, latest?.version ?: 1)
                body = stringResource(R.string.ws6_pinned_send_body)
                buttons += { PrimaryButton(stringResource(if (declined) R.string.ws6_send_new_version else R.string.ws6_send_booking_card), onSendCard) }
                if (v.item.role.isCast && v.item.project.auditionMode != null && m.state == MatchState.TALKING) {
                    buttons += { SecondaryButton(stringResource(R.string.ws6_request_audition), onAudition) }
                }
            } else {
                icon = Icons.Rounded.HourglassTop; tint = c.textSecondary
                title = stringResource(if (declined) R.string.ws6_pinned_declined_collab else R.string.ws6_pinned_waiting_card)
                body = stringResource(R.string.ws6_pinned_waiting_body)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
            .clip(CtShapes.field)
            .background(c.surface)
            .border(1.dp, if (tint == c.go) c.go.copy(alpha = 0.35f) else c.outline.copy(alpha = 0.7f), CtShapes.field)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.bodyS.lineHeight),
                    color = if (tint == c.go) c.go else c.text)
                if (!body.isNullOrBlank()) Text(body, style = Ct.type.label, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (buttons.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { it() }
            }
        }
    }
}

// ───────────────────────── Messages ─────────────────────────

@Composable
private fun MessageList(
    data: ChatData,
    modifier: Modifier,
    me: String?,
    bookedLike: Boolean,
    active: Boolean,
    cpFirst: String,
    nav: AppNavigator,
    onReport: (Message) -> Unit,
    onBlock: () -> Unit,
    onAudition: (Message, AuditionResponse) -> Unit,
    onSendTape: (Message) -> Unit,
    onAuditionReport: (Message) -> Unit,
) {
    val v = data.view!!
    val m = v.match
    val messages = data.messages.ifEmpty {
        // Empty chat: the role's terms are always the first thing in it.
        listOf(Message("terms_${m.id}", m.id, null, MessageKind.SYSTEM, CardText.termsMessage(v.item), createdAt = m.createdAt))
    }
    val items = remember(messages) { buildItems(messages).asReversed() }
    val cards = data.cards.associateBy { it.id }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0) }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items, key = { it.key }) { it ->
            when (it) {
                is ChatItem.Separator -> DaySeparatorRow(daySeparator(it.date))
                is ChatItem.Msg -> {
                    val msg = it.m
                    val mine = msg.senderId != null && msg.senderId == me
                    val senderIsOwnerSide = msg.senderId == m.ownerId || msg.senderId != m.collaboratorId
                    val flags = msg.flags.ifEmpty { MessageSafety.analyze(msg.body) }
                    val banners = if (msg.senderId == null) emptyList() else bannersForMessage(flags, mine, senderIsOwnerSide, bookedLike)
                    Column(Modifier.fillMaxWidth()) {
                        if (banners.isNotEmpty()) BannersFor(banners, onReport = { onReport(msg) }, onBlock = onBlock, onLearnMore = { nav.toSafetyCenter() })
                        when (msg.kind) {
                            MessageKind.SYSTEM -> SystemMessage(msg.body)
                            MessageKind.TEXT -> {
                                val body = if (!bookedLike && !mine && msg.senderId != null) MessageSafety.mask(msg.body) else msg.body
                                TextBubble(body, mine, if (it.showTime) clock(msg.createdAt) else null, onLongPress = if (!mine && msg.senderId != null) ({ onReport(msg) }) else null)
                            }
                            MessageKind.AUDITION -> msg.audition?.let { payload ->
                                AuditionCard(
                                    payload = payload,
                                    isActor = !v.isOwner,
                                    active = active,
                                    otherFirstName = cpFirst,
                                    onWillDo = { onAudition(msg, AuditionResponse.WILL_DO) },
                                    onCantMake = { onAudition(msg, AuditionResponse.CANT_MAKE_IT) },
                                    onSendTape = { onSendTape(msg) },
                                    onReport = { onAuditionReport(msg) },
                                    onOpenUrl = nav::openUrl,
                                )
                            } ?: SystemMessage(msg.body)
                            MessageKind.BOOKING -> BookingMessageCard(cards[msg.bookingCardId], msg.body, mine) { nav.toBookingCard(m.id) }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Composer ─────────────────────────

@Composable
private fun quickReplies(isOwner: Boolean, state: MatchState, post: Boolean): List<String> {
    val ids = when {
        state == MatchState.WRAPPED -> listOf(R.string.ws6_qr_thanks_wrap, R.string.ws6_qr_work_again)
        state == MatchState.BOOKED && post -> listOf(R.string.ws6_qr_got_it, R.string.ws6_qr_delivery_soon, R.string.ws6_qr_question)
        state == MatchState.BOOKED -> listOf(R.string.ws6_qr_got_sheet, R.string.ws6_qr_see_you, R.string.ws6_qr_late)
        isOwner -> listOf(R.string.ws6_qr_owner_dates, R.string.ws6_qr_owner_card, R.string.ws6_qr_owner_questions)
        post -> listOf(R.string.ws6_qr_in, R.string.ws6_qr_spec, R.string.ws6_qr_cant_deadline, R.string.ws6_qr_send_card)
        else -> listOf(R.string.ws6_qr_in, R.string.ws6_qr_call_times, R.string.ws6_qr_cant_dates, R.string.ws6_qr_send_card)
    }
    return ids.map { stringResource(it) }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    hint: String,
    warning: String?,
    quickReplies: List<String>,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val c = Ct.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
        if (warning != null) {
            val danger = warning != MessageSafety.DRAFT_CONTACT
            Row(
                Modifier.fillMaxWidth().background(if (danger) c.dangerContainer else c.warnContainer).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = if (danger) c.stop else c.onWarnContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(warning, style = Ct.type.label, color = if (danger) c.onDangerContainer else c.onWarnContainer)
            }
        }
        if (text.isBlank() && quickReplies.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(quickReplies) { q -> CtChip(q, onClick = { onText(q) }) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = text,
                onValueChange = { onText(it.take(2000)) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                placeholder = { Text(hint, style = Ct.type.body, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                textStyle = Ct.type.body.copy(color = c.text),
                shape = RoundedCornerShape(26.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.surfaceRaised, unfocusedContainerColor = c.surfaceRaised,
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.outline, cursorColor = c.accent,
                    focusedTextColor = c.text, unfocusedTextColor = c.text,
                ),
            )
            Spacer(Modifier.width(8.dp))
            val can = text.isNotBlank() && !sending
            val sendCd = stringResource(R.string.ws6_chat_send)
            IconButton(
                onClick = onSend,
                enabled = can,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (can) c.accent else c.surfaceHigh)
                    .semantics { contentDescription = sendCd },
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = if (can) c.onAccent else c.textMuted, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun EndedBar(reason: String?) {
    val c = Ct.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.ws6_chat_ended), style = Ct.type.bodyS, color = c.textSecondary)
        if (!reason.isNullOrBlank()) Text(reason, style = Ct.type.label, color = c.textMuted)
    }
}
