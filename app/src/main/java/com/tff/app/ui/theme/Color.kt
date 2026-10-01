package com.tff.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** "Darkroom" tokens (PRD §12.1). Dark only in the MVP. Read them via `Ct.colors`. */
@Immutable
data class CtColors(
    val bg: Color = Color(0xFF0B0B0D),
    val surface: Color = Color(0xFF141418),
    val surfaceRaised: Color = Color(0xFF1C1C22),
    val surfaceHigh: Color = Color(0xFF26262E),
    val outline: Color = Color(0xFF34343E),
    val text: Color = Color(0xFFF2EFE9),            // Paper
    val textSecondary: Color = Color(0xFFA8A39A),
    val textMuted: Color = Color(0xFF8A857E),
    val accent: Color = Color(0xFFFFB547),          // Tungsten
    val accentPressed: Color = Color(0xFFE09A2E),
    val onAccent: Color = Color(0xFF1A1206),
    val go: Color = Color(0xFF3DDC97),              // Greenlit
    val stop: Color = Color(0xFFFF5A4E),            // Safelight
    val info: Color = Color(0xFF6CB4FF),            // Daylight
    val dangerContainer: Color = Color(0xFF3A1716),
    val onDangerContainer: Color = Color(0xFFFF8A80),
    val infoContainer: Color = Color(0xFF132235),
    val onInfoContainer: Color = Color(0xFFA9D1FF),
    val warnContainer: Color = Color(0xFF33260F),
    val onWarnContainer: Color = Color(0xFFFFD08A),
)
