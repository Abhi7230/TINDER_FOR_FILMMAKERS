package com.tff.app.ui.screens.wrap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.WrapItemsFactory
import com.tff.app.model.BookingTerms
import com.tff.app.model.FeedbackState
import com.tff.app.model.MatchState
import com.tff.app.model.Message
import com.tff.app.model.OwedBy
import com.tff.app.model.ProfileCard
import com.tff.app.model.ReportReason
import com.tff.app.model.ReportTarget
import com.tff.app.model.RoleCode
import com.tff.app.model.WrapItem
import com.tff.app.model.WrapKind
import com.tff.app.model.WrapStatus
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.screens.booking.BookingCopy
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.localDateOf
import com.tff.app.ui.screens.chat.userMessage
import com.tff.app.ui.screens.chat.wrapKindIcon
import com.tff.app.ui.screens.chat.wrapKindLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

private val LINK_RX = Regex("^https?://\\S+\\.\\S+")

/**
 * What the viewer can do with an item, worked out once. [terms] are the accepted terms (pay owed),
 * [mine] the viewer's own chat messages (reminder cooldown), [report] what a "didn't deliver" report targets.
 */
private data class ItemCtx(
    val item: WrapItem,
    val iOwe: Boolean,
    val otherFirst: String,
    val isPostRole: Boolean,
    val footageDefinition: String?,
    val terms: BookingTerms? = null,
    val projectTitle: String = "",
    val mine: List<Message> = emptyList(),
    val report: Pair<ReportTarget, String>? = null,
)

private data class FeedbackTarget(val matchId: String, val isOwner: Boolean, val isPostRole: Boolean, val first: String, val counterpartId: String)

