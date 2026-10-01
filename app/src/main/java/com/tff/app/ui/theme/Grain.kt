package com.tff.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.random.Random

/**
 * Darkroom texture (PRD §12.3): a tiled film-grain bitmap generated once, a Tungsten light leak and a
 * lens vignette. Use them on posters and overlays only — never over text blocks (draw text after).
 */
object Grain {
    /** Default grain strength: 3.5% (spec: 3–4%). */
    const val ALPHA = 0.035f
    /** Light leak strength: Tungsten at 18%. */
    const val LEAK_ALPHA = 0.18f

    private const val TILE = 192
    private const val BLOCK = 2   // 2×2 px grains read as film at phone densities

    /** The noise tile, generated lazily once per process (deterministic seed, ~150 KB). */
    val tile: ImageBitmap by lazy {
        val rnd = Random(0x5EED_F11L)
        val px = IntArray(TILE * TILE)
        val cells = TILE / BLOCK
        for (cy in 0 until cells) for (cx in 0 until cells) {
            // Triangular distribution around mid-grey: soft, film-like rather than salt-and-pepper.
            val v = ((rnd.nextInt(256) + rnd.nextInt(256)) / 2).coerceIn(0, 255)
            val argb = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            for (dy in 0 until BLOCK) for (dx in 0 until BLOCK) {
                px[(cy * BLOCK + dy) * TILE + (cx * BLOCK + dx)] = argb
            }
        }
        Bitmap.createBitmap(px, TILE, TILE, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    val brush: ShaderBrush by lazy { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
}

/** Draws tiled film grain over this element's content at [alpha] (3–4%). */
fun Modifier.filmGrain(alpha: Float = Grain.ALPHA): Modifier = drawWithCache {
    val brush = Grain.brush
    onDrawWithContent {
        drawContent()
        drawRect(brush, alpha = alpha)
    }
}

/** Film grain drawn *under* this element's content (after its background): for overlays with text. */
fun Modifier.filmGrainBehind(alpha: Float = Grain.ALPHA): Modifier = drawWithCache {
    val brush = Grain.brush
    onDrawWithContent {
        drawRect(brush, alpha = alpha)
        drawContent()
    }
}

/** Tungsten light leak: a radial wash from the top-left corner fading to transparent. */
fun Modifier.lightLeak(color: Color, alpha: Float = Grain.LEAK_ALPHA): Modifier = drawWithCache {
    val r = max(size.width, size.height) * 0.95f
    val brush = Brush.radialGradient(
        0f to color.copy(alpha = alpha),
        0.45f to color.copy(alpha = alpha * 0.35f),
        1f to Color.Transparent,
        center = Offset(size.width * 0.04f, size.height * 0.02f),
        radius = r,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush)
    }
}

/** Lens vignette: darkens the edges toward [color] (usually `Ct.colors.bg`). */
fun Modifier.vignette(color: Color, strength: Float = 0.55f): Modifier = drawWithCache {
    val r = max(size.width, size.height) * 0.75f
    val brush = Brush.radialGradient(
        0.35f to Color.Transparent,
        1f to color.copy(alpha = strength),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = r,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush)
    }
}
