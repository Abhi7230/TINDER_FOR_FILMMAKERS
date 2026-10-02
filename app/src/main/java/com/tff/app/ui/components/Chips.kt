package com.tff.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.domain.ReputationCalc
import com.tff.app.model.ApplicationStatus
import com.tff.app.model.MatchState
import com.tff.app.model.Profile
import com.tff.app.model.ProjectStatus
import com.tff.app.model.Reputation
import com.tff.app.model.RoleStatus
import com.tff.app.model.WrapStatus
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.launch

/** Semantic chip colours. NEUTRAL for facts, ACCENT for Tungsten highlights, GO/STOP for good/bad, INFO for tips. */
enum class ChipTone { NEUTRAL, ACCENT, GO, STOP, INFO, WARN }

internal data class ToneColors(val fg: Color, val border: Color, val fill: Color, val filledFg: Color)

@Composable
internal fun toneColors(tone: ChipTone): ToneColors {
    val c = Ct.colors
    return when (tone) {
        ChipTone.NEUTRAL -> ToneColors(c.textSecondary, c.outline, c.surfaceHigh, c.text)
        ChipTone.ACCENT -> ToneColors(c.accent, c.accent.copy(alpha = 0.5f), c.accent.copy(alpha = 0.14f), c.accent)
        ChipTone.GO -> ToneColors(c.go, c.go.copy(alpha = 0.45f), c.go.copy(alpha = 0.13f), c.go)
        ChipTone.STOP -> ToneColors(c.stop, c.stop.copy(alpha = 0.5f), c.dangerContainer, c.onDangerContainer)
        ChipTone.INFO -> ToneColors(c.info, c.info.copy(alpha = 0.45f), c.infoContainer, c.onInfoContainer)
        ChipTone.WARN -> ToneColors(c.onWarnContainer, c.onWarnContainer.copy(alpha = 0.4f), c.warnContainer, c.onWarnContainer)
    }
}

/** Chip text styles: [CAPS] = Barlow slate caps (badges, statuses), [MONO] = timecode, [BODY] = Inter label. */
enum class ChipStyle { BODY, CAPS, MONO }

@Composable
private fun chipTextStyle(style: ChipStyle): TextStyle = when (style) {
    ChipStyle.BODY -> Ct.type.label.copy(fontSize = 13.sp, lineHeight = 16.sp)
    ChipStyle.CAPS -> Ct.type.slate.copy(fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)
    ChipStyle.MONO -> Ct.type.monoS
}

/**
 * The one chip. Outline by default, [filled] for tinted backgrounds. Non-interactive chips are 28dp;
 * pass [onClick] and the chip gets a 48dp touch target. [monoNumbers] sets digits and ratios in mono
 * ("Showed up 9/9"). TalkBack reads [contentDescription] or the text with ratios spoken ("9 of 9").
 */
@Composable
fun CtChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.NEUTRAL,
    filled: Boolean = false,
    style: ChipStyle = ChipStyle.BODY,
    icon: ImageVector? = null,
    monoNumbers: Boolean = false,
    contentDescription: String? = null,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val t = toneColors(tone)
    val ts = chipTextStyle(style).let { if (small) it.copy(fontSize = it.fontSize * 0.92f, lineHeight = 15.sp) else it }
    val fg = if (filled) t.filledFg else t.fg
    val cd = contentDescription ?: spokenRatios(text)
    Box(
        modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .heightIn(min = if (small) 24.dp else 28.dp)
            .clip(CtShapes.chip)
            .background(if (filled) t.fill else Color.Transparent)
            .border(1.dp, if (filled) t.border.copy(alpha = t.border.alpha * 0.6f) else t.border, CtShapes.chip)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics { this.contentDescription = cd; if (onClick != null) role = Role.Button }
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (small) 13.dp else 14.dp))
                Spacer(Modifier.width(if (small) 4.dp else 5.dp))
            }
            val label = if (monoNumbers) monoNumbers(text, Ct.type.mono) else AnnotatedString(text)
            Text(label, style = ts, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Selectable chip for pickers (MultiChipPicker, filters). 40dp visual, 48dp touch target, check when selected. */
@Composable
fun CtFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Ct.colors
    val bg by animateColorAsState(if (selected) c.accent.copy(alpha = 0.16f) else c.surfaceRaised, label = "chipBg")
    val border by animateColorAsState(if (selected) c.accent else c.outline, label = "chipBorder")
    val fg = when { !enabled -> c.textMuted; selected -> c.accent; else -> c.text }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .clip(CtShapes.chip)
            .background(bg)
            .border(1.dp, border, CtShapes.chip)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val lead = if (selected) Icons.Rounded.Check else icon
            if (lead != null) {
                Icon(lead, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = Ct.type.bodyS.copy(fontWeight = Ct.type.bodyStrong.fontWeight), color = fg, maxLines = 1)
        }
    }
}

