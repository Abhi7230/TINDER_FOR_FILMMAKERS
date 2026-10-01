package com.tff.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.tff.app.R
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtMotion
import com.tff.app.ui.theme.rememberReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Swipe directions. RIGHT = Interested / Let's talk, LEFT = Pass / Not this time, UP = Save / Backup. */
enum class SwipeDirection { LEFT, RIGHT, UP }

/**
 * Words for one deck: TalkBack custom-action labels and the stamps that fade in while dragging.
 * [up] null = no third action (Scout deck). Use the presets [gigs], [applicants], [scout].
 */
@Immutable
data class SwipeLabels(
    val right: String,
    val left: String,
    val up: String?,
    val rightStamp: String,
    val leftStamp: String,
    val upStamp: String?,
) {
    companion object {
        /** Gigs deck: Interested · Pass · Save. */
        @Composable fun gigs() = SwipeLabels(
            stringResource(R.string.ws1_action_interested), stringResource(R.string.ws1_action_pass), stringResource(R.string.ws1_action_save),
            stringResource(R.string.ws1_stamp_interested), stringResource(R.string.ws1_stamp_pass), stringResource(R.string.ws1_stamp_save),
        )
        /** Crew up · Applicants: Let's talk · Not this time · Backup. */
        @Composable fun applicants() = SwipeLabels(
            stringResource(R.string.ws1_action_lets_talk), stringResource(R.string.ws1_action_not_this_time), stringResource(R.string.ws1_action_backup),
            stringResource(R.string.ws1_stamp_lets_talk), stringResource(R.string.ws1_stamp_not_this_time), stringResource(R.string.ws1_stamp_backup),
        )
        /** Crew up · Scout: Send ask · Skip (no up action). */
        @Composable fun scout() = SwipeLabels(
            stringResource(R.string.ws1_action_send_ask), stringResource(R.string.ws1_action_skip), null,
            stringResource(R.string.ws1_stamp_ask), stringResource(R.string.ws1_stamp_skip), null,
        )
    }
}

/**
 * State for a [SwipeDeck]: programmatic swipes from the deck buttons, undo of the last swipe, and the
 * live drag progress (for lighting up the buttons). Create with [rememberSwipeDeckState].
 *
 * The deck hides a swiped item immediately, whether or not the caller removes it from its list; undo
 * brings it back on top even if the caller already dropped it.
 */
@Stable
class SwipeDeckState<T> internal constructor() {
    internal val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    internal val alpha = Animatable(1f)
    internal var width by mutableIntStateOf(0)
    internal var height by mutableIntStateOf(0)
    internal val dismissed = mutableStateMapOf<Any, Boolean>()
    internal var restored by mutableStateOf<T?>(null)
    private val history = mutableStateListOf<Pair<T, SwipeDirection>>()

    internal var items: List<T> = emptyList()
    internal var keyOf: (T) -> Any = { it as Any }
    internal var reduceMotion = false
    internal var allowUp = true
    internal var scope: CoroutineScope? = null
    internal var onSwipedCb: (T, SwipeDirection) -> Unit = { _, _ -> }
    internal var canSwipeCb: (T, SwipeDirection) -> Boolean = { _, _ -> true }

    /** True while a card is flying out or coming back. Buttons can ignore taps meanwhile. */
    var isAnimating by mutableStateOf(false)
        private set

    /** True while a finger is dragging the top card. */
    var isDragging by mutableStateOf(false)
        internal set

    /** Whether [undo] has something to bring back (last 10 swipes). */
    val canUndo: Boolean get() = history.isNotEmpty()

    /** The most recent swipe, if any. */
    val lastSwipe: Pair<T, SwipeDirection>? get() = history.lastOrNull()

    /** The card on top, or null when the deck is empty. */
    val topItem: T? get() = visible(1).firstOrNull()

    /** Drag progress toward a horizontal commit: −1 (Pass) … 0 … 1 (Interested). */
    val horizontalProgress: Float
        get() {
            val w = width
            if (w == 0) return 0f
            val o = offset.value
            if (allowUp && -o.y > abs(o.x)) return 0f
            return (o.x / (w * CtMotion.SWIPE_COMMIT_FRACTION)).coerceIn(-1f, 1f)
        }

    /** Drag progress toward an up-swipe commit (Save / Backup): 0 … 1. */
    val upProgress: Float
        get() {
            val w = width
            if (!allowUp || w == 0) return 0f
            val o = offset.value
            if (-o.y <= abs(o.x)) return 0f
            return (-o.y / (w * CtMotion.SWIPE_COMMIT_FRACTION)).coerceIn(0f, 1f)
        }

