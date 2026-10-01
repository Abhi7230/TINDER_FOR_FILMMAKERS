package com.tff.app.ui.screens.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.domain.RuleResult
import com.tff.app.domain.Severity
import com.tff.app.model.City
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.ChipStyle
import com.tff.app.ui.components.DateField
import com.tff.app.ui.components.SelectedMark
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.delay
import java.time.LocalDate

// Small building blocks shared by the wizard steps, the dashboard and the shoot-day editor (all WS5).

/** Field label in the same style as CtTextField's (secondary text, Safelight-ish when the field has an error). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, error: Boolean = false, hint: String? = null) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text, style = Ct.type.bodyS.copy(fontWeight = Ct.type.label.fontWeight),
            color = if (error) Ct.colors.onDangerContainer else Ct.colors.textSecondary,
        )
        if (hint != null) Text(hint, style = Ct.type.label, color = Ct.colors.textMuted)
    }
}

/**
 * Inline rule messages under a field: BLOCK in Safelight (only when [showBlocks]), WARN in amber, AUTO in blue.
 * Every message says how to fix it (ListingRules copy).
 */
@Composable
fun RuleMessages(results: List<RuleResult>, showBlocks: Boolean, modifier: Modifier = Modifier) {
    val shown = results.filter { it.severity != Severity.BLOCK || showBlocks }.distinctBy { it.message }
    if (shown.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { r ->
            val (icon, color) = when (r.severity) {
                Severity.BLOCK -> Icons.Rounded.ErrorOutline to Ct.colors.onDangerContainer
                Severity.WARN -> Icons.Rounded.WarningAmber to Ct.colors.onWarnContainer
                Severity.AUTO -> Icons.Rounded.Info to Ct.colors.onInfoContainer
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(r.message, style = Ct.type.label.copy(fontSize = 13.sp, lineHeight = 18.sp), color = color)
            }
        }
    }
}

/** First BLOCK message for a field, for components that take a single errorText. */
fun blockText(results: List<RuleResult>, show: Boolean): String? =
    if (!show) null else results.firstOrNull { it.severity == Severity.BLOCK }?.message

/** Brings this field into view when the wizard asks for it (red Review items, [Next] on a step with blockers). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.focusAnchor(key: String, focusField: String?, focusSeq: Int, onDone: () -> Unit): Modifier {
    val req = remember { BringIntoViewRequester() }
    LaunchedEffect(focusField, focusSeq) {
        if (focusField == key) {
            delay(220)
            runCatching { req.bringIntoView() }
            onDone()
        }
    }
    return this.bringIntoViewRequester(req)
}

/** Single-choice chips (format, meals, transport...). [selected] null = not answered yet. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
    enabled: Boolean = true,
) {
    ChipFlow(modifier.fillMaxWidth(), spacing = 8.dp, lineSpacing = 0.dp) {
        options.forEach { o ->
            CtFilterChip(label(o), selected = o == selected, onClick = { onSelect(o) }, enabled = enabled, icon = icon?.invoke(o))
        }
    }
}

/** Yes / No chips for the safety follow-ups (intimacy coordinator, armourer, guardian). */
@Composable
fun YesNoChips(value: Boolean?, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val yes = stringResource(R.string.ws5_yes)
    val no = stringResource(R.string.ws5_no)
    ChoiceChips(listOf(true, false), value, { if (it) yes else no }, onChange, modifier)
}

/** The collapsed "More details" fold (S11: everything that isn't required sits here). */
@Composable
fun MoreDetails(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, summary: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Ct.colors
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "moreChevron")
    val stateText = stringResource(if (expanded) R.string.ws5_expanded else R.string.ws5_collapsed)
    Column(
        modifier.fillMaxWidth().clip(CtShapes.field).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.field),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ws5_more_details), style = Ct.type.bodyStrong, color = c.text)
                if (summary != null) Text(summary, style = Ct.type.label, color = c.textMuted)
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.rotate(angle))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
                content()
            }
        }
    }
}

