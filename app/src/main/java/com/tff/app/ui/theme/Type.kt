package com.tff.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tff.app.R

/** Barlow Condensed (display / slate labels), Inter (body), JetBrains Mono (dates, call times, counters). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object CtFonts {
    val display = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))
    val body = FontFamily(
        Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val mono = FontFamily(
        Font(R.font.jetbrains_mono_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.jetbrains_mono_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
}

/** Type scale (PRD §12.2). Read via `Ct.type`. Uppercase slate labels use [label] / [displayM] with letterSpacing. */
@Immutable
data class CtType(
    val displayL: TextStyle = TextStyle(fontFamily = CtFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 1.5.sp),
    val displayM: TextStyle = TextStyle(fontFamily = CtFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 1.5.sp),
    val titleL: TextStyle = TextStyle(fontFamily = CtFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp),
    val slate: TextStyle = TextStyle(fontFamily = CtFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 1.5.sp),
    val titleM: TextStyle = TextStyle(fontFamily = CtFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    val body: TextStyle = TextStyle(fontFamily = CtFonts.body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = CtFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    val bodyS: TextStyle = TextStyle(fontFamily = CtFonts.body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    val label: TextStyle = TextStyle(fontFamily = CtFonts.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    val mono: TextStyle = TextStyle(fontFamily = CtFonts.mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
    val monoS: TextStyle = TextStyle(fontFamily = CtFonts.mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    val monoL: TextStyle = TextStyle(fontFamily = CtFonts.mono, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 1.sp),
)