    /** 0..1: how far the top card has travelled toward any commit; drives the next card's rise. */
    internal val stackProgress: Float
        get() {
            val w = width
            if (w == 0) return 0f
            val o = offset.value
            val d = maxOf(abs(o.x), if (allowUp) -o.y else 0f)
            return (d / (w * CtMotion.SWIPE_COMMIT_FRACTION)).coerceIn(0f, 1f)
        }

    internal fun visible(n: Int): List<T> {
        val r = restored
        val rk = r?.let(keyOf)
        val out = ArrayList<T>(n)
        if (r != null) out += r
        for (it in items) {
            if (out.size >= n) break
            val k = keyOf(it)
            if (k == rk || dismissed.containsKey(k)) continue
            out += it
        }
        return out
    }

    /**
     * Swipe the top card as if dragged (deck buttons, TalkBack). Suspends until the card is gone and
     * `onSwiped` has run. Returns false if nothing was swiped (empty deck, busy, vetoed by `canSwipe`).
     */
    suspend fun swipe(direction: SwipeDirection): Boolean {
        if (isAnimating) return false
        if (direction == SwipeDirection.UP && !allowUp) return false
        val item = topItem ?: return false
        if (!canSwipeCb(item, direction)) return false
        flingAndCommit(item, direction)
        return true
    }

    /**
     * Bring the last swiped card back on top (the fling reverses in 260 ms). Returns what was undone so
     * the caller can revert it in the Repo (`repo.undoSwipe(roleId)`), or null if there was nothing.
     */
    suspend fun undo(): Pair<T, SwipeDirection>? {
        if (isAnimating) return null
        val last = history.lastOrNull() ?: return null
        history.removeAt(history.lastIndex)
        val (item, dir) = last
        isAnimating = true
        try {
            if (reduceMotion) {
                alpha.snapTo(0f)
                offset.snapTo(Offset.Zero)
                restored = item
                dismissed.remove(keyOf(item))
                alpha.animateTo(1f, CtMotion.fade())
            } else {
                offset.snapTo(exitOffset(dir))
                restored = item
                dismissed.remove(keyOf(item))
                offset.animateTo(Offset.Zero, CtMotion.undo())
            }
        } finally {
            isAnimating = false
        }
        return last
    }

    /** Fire-and-forget [swipe] in the deck's own scope (for onClick handlers). */
    fun swipeAsync(direction: SwipeDirection) {
        scope?.launch { swipe(direction) }
    }

    /** Fire-and-forget [undo]; [onUndone] receives what came back. */
    fun undoAsync(onUndone: (T, SwipeDirection) -> Unit = { _, _ -> }) {
        scope?.launch { undo()?.let { (i, d) -> onUndone(i, d) } }
    }

    /** Forget undo history (e.g. after the 5 s Undo chip times out). */
    fun clearUndo() = history.clear()

    /** Show every item again and forget history (e.g. after a filter change rebuilds the deck). */
    fun reset() {
        dismissed.clear()
        restored = null
        history.clear()
    }

    internal suspend fun dragBy(delta: Offset) {
        offset.snapTo(offset.value + delta)
    }

    internal fun isPastCommit(o: Offset): Boolean {
        val c = width * CtMotion.SWIPE_COMMIT_FRACTION
        return if (allowUp && -o.y > abs(o.x)) -o.y > c else abs(o.x) > c
    }

    internal fun stampAlpha(dir: SwipeDirection): Float {
        val w = width
        if (w == 0) return 0f
        val o = offset.value
        val upDominant = allowUp && -o.y > abs(o.x)
        val d = when (dir) {
            SwipeDirection.RIGHT -> if (upDominant) 0f else o.x
            SwipeDirection.LEFT -> if (upDominant) 0f else -o.x
            SwipeDirection.UP -> if (upDominant) -o.y else 0f
        }
        val start = w * CtMotion.SWIPE_STAMP_START
        val full = w * CtMotion.SWIPE_COMMIT_FRACTION
        return ((d - start) / (full - start)).coerceIn(0f, 1f)
    }

    internal suspend fun release(v: Velocity) {
        if (isAnimating) return
        val item = topItem
        val w = width.toFloat()
        if (item == null || w <= 0f) {
            offset.animateTo(Offset.Zero, CtMotion.springBack())
            return
        }
        val o = offset.value
        val commit = w * CtMotion.SWIPE_COMMIT_FRACTION
        val vel = CtMotion.SWIPE_COMMIT_VELOCITY
        val upDominant = allowUp && -o.y > abs(o.x)
        val dir = when {
            upDominant && (-o.y > commit || (-v.y > vel && o.y < 0f)) -> SwipeDirection.UP
            !upDominant && (o.x > commit || (v.x > vel && o.x > 0f)) -> SwipeDirection.RIGHT
            !upDominant && (o.x < -commit || (v.x < -vel && o.x < 0f)) -> SwipeDirection.LEFT
            else -> null
        }
        if (dir == null || !canSwipeCb(item, dir)) {
            offset.animateTo(Offset.Zero, CtMotion.springBack(), initialVelocity = Offset(v.x, v.y))
            return
        }
        flingAndCommit(item, dir)
    }