/** Wrapping chip layout (FlowRow) with 8dp gaps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    lineSpacing: Dp = 8.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(lineSpacing),
        content = content,
    )
}

/**
 * Chips laid out on at most [maxLines] lines; whatever doesn't fit collapses into a "+2" chip at the
 * end of the last line (PRD §12.2: the card's credibility row at large font scales). [items] are
 * composables, laid out in order.
 */
@Composable
fun OverflowChipRow(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
    maxLines: Int = 1,
    lineSpacing: Dp = 6.dp,
    small: Boolean = false,
) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = spacing.roundToPx()
        val lineGap = lineSpacing.roundToPx()
        val placeables = items.mapIndexed { i, item -> subcompose("chip$i", item).map { it.measure(loose) } }
        val widths = placeables.map { ps -> ps.maxOfOrNull { it.width } ?: 0 }
        val maxW = constraints.maxWidth
        val lines = maxLines.coerceAtLeast(1)

        /** Greedy line fill; [reserve] px are kept free at the end of the last line. Returns line → indices. */
        fun fill(reserve: Int): List<List<Int>> {
            val out = mutableListOf<MutableList<Int>>(mutableListOf())
            var used = 0
            for (i in widths.indices) {
                val w = widths[i]
                val last = out.size == lines
                val limit = maxW - if (last) reserve else 0
                val need = (if (out.last().isEmpty()) 0 else gap) + w
                if (used + need <= limit || (out.last().isEmpty() && !last && w > maxW)) {
                    out.last().add(i); used += need; continue
                }
                if (last) break
                out.add(mutableListOf())
                used = 0
                val limit2 = maxW - if (out.size == lines) reserve else 0
                if (w <= limit2) { out.last().add(i); used = w } else break
            }
            return out.filter { it.isNotEmpty() }
        }

        var rows = fill(0)
        var shown = rows.sumOf { it.size }
        var more: List<Placeable> = emptyList()
        if (shown < items.size) {
            val probe = subcompose("moreProbe") { OverflowCountChip(items.size, small) }.map { it.measure(loose) }
            rows = fill((probe.maxOfOrNull { it.width } ?: 0) + gap)
            shown = rows.sumOf { it.size }
            more = subcompose("more") { OverflowCountChip(items.size - shown, small) }.map { it.measure(loose) }
        }
        val lineHeights = rows.mapIndexed { li, idx ->
            val h = idx.maxOfOrNull { i -> placeables[i].maxOfOrNull { it.height } ?: 0 } ?: 0
            if (li == rows.lastIndex) maxOf(h, more.maxOfOrNull { it.height } ?: 0) else h
        }.ifEmpty { listOf(more.maxOfOrNull { it.height } ?: 0) }
        val height = lineHeights.sum() + lineGap * (lineHeights.size - 1).coerceAtLeast(0)
        val lineWidths = rows.map { idx -> idx.sumOf { widths[it] } + gap * (idx.size - 1).coerceAtLeast(0) }.toMutableList()
        if (more.isNotEmpty()) {
            val mw = more.maxOf { it.width }
            if (lineWidths.isEmpty()) lineWidths.add(mw) else lineWidths[lineWidths.lastIndex] += gap + mw
        }
        val width = lineWidths.maxOrNull() ?: 0
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            var y = 0
            val count = maxOf(rows.size, if (more.isNotEmpty()) 1 else 0)
            for (li in 0 until count) {
                val lh = lineHeights[li]
                var x = 0
                rows.getOrNull(li)?.forEach { i ->
                    placeables[i].forEach { it.placeRelative(x, y + (lh - it.height) / 2) }
                    x += widths[i] + gap
                }
                if (li == count - 1 && more.isNotEmpty()) more.forEach { it.placeRelative(x, y + (lh - it.height) / 2) }
                y += lh + lineGap
            }
        }
    }
}