/** S17 Wrap. Route wrap/m/{matchId} (one booking, either side) or wrap/p/{projectId} (owner, per person). */
@Composable
fun WrapScreen(nav: AppNavigator, matchId: String?, projectId: String?) {
    val vm = rememberVm(key = "wrap_${matchId}_$projectId") { repo -> WrapViewModel(repo, matchId, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val res = LocalContext.current.resources
    val generic = stringResource(R.string.ws6_generic_error)
    var marking by remember { mutableStateOf<ItemCtx?>(null) }
    var feedback by remember { mutableStateOf<FeedbackTarget?>(null) }
    var reporting by remember { mutableStateOf<ItemCtx?>(null) }

    val onMark = { ctx: ItemCtx -> marking = ctx }
    val actions = OverdueActions(
        onRemind = { ctx, body ->
            vm.remind(ctx.item.matchId, ctx.item.id, body) { r ->
                r.onSuccess { nav.toast(res.getString(R.string.ws6e_wrap_reminded_toast, ctx.otherFirst)) }
                    .onFailure { nav.toast(it.userMessage() ?: generic) }
            }
        },
        onReport = { ctx -> reporting = ctx },
    )
    val onConfirm = { item: WrapItem, received: Boolean ->
        vm.confirm(item.id, received) { r ->
            r.onSuccess { nav.toast(res.getString(if (received) R.string.ws6_wrap_confirmed_toast else R.string.ws6_wrap_not_received_toast)) }
                .onFailure { nav.toast(it.userMessage() ?: generic) }
        }
    }

    Column(Modifier.fillMaxSize().background(Ct.colors.bg)) {
        when (val s = state) {
            Load.Loading -> {
                CtTopBar(stringResource(R.string.ws6_wrap_title), onBack = nav::back)
                LoadingSkeleton(Modifier.fillMaxWidth(), kind = SkeletonKind.LIST, rows = 4)
            }
            is Load.Failed -> {
                CtTopBar(stringResource(R.string.ws6_wrap_title), onBack = nav::back)
                ErrorState(onRetry = vm::retry, modifier = Modifier.fillMaxSize(), message = s.message)
            }
            is Load.Ready -> when (val d = s.data) {
                is WrapData.Single -> SingleWrap(nav, d, busy, vm.features.feedback, onMark, onConfirm, actions) { feedback = it }
                is WrapData.Groups -> GroupsWrap(nav, d, busy, vm.features.feedback, onMark, onConfirm, actions) { feedback = it }
            }
        }
    }

    marking?.let { ctx ->
        MarkDoneSheet(ctx, busy = ctx.item.id in busy, onDismiss = { marking = null }) { link, note, err ->
            vm.mark(ctx.item.id, link, note) { r ->
                r.onSuccess { marking = null; nav.toast(res.getString(R.string.ws6_wrap_marked_toast, ctx.otherFirst)) }
                    .onFailure { err(it.userMessage() ?: generic) }
            }
        }
    }
    reporting?.let { ctx ->
        val target = ctx.report
        if (target != null) ReportSheet(
            target.first, target.second, onDismiss = { reporting = null },
            prefill = if (ctx.item.kind == WrapKind.PAID) ReportReason.NOT_PAID else ReportReason.OTHER,
        )
    }
    feedback?.let { f ->
        FeedbackSheet(
            isOwner = f.isOwner, isPostRole = f.isPostRole, counterpartFirst = f.first, counterpartId = f.counterpartId,
            busy = "fb_${f.matchId}" in busy, onDismiss = { feedback = null },
        ) { answers, err, done ->
            vm.submitFeedback(f.matchId, answers) { r -> r.onSuccess { done() }.onFailure { err(it.userMessage() ?: generic) } }
        }
    }
}

private fun firstName(p: ProfileCard): String = p.profile.displayName.trim().split(Regex("\\s+")).firstOrNull() ?: p.profile.shortName

@Composable
private fun SingleWrap(
    nav: AppNavigator,
    d: WrapData.Single,
    busy: Set<String>,
    feedbackOn: Boolean,
    onMark: (ItemCtx) -> Unit,
    onConfirm: (WrapItem, Boolean) -> Unit,
    actions: OverdueActions,
    onFeedback: (FeedbackTarget) -> Unit,
) {
    val v = d.view
    if (v == null) {
        CtTopBar(stringResource(R.string.ws6_wrap_title), onBack = nav::back)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.ws6_wrap_missing_title), stringResource(R.string.ws6_wrap_missing_body), icon = Icons.Rounded.Lock) {
                PrimaryButton(stringResource(R.string.ws6_back), { nav.back() })
            }
        }
        return
    }
    val m = v.match
    val first = firstName(v.counterpart)
    val post = m.isPostRole || v.item.role.isPostRole
    val terms = v.acceptedCard?.terms
    CtTopBar(stringResource(R.string.ws6_wrap_title), subtitle = "${CardText.roleName(v.item.role)} · ${v.item.project.title}", onBack = nav::back)
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "head") {
            PersonHeader(
                name = v.counterpart.profile.shortName, initials = v.counterpart.profile.initials,
                line = if (v.isOwner) CardText.roleName(v.item.role) else stringResource(R.string.ws6_wrap_production_line, v.item.project.title),
                state = m.state, items = d.items,
            )
        }
        if (feedbackOn) item(key = "fb") {
            FeedbackBanner(d.feedback, m.state, post, first) {
                onFeedback(FeedbackTarget(m.id, v.isOwner, post, first, v.counterpart.profile.id))
            }
        }
        if (d.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(stringResource(R.string.ws6_wrap_clean_title), stringResource(R.string.ws6_wrap_clean_body), icon = Icons.Rounded.TaskAlt)
            }
        } else {
            item(key = "h_items") { SectionHeader(stringResource(R.string.ws6_wrap_items), count = d.items.size) }
            items(d.items, key = { it.id }) { item ->
                val iOwe = (item.owedBy == OwedBy.PRODUCTION) == v.isOwner
                val report = v.acceptedCard?.id?.let { ReportTarget.BOOKING to it } ?: (ReportTarget.PROFILE to v.counterpart.profile.id)
                WrapItemCard(
                    ItemCtx(item, iOwe, first, post, terms?.footageDefinition, terms, v.item.project.title, d.mine, report),
                    busy = item.id in busy || "remind_${item.id}" in busy, nav = nav, onMark = onMark, onConfirm = onConfirm, actions = actions,
                )
            }
        }
        if (m.state == MatchState.BOOKED && !post) item(key = "note") {
            Text(stringResource(R.string.ws6_wrap_before_wrap), style = Ct.type.label, color = Ct.colors.textMuted)
        }
    }
}