    private suspend fun flingAndCommit(item: T, dir: SwipeDirection) {
        isAnimating = true
        try {
            if (reduceMotion) alpha.animateTo(0f, tween(CtMotion.CROSSFADE_MS))
            else offset.animateTo(exitOffset(dir), CtMotion.flingOut())
            // Atomic within one frame: hide the card, then reset the shared offset for the next one.
            val k = keyOf(item)
            dismissed[k] = true
            if (restored?.let(keyOf) == k) restored = null
            history.add(item to dir)
            while (history.size > MAX_UNDO) history.removeAt(0)
            offset.snapTo(Offset.Zero)
            alpha.snapTo(1f)
        } finally {
            isAnimating = false
        }
        onSwipedCb(item, dir)
    }

    private fun exitOffset(dir: SwipeDirection): Offset {
        val o = offset.value
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        return when (dir) {
            SwipeDirection.RIGHT -> Offset(w * 1.5f, o.y - h * 0.04f)
            SwipeDirection.LEFT -> Offset(-w * 1.5f, o.y - h * 0.04f)
            SwipeDirection.UP -> Offset(o.x, -h * 1.3f)
        }
    }

    private companion object { const val MAX_UNDO = 10 }
}

@Composable
fun <T> rememberSwipeDeckState(): SwipeDeckState<T> = remember { SwipeDeckState() }

/** Space reserved under the top card so the next two cards can peek out. */
private val STACK_RESERVE = 28.dp

/**
 * A generic card stack (PRD §12.4) that renders the top 3 [items]. Drag to swipe: the card follows the
 * finger and tilts (dx/width × 12°, max ±15°); stamps fade in past 8% of the width; release past 35% or
 * faster than 1,200 px/s to commit (220 ms fling), otherwise it springs back. The next card rises from
 * 0.94 / +16dp. Up-swipes are the third action unless [labels] has none.
 *
 * Every swipe also exists as a button ([SwipeDeckState.swipe]) and a TalkBack custom action. With
 * "Remove animations" on, cards cross-fade with no rotation.
 *
 * @param canSwipe veto a swipe (guests, daily cap): return false and the card springs back.
 * @param onSwiped called after the card has left, with the direction.
 * @param onTap tap on the top card (open details).
 * @param empty shown when nothing is left.
 */
