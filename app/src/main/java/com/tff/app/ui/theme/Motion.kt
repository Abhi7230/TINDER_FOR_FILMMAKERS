package com.tff.app.ui.theme

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Motion tokens (PRD §12.4). Every animated component reads its numbers from here so the deck, the
 * overlays and the sheets feel like one system. Pair with [rememberReduceMotion] for the
 * "Remove animations" fallback (cross-fades only, no rotation).
 */
object CtMotion {
    // ── Swipe deck ──
    /** Card rotation per full width of horizontal drag: rotation = dx / width × 12°. */
    const val SWIPE_ROTATION_DEG = 12f
    /** Rotation clamp (±15°). */
    const val SWIPE_MAX_ROTATION_DEG = 15f
    /** Stamps start to fade in past 8% of the card width. */
    const val SWIPE_STAMP_START = 0.08f
    /** A drag commits past 35% of the card width… */
    const val SWIPE_COMMIT_FRACTION = 0.35f
    /** …or when released faster than 1,200 px/s. */
    const val SWIPE_COMMIT_VELOCITY = 1200f
    /** Fling-out duration (FastOutLinearIn). */
    const val SWIPE_FLING_MS = 220
    /** Undo reverses the fling in 260 ms. */
    const val SWIPE_UNDO_MS = 260
    /** Reduce-motion cross-fade. */
    const val CROSSFADE_MS = 160
    /** The next card waits at 0.94 scale, 16dp lower. */
    const val NEXT_CARD_SCALE = 0.94f
    val NEXT_CARD_OFFSET = 16.dp

    // ── Overlays ──
    const val SLATE_CLAP_MS = 180
    const val STAMP_MS = 240
    const val STAMP_START_SCALE = 1.3f
    const val STAMP_ROTATION_DEG = -4f
    const val OVERLAY_AUTO_DISMISS_MS = 1600L
    /** Per-character delay when the call time "types on". */
    const val TYPE_ON_CHAR_MS = 38L

    // ── General ──
    const val SHORT_MS = 150
    const val MEDIUM_MS = 240
    const val SHIMMER_MS = 1100

    val flingEasing: Easing = FastOutLinearInEasing
    val undoEasing: Easing = LinearOutSlowInEasing
    /** Gentle "settle" curve for entering content. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Below the commit threshold the card springs back (dampingRatio 0.75, stiffness 400). */
    fun <T> springBack(): AnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 400f)
    /** The next card rises to the top (dampingRatio 0.8, stiffness 300). */
    fun <T> rise(): AnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 300f)
    fun <T> flingOut(): AnimationSpec<T> = tween(SWIPE_FLING_MS, easing = flingEasing)
    fun <T> undo(): AnimationSpec<T> = tween(SWIPE_UNDO_MS, easing = undoEasing)
    fun <T> fade(): AnimationSpec<T> = tween(CROSSFADE_MS)
    fun <T> snappy(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * True when the system asks for reduced motion: animator duration scale is 0 ("Remove animations" in
 * Accessibility, or developer options). Re-checked whenever the screen resumes. Components then use
 * cross-fades only, with no rotation, scale pops or typing effects.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun read(): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale == 0f || !ValueAnimator.areAnimatorsEnabled()
    }
    var reduce by remember { mutableStateOf(read()) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reduce = read() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return reduce
}
