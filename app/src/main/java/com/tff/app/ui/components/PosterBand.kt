package com.tff.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tff.app.R
import com.tff.app.domain.CardText
import com.tff.app.model.Project
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtColors
import com.tff.app.ui.theme.filmGrain
import com.tff.app.ui.theme.lightLeak
import com.tff.app.ui.theme.vignette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Scope ("2.39:1") letterbox ratio used by every poster and reel band. */
const val SCOPE_RATIO = 2.39f

private val ytId = Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtu\.be/)([A-Za-z0-9_-]{11})""")

/** YouTube video id from any common link form, or null. */
fun youTubeId(url: String?): String? = url?.let { ytId.find(it)?.groupValues?.get(1) }

/** `https://img.youtube.com/vi/<id>/hqdefault.jpg` for a YouTube link, else null. */
fun youTubeThumbnail(url: String?): String? = youTubeId(url)?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }

/**
 * Role-card poster (PRD §6.5 item 1): a 2.39:1 band showing the lookbook's YouTube thumbnail, or a
 * generated gradient poster (deterministic from the title) with the title in display type. Tungsten
 * light leak at 18% and 3.5% film grain over the picture, never over the title. [topLeft] defaults to
 * "SHORT · 12 MIN"; [topRight] is "4 KM" / "REMOTE" from the caller (distance isn't on the project).
 */
@Composable
fun PosterBand(
    project: Project,
    modifier: Modifier = Modifier,
    topLeft: String? = safeText({ "${project.format.name.replace('_', ' ')} · ${project.runtimeMin} MIN" }) { CardText.formatChip(project) },
    topRight: String? = null,
    topRightTone: ChipTone = ChipTone.NEUTRAL,
    shape: Shape = RectangleShape,
    showTitle: Boolean = true,
) {
    val thumb = remember(project.lookbookUrl) { youTubeThumbnail(project.lookbookUrl) }
    val cd = stringResource(R.string.ws1_poster_cd, project.title)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(SCOPE_RATIO)
            .clip(shape)
            .semantics { contentDescription = cd },
    ) {
        GeneratedPosterArt(seed = project.title, Modifier.fillMaxSize())
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().posterTexture(),
            )
        } else if (showTitle) {
            PosterTitle(
                title = project.title,
                kicker = project.genres.firstOrNull()?.uppercase(),
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        PosterChips(topLeft, topRight, topRightTone)
    }
}

/**
 * Bare generated poster art (gradient + practical-light glow + light leak + grain + vignette), for
 * places with no project (a person's reel band, empty states). Deterministic from [seed].
 */
@Composable
fun GeneratedPosterArt(seed: String, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val palette = remember(seed, c) { posterPalette(seed, c) }
    Box(
        modifier
            .drawBehind {
                val a = palette.angle
                val dx = cos(a) * size.width
                val dy = sin(a) * size.height
                drawRect(
                    Brush.linearGradient(
                        listOf(palette.from, palette.to),
                        start = Offset(size.width / 2 - dx / 2, size.height / 2 - dy / 2),
                        end = Offset(size.width / 2 + dx / 2, size.height / 2 + dy / 2),
                    )
                )
                // A soft "practical" light somewhere in frame.
                val r = max(size.width, size.height) * palette.glowRadius
                drawRect(
                    Brush.radialGradient(
                        listOf(palette.glow, Color.Transparent),
                        center = Offset(size.width * palette.glowX, size.height * palette.glowY),
                        radius = r,
                    )
                )
                // Thin anamorphic streak through the glow.
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, palette.glowX to palette.glow.copy(alpha = palette.glow.alpha * 0.9f), 1f to Color.Transparent,
                    ),
                    topLeft = Offset(0f, size.height * palette.glowY - 1.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()),
                )
            }
            .posterTexture(),
    )
}

/** Light leak + grain + vignette, the darkroom texture stack for pictures. */
@Composable
fun Modifier.posterTexture(): Modifier {
    val c = Ct.colors
    return this.lightLeak(c.accent).filmGrain().vignette(c.bg)
}

@Composable
private fun PosterTitle(title: String, kicker: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Ct.colors.bg.copy(alpha = 0.55f))))
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
            .clearAndSetSemantics { },
    ) {
        if (kicker != null) {
            Text(kicker, style = Ct.type.monoS.copy(letterSpacing = 2.sp), color = Ct.colors.text.copy(alpha = 0.72f), maxLines = 1)
        }
        Text(
            title.uppercase(),
            style = Ct.type.displayM.copy(fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 1.sp),
            color = Ct.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BoxScope.PosterChips(topLeft: String?, topRight: String?, topRightTone: ChipTone) {
    Row(
        Modifier.fillMaxWidth().padding(10.dp).align(Alignment.TopStart),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        if (topLeft != null) PosterChip(topLeft) else Box(Modifier)
        if (topRight != null) PosterChip(topRight, topRightTone)
    }
}

/** Dark translucent timecode chip used over pictures ("SHORT · 12 MIN", "4 KM"). */
@Composable
fun PosterChip(text: String, tone: ChipTone = ChipTone.NEUTRAL, modifier: Modifier = Modifier) {
    val fg = if (tone == ChipTone.NEUTRAL) Ct.colors.text else toneColors(tone).fg
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        style = Ct.type.monoS,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(Ct.colors.bg.copy(alpha = 0.66f))
            .border(1.dp, fg.copy(alpha = 0.16f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = spokenRatios(text).lowercase() },
    )
}

private data class PosterPalette(
    val from: Color, val to: Color, val glow: Color,
    val glowX: Float, val glowY: Float, val glowRadius: Float, val angle: Float,
)

/** Token-derived, deterministic poster palettes (never raw colours): darkroom mixes of the accents. */
private fun posterPalette(seed: String, c: CtColors): PosterPalette {
    var h = 1125899906842597L
    for (ch in seed.lowercase()) h = 31 * h + ch.code
    fun bits(shift: Int, n: Int) = (abs(h shr shift) % n).toInt()
    val looks = listOf(
        Triple(lerp(c.bg, c.accent, 0.34f), lerp(c.bg, c.stop, 0.16f), c.accent.copy(alpha = 0.30f)),   // tungsten night
        Triple(lerp(c.bg, c.info, 0.36f), lerp(c.bg, c.go, 0.10f), c.info.copy(alpha = 0.26f)),         // blue hour
        Triple(lerp(c.bg, c.go, 0.26f), lerp(c.bg, c.info, 0.18f), c.go.copy(alpha = 0.22f)),           // sodium teal
        Triple(lerp(c.bg, c.stop, 0.34f), lerp(c.bg, c.accent, 0.12f), c.stop.copy(alpha = 0.26f)),     // safelight red
        Triple(lerp(c.surfaceHigh, c.textSecondary, 0.30f), c.bg, c.text.copy(alpha = 0.16f)),          // silver gelatin
        Triple(lerp(c.bg, c.info, 0.22f), lerp(c.bg, c.stop, 0.22f), c.accent.copy(alpha = 0.22f)),     // dusk
    )
    val (from, to, glow) = looks[bits(3, looks.size)]
    return PosterPalette(
        from = from, to = to, glow = glow,
        glowX = 0.55f + bits(11, 40) / 100f,          // 0.55..0.94: away from the title corner
        glowY = 0.18f + bits(17, 40) / 100f,          // 0.18..0.57
        glowRadius = 0.32f + bits(23, 20) / 100f,
        angle = (bits(29, 360) / 360f) * 6.2832f,
    )
}