@Composable
fun <T> SwipeDeck(
    items: List<T>,
    key: (T) -> Any,
    onSwiped: (item: T, direction: SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    state: SwipeDeckState<T> = rememberSwipeDeckState(),
    labels: SwipeLabels = SwipeLabels.gigs(),
    enabled: Boolean = true,
    canSwipe: (item: T, direction: SwipeDirection) -> Boolean = { _, _ -> true },
    onTap: ((T) -> Unit)? = null,
    empty: @Composable BoxScope.() -> Unit = {},
    card: @Composable (item: T) -> Unit,
) {
    val reduce = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    state.items = items
    state.keyOf = key
    state.reduceMotion = reduce
    state.allowUp = labels.up != null
    state.onSwipedCb = onSwiped
    state.canSwipeCb = canSwipe
    state.scope = scope

    LaunchedEffect(items) {
        val keys = items.mapTo(HashSet()) { key(it) }
        state.dismissed.keys.filter { it !in keys }.forEach { state.dismissed.remove(it) }
    }

    val visible = state.visible(3)
    val peekPx = with(LocalDensity.current) { CtMotion.NEXT_CARD_OFFSET.toPx() }
    val gestures = enabled && visible.isNotEmpty()

    Box(
        modifier
            .onSizeChanged { state.width = it.width; state.height = it.height }
            .then(
                if (!gestures) Modifier else Modifier.pointerInput(state) {
                    val tracker = VelocityTracker()
                    var past = false
                    var active = false
                    detectDragGestures(
                        onDragStart = {
                            active = !state.isAnimating
                            tracker.resetTracking()
                            past = false
                            state.isDragging = active
                        },
                        onDragEnd = {
                            state.isDragging = false
                            if (active) {
                                val v = tracker.calculateVelocity()
                                scope.launch { state.release(v) }
                            }
                        },
                        onDragCancel = {
                            state.isDragging = false
                            if (active) scope.launch { state.release(Velocity.Zero) }
                        },
                        onDrag = { change, amount ->
                            if (!active) return@detectDragGestures
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val next = state.offset.value + amount
                            scope.launch { state.dragBy(amount) }
                            val nowPast = state.isPastCommit(next)
                            if (nowPast && !past) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            past = nowPast
                        },
                    )
                }
            ),
    ) {
        if (visible.isEmpty()) empty()
        for (i in visible.indices.reversed()) {
            val item = visible[i]
            key(key(item)) {
                DeckLayer(
                    depth = i, state = state, reduce = reduce, peekPx = peekPx, labels = labels, scope = scope,
                    onTap = if (onTap != null) ({ onTap(item) }) else null,
                ) { card(item) }
            }
        }
    }
}

@Composable
private fun <T> DeckLayer(
    depth: Int,
    state: SwipeDeckState<T>,
    reduce: Boolean,
    peekPx: Float,
    labels: SwipeLabels,
    scope: CoroutineScope,
    onTap: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val isTop = depth == 0
    val p = if (isTop) 1f else state.stackProgress
    val targetScale = if (reduce) 1f else when (depth) {
        0 -> 1f
        1 -> lerp(CtMotion.NEXT_CARD_SCALE, 1f, p)
        else -> lerp(0.88f, CtMotion.NEXT_CARD_SCALE, p)
    }
    val targetY = if (reduce) 0f else when (depth) {
        0 -> 0f
        1 -> lerp(peekPx, 0f, p)
        else -> lerp(peekPx * 1.75f, peekPx, p)
    }
    val targetDim = when (depth) { 0 -> 0f; 1 -> lerp(0.32f, 0f, p); else -> lerp(0.55f, 0.32f, p) }
    val scale by animateFloatAsState(targetScale, CtMotion.rise(), label = "deckScale")
    val ty by animateFloatAsState(targetY, CtMotion.rise(), label = "deckY")
    val dim by animateFloatAsState(if (reduce) 0f else targetDim, CtMotion.rise(), label = "deckDim")
    val bg = Ct.colors.bg
    val openLabel = stringResource(R.string.ws1_open_details)
    val actions = if (isTop) remember(labels) {
        listOfNotNull(
            CustomAccessibilityAction(labels.right) { scope.launch { state.swipe(SwipeDirection.RIGHT) }; true },
            CustomAccessibilityAction(labels.left) { scope.launch { state.swipe(SwipeDirection.LEFT) }; true },
            labels.up?.let { CustomAccessibilityAction(it) { scope.launch { state.swipe(SwipeDirection.UP) }; true } },
        )
    } else emptyList()

    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = STACK_RESERVE)
            .zIndex(-depth.toFloat())
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = scale
                scaleY = scale
                if (isTop) {
                    val o = state.offset.value
                    translationX = o.x
                    translationY = ty + o.y
                    rotationZ = if (reduce || state.width == 0) 0f
                    else (o.x / state.width * CtMotion.SWIPE_ROTATION_DEG).coerceIn(-CtMotion.SWIPE_MAX_ROTATION_DEG, CtMotion.SWIPE_MAX_ROTATION_DEG)
                    alpha = state.alpha.value
                } else {
                    translationY = ty
                }
            }
            .drawWithContent {
                drawContent()
                if (dim > 0.001f) drawRect(bg, alpha = dim)
            }
            .then(
                if (isTop) Modifier.semantics { customActions = actions }
                else Modifier.clearAndSetSemantics { }
            )
            .then(
                if (isTop && onTap != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = openLabel, onClick = onTap,
                ) else Modifier
            ),
    ) {
        content()
        if (isTop) Stamps(state, labels)
    }
}

@Composable
private fun BoxScope.Stamps(state: SwipeDeckState<*>, labels: SwipeLabels) {
    val c = Ct.colors
    Stamp(
        labels.rightStamp, c.go,
        Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 30.dp)
            .graphicsLayer { alpha = state.stampAlpha(SwipeDirection.RIGHT); rotationZ = -12f },
    )
    Stamp(
        labels.leftStamp, c.stop,
        Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 30.dp)
            .graphicsLayer { alpha = state.stampAlpha(SwipeDirection.LEFT); rotationZ = 12f },
    )
    labels.upStamp?.let {
        Stamp(
            it, c.accent,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)
                .graphicsLayer { alpha = state.stampAlpha(SwipeDirection.UP) },
        )
    }
}

/** A slate-tag stamp ("INTERESTED" in Greenlit, "PASS" in Safelight, "SAVE" in Tungsten). */
@Composable
fun SwipeStamp(text: String, color: Color, modifier: Modifier = Modifier) = Stamp(text, color, modifier)

@Composable
private fun Stamp(text: String, color: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        style = Ct.type.displayM.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 2.5.sp),
        color = color,
        maxLines = 1,
        modifier = modifier
            .clearAndSetSemantics { }
            .background(Ct.colors.bg.copy(alpha = 0.55f), shape)
            .border(3.dp, color, shape)
            .padding(horizontal = 14.dp, vertical = 2.dp),
    )
}
