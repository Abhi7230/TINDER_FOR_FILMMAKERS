package com.tff.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalCtColors = staticCompositionLocalOf { CtColors() }
private val LocalCtType = staticCompositionLocalOf { CtType() }

/** Token accessor: `Ct.colors.accent`, `Ct.type.mono`. */
object Ct {
    val colors: CtColors @Composable @ReadOnlyComposable get() = LocalCtColors.current
    val type: CtType @Composable @ReadOnlyComposable get() = LocalCtType.current
}

@Composable
fun CalltimeTheme(content: @Composable () -> Unit) {
    val c = CtColors()
    val t = CtType()
    val scheme = darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentPressed, onPrimaryContainer = c.onAccent,
        secondary = c.go, onSecondary = c.bg, tertiary = c.info, onTertiary = c.bg,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceHigh,
        outline = c.outline, outlineVariant = c.outline, error = c.stop, onError = c.bg,
        errorContainer = c.dangerContainer, onErrorContainer = c.onDangerContainer,
    )
    val typography = Typography(
        displayLarge = t.displayL, displayMedium = t.displayM, headlineSmall = t.titleL,
        titleLarge = t.titleL, titleMedium = t.titleM, bodyLarge = t.body, bodyMedium = t.bodyS,
        labelLarge = t.bodyStrong, labelMedium = t.label, labelSmall = t.label,
    )
    CompositionLocalProvider(LocalCtColors provides c, LocalCtType provides t) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
