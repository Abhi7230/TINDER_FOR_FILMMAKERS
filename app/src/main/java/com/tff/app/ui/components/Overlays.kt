package com.tff.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtMotion
import com.tff.app.ui.theme.filmGrainBehind
import com.tff.app.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/**
 * "IN TALKS" moment (PRD §3.3, §12.4): a clapperboard whose two bars clap closed in 180 ms, the title
 * in Barlow and a subtitle ("Rohan K. · Sound Mixer · Filter Coffee, 4 AM"). LongPress haptic on the
 * clap. Without [actions] it auto-dismisses after 1.6 s; tap anywhere dismisses. Never "It's a match".
 * Place it last in a full-screen Box so it covers the screen.
 */
@Composable
fun SlateOverlay(
    visible: Boolean,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ws1_in_talks),
    autoDismiss: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val reduce = rememberReduceMotion()
    val dismiss by rememberUpdatedState(onDismiss)
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(140)), exit = fadeOut(tween(200))) {
        val haptic = LocalHapticFeedback.current
        val clap = remember { Animatable(if (reduce) 0f else 1f) }   // 1 = open, 0 = closed
        val board = remember { Animatable(if (reduce) 1f else 0.94f) }
        LaunchedEffect(Unit) {
            if (!reduce) {
                board.animateTo(1f, tween(140, easing = CtMotion.emphasized))
                clap.animateTo(0f, tween(CtMotion.SLATE_CLAP_MS, easing = FastOutLinearInEasing))
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (autoDismiss && actions == null) {
                delay(CtMotion.OVERLAY_AUTO_DISMISS_MS)
                dismiss()
            }
        }
        OverlayScrim(onTap = { dismiss() }, showHint = actions == null) {
            Column(
                Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer { scaleX = board.value; scaleY = board.value }
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = "$title. $subtitle" },
            ) {
                Clapper(openFraction = clap.value)
                SlateBoard(title, subtitle)
            }
            if (actions != null) {
                Spacer(Modifier.height(28.dp))
                Row(Modifier.padding(horizontal = 28.dp).widthIn(max = 340.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
            }
        }
    }
}

/** The two striped clapper bars. The top bar hinges at the left; [openFraction] 1 = open (−26°). */
@Composable
private fun Clapper(openFraction: Float) {
    val c = Ct.colors
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        // Bottom (fixed) bar
        StripedBar(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(30.dp), offsetPhase = 0.5f)
        // Top (moving) bar, hinged at its bottom-left corner
        StripedBar(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .height(30.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 1f)
                    rotationZ = -26f * openFraction
                },
            offsetPhase = 0f,
        )
        // Hinge pin
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 26.dp)
                .width(10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.textMuted)
        )
    }
}

@Composable
private fun StripedBar(modifier: Modifier, offsetPhase: Float) {
    val c = Ct.colors
    Canvas(modifier.clip(RoundedCornerShape(4.dp))) {
        drawRect(c.bg)
        val stripe = size.height * 1.1f
        val path = Path()
        var x = -size.height + stripe * offsetPhase * 2
        clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }) {
            while (x < size.width + size.height) {
                path.reset()
                path.moveTo(x, size.height)
                path.lineTo(x + size.height * 0.8f, 0f)
                path.lineTo(x + size.height * 0.8f + stripe, 0f)
                path.lineTo(x + stripe, size.height)
                path.close()
                drawPath(path, c.text)
                x += stripe * 2
            }
        }
    }
}

@Composable
private fun SlateBoard(title: String, subtitle: String) {
    val c = Ct.colors
    val shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp, topStart = 2.dp, topEnd = 2.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.5.dp, c.text.copy(alpha = 0.85f), shape)
            .filmGrainBehind(0.03f)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SCENE 1", style = Ct.type.monoS, color = c.textMuted)
            Text("TAKE 1", style = Ct.type.monoS, color = c.textMuted)
            Text(Dates.shortUpper(AppClock.today()), style = Ct.type.monoS, color = c.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.text.copy(alpha = 0.25f)))
        Spacer(Modifier.height(14.dp))
        Text(title, style = Ct.type.displayL.copy(fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = 3.sp), color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = Ct.type.bodyS, color = c.textSecondary)
    }
}

/**
 * "BOOKED" moment (PRD §12.4, S16): a Greenlit stamp lands from 1.3× to 1.0× with −4° rotation in
 * 240 ms (LongPress haptic), then [callLine] types on in mono ("CALL SAT 14 NOV · 07:30", or
 * "DELIVER BY 7 FEB" for post roles), over a sprocket strip. Tap dismisses; set [autoDismissMillis]
 * to dismiss on a timer. Reduce motion: a simple fade.
 */
@Composable
fun BookedOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    callLine: String? = null,
    subtitle: String? = null,
    autoDismissMillis: Long? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val reduce = rememberReduceMotion()
    val dismiss by rememberUpdatedState(onDismiss)
    val bookedLabel = stringResource(R.string.ws1_booked)
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(140)), exit = fadeOut(tween(200))) {
        val haptic = LocalHapticFeedback.current
        val land = remember { Animatable(if (reduce) 1f else 0f) }
        var typed by remember { mutableIntStateOf(if (reduce) callLine?.length ?: 0 else 0) }
        LaunchedEffect(Unit) {
            if (!reduce) land.animateTo(1f, tween(CtMotion.STAMP_MS, easing = CtMotion.emphasized))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (!reduce && callLine != null) {
                delay(120)
                while (typed < callLine.length) { delay(CtMotion.TYPE_ON_CHAR_MS); typed++ }
            }
            autoDismissMillis?.let { delay(it); dismiss() }
        }
        val c = Ct.colors
        Box(Modifier.fillMaxSize()) {
            OverlayScrim(onTap = { dismiss() }, showHint = actions == null) {
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = listOfNotNull(bookedLabel, subtitle, callLine).joinToString(". ")
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val s = CtMotion.STAMP_START_SCALE + (1f - CtMotion.STAMP_START_SCALE) * land.value
                    Text(
                        bookedLabel,
                        style = Ct.type.displayL.copy(fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = 6.sp),
                        color = c.go,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = s; scaleY = s
                                rotationZ = CtMotion.STAMP_ROTATION_DEG * land.value
                                alpha = (land.value * 2.5f).coerceAtMost(1f)
                            }
                            .border(4.dp, c.go, RoundedCornerShape(12.dp))
                            .background(c.go.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 22.dp, vertical = 2.dp),
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(22.dp))
                        Text(subtitle, style = Ct.type.bodyS, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    }
                    if (callLine != null) {
                        Spacer(Modifier.height(14.dp))
                        val shown = callLine.take(typed)
                        Text(
                            shown + if (typed < callLine.length) "▌" else "",
                            style = Ct.type.mono.copy(fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 1.sp),
                            color = c.text,
                        )
                    }
                }
                if (actions != null) {
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.padding(horizontal = 28.dp).widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
                }
            }
            SprocketDivider(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp), height = 22.dp)
        }
    }
}

/** Full-screen darkroom scrim with grain; content centred; tap anywhere to dismiss. */
@Composable
private fun OverlayScrim(onTap: () -> Unit, showHint: Boolean = true, content: @Composable () -> Unit) {
    val c = Ct.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg.copy(alpha = 0.97f))
            .filmGrainBehind(0.04f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            if (showHint) {
                Spacer(Modifier.height(36.dp))
                Text(stringResource(R.string.ws1_tap_to_continue), style = Ct.type.label, color = c.textMuted)
            }
        }
    }
}
