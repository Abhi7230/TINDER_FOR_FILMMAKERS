package com.tff.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

private data class ButtonColors(val container: Color, val content: Color, val border: BorderStroke?)

/** Shared 52dp pill. Width is the caller's choice (pass `Modifier.fillMaxWidth()` for full-width CTAs). */
@Composable
private fun CtButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    icon: ImageVector?,
    colors: (pressed: Boolean, enabled: Boolean) -> ButtonColors,
    height: Dp = CtShapes.buttonHeight,
    shape: Shape = CtShapes.pill,
    horizontalPadding: Dp = 24.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val c = colors(pressed, enabled)
    val container by animateColorAsState(c.container, label = "btnContainer")
    val active = enabled && !loading
    Box(
        modifier
            .defaultMinSize(minWidth = 64.dp)
            .height(height)
            .clip(shape)
            .background(container)
            .then(if (c.border != null) Modifier.border(c.border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = c.content),
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.alpha(if (loading) 0f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = Ct.type.bodyStrong, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.content, strokeWidth = 2.dp, trackColor = Color.Transparent)
        }
    }
}

/** Primary CTA: Tungsten pill, 52dp tall, dark text. One per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val cs = Ct.colors
    CtButtonBase(text, onClick, modifier, enabled, loading, icon, colors = { pressed, en ->
        when {
            !en -> ButtonColors(cs.surfaceHigh, cs.textMuted, null)
            pressed -> ButtonColors(cs.accentPressed, cs.onAccent, null)
            else -> ButtonColors(cs.accent, cs.onAccent, null)
        }
    })
}

/** Secondary action: outlined pill in Paper. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val cs = Ct.colors
    CtButtonBase(text, onClick, modifier, enabled, loading, icon, colors = { pressed, en ->
        when {
            !en -> ButtonColors(Color.Transparent, cs.textMuted, BorderStroke(1.dp, cs.outline))
            pressed -> ButtonColors(cs.surfaceHigh, cs.text, BorderStroke(1.dp, cs.textMuted))
            else -> ButtonColors(Color.Transparent, cs.text, BorderStroke(1.dp, cs.textMuted.copy(alpha = 0.7f)))
        }
    })
}

/** Low-emphasis text button in Tungsten ("Not now", "See saved"). 48dp tall. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = Ct.colors.accent,
) {
    val cs = Ct.colors
    CtButtonBase(text, onClick, modifier, enabled, loading = false, icon = icon, height = 48.dp, horizontalPadding = 16.dp,
        colors = { pressed, en ->
            when {
                !en -> ButtonColors(Color.Transparent, cs.textMuted, null)
                pressed -> ButtonColors(cs.surfaceHigh, color, null)
                else -> ButtonColors(Color.Transparent, color, null)
            }
        })
}

/** Destructive action (Withdraw, Delete account, Block): Safelight on a danger container. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val cs = Ct.colors
    CtButtonBase(text, onClick, modifier, enabled, loading, icon, colors = { pressed, en ->
        when {
            !en -> ButtonColors(cs.surfaceHigh, cs.textMuted, null)
            pressed -> ButtonColors(cs.stop.copy(alpha = 0.32f), cs.onDangerContainer, BorderStroke(1.dp, cs.stop))
            else -> ButtonColors(cs.dangerContainer, cs.onDangerContainer, BorderStroke(1.dp, cs.stop.copy(alpha = 0.55f)))
        }
    })
}

// ───────────────────────── Deck actions ─────────────────────────

/** The round buttons under a swipe deck (S05 Gigs, S09 Crew up). Every swipe has one of these. */
enum class DeckAction { PASS, SAVE, INTERESTED, NOT_THIS_TIME, BACKUP, LETS_TALK, SKIP, SEND_ASK }

private val DeckAction.isMajor: Boolean get() = this != DeckAction.SAVE && this != DeckAction.BACKUP