@Composable
private fun GroupsWrap(
    nav: AppNavigator,
    d: WrapData.Groups,
    busy: Set<String>,
    feedbackOn: Boolean,
    onMark: (ItemCtx) -> Unit,
    onConfirm: (WrapItem, Boolean) -> Unit,
    actions: OverdueActions,
    onFeedback: (FeedbackTarget) -> Unit,
) {
    val p = d.project
    CtTopBar(stringResource(R.string.ws6_wrap_title), subtitle = p?.project?.title, onBack = nav::back)
    if (p == null || !p.isOwner) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.ws6_wrap_missing_title), stringResource(R.string.ws6_wrap_owner_only), icon = Icons.Rounded.Lock) {
                PrimaryButton(stringResource(R.string.ws6_back), { nav.back() })
            }
        }
        return
    }
    if (d.groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.ws6_wrap_no_team_title), stringResource(R.string.ws6_wrap_no_team_body), icon = Icons.Rounded.TaskAlt)
        }
        return
    }
    val today = AppClock.today()
    val overdue = d.groups.sumOf { g -> g.items.count { WrapItemsFactory.isOverdue(it, today) && it.owedBy == OwedBy.PRODUCTION } }
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (overdue > 0) item(key = "overdue") {
            Text(stringResource(R.string.ws6_wrap_overdue_summary, overdue), style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize), color = Ct.colors.stop)
        }
        d.groups.forEach { g ->
            val first = firstName(g.person)
            val post = g.match.isPostRole
            item(key = "g_${g.match.id}") {
                Column(
                    Modifier.fillMaxWidth().clip(CtShapes.card).background(Ct.colors.surface)
                        .border(1.dp, Ct.colors.outline.copy(alpha = 0.7f), CtShapes.card).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PersonHeader(g.person.profile.shortName, g.person.profile.initials, roleName(g.roleCode), g.match.state, g.items, card = false)
                    val fb = d.feedback[g.match.id]
                    if (feedbackOn && fb != null) FeedbackBanner(fb, g.match.state, post, first) {
                        onFeedback(FeedbackTarget(g.match.id, true, post, first, g.person.profile.id))
                    }
                    val extra = d.extras[g.match.id]
                    val report = extra?.cardId?.let { ReportTarget.BOOKING to it } ?: (ReportTarget.PROFILE to g.person.profile.id)
                    g.items.forEach { item ->
                        WrapItemCard(
                            ItemCtx(item, item.owedBy == OwedBy.PRODUCTION, first, post, extra?.terms?.footageDefinition, extra?.terms,
                                p.project.title, extra?.mine.orEmpty(), report),
                            busy = item.id in busy || "remind_${item.id}" in busy, nav = nav,
                            onMark = onMark, onConfirm = onConfirm, actions = actions, nested = true,
                        )
                    }
                    GhostButton(stringResource(R.string.ws6_wrap_open_chat), { nav.toChat(g.match.id) }, Modifier.offset(x = (-16).dp))
                }
            }
        }
    }
}

private fun roleName(code: RoleCode) = CardText.roleName(code)

