package com.tff.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.domain.BannerLevel
import com.tff.app.domain.SafetyBannerSpec
import com.tff.app.model.TeamMember
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import java.time.LocalTime

/** Slate-style section label ("WHAT YOU GET") with an optional count and a trailing text action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).clip(CircleShape).background(Ct.colors.accent))
        Spacer(Modifier.width(10.dp))
        Text(
            title.uppercase(),
            style = Ct.type.slate.copy(fontSize = 15.sp, lineHeight = 18.sp),
            color = Ct.colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = Ct.type.monoS, color = Ct.colors.textMuted)
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) GhostButton(action, onAction)
    }
}

/** Label/value row for detail screens ("Hours cap" · "≤10h"). [mono] for dates, times and amounts. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    valueColor: Color = Ct.colors.text,
    icon: ImageVector? = null,
    divider: Boolean = false,
) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Ct.colors.textMuted, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(label, style = Ct.type.bodyS, color = Ct.colors.textSecondary, modifier = Modifier.weight(0.42f).padding(top = 1.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (mono) monoNumbers(value, Ct.type.mono) else androidx.compose.ui.text.AnnotatedString(value),
                style = if (mono) Ct.type.mono else Ct.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.58f),
            )
        }
        if (divider) HorizontalDivider(color = Ct.colors.outline.copy(alpha = 0.6f), thickness = 1.dp)
    }
}

/** Contextual safety or how-it-works tip (PRD §5.6) on the info container. Short, never a wall of text. */
@Composable
fun InfoTipCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Rounded.Lightbulb,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val c = Ct.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(CtShapes.field)
            .background(c.infoContainer)
            .border(1.dp, c.info.copy(alpha = 0.22f), CtShapes.field)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = if (onDismiss != null) 4.dp else 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.info, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = Ct.type.bodyStrong.copy(fontSize = 15.sp, lineHeight = 20.sp), color = c.text)
                Spacer(Modifier.height(4.dp))
            }
            Text(text, style = Ct.type.bodyS, color = c.onInfoContainer)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                GhostButton(actionLabel, onAction, color = c.info, modifier = Modifier.offset(x = (-16).dp))
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.offset(y = (-10).dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ws1_dismiss), tint = c.onInfoContainer, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Chat safety banner (PRD §5.2) from [MessageSafety.bannersFor]: DANGER (money, body requests) in the
 * danger container with [Report] [Block]; WARN (private meetings) amber; INFO (off-platform) blue.
 */
@Composable
fun SafetyBanner(
    spec: SafetyBannerSpec,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
    onLearnMore: (() -> Unit)? = null,
) {
    val c = Ct.colors
    val (bg, fg, accent, icon) = when (spec.level) {
        BannerLevel.DANGER -> Quad(c.dangerContainer, c.onDangerContainer, c.stop, Icons.Rounded.Report)
        BannerLevel.WARN -> Quad(c.warnContainer, c.onWarnContainer, c.onWarnContainer, Icons.Rounded.WarningAmber)
        BannerLevel.INFO -> Quad(c.infoContainer, c.onInfoContainer, c.info, Icons.Rounded.Shield)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(CtShapes.field)
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.35f), CtShapes.field)
            .padding(14.dp),
    ) {
        Row {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(spec.text, style = Ct.type.bodyS.copy(fontWeight = Ct.type.bodyStrong.fontWeight), color = fg, modifier = Modifier.weight(1f))
        }
        val showLearn = onLearnMore != null && spec.level == BannerLevel.INFO
        if (spec.offerReport || spec.offerBlock || showLearn) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (spec.offerReport) BannerButton(stringResource(R.string.ws1_safety_report), fg, onReport)
                if (spec.offerBlock) BannerButton(stringResource(R.string.ws1_safety_block), fg, onBlock)
                if (showLearn) BannerButton(stringResource(R.string.ws1_learn_why), fg, onLearnMore!!)
            }
        }
    }
}

private data class Quad(val bg: Color, val fg: Color, val accent: Color, val icon: ImageVector)

@Composable
private fun BannerButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .height(36.dp)
            .clip(CtShapes.pill)
            .border(1.dp, color.copy(alpha = 0.6f), CtShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ct.type.bodyS.copy(fontWeight = Ct.type.bodyStrong.fontWeight), color = color)
    }
}

