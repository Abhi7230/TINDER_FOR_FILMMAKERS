package com.tff.app.ui.screens.inbox

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.MessageSafety
import com.tff.app.model.AppNotification
import com.tff.app.model.AskDeclineReason
import com.tff.app.model.AskView
import com.tff.app.model.ConversationRow
import com.tff.app.model.Inbox
import com.tff.app.model.ReportReason
import com.tff.app.model.ReportTarget
import com.tff.app.nav.AppNavigator
import com.tff.app.nav.MainTab
import com.tff.app.ui.components.ChipStyle
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.components.SafetyBanner
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.SlateOverlay
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.screens.chat.BlockDialog
import com.tff.app.ui.screens.chat.ENDED_STATES
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.askDeclineLabel
import com.tff.app.ui.screens.chat.notificationIcon
import com.tff.app.ui.screens.chat.rowTime
import com.tff.app.ui.screens.chat.userMessage
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

private const val UPDATES_COLLAPSED = 6

/** S14 Inbox tab: Asks for you · Conversations · Updates. Sits inside the bottom-nav scaffold. */
@Composable
fun InboxScreen(nav: AppNavigator) {
    val vm = rememberVm { repo -> InboxViewModel(repo) }
    val session by vm.session.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val c = Ct.colors

    var inTalks by remember { mutableStateOf<Pair<String, String>?>(null) }   // matchId to subtitle
    var report by remember { mutableStateOf<AskView?>(null) }
    var block by remember { mutableStateOf<AskView?>(null) }
    val genericError = stringResource(R.string.ws6_generic_error)
    val res = LocalContext.current.resources

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            val inbox = (state as? Load.Ready)?.data
            val subtitle = when {
                session == null || inbox == null -> null
                inbox.unreadCount > 0 -> stringResource(R.string.ws6_inbox_unread, inbox.unreadCount)
                inbox.conversations.isNotEmpty() -> stringResource(R.string.ws6_inbox_all_read)
                else -> null
            }
            CtTopBar(title = stringResource(R.string.ws6_inbox_title), subtitle = subtitle)
            when {
                session == null -> GuestInbox(nav)
                else -> when (val s = state) {
                    Load.Loading -> LoadingSkeleton(Modifier.fillMaxWidth(), kind = SkeletonKind.LIST, rows = 6)
                    is Load.Failed -> ErrorState(onRetry = vm::retry, modifier = Modifier.fillMaxSize(), message = s.message)
                    is Load.Ready -> InboxBody(
                        inbox = s.data,
                        busy = busy,
                        nav = nav,
                        onAccept = { ask ->
                            vm.acceptAsk(ask.application.id) { r ->
                                r.onSuccess { m ->
                                    if (m != null) inTalks = m.id to "${ask.item.owner.shortName} · ${CardText.roleName(ask.item.role)} · ${ask.item.project.title}"
                                }.onFailure { nav.toast(it.userMessage() ?: genericError) }
                            }
                        },
                        onDecline = { ask, reason, label ->
                            vm.declineAsk(ask.application.id, reason) { r ->
                                r.onSuccess { nav.toast(res.getString(R.string.ws6_ask_declined_toast, ask.item.owner.shortName, label)) }
                                    .onFailure { nav.toast(it.userMessage() ?: genericError) }
                            }
                        },
                        onReport = { report = it },
                        onBlock = { block = it },
                        onOpenNotification = { n ->
                            vm.markRead(n)
                            n.deepLink?.let { link -> if (link != "calltime://inbox") nav.go(link) }
                        },
                    )
                }
            }
        }
        SlateOverlay(
            visible = inTalks != null,
            subtitle = inTalks?.second ?: "",
            onDismiss = { inTalks = null },
            autoDismiss = false,
            actions = {
                SecondaryButton(stringResource(R.string.ws6_later), { inTalks = null }, Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.ws6_open_chat), {
                    val id = inTalks?.first
                    inTalks = null
                    if (id != null) nav.toChat(id)
                }, Modifier.weight(1f))
            },
        )
    }

    report?.let { ask ->
        ReportSheet(ReportTarget.ROLE, ask.item.role.id, onDismiss = { report = null }, prefill = ReportReason.MONEY_REQUEST)
    }
    block?.let { ask ->
        BlockDialog(
            name = ask.item.owner.shortName,
            profileId = ask.item.owner.id,
            nav = nav,
            onDone = { block = null },
            onDismiss = { block = null },
        )
    }
}

