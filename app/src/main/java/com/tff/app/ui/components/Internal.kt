package com.tff.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.tff.app.R
import com.tff.app.model.Dept
import com.tff.app.model.RoleCode
import com.tff.app.model.Taxonomy

/**
 * Domain text calls (CardText, ReputationCalc) are pure functions owned by WS2. While a body is still a
 * stub it throws [NotImplementedError]; cards fall back to [fallback] instead of crashing the deck.
 */
internal inline fun safeText(fallback: () -> String, block: () -> String): String =
    try { block() } catch (_: NotImplementedError) { fallback() }

internal inline fun <T> safeList(block: () -> List<T>): List<T> =
    try { block() } catch (_: NotImplementedError) { emptyList() }

private val numberRun = Regex("""[₹$£€]?\d[\d,]*(?:/\d+)?(?:[.:]\d+)?""")
private val ratio = Regex("""(\d+)/(\d+)""")

/** Renders digits, ratios, times and amounts in mono ("Showed up **9/9**"), the rest in [base]. */
internal fun monoNumbers(text: String, mono: TextStyle): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in numberRun.findAll(text)) {
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(fontFamily = mono.fontFamily, fontWeight = mono.fontWeight, letterSpacing = mono.letterSpacing)) {
            append(m.value)
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** "Paid on time 11/12" → "Paid on time 11 of 12" for TalkBack. */
internal fun spokenRatios(text: String): String = text.replace(ratio, "$1 of $2").replace("≤", "up to ").replace("×", "times ")

/** Parsed "x/y" ratio in a chip, or null. */
internal fun ratioIn(text: String): Pair<Int, Int>? =
    ratio.find(text)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

/** Short role names for tight chips ("→ DP", "1st AD"). Everything else uses the taxonomy label. */
fun shortRoleLabel(code: RoleCode): String = when (code) {
    RoleCode.DP -> "DP"
    RoleCode.FIRST_AD -> "1st AD"
    RoleCode.SECOND_AD -> "2nd AD"
    RoleCode.FIRST_AC -> "1st AC"
    RoleCode.PA -> "PA"
    RoleCode.SOUND_MIXER -> "Sound mixer"
    RoleCode.SOUND_DESIGNER -> "Sound designer"
    RoleCode.PRODUCTION_DESIGNER -> "Production designer"
    RoleCode.HMU -> "Hair & makeup"
    RoleCode.COSTUME -> "Costume"
    RoleCode.CAMERA_OP -> "Camera op"
    RoleCode.SCRIPT_SUPERVISOR -> "Script supervisor"
    RoleCode.INTIMACY_COORDINATOR -> "Intimacy coordinator"
    RoleCode.STUNT_COORDINATOR -> "Stunt coordinator"
    RoleCode.LOCATION_MANAGER -> "Locations"
    RoleCode.BTS_STILLS -> "BTS / stills"
    else -> Taxonomy.label(code)
}

/** Department name for grouped pickers (Appendix A). */
@Composable
@ReadOnlyComposable
fun deptLabel(d: Dept): String = stringResource(
    when (d) {
        Dept.DIRECTION -> R.string.ws1_dept_direction
        Dept.WRITING -> R.string.ws1_dept_writing
        Dept.PRODUCTION -> R.string.ws1_dept_production
        Dept.CAMERA -> R.string.ws1_dept_camera
        Dept.GE -> R.string.ws1_dept_ge
        Dept.SOUND -> R.string.ws1_dept_sound
        Dept.ART -> R.string.ws1_dept_art
        Dept.CAST -> R.string.ws1_dept_cast
        Dept.POST -> R.string.ws1_dept_post
        Dept.MUSIC -> R.string.ws1_dept_music
        Dept.OTHER -> R.string.ws1_dept_other
        Dept.SAFETY -> R.string.ws1_dept_safety
    }
)