/** Initials monogram ("MK"). No profile photos in the MVP. */
@Composable
fun Monogram(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    highlight: Boolean = false,
    ring: Color = Ct.colors.bg,
) {
    val c = Ct.colors
    val fs = with(LocalDensity.current) { (size * 0.42f).toSp() }
    val border by animateColorAsState(if (highlight) c.accent else c.outline, label = "monoRing")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ring)
            .padding(if (size >= 32.dp) 1.5.dp else 1.dp)
            .clip(CircleShape)
            .background(c.surfaceHigh)
            .border(1.dp, border, CircleShape)
            .clearAndSetSemantics { contentDescription = initials },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials.take(2).uppercase(),
            style = Ct.type.slate.copy(fontSize = fs, lineHeight = fs, letterSpacing = 0.5.sp),
            color = if (highlight) c.accent else c.text,
            maxLines = 1,
        )
    }
}

/** Team attached: overlapping monograms plus "DP · Sound attached" (§6.2 team_attached). */
@Composable
fun TeamMonograms(
    team: List<TeamMember>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    max: Int = 3,
) {
    if (team.isEmpty()) return
    val size = if (compact) 22.dp else 32.dp
    val roles = team.map { shortRoleLabel(it.roleCode) }.distinct()
    val rolesText = roles.take(2).joinToString(" · ") + if (roles.size > 2) " +${roles.size - 2}" else ""
    val text = stringResource(R.string.ws1_attached, rolesText)
    Row(
        modifier.heightIn(min = if (compact) 28.dp else 40.dp).clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            team.take(max).forEachIndexed { i, m ->
                Monogram(m.initials, Modifier.offset(x = (size - 7.dp) * i), size = size, ring = if (compact) Ct.colors.surface else Ct.colors.bg)
            }
        }
        val shown = team.take(max).size
        Spacer(Modifier.width((size - 7.dp) * (shown - 1) + 7.dp))
        Text(text, style = if (compact) Ct.type.label.copy(fontSize = 13.sp) else Ct.type.bodyS, color = Ct.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mono "timecode" text for dates, call times, rates and counters ("SAT 14 NOV · 07:30", "18 left today"). */
@Composable
fun TimecodeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Ct.type.mono,
    color: Color = Ct.colors.text,
    maxLines: Int = 1,
) {
    Text(text, modifier.semantics { contentDescription = spokenRatios(text).lowercase() }, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** A call time in big mono ("07:30") with a slate label ("YOUR CALL"), for day sheets and the Booked moment. */
@Composable
fun CallTimeDisplay(label: String, time: LocalTime, modifier: Modifier = Modifier, caption: String? = null) {
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(label.uppercase(), style = Ct.type.slate, color = Ct.colors.accent)
        TimecodeText(Dates.time(time), style = Ct.type.monoL.copy(fontSize = 56.sp, lineHeight = 60.sp), color = Ct.colors.text)
        if (caption != null) TimecodeText(caption, style = Ct.type.monoS, color = Ct.colors.textSecondary)
    }
}

/**
 * Wizard progress (S04 onboarding: 3 segments; S11 post wizard: 5 + review). [current] is 1-based.
 * Done and current segments are Tungsten.
 */
@Composable
fun StepHeader(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    title: String? = null,
    stepLabel: String? = stringResource(R.string.ws1_step_of, current, total),
) {
    val c = Ct.colors
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = listOfNotNull(stepLabel?.lowercase(), title).joinToString(". ") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..total) {
                val color by animateColorAsState(if (i <= current) c.accent else c.outline, label = "step$i")
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
            }
        }
        if (stepLabel != null) {
            Spacer(Modifier.height(12.dp))
            Text(stepLabel, style = Ct.type.monoS, color = c.textMuted)
        }
        if (title != null) {
            Spacer(Modifier.height(4.dp))
            Text(title, style = Ct.type.titleL, color = c.text)
        }
    }
}

/** Film sprocket-hole strip (§12.3): Day Sheet headers and the Booked overlay. */
@Composable
fun SprocketDivider(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    strip: Color = Ct.colors.surfaceRaised,
    hole: Color = Ct.colors.bg,
) {
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        drawRect(strip)
        val hh = size.height * 0.46f
        val hw = hh * 1.35f
        val pitch = hw * 2.1f
        val count = (size.width / pitch).toInt().coerceAtLeast(1)
        val start = (size.width - (count - 1) * pitch - hw) / 2f
        val r = CornerRadius(hh * 0.28f)
        for (i in 0 until count) {
            drawRoundRect(hole, topLeft = Offset(start + i * pitch, (size.height - hh) / 2f), size = Size(hw, hh), cornerRadius = r)
        }
    }
}