@Composable
private fun GuestInbox(nav: AppNavigator) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.ws6_inbox_guest_title),
            body = stringResource(R.string.ws6_inbox_guest_body),
            icon = Icons.Rounded.Lock,
        ) {
            PrimaryButton(stringResource(R.string.ws6_inbox_guest_cta), { nav.toWelcome() })
        }
    }
}

@Composable
private fun InboxBody(
    inbox: Inbox,
    busy: Set<String>,
    nav: AppNavigator,
    onAccept: (AskView) -> Unit,
    onDecline: (AskView, AskDeclineReason, String) -> Unit,
    onReport: (AskView) -> Unit,
    onBlock: (AskView) -> Unit,
    onOpenNotification: (AppNotification) -> Unit,
) {
    if (inbox.asks.isEmpty() && inbox.conversations.isEmpty() && inbox.updates.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = stringResource(R.string.ws6_inbox_empty_title),
                body = stringResource(R.string.ws6_inbox_empty_body),
                icon = Icons.Rounded.ChatBubbleOutline,
            ) {
                PrimaryButton(stringResource(R.string.ws6_inbox_find_gigs), { nav.toMain(MainTab.DISCOVER) })
            }
        }
        return
    }
    var showAllUpdates by rememberSaveable { mutableStateOf(false) }
    val updates = if (showAllUpdates) inbox.updates else inbox.updates.take(UPDATES_COLLAPSED)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        if (inbox.asks.isNotEmpty()) {
            item(key = "h_asks") { SectionHeader(stringResource(R.string.ws6_inbox_asks), count = inbox.asks.size) }
            items(inbox.asks, key = { "ask_" + it.application.id }) { ask ->
                AskCard(ask, busy = ask.application.id in busy, nav = nav, onAccept = { onAccept(ask) },
                    onDecline = { r, l -> onDecline(ask, r, l) }, onReport = { onReport(ask) }, onBlock = { onBlock(ask) })
                Spacer(Modifier.height(12.dp))
            }
        }
        item(key = "h_convs") {
            SectionHeader(stringResource(R.string.ws6_inbox_conversations), count = inbox.conversations.size.takeIf { it > 0 })
        }
        if (inbox.conversations.isEmpty()) {
            item(key = "no_convs") {
                Text(stringResource(R.string.ws6_inbox_no_conversations), style = Ct.type.bodyS, color = Ct.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            items(inbox.conversations, key = { "conv_" + it.match.id }) { row ->
                ConversationItem(row) { nav.toChat(row.match.id) }
                HorizontalDivider(color = Ct.colors.outline.copy(alpha = 0.5f), thickness = 1.dp)
            }
        }
        if (inbox.updates.isNotEmpty()) {
            item(key = "h_updates") {
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.ws6_inbox_updates), count = inbox.updates.count { it.readAt == null }.takeIf { it > 0 })
            }
            items(updates, key = { "upd_" + it.id }) { n ->
                UpdateItem(n) { onOpenNotification(n) }
            }
            if (inbox.updates.size > UPDATES_COLLAPSED) {
                item(key = "more_updates") {
                    GhostButton(
                        if (showAllUpdates) stringResource(R.string.ws6_inbox_show_fewer)
                        else stringResource(R.string.ws6_inbox_show_all_updates, inbox.updates.size),
                        { showAllUpdates = !showAllUpdates },
                    )
                }
            }
        }
    }
}

// ───────────────────────── Asks ─────────────────────────