@Composable
private fun PersonHeader(name: String, initials: String, line: String, state: MatchState, items: List<WrapItem>, card: Boolean = true) {
    val c = Ct.colors
    val done = items.count { it.status == WrapStatus.CONFIRMED }
    val progressCd = stringResource(R.string.ws6_wrap_progress_cd, done, items.size)
    Column(
        Modifier.fillMaxWidth()
            .then(if (card) Modifier.clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card).padding(14.dp) else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(initials, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = Ct.type.bodyStrong, color = c.text)
                Text(line, style = Ct.type.label, color = c.textSecondary)
            }
            StatusChip(state)
        }
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = progressCd }, verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { if (items.isEmpty()) 0f else done.toFloat() / items.size },
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    color = c.go, trackColor = c.surfaceHigh, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ws6_wrap_progress, done, items.size), style = Ct.type.monoS, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun FeedbackBanner(fb: FeedbackState, state: MatchState, post: Boolean, first: String, onGive: () -> Unit) {
    val c = Ct.colors
    val (icon, title, body) = when {
        fb.revealed -> Triple(Icons.Rounded.CheckCircle, stringResource(R.string.ws6_fb_revealed_title), stringResource(R.string.ws6_fb_revealed_body))
        fb.iSubmitted -> Triple(Icons.Rounded.HourglassTop, stringResource(R.string.ws6_fb_sent_title), stringResource(R.string.ws6_fb_sent_body, first))
        fb.unlocked -> Triple(Icons.Rounded.RateReview, stringResource(R.string.ws6_fb_open_title),
            if (fb.otherSubmitted) stringResource(R.string.ws6_fb_open_other, first) else stringResource(R.string.ws6_fb_open_body, first))
        state == MatchState.BOOKED -> Triple(Icons.Rounded.Lock, stringResource(R.string.ws6_fb_locked_title),
            stringResource(if (post) R.string.ws6_fb_locked_post else R.string.ws6_fb_locked_onset))
        else -> return
    }
    val open = fb.unlocked && !fb.iSubmitted
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.field)
            .background(if (open) c.accent.copy(alpha = 0.12f) else c.surfaceRaised)
            .border(1.dp, if (open) c.accent.copy(alpha = 0.45f) else c.outline.copy(alpha = 0.6f), CtShapes.field)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = if (open) c.accent else c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize), color = c.text)
                Text(body, style = Ct.type.label, color = c.textSecondary)
            }
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(stringResource(R.string.ws6_fb_give), onGive, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WrapItemCard(
    ctx: ItemCtx,
    busy: Boolean,
    nav: AppNavigator,
    onMark: (ItemCtx) -> Unit,
    onConfirm: (WrapItem, Boolean) -> Unit,
    actions: OverdueActions,
    nested: Boolean = false,
) {
    val c = Ct.colors
    val item = ctx.item
    val today = AppClock.today()
    val overdue = WrapItemsFactory.isOverdue(item, today)
    val daysLate = BookingCopy.daysOverdue(item, today)
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.field)
            .background(if (nested) c.surfaceRaised else c.surface)
            .border(1.dp, if (overdue) c.stop.copy(alpha = 0.5f) else c.outline.copy(alpha = 0.6f), CtShapes.field)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.bg), contentAlignment = Alignment.Center) {
                Icon(wrapKindIcon(item.kind), contentDescription = null, tint = if (item.status == WrapStatus.CONFIRMED) c.go else c.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(wrapKindLabel(item.kind), style = Ct.type.bodyStrong, color = c.text)
                val settled = item.status == WrapStatus.DONE || item.status == WrapStatus.CONFIRMED
                val who = when {
                    ctx.iOwe -> stringResource(if (settled) R.string.ws6_wrap_from_you else R.string.ws6_wrap_you_owe)
                    item.owedBy == OwedBy.PRODUCTION -> stringResource(if (settled) R.string.ws6_wrap_from_production else R.string.ws6_wrap_production_owes)
                    else -> if (settled) stringResource(R.string.ws6_wrap_from_them, ctx.otherFirst) else stringResource(R.string.ws6_wrap_they_owe, ctx.otherFirst)
                }
                Text(who, style = Ct.type.label, color = c.textSecondary)
                item.dueDate?.let {
                    Spacer(Modifier.height(2.dp))
                    val late = when {
                        !overdue || daysLate <= 0 -> null
                        daysLate == 1 -> stringResource(R.string.ws6e_wrap_one_day_overdue)
                        else -> stringResource(R.string.ws6e_wrap_days_overdue, daysLate)
                    }
                    Text(
                        "${stringResource(R.string.ws6_due)} ${Dates.shortUpper(it)}" + (late?.let { l -> " · $l" } ?: ""),
                        style = Ct.type.monoS, color = if (overdue) c.stop else c.textMuted,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            WrapChip(item, ctx.iOwe, overdue)
        }
        // Pay states the number that matters: what was agreed on the accepted card (full width, it's long).
        if (item.kind == WrapKind.PAID) ctx.terms?.let(BookingCopy::payOwed)?.let {
            Spacer(Modifier.height(8.dp))
            val settledPay = item.status == WrapStatus.DONE || item.status == WrapStatus.CONFIRMED
            Text(stringResource(if (settledPay) R.string.ws6e_wrap_pay_agreed else R.string.ws6e_wrap_pay_owed, it), style = Ct.type.mono, color = c.text)
        }
        if (item.kind == WrapKind.FOOTAGE && ctx.footageDefinition != null && (item.status == WrapStatus.OPEN || item.status == WrapStatus.NOT_RECEIVED)) {
            Spacer(Modifier.height(8.dp))
            Text(ctx.footageDefinition, style = Ct.type.label, color = c.textMuted)
        }
        item.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text("“$it”", style = Ct.type.bodyS, color = c.textSecondary)
        }
        item.link?.let { url ->
            GhostButton(stringResource(R.string.ws6_open_link), { nav.openUrl(url) }, Modifier.offset(x = (-16).dp), color = c.info)
        }
        if (item.autoConfirmed) {
            Text(stringResource(R.string.ws6_wrap_auto_confirmed), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
        }
        // Overdue and owed to the viewer: never a dead end. Remind (once a week), open the chat, or report it.
        val owedOverdue = !ctx.iOwe && overdue
        val remindPrefix = stringResource(R.string.ws6e_remind_prefix)
        val remindPhrase = stringResource(
            when (item.kind) {
                WrapKind.PAID -> R.string.ws6e_remind_paid
                WrapKind.FOOTAGE -> R.string.ws6e_remind_footage
                WrapKind.FINAL_FILM -> R.string.ws6e_remind_final_film
                WrapKind.CREDIT -> R.string.ws6e_remind_credit
                WrapKind.DELIVERY -> R.string.ws6e_remind_delivery
            },
            ctx.projectTitle,
        )
        val marker = "$remindPrefix $remindPhrase"
        val lastReminder = if (owedOverdue) ctx.mine.filter { it.body.startsWith(marker) }.maxOfOrNull { it.createdAt } else null
        val canRemind = lastReminder == null || AppClock.nowMillis() - lastReminder >= BookingCopy.REMIND_EVERY_MS
        val waiting = when {
            owedOverdue && lastReminder != null -> stringResource(
                R.string.ws6e_wrap_reminded, Dates.shortUpper(localDateOf(lastReminder)),
                Dates.shortUpper(localDateOf(lastReminder + BookingCopy.REMIND_EVERY_MS)),
            )
            item.status == WrapStatus.CONFIRMED -> item.confirmedAt?.let { stringResource(R.string.ws6_wrap_confirmed_on, Dates.shortUpper(localDateOf(it))) }
            ctx.iOwe && item.status == WrapStatus.DONE -> stringResource(R.string.ws6_wrap_waiting_confirm, ctx.otherFirst)
            ctx.iOwe && item.status == WrapStatus.NOT_RECEIVED -> stringResource(R.string.ws6_wrap_not_received_owe, ctx.otherFirst)
            !ctx.iOwe && item.status == WrapStatus.OPEN && overdue -> stringResource(R.string.ws6_wrap_overdue_nudged, ctx.otherFirst)
            !ctx.iOwe && item.status == WrapStatus.OPEN -> stringResource(R.string.ws6_wrap_waiting_mark, ctx.otherFirst)
            !ctx.iOwe && item.status == WrapStatus.NOT_RECEIVED -> stringResource(R.string.ws6_wrap_not_received_wait, ctx.otherFirst)
            else -> null
        }
        if (waiting != null) {
            Spacer(Modifier.height(8.dp))
            Text(waiting, style = if (item.status == WrapStatus.CONFIRMED) Ct.type.monoS else Ct.type.label,
                color = if (item.status == WrapStatus.CONFIRMED) c.go else c.textSecondary)
        }
        if (owedOverdue) {
            val record = when (item.kind) {
                WrapKind.PAID -> R.string.ws6e_wrap_record_paid
                WrapKind.FOOTAGE -> R.string.ws6e_wrap_record_footage
                WrapKind.DELIVERY -> R.string.ws6e_wrap_record_delivery
                else -> null
            }
            if (record != null) {
                Text(stringResource(R.string.ws6e_wrap_counts_record, ctx.otherFirst, stringResource(record)), style = Ct.type.label, color = c.textMuted,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            val body = "$remindPrefix " + stringResource(R.string.ws6e_remind_body, remindPhrase, item.dueDate?.let { Dates.short(it) } ?: "")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(stringResource(R.string.ws6e_wrap_remind, ctx.otherFirst), { actions.onRemind(ctx, body) }, Modifier.weight(1f),
                    enabled = canRemind && !busy)
                if (!nested) GhostButton(stringResource(R.string.ws6_wrap_open_chat), { nav.toChat(item.matchId) })
            }
            if (ctx.report != null) {
                GhostButton(stringResource(R.string.ws6e_wrap_report), { actions.onReport(ctx) }, Modifier.offset(x = (-16).dp), color = c.stop)
            }
        }
        val canMark = ctx.iOwe && (item.status == WrapStatus.OPEN || item.status == WrapStatus.NOT_RECEIVED)
        val canConfirm = !ctx.iOwe && item.status == WrapStatus.DONE
        if (canMark || canConfirm) {
            Spacer(Modifier.height(12.dp))
            if (canMark) {
                PrimaryButton(
                    stringResource(if (item.kind == WrapKind.DELIVERY) R.string.ws6_wrap_mark_delivered else R.string.ws6_wrap_mark_done),
                    { onMark(ctx) }, Modifier.fillMaxWidth(), loading = busy,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(stringResource(R.string.ws6_wrap_not_received), { onConfirm(item, false) }, enabled = !busy)
                    PrimaryButton(stringResource(R.string.ws6_got_it), { onConfirm(item, true) }, Modifier.weight(1f), loading = busy)
                }
            }
        }
    }
}

/** Callbacks for the overdue actions on an item the viewer is owed. */
private class OverdueActions(val onRemind: (ItemCtx, String) -> Unit, val onReport: (ItemCtx) -> Unit)

/**
 * Wrap item chip that agrees with the "confirmed" counter: a marked-but-unconfirmed item reads
 * "MARKED PAID · CONFIRM?" (Tungsten) to the person owed and "MARKED DONE" to the one who owes it.
 */
@Composable
private fun WrapChip(item: WrapItem, iOwe: Boolean, overdue: Boolean) {
    if (item.status != WrapStatus.DONE) { StatusChip(item.status, overdue); return }
    val (res, tone) = when {
        iOwe -> R.string.ws6e_wrap_chip_marked to ChipTone.INFO
        item.kind == WrapKind.PAID -> R.string.ws6e_wrap_chip_paid_confirm to ChipTone.ACCENT
        item.kind == WrapKind.DELIVERY -> R.string.ws6e_wrap_chip_delivered_confirm to ChipTone.ACCENT
        item.kind == WrapKind.CREDIT -> R.string.ws6e_wrap_chip_done_confirm to ChipTone.ACCENT
        else -> R.string.ws6e_wrap_chip_sent_confirm to ChipTone.ACCENT
    }
    StatusChip(stringResource(res), tone)
}

/**
 * [Mark done]: FOOTAGE, FINAL_FILM and DELIVERY need a link; PAID takes an optional "paid via" note;
 * CREDIT takes an optional link (a festival or credits page).
 */
@Composable
private fun MarkDoneSheet(ctx: ItemCtx, busy: Boolean, onDismiss: () -> Unit, onSend: (String?, String?, (String?) -> Unit) -> Unit) {
    val c = Ct.colors
    val kind = ctx.item.kind
    val needsLink = kind == WrapKind.FOOTAGE || kind == WrapKind.FINAL_FILM || kind == WrapKind.DELIVERY
    var link by rememberSaveable { mutableStateOf(ctx.item.link ?: "") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val badLink = stringResource(R.string.ws6_tape_bad_link)
    val title = when (kind) {
        WrapKind.PAID -> stringResource(R.string.ws6_mark_paid_title)
        WrapKind.FOOTAGE -> stringResource(R.string.ws6_mark_footage_title)
        WrapKind.FINAL_FILM -> stringResource(R.string.ws6_mark_final_title)
        WrapKind.CREDIT -> stringResource(R.string.ws6_mark_credit_title)
        WrapKind.DELIVERY -> stringResource(R.string.ws6_mark_delivery_title)
    }
    CtBottomSheet(onDismiss = onDismiss, title = title, subtitle = stringResource(R.string.ws6_mark_subtitle, ctx.otherFirst)) {
        if (kind == WrapKind.FOOTAGE && ctx.footageDefinition != null) {
            Text(ctx.footageDefinition, style = Ct.type.bodyS, color = c.textSecondary)
            Spacer(Modifier.height(14.dp))
        }
        if (kind == WrapKind.PAID) {
            Text(stringResource(R.string.ws6_mark_paid_via), style = Ct.type.bodyS, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            val options = listOf(stringResource(R.string.ws6_paid_upi), stringResource(R.string.ws6_paid_bank), stringResource(R.string.ws6_paid_cash))
            ChipFlow { options.forEach { o -> CtFilterChip(o, note == o, onClick = { note = if (note == o) "" else o }) } }
            Spacer(Modifier.height(14.dp))
        }
        if (kind != WrapKind.PAID) {
            CtTextField(link, { link = it; error = null },
                stringResource(if (needsLink) R.string.ws6_mark_link_required else R.string.ws6_mark_link_optional),
                placeholder = "https://", singleLine = true, errorText = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = stringResource(if (kind == WrapKind.DELIVERY) R.string.ws6_mark_delivery_hint else R.string.ws6_mark_link_hint))
            Spacer(Modifier.height(14.dp))
        }
        CtTextField(note, { note = it.take(200) }, stringResource(R.string.ws6_mark_note), maxLength = 200,
            placeholder = stringResource(if (kind == WrapKind.PAID) R.string.ws6_mark_note_paid_hint else R.string.ws6_mark_note_hint))
        if (kind == WrapKind.PAID) error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Ct.type.bodyS, color = c.stop)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            stringResource(if (kind == WrapKind.DELIVERY) R.string.ws6_wrap_mark_delivered else R.string.ws6_wrap_mark_done),
            {
                val l = link.trim().ifEmpty { null }
                when {
                    needsLink && (l == null || !LINK_RX.containsMatchIn(l)) -> error = badLink
                    !needsLink && l != null && !LINK_RX.containsMatchIn(l) -> error = badLink
                    else -> onSend(l, note.trim().ifEmpty { null }) { e -> error = e }
                }
            },
            Modifier.fillMaxWidth(), loading = busy,
        )
    }
}