@Composable
private fun OverflowCountChip(hidden: Int, small: Boolean) {
    CtChip(
        text = stringResource(R.string.ws1_more_chips, hidden), style = ChipStyle.MONO, small = small,
        contentDescription = stringResource(R.string.ws1_more_chips_cd, hidden),
    )
}

// ───────────────────────── Status ─────────────────────────

/** Uppercase state pill with a dot (IN TALKS, OFFER OUT, BOOKED…). Prefer the typed overloads below. */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val t = toneColors(tone)
    Row(
        modifier
            .heightIn(min = 26.dp)
            .clip(CtShapes.chip)
            .background(t.fill)
            .clearAndSetSemantics { contentDescription = text.lowercase().replaceFirstChar { it.uppercase() } }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(t.fg))
        Spacer(Modifier.width(6.dp))
        Text(text, style = chipTextStyle(ChipStyle.CAPS), color = t.filledFg, maxLines = 1)
    }
}

/** Match state chip for Inbox rows and chat headers (S14, S15). */
@Composable
fun StatusChip(state: MatchState, modifier: Modifier = Modifier) {
    val (res, tone) = when (state) {
        MatchState.TALKING -> R.string.ws1_status_in_talks to ChipTone.ACCENT
        MatchState.OFFERED -> R.string.ws1_status_offer_out to ChipTone.INFO
        MatchState.BOOKED -> R.string.ws1_status_booked to ChipTone.GO
        MatchState.WRAPPED -> R.string.ws1_status_wrapped to ChipTone.NEUTRAL
        MatchState.WITHDRAWN -> R.string.ws1_status_withdrawn to ChipTone.NEUTRAL
        MatchState.RELEASED -> R.string.ws1_status_released to ChipTone.NEUTRAL
        MatchState.NO_SHOW -> R.string.ws1_status_no_show to ChipTone.STOP
        MatchState.CLOSED -> R.string.ws1_status_closed to ChipTone.NEUTRAL
    }
    StatusChip(stringResource(res), tone, modifier)
}

/** Application status chip (My applications, Updates). */
@Composable
fun StatusChip(status: ApplicationStatus, modifier: Modifier = Modifier) {
    val (res, tone) = when (status) {
        ApplicationStatus.PENDING -> R.string.ws1_status_applied to ChipTone.NEUTRAL
        ApplicationStatus.MATCHED -> R.string.ws1_status_in_talks to ChipTone.ACCENT
        ApplicationStatus.BACKUP -> R.string.ws1_status_backup to ChipTone.INFO
        ApplicationStatus.DECLINED -> R.string.ws1_status_not_this_time to ChipTone.NEUTRAL
        ApplicationStatus.WITHDRAWN -> R.string.ws1_status_withdrawn to ChipTone.NEUTRAL
        ApplicationStatus.CLOSED -> R.string.ws1_status_closed to ChipTone.NEUTRAL
        ApplicationStatus.EXPIRED -> R.string.ws1_status_no_answer to ChipTone.WARN
    }
    StatusChip(stringResource(res), tone, modifier)
}