/** Whole-currency number field ("₹" prefix, mono digits). Blank = null. */
@Composable
fun MoneyField(
    value: Int?,
    onChange: (Int?) -> Unit,
    label: String,
    currency: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    placeholder: String? = null,
) {
    // The currency symbol sits in the label ("Day rate (₹)") so the field itself stays a clean number pad.
    CtTextField(
        value = value?.toString() ?: "",
        onValueChange = { t -> onChange(t.filter(Char::isDigit).take(7).toIntOrNull()) },
        label = "$label (${Money.symbol(currency).trim()})", modifier = modifier, singleLine = true, mono = true,
        placeholder = placeholder, supportingText = supportingText, errorText = errorText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** Plain integer field (runtime minutes, travel allowance). */
@Composable
fun IntField(
    value: Int?,
    onChange: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    maxDigits: Int = 3,
) {
    CtTextField(
        value = value?.toString() ?: "",
        onValueChange = { t -> onChange(t.filter(Char::isDigit).take(maxDigits).toIntOrNull()) },
        label = label, modifier = modifier, singleLine = true, mono = true,
        supportingText = supportingText, errorText = errorText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * Shoot dates: quick weekend chips, a start/end pair and a chip per day in the range (tap to drop a day), so a
 * 2-day weekend is one tap and a 5-day spread with gaps is still easy. [dates] is the exact list (Project.shootDates).
 */
@Composable
fun ShootDatesField(
    dates: List<LocalDate>,
    onChange: (List<LocalDate>) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    val c = Ct.colors
    val sorted = dates.distinct().sorted()
    val start = sorted.firstOrNull()
    val end = sorted.lastOrNull()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (enabled) {
            FieldLabel(stringResource(R.string.ws5_quick_weekend))
            ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
                WizardLogic.upcomingWeekends(today).forEach { (sat, sun) ->
                    val sel = sorted == listOf(sat, sun)
                    CtFilterChip(Dates.span(sat, sun), selected = sel, onClick = { onChange(listOf(sat, sun)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(
                label = stringResource(R.string.ws5_first_day), value = start, enabled = enabled,
                onValueChange = { s -> onChange(WizardLogic.range(s, if (end == null || end.isBefore(s)) s else end)) },
                minDate = today, modifier = Modifier.weight(1f), errorText = if (start == null) errorText else null,
            )
            DateField(
                label = stringResource(R.string.ws5_last_day), value = end, enabled = enabled && start != null,
                onValueChange = { e -> onChange(WizardLogic.range(start ?: e, e)) },
                minDate = start ?: today, maxDate = start?.plusDays(WizardLogic.MAX_RANGE_DAYS - 1), modifier = Modifier.weight(1f),
            )
        }
        if (start != null && end != null && start != end) {
            val all = WizardLogic.range(start, end)
            FieldLabel(stringResource(R.string.ws5_shoot_days_pick), hint = stringResource(R.string.ws5_shoot_days_hint))
            ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
                all.forEach { d ->
                    val on = d in sorted
                    val isEdge = d == start || d == end
                    CtFilterChip(
                        Dates.shortUpper(d), selected = on, enabled = enabled && !isEdge,
                        onClick = { onChange(if (on) sorted - d else (sorted + d).sorted()) },
                    )
                }
            }
        }
        if (sorted.isNotEmpty()) {
            val n = sorted.size
            Text(
                pluralDays(n) + " · " + (if (start == end) Dates.short(start!!) else Dates.span(start!!, end!!)),
                style = Ct.type.mono, color = c.accent,
            )
        }
        if (errorText != null && start != null) {
            Text(errorText, style = Ct.type.label.copy(fontSize = 13.sp), color = c.onDangerContainer)
        }
    }
}

@Composable
fun pluralDays(n: Int): String = if (n == 1) stringResource(R.string.ws5_one_day) else stringResource(R.string.ws5_n_days, n)

/** Field-like row that opens the city sheet. */
@Composable
fun CityPickerRow(city: City?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Ct.colors
    Column(modifier.fillMaxWidth()) {
        FieldLabel(stringResource(R.string.ws5_city))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(CtShapes.field).background(c.surfaceRaised)
                .border(1.dp, c.outline, CtShapes.field).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Place, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(city?.name ?: stringResource(R.string.ws5_pick_city), style = Ct.type.body, color = if (city != null) c.text else c.textMuted, modifier = Modifier.weight(1f))
            if (city?.isEarly == true && !city.isLive) CtChip(stringResource(R.string.ws5_early_city), tone = ChipTone.INFO, small = true)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary)
        }
    }
}

/** Listed cities, live first, early cities labelled honestly (PRD §11.2). */
@Composable
fun CityPickerSheet(cities: List<City>, selectedId: String?, onPick: (City) -> Unit, onDismiss: () -> Unit) {
    val c = Ct.colors
    CtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ws5_city_sheet_title), subtitle = stringResource(R.string.ws5_city_sheet_sub)) {
        cities.forEachIndexed { i, city ->
            if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.5f), thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton) { onPick(city) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(city.name, style = Ct.type.body, color = c.text)
                    val note = when {
                        city.isLive -> stringResource(R.string.ws5_city_live)
                        city.isEarly -> stringResource(R.string.ws5_city_early)
                        else -> null
                    }
                    if (note != null) Text(note, style = Ct.type.label, color = c.textMuted)
                }
                if (city.id == selectedId) SelectedMark()
            }
        }
    }
}

/** A heading inside a step ("SHOOT DATES"), slate caps. */
@Composable
fun StepSubhead(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = Ct.type.slate.copy(fontSize = 15.sp, lineHeight = 18.sp), color = Ct.colors.textSecondary,
        modifier = modifier.padding(top = 4.dp).semantics { heading() },
    )
}

/** Review checklist row: green tick when clean, red with the count otherwise. */
@Composable
fun CheckLine(title: String, ok: Boolean, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val cd = title + ". " + stringResource(if (ok) R.string.ws5_check_ok_cd else R.string.ws5_check_bad_cd)
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).semantics(mergeDescendants = true) { contentDescription = cd }, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, contentDescription = null,
            tint = if (ok) c.go else c.stop, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(title, style = Ct.type.body, color = if (ok) c.text else c.onDangerContainer, modifier = Modifier.weight(1f))
    }
}

/** Small mono label used for counters ("7/25 APPLIED"). */
@Composable
fun MonoNote(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Ct.colors.textSecondary) {
    Text(text, style = Ct.type.monoS, color = color, modifier = modifier)
}

/** CAPS chip, e.g. "FIXED" next to "No nudity at auditions". */
@Composable
fun CapsChip(text: String, tone: ChipTone = ChipTone.NEUTRAL) = CtChip(text, tone = tone, style = ChipStyle.CAPS, small = true)
