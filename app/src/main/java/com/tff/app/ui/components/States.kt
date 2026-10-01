package com.tff.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtMotion
import com.tff.app.ui.theme.CtShapes
import com.tff.app.ui.theme.rememberReduceMotion

/**
 * Empty state: an icon in a slate ring, a short title, an honest line of body copy and up to a few
 * actions (stack PrimaryButton / SecondaryButton / GhostButton in [actions]). Centred in its space.
 */
@Composable
fun EmptyState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Movie,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Ct.colors
    Box(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = Ct.type.titleM, color = c.text, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = Ct.type.bodyS, color = c.textSecondary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                actions()
            }
        }
    }
}

/**
 * Error state (PRD §8.1): "Couldn't load." + [message] (how to fix it) + [Retry]. Pass the Repo's
 * user-facing message when there is one.
 */
@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    EmptyState(
        title = stringResource(R.string.ws1_error_title),
        body = message ?: stringResource(R.string.ws1_error_body),
        icon = Icons.Rounded.CloudOff,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        PrimaryButton(stringResource(R.string.ws1_retry), onRetry, Modifier.widthIn(min = 160.dp))
    }
}

/** Shimmering placeholder fill in surface colours. Static under reduce motion. */
@Composable
fun Modifier.shimmer(): Modifier {
    val c = Ct.colors
    val reduce = rememberReduceMotion()
    if (reduce) return this.background(c.surfaceRaised)
    val t = rememberInfiniteTransition(label = "shimmer")
    val xs = t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(CtMotion.SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    return this.drawWithCache {
        val x = xs.value
        val w = size.width
        val brush = Brush.linearGradient(
            listOf(c.surfaceRaised, c.surfaceHigh, c.surfaceRaised),
            start = Offset(w * x - w * 0.6f, 0f),
            end = Offset(w * x, size.height),
        )
        onDrawBehind { drawRect(brush) }
    }
}

/** One skeleton block. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, width: Dp? = null, height: Dp = 14.dp, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .shimmer()
    )
}

/** Which layout a [LoadingSkeleton] imitates. */
enum class SkeletonKind { CARD, LIST, DETAIL }

/** Loading state (PRD §8.1): skeleton shimmer shaped like what's coming. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, kind: SkeletonKind = SkeletonKind.LIST, rows: Int = 5) {
    val label = stringResource(R.string.ws1_loading)
    Box(modifier.clearAndSetSemantics { contentDescription = label }) {
        when (kind) {
            SkeletonKind.CARD -> Column(
                Modifier.fillMaxSize().clip(CtShapes.card).background(Ct.colors.surface).border(1.dp, Ct.colors.outline.copy(alpha = 0.6f), CtShapes.card),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(SCOPE_RATIO).shimmer())
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(width = 140.dp, height = 16.dp)
                    SkeletonBlock(width = 220.dp, height = 24.dp)
                    SkeletonBlock(width = 260.dp, height = 18.dp)
                    Spacer(Modifier.height(4.dp))
                    SkeletonBlock(height = 12.dp)
                    SkeletonBlock(width = 200.dp, height = 12.dp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { SkeletonBlock(width = 84.dp, height = 26.dp, shape = CtShapes.chip) }
                    }
                }
            }
            SkeletonKind.LIST -> Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(rows) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).shimmer())
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonBlock(width = 160.dp, height = 14.dp)
                            SkeletonBlock(height = 12.dp)
                        }
                    }
                }
            }
            SkeletonKind.DETAIL -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(SCOPE_RATIO).shimmer())
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBlock(width = 120.dp, height = 16.dp)
                    SkeletonBlock(width = 240.dp, height = 26.dp)
                    SkeletonBlock(width = 200.dp, height = 18.dp)
                    repeat(rows) { SkeletonBlock(height = 44.dp, shape = CtShapes.field) }
                }
            }
        }
    }
}