/** Wrap item chip (S17): OPEN, DONE, CONFIRMED, NOT RECEIVED, or OVERDUE when [overdue]. */
@Composable
fun StatusChip(status: WrapStatus, overdue: Boolean, modifier: Modifier = Modifier) {
    val (res, tone) = when {
        overdue && status == WrapStatus.OPEN -> R.string.ws1_status_overdue to ChipTone.STOP
        status == WrapStatus.OPEN -> R.string.ws1_status_open to ChipTone.NEUTRAL
        status == WrapStatus.DONE -> R.string.ws1_status_done to ChipTone.INFO
        status == WrapStatus.CONFIRMED -> R.string.ws1_status_confirmed to ChipTone.GO
        else -> R.string.ws1_status_not_received to ChipTone.STOP
    }
    StatusChip(stringResource(res), tone, modifier)
}

/** Project status chip (S12, S13). OPEN reads "CREWING". */
@Composable
fun StatusChip(status: ProjectStatus, modifier: Modifier = Modifier) {
    val (res, tone) = when (status) {
        ProjectStatus.DRAFT -> R.string.ws1_status_draft to ChipTone.NEUTRAL
        ProjectStatus.OPEN -> R.string.ws1_status_crewing to ChipTone.ACCENT
        ProjectStatus.CREWED -> R.string.ws1_status_crewed to ChipTone.GO
        ProjectStatus.WRAPPED -> R.string.ws1_status_wrapped to ChipTone.NEUTRAL
        ProjectStatus.CANCELLED -> R.string.ws1_status_cancelled to ChipTone.STOP
    }
    StatusChip(stringResource(res), tone, modifier)
}

/** Role status chip (dashboard Roles tab). */
@Composable
fun StatusChip(status: RoleStatus, modifier: Modifier = Modifier) {
    val (res, tone) = when (status) {
        RoleStatus.OPEN -> R.string.ws1_status_open to ChipTone.ACCENT
        RoleStatus.FILLED -> R.string.ws1_status_filled to ChipTone.GO
        RoleStatus.CLOSED -> R.string.ws1_status_closed to ChipTone.NEUTRAL
    }
    StatusChip(stringResource(res), tone, modifier)
}

// ───────────────────────── Badges ─────────────────────────

/** Card-face badges next to the role label (§6.3). */
enum class CardBadge { STEP_UP_OK, BEGINNERS_WELCOME, REMOTE }

@Composable
fun BadgeChip(badge: CardBadge, modifier: Modifier = Modifier) {
    val (res, tone) = when (badge) {
        CardBadge.STEP_UP_OK -> R.string.ws1_badge_step_up to ChipTone.GO
        CardBadge.BEGINNERS_WELCOME -> R.string.ws1_badge_beginners to ChipTone.INFO
        CardBadge.REMOTE -> R.string.ws1_badge_remote to ChipTone.NEUTRAL
    }
    val text = stringResource(res)
    CtChip(text, modifier, tone = tone, style = ChipStyle.CAPS, contentDescription = text.lowercase().replaceFirstChar { it.uppercase() })
}

/**
 * Wraps [content] (usually a chip) so a tap shows [tip] in a tooltip. Used for the exact §5.1 badge
 * wording ("Email confirmed by code."). The tip is also part of the chip's TalkBack description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltip(tip: String, modifier: Modifier = Modifier, content: @Composable (show: () -> Unit) -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(containerColor = Ct.colors.surfaceHigh, contentColor = Ct.colors.text) {
                Text(tip, style = Ct.type.bodyS, color = Ct.colors.text, modifier = Modifier.padding(4.dp))
            }
        },
        state = state,
        modifier = modifier,
    ) {
        content { scope.launch { state.show() } }
    }
}

/**
 * Verification badges with the exact §5.1 tooltips. Tap a badge to read what it means. "ID verified"
 * is the only one that involves a person; none of them vouch for a project.
 */
