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
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.text.style.TextAlign
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
 * light leak at 18% and 3.5% film grain over the picture, never over the title; thin letterbox bars
 * top and bottom. [topLeft] defaults to
 * "SHORT · 12 MIN"; [topRight] is "4 KM" / "REMOTE" from the caller (distance isn't on the project).
 */
@Composable
fun PosterBand(
    project: Project,
    modifier: Modifier = Modifier,
    topLeft: String? = safeText({ "${project.format.name.replace('_', ' ')} · ${project.runtimeMin} MIN" }) { CardText.formatChip(project) },
    topRight: String? = null,
    topRightTone: ChipTone = ChipTone.NEUTRAL,
    topRightNote: String? = null,
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
        LetterboxBars(Modifier.fillMaxSize())
        PosterChips(topLeft, topRight, topRightTone, topRightNote)
    }
}

/** Thin scope letterbox bars top and bottom of a picture band (PRD §6.5 / §12.3), drawn over the picture. */
@Composable
fun LetterboxBars(modifier: Modifier = Modifier) {
    val bar = Ct.colors.bg
    Box(
        modifier.drawBehind {
            val h = (size.height * 0.055f).coerceAtLeast(3.dp.toPx())
            drawRect(bar, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, h))
            drawRect(bar, topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
        }
    )
}

/**
 * Bare generated poster art (graded gradient, a practical light with an anamorphic streak, a second
 * fill light, then light leak + grain + vignette), for places with no picture. Deterministic from [seed].
 */
@Composable
fun GeneratedPosterArt(seed: String, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val p = remember(seed, c) { posterPalette(seed, c) }
    Box(
        modifier
            .drawBehind {
                val w = size.width
                val h = size.height
                // Grade: a mostly vertical gradient, dark at the bottom so the title always reads.
                val dx = sin(p.angle) * w * 0.5f
                drawRect(
                    Brush.linearGradient(
                        0f to p.top, 0.55f to p.mid, 1f to c.bg,
                        start = Offset(w / 2 - dx, 0f), end = Offset(w / 2 + dx, h),
                    )
                )
                // Fill light, low and opposite the key.
                drawRect(
                    Brush.radialGradient(
                        listOf(p.fill, Color.Transparent),
                        center = Offset(w * p.fillX, h * 0.95f), radius = w * 0.42f,
                    )
                )
                // Key "practical" light: a soft glow, no hard point (a dot on a line reads as a video scrubber).
                val key = Offset(w * p.keyX, h * p.keyY)
                drawRect(Brush.radialGradient(listOf(p.key, p.key.copy(alpha = p.key.alpha * 0.25f), Color.Transparent), center = key, radius = w * p.keyRadius))
                // Soft anamorphic flare: a wide, short glow that falls off both ways (radial, squashed to 1:12).
                val flareW = w * 0.42f
                val flareH = flareW / 12f
                withTransform({ scale(1f, flareH / flareW, pivot = key) }) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to p.key.copy(alpha = (p.key.alpha * 0.55f).coerceAtMost(0.35f)),
                            0.4f to p.key.copy(alpha = (p.key.alpha * 0.18f).coerceAtMost(0.12f)),
                            1f to Color.Transparent,
                            center = key, radius = flareW,
                        ),
                        radius = flareW, center = key,
                    )
                }
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
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (kicker != null) {
            Text(kicker, style = Ct.type.monoS.copy(fontSize = 10.sp, letterSpacing = 3.sp), color = Ct.colors.text.copy(alpha = 0.7f), maxLines = 1)
        }
        Text(
            title.uppercase(),
            style = Ct.type.displayM.copy(fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center),
            color = Ct.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BoxScope.PosterChips(topLeft: String?, topRight: String?, topRightTone: ChipTone, topRightNote: String?) {
    Row(
        Modifier.fillMaxWidth().padding(10.dp).align(Alignment.TopStart),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) { if (topLeft != null) PosterChip(topLeft) }
        if (topRight != null || topRightNote != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (topRight != null) PosterChip(topRight, topRightTone)
                if (topRightNote != null) PosterChip(topRightNote, ChipTone.WARN)
            }
        }
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
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(Ct.colors.bg.copy(alpha = 0.66f))
            .border(1.dp, fg.copy(alpha = 0.16f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = spokenRatios(text).lowercase() },
    )
}

private data class PosterPalette(
    val top: Color, val mid: Color, val key: Color, val fill: Color,
    val keyX: Float, val keyY: Float, val keyRadius: Float, val fillX: Float, val angle: Float,
)

/** Token-derived, deterministic poster grades (never raw colours): darkroom mixes of the accents. */
private fun posterPalette(seed: String, c: CtColors): PosterPalette {
    var h = 1125899906842597L
    for (ch in seed.lowercase()) h = 31 * h + ch.code
    h = h xor (h ushr 29)
    fun bits(shift: Int, n: Int) = (abs(h shr shift) % n).toInt()
    data class Look(val top: Color, val mid: Color, val key: Color, val fill: Color)
    val looks = listOf(
        // tungsten night
        Look(lerp(c.bg, c.accent, 0.30f), lerp(c.bg, c.stop, 0.14f), c.accent.copy(alpha = 0.55f), c.stop.copy(alpha = 0.18f)),
        // blue hour
        Look(lerp(c.bg, c.info, 0.38f), lerp(c.bg, c.info, 0.12f), c.info.copy(alpha = 0.50f), c.accent.copy(alpha = 0.16f)),
        // sodium teal
        Look(lerp(c.bg, c.go, 0.26f), lerp(c.bg, c.info, 0.12f), c.go.copy(alpha = 0.38f), c.info.copy(alpha = 0.20f)),
        // safelight red
        Look(lerp(c.bg, c.stop, 0.36f), lerp(c.bg, c.stop, 0.10f), c.stop.copy(alpha = 0.50f), c.accent.copy(alpha = 0.16f)),
        // silver gelatin
        Look(lerp(c.surfaceHigh, c.textSecondary, 0.32f), lerp(c.bg, c.surfaceHigh, 0.6f), c.text.copy(alpha = 0.32f), c.info.copy(alpha = 0.10f)),
        // dusk
        Look(lerp(lerp(c.bg, c.info, 0.30f), c.stop, 0.18f), lerp(c.bg, c.info, 0.10f), c.accent.copy(alpha = 0.42f), c.info.copy(alpha = 0.22f)),
    )
    val l = looks[bits(3, looks.size)]
    val keyX = 0.58f + bits(11, 34) / 100f        // 0.58..0.91: away from the centre title
    return PosterPalette(
        top = l.top, mid = l.mid, key = l.key, fill = l.fill,
        keyX = if (bits(41, 2) == 0) keyX else 1f - keyX,
        keyY = 0.16f + bits(17, 22) / 100f,       // 0.16..0.37, above the title block
        keyRadius = 0.30f + bits(23, 18) / 100f,
        fillX = 0.15f + bits(31, 70) / 100f,
        angle = (bits(37, 60) - 30) / 100f,       // ±0.3 rad tilt
    )
}
