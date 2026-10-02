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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Flag
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.AppClock
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
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.DangerButton
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
    // Organisation-style handles ("Casting Hub Official") keep their full name everywhere in the chat.
    val cpName = displayName(cp.displayName, cp.shortName)
    val cpFirst = if (isOrgName(cp.displayName)) cpName else cp.displayName.trim().split(Regex("\\s+")).firstOrNull() ?: cp.shortName
    val res = LocalContext.current.resources
    val generic = stringResource(R.string.ws6_generic_error)
    val sending by vm.sending.collectAsStateWithLifecycle()
    val features = vm.features

    var field by rememberSaveable(vm.matchId, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val text = field.text
    val composerFocus = remember { FocusRequester() }
    /** Replace the draft and put the cursor at its end (prefills, quick replies). */
    fun setDraft(t: String) { field = TextFieldValue(t, TextRange(t.length)) }
    val prefill by ChatBridge.prefill.collectAsStateWithLifecycle()
    LaunchedEffect(prefill[vm.matchId]) {
        ChatBridge.consumePrefill(vm.matchId)?.let { setDraft(it); runCatching { composerFocus.requestFocus() } }
    }
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

    // Thread safety: the other person's red flags switch the chat to safety-first replies (no "Yes, I'm in").
    val msgIds = remember(data.messages) { data.messages.map { it.id }.toSet() }
    val myReport = reportForThread(data.reports, cp.id, m.id, msgIds)
    val redFlags = remember(data.messages, me) { threadRedFlags(data.messages, m, me) }
    val flaggedMsg = remember(data.messages, me) { latestRedFlagMessage(data.messages, m, me) }
    val post = m.isPostRole || v.item.role.isPostRole
    val quick = quickReplySpecs(
        QuickReplyContext(
            isOwner = isOwner, state = m.state, post = post, isCast = v.item.role.isCast,
            redFlags = redFlags, reported = myReport != null,
            shootToday = isShootDay(AppClock.today(), v.acceptedCard?.terms?.dates.orEmpty(), v.nextShootDay?.day),
            daySheetOut = v.nextShootDay != null,
        )
    ).map { it.kind to stringResource(it.text) }
    val idLine = if (cp.idVerified || !(v.counterpart.reputation.isNew || redFlags.isNotEmpty())) null
    else listOfNotNull(
        stringResource(R.string.ws6c_not_id_verified),
        stringResource(R.string.ws6c_new_on_calltime).takeIf { v.counterpart.reputation.isNew },
    ).joinToString(" · ")

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Scaffold(
            containerColor = c.bg,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                ChatTopBar(
                    name = cpName, initials = cp.initials,
                    roleLine = "${CardText.roleName(v.item.role)} · ${v.item.project.title}",
                    idLine = idLine,
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
                    field = field,
                    onField = { field = it.copy(text = it.text.take(2000)) },
                    onQuickReply = { q -> setDraft(q); runCatching { composerFocus.requestFocus() } },
                    focus = composerFocus,
                    hint = stringResource(R.string.ws6_chat_hint),
                    warning = MessageSafety.draftWarning(text, booked = bookedLike, senderIsOwnerSide = isOwner),
                    quickReplies = quick,
                    onQuickAction = { kind ->
                        when (kind) {
                            QuickReplyKind.REPORT -> flaggedMsg?.let(reportFor) ?: run { sheet = ChatSheet.Report(ReportTarget.PROFILE, cp.id, null) }
                            QuickReplyKind.BLOCK -> sheet = ChatSheet.Block
                            QuickReplyKind.DRAFT -> {}
                        }
                    },
                    sending = sending,
                    onSend = {
                        val t = text
                        vm.send(t) { r ->
                            r.onSuccess { if (field.text == t) field = TextFieldValue("") }.onFailure { nav.toast(it.userMessage() ?: generic) }
                        }
                    },
                )
            },
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
                if (myReport != null) {
                    // Reported: one clear state with Block as the next step; the "waiting for the booking card"
                    // bar is hidden so the talks don't look like they carry on as normal.
                    ReportedPanel(
                        status = reportStatusLabel(myReport.status),
                        reviewing = myReport.status == ReportStatus.RECEIVED || myReport.status == ReportStatus.REVIEWING,
                        name = cpName,
                        canBlock = !ended,
                        onBlock = { sheet = ChatSheet.Block },
                        onSafetyCenter = { nav.toSafetyCenter() },
                    )
                }
                if (myReport == null || bookedLike) PinnedBar(
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
                MessageList(
                    data = data,
                    modifier = Modifier.weight(1f),
                    me = me,
                    bookedLike = bookedLike,
                    active = active,
                    cpFirst = cpFirst,
                    reported = myReport != null,
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
        ChatSheet.Block -> BlockDialog(cpName, cp.id, nav, onDone = { sheet = null; nav.back() }, onDismiss = { sheet = null })
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

/**
 * Two-line title: name + state chip (the chip wraps under a long name), then "Lead Actor · Roommates Wanted"
 * at full width, wrapping to two lines instead of cutting the project off. [idLine] is the muted
 * "Not ID verified · New on Calltime" cue. The DEMO tag sits above the overflow so it takes no width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatTopBar(
    name: String,
    initials: String,
    roleLine: String,
    idLine: String?,
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
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ws1_back), tint = c.text)
            }
            Row(
                Modifier.weight(1f).clip(CtShapes.field).clickable(onClick = onProfile).padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Monogram(initials, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = Ct.type.titleM, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() })
                        StatusChip(state)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(roleLine, style = Ct.type.label, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (idLine != null) {
                        Text(idLine, style = Ct.type.label, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (demo) DemoTag()
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.ws6_more_options), tint = c.text)
                    }
                    DropdownMenu(open, onDismissRequest = { open = false }, containerColor = c.surfaceRaised) { menu { open = false } }
                }
            }
        }
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
    }
}

/**
 * After a report: "Reported · we're reviewing" with the status, Block as the obvious next step and a link to
 * Safety Center. Replaces the pinned "waiting for the booking card" bar until the talks are booked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportedPanel(
    status: String,
    reviewing: Boolean,
    name: String,
    canBlock: Boolean,
    onBlock: () -> Unit,
    onSafetyCenter: () -> Unit,
) {
    val c = Ct.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
            .clip(CtShapes.field)
            .background(c.dangerContainer.copy(alpha = 0.55f))
            .border(1.dp, c.stop.copy(alpha = 0.45f), CtShapes.field)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.stop, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (reviewing) stringResource(R.string.ws6c_reported_title) else stringResource(R.string.ws6c_row_reported),
                    style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.bodyS.lineHeight),
                    color = c.text, modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.ws6c_reported_body, status),
                    style = Ct.type.label, color = c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            Modifier.padding(start = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (canBlock) DangerButton(stringResource(R.string.ws6c_block_cta, name), onBlock, icon = Icons.Rounded.Block)
            GhostButton(stringResource(R.string.ws6_safety_center), onSafetyCenter, color = c.info)
        }
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
                onTap = onSendCard
                val inThread = data.messages.any { it.kind == MessageKind.BOOKING && it.bookingCardId == latest.id }
                if (inThread) {
                    // The full terms are in the booking card bubble below: keep the bar to one short line + the CTA.
                    CompactPinnedBar(title, offerShortLine(latest.terms), stringResource(R.string.ws6_review_offer), onSendCard)
                    return
                }
                body = CardText.bookingSummary(latest.terms).substringAfter(" · ", "").substringBefore(" · Payer").replaceFirstChar { it.uppercase() }
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
                if (!body.isNullOrBlank()) Text(body, style = Ct.type.label, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
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

/** One-row pinned bar: "Booking card v1 is here" / "$2,400 flat · deliver by 4 Nov" + [Review offer]. */
@Composable
private fun CompactPinnedBar(title: String, line: String, cta: String, onClick: () -> Unit) {
    val c = Ct.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
            .clip(CtShapes.field)
            .background(c.surface)
            .border(1.dp, c.accent.copy(alpha = 0.45f), CtShapes.field)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Movie, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.bodyS.lineHeight), color = c.text)
            Text(line, style = Ct.type.label, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        GhostButton(cta, onClick)
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
    reported: Boolean,
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
    // Once a booking card is in the thread it carries the terms; the listing-terms system line folds away.
    val foldTerms = data.cards.isNotEmpty()
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
                        if (banners.isNotEmpty()) BannersFor(banners, reported = reported, onReport = { onReport(msg) }, onBlock = onBlock, onLearnMore = { nav.toSafetyCenter() })
                        when (msg.kind) {
                            MessageKind.SYSTEM -> SystemMessage(msg.body, foldListingTerms = foldTerms)
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
                            MessageKind.BOOKING -> BookingMessageCard(
                                cards[msg.bookingCardId], msg.body, mine,
                                changeToBooking = m.state == MatchState.BOOKED || m.state == MatchState.WRAPPED,
                                ownerView = v.isOwner,
                            ) { nav.toBookingCard(m.id) }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Composer ─────────────────────────

@Composable
private fun Composer(
    field: TextFieldValue,
    onField: (TextFieldValue) -> Unit,
    onQuickReply: (String) -> Unit,
    focus: FocusRequester,
    hint: String,
    warning: String?,
    quickReplies: List<Pair<QuickReplyKind, String>>,
    onQuickAction: (QuickReplyKind) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val c = Ct.colors
    val text = field.text
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
                items(quickReplies) { (kind, q) ->
                    when (kind) {
                        QuickReplyKind.DRAFT -> CtChip(q, onClick = { onQuickReply(q) })
                        QuickReplyKind.REPORT -> CtChip(q, tone = ChipTone.STOP, filled = true, icon = Icons.Rounded.Flag, onClick = { onQuickAction(kind) })
                        QuickReplyKind.BLOCK -> CtChip(q, tone = ChipTone.STOP, filled = true, icon = Icons.Rounded.Block, onClick = { onQuickAction(kind) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = field,
                onValueChange = onField,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).focusRequester(focus),
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