@Composable
fun VerificationBadges(
    emailVerified: Boolean,
    phoneVerified: Boolean,
    idVerified: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val items = buildList {
        if (idVerified) add(Triple(R.string.ws1_badge_id, R.string.ws1_tip_id, Icons.Rounded.Badge))
        if (emailVerified) add(Triple(R.string.ws1_badge_email, R.string.ws1_tip_email, Icons.Rounded.MarkEmailRead))
        if (phoneVerified) add(Triple(R.string.ws1_badge_phone, R.string.ws1_tip_phone, Icons.Rounded.PhoneAndroid))
    }
    if (items.isEmpty()) return
    ChipFlow(modifier, spacing = 6.dp, lineSpacing = if (interactive) 0.dp else 6.dp) {
        items.forEach { (label, tip, icon) ->
            val l = stringResource(label)
            val t = stringResource(tip)
            val tone = if (label == R.string.ws1_badge_id) ChipTone.INFO else ChipTone.NEUTRAL
            if (interactive) {
                InfoTooltip(t) { show ->
                    CtChip(l, tone = tone, filled = true, icon = icon, contentDescription = "$l. $t", onClick = show)
                }
            } else {
                CtChip(l, tone = tone, filled = true, icon = icon, small = true, contentDescription = "$l. $t")
            }
        }
    }
}

/** Convenience overload for a profile. */
@Composable
fun VerificationBadges(profile: Profile, modifier: Modifier = Modifier, interactive: Boolean = true) =
    VerificationBadges(profile.emailVerified, profile.phoneVerified, profile.idVerified, modifier, interactive)

/**
 * Fact-only track record (F14) from [ReputationCalc.chips]. Numbers in mono; ratios below 75% are
 * shown in Safelight so a bad number is never hidden. "New on Calltime" explains itself on tap.
 * [headlineOnly] shows just the one card-face chip ([ReputationCalc.headlineChip]).
 */
@Composable
fun TrackRecordChips(
    reputation: Reputation,
    asOwner: Boolean,
    modifier: Modifier = Modifier,
    headlineOnly: Boolean = false,
    singleLine: Boolean = false,
) {
    val newLabel = stringResource(R.string.ws1_badge_new)
    val chips = if (headlineOnly) {
        listOf(safeText({ if (ReputationCalc.showsNew(reputation)) newLabel else "" }) { ReputationCalc.headlineChip(reputation, asOwner) })
    } else {
        safeList { ReputationCalc.chips(reputation, asOwner) }.ifEmpty { if (ReputationCalc.showsNew(reputation)) listOf(newLabel) else emptyList() }
    }.filter { it.isNotBlank() }
    if (chips.isEmpty()) return
    val render: List<@Composable () -> Unit> = chips.map { label -> { TrackRecordChip(label, newLabel) } }
    if (singleLine) OverflowChipRow(render, modifier)
    else ChipFlow(modifier, spacing = 6.dp, lineSpacing = 6.dp) { render.forEach { it() } }
}

/**
 * One track-record chip ("Kept bookings 6/6"); used on cards and in detail views. [interactive] lets
 * "New on Calltime" explain itself on tap (keep it off on card faces, where a tap opens the card).
 */
@Composable
fun TrackRecordChip(
    label: String,
    newLabel: String = stringResource(R.string.ws1_badge_new),
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    small: Boolean = false,
) {
    if (label == newLabel) {
        val tip = stringResource(R.string.ws1_tip_new)
        if (interactive) {
            InfoTooltip(tip, modifier) { show ->
                CtChip(label, filled = true, small = small, contentDescription = "$label. $tip", onClick = show)
            }
        } else {
            CtChip(label, modifier, filled = true, small = small, contentDescription = "$label. $tip")
        }
    } else {
        val r = ratioIn(label)
        val weak = r != null && r.second > 0 && r.first.toDouble() / r.second < 0.75
        CtChip(label, modifier, tone = if (weak) ChipTone.STOP else ChipTone.NEUTRAL, filled = true, monoNumbers = true, small = small)
    }
}