@Composable
private fun AskCard(
    ask: AskView,
    busy: Boolean,
    nav: AppNavigator,
    onAccept: () -> Unit,
    onDecline: (AskDeclineReason, String) -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val c = Ct.colors
    val item = ask.item
    val role = item.role
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CtShapes.card)
            .background(c.surface)
            .border(1.dp, if (ask.isRebook) c.accent.copy(alpha = 0.45f) else c.outline.copy(alpha = 0.7f), CtShapes.card)
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { nav.toRoleDetail(role.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtChip(
                    stringResource(if (ask.isRebook) R.string.ws6_rebook_label else R.string.ws6_ask_label),
                    tone = ChipTone.ACCENT, filled = true, style = ChipStyle.CAPS, small = true,
                )
                Spacer(Modifier.weight(1f))
                Text(rowTime(ask.application.createdAt), style = Ct.type.monoS, color = c.textMuted)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(item.owner.displayName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.ws6_ask_from, item.owner.shortName), style = Ct.type.bodyS, color = c.textSecondary)
            }
            Spacer(Modifier.height(10.dp))
            Text(CardText.roleName(role).uppercase(), style = Ct.type.slate, color = c.accent)
            Text(item.project.title, style = Ct.type.titleL, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(CardText.payLine(role, item.project), style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize),
                color = if (role.isPaid) c.go else c.text)
            Spacer(Modifier.height(4.dp))
            Text(CardText.whenLine(role, item.project), style = Ct.type.monoS, color = c.textSecondary)
            val note = ask.application.note?.takeIf { it.isNotBlank() }
            if (note != null) {
                val flags = MessageSafety.analyze(note)
                val banners = MessageSafety.bannersFor(flags, senderIsOwnerSide = true)
                Spacer(Modifier.height(12.dp))
                banners.forEach { b ->
                    SafetyBanner(b, onReport = onReport, onBlock = onBlock)
                    Spacer(Modifier.height(8.dp))
                }
                Text("“${MessageSafety.mask(note)}”", style = Ct.type.bodyS.copy(fontStyle = FontStyle.Italic), color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                val noCd = stringResource(R.string.ws6_ask_no_cd)
                SecondaryButton(
                    stringResource(R.string.ws6_ask_no), { menu = true },
                    Modifier.fillMaxWidth().semantics { contentDescription = noCd },
                    enabled = !busy, icon = Icons.Rounded.ExpandMore,
                )
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = c.surfaceRaised) {
                    AskDeclineReason.entries.forEach { r ->
                        val label = askDeclineLabel(r)
                        DropdownMenuItem(
                            text = { Text(label, style = Ct.type.body, color = c.text) },
                            onClick = { menu = false; onDecline(r, label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            PrimaryButton(stringResource(R.string.ws6_ask_yes), onAccept, Modifier.weight(1f), loading = busy)
        }
    }
}

// ───────────────────────── Conversations ─────────────────────────

@Composable
private fun ConversationItem(row: ConversationRow, onClick: () -> Unit) {
    val c = Ct.colors
    val ended = row.match.state in ENDED_STATES
    val roleLine = "${CardText.roleName(row.roleCode)} · ${row.projectTitle}"
    val last = row.lastMessage?.let { MessageSafety.mask(it) }?.replace('\n', ' ')
    val cd = stringResource(R.string.ws6_conversation_cd, row.counterpartName, roleLine, last ?: "") +
        if (row.unread > 0) ". " + stringResource(R.string.ws6_unread_cd, row.unread) else ""
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cd }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Monogram(row.counterpartInitials, size = 44.dp, highlight = row.unread > 0)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.counterpartName, style = Ct.type.bodyStrong, color = if (ended) c.textSecondary else c.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(row.match.state)
                Spacer(Modifier.weight(1f))
                Text(rowTime(row.lastAt), style = Ct.type.monoS, color = if (row.unread > 0) c.accent else c.textMuted)
            }
            Spacer(Modifier.height(2.dp))
            Text(roleLine, style = Ct.type.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (last != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        last, style = Ct.type.bodyS, color = if (row.unread > 0) c.text else c.textMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (row.unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.heightIn(min = 20.dp).clip(CircleShape).background(c.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${row.unread}", style = Ct.type.monoS, color = c.onAccent)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Updates ─────────────────────────

@Composable
private fun UpdateItem(n: AppNotification, onClick: () -> Unit) {
    val c = Ct.colors
    val unread = n.readAt == null
    val unreadCd = stringResource(R.string.ws6_update_unread_cd)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (unread) contentDescription = "$unreadCd. ${n.title}. ${n.body}" }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(notificationIcon(n.kind), contentDescription = null, tint = if (unread) c.accent else c.textMuted, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.bodyS.lineHeight),
                color = if (unread) c.text else c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(n.body, style = Ct.type.bodyS, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(rowTime(n.createdAt), style = Ct.type.monoS, color = c.textMuted, modifier = Modifier.clearAndSetSemantics { })
            if (unread) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
            }
        }
    }
}