/** Which swipe direction each action mirrors. */
val DeckAction.direction: SwipeDirection
    get() = when (this) {
        DeckAction.PASS, DeckAction.NOT_THIS_TIME, DeckAction.SKIP -> SwipeDirection.LEFT
        DeckAction.SAVE, DeckAction.BACKUP -> SwipeDirection.UP
        DeckAction.INTERESTED, DeckAction.LETS_TALK, DeckAction.SEND_ASK -> SwipeDirection.RIGHT
    }

@Composable
fun deckActionLabel(a: DeckAction): String = stringResource(
    when (a) {
        DeckAction.PASS -> R.string.ws1_action_pass
        DeckAction.SAVE -> R.string.ws1_action_save
        DeckAction.INTERESTED -> R.string.ws1_action_interested
        DeckAction.NOT_THIS_TIME -> R.string.ws1_action_not_this_time
        DeckAction.BACKUP -> R.string.ws1_action_backup
        DeckAction.LETS_TALK -> R.string.ws1_action_lets_talk
        DeckAction.SKIP -> R.string.ws1_action_skip
        DeckAction.SEND_ASK -> R.string.ws1_action_send_ask
    }
)

/**
 * One round deck button with its label underneath. Pass/Not this time/Skip = ✕ in Safelight;
 * Interested/Let's talk/Send ask = ✓ on Greenlit; Save/Backup = bookmark. [emphasis] (0..1, e.g. the
 * deck's drag progress toward this action) grows and lights the button while a card is dragged.
 */
@Composable
fun DeckActionButton(
    action: DeckAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    emphasis: Float = 0f,
) {
    val cs = Ct.colors
    val label = deckActionLabel(action)
    val size = if (action.isMajor) 64.dp else 52.dp
    val icon = when (action.direction) {
        SwipeDirection.LEFT -> Icons.Rounded.Close
        SwipeDirection.UP -> Icons.Rounded.BookmarkBorder
        SwipeDirection.RIGHT -> if (action == DeckAction.SEND_ASK) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Check
    }
    val e by animateFloatAsState(emphasis.coerceIn(0f, 1f), label = "deckEmphasis")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (container, content, border) = when (action.direction) {
        SwipeDirection.RIGHT -> Triple(if (pressed) cs.go.copy(alpha = 0.8f) else cs.go, cs.bg, null)
        SwipeDirection.LEFT -> Triple(
            cs.stop.copy(alpha = if (pressed) 0.28f else 0.08f + 0.22f * e), cs.stop,
            BorderStroke(2.dp, cs.stop.copy(alpha = 0.75f)),
        )
        SwipeDirection.UP -> Triple(
            if (pressed) cs.surfaceHigh else cs.surfaceRaised.copy(alpha = 1f), if (e > 0.5f) cs.accent else cs.text,
            BorderStroke(1.5.dp, if (e > 0.01f) cs.accent.copy(alpha = 0.4f + 0.6f * e) else cs.outline),
        )
    }
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer { val s = 1f + 0.12f * e; scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.4f }
                .size(size)
                .clip(CircleShape)
                .background(container)
                .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
                .clickable(interactionSource = interaction, indication = ripple(color = content), enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (action.isMajor) 30.dp else 24.dp))
        }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                label.uppercase(),
                style = Ct.type.slate.copy(fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
                color = if (enabled) cs.textSecondary else cs.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * The standard row of deck buttons, e.g. `listOf(PASS, SAVE, INTERESTED)` for Gigs or
 * `listOf(NOT_THIS_TIME, BACKUP, LETS_TALK)` for Applicants. Pass the deck state to light the buttons
 * as a card is dragged toward them.
 */
@Composable
fun DeckActionBar(
    actions: List<DeckAction>,
    onAction: (DeckAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SwipeDeckState<*>? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { a ->
            val emphasis = when (a.direction) {
                SwipeDirection.RIGHT -> state?.horizontalProgress?.coerceAtLeast(0f) ?: 0f
                SwipeDirection.LEFT -> -(state?.horizontalProgress?.coerceAtMost(0f) ?: 0f)
                SwipeDirection.UP -> state?.upProgress ?: 0f
            }
            DeckActionButton(a, onClick = { onAction(a) }, enabled = enabled, emphasis = emphasis)
        }
    }
}
