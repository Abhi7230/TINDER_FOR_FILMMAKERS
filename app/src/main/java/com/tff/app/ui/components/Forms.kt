package com.tff.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// ───────────────────────── Text field ─────────────────────────

/**
 * Dark outlined text field with the label above it. [maxLength] caps input and shows a mono "42/140"
 * counter (hide it with [showCounter] = false). [errorText] replaces [supportingText] and turns the outline Safelight; say how to fix it.
 */
@Composable
fun CtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    maxLength: Int? = null,
    showCounter: Boolean = maxLength != null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    mono: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = Ct.colors
    val isError = errorText != null
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, style = Ct.type.bodyS.copy(fontWeight = Ct.type.label.fontWeight), color = if (isError) c.onDangerContainer else c.textSecondary)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(if (maxLength != null) it.take(maxLength) else it) },
            modifier = Modifier.fillMaxWidth().semantics { if (label != null) contentDescription = label },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = if (mono) Ct.type.mono.copy(fontSize = 16.sp, color = c.text) else Ct.type.body.copy(color = c.text),
            placeholder = placeholder?.let { { Text(it, style = Ct.type.body, color = c.textMuted) } },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = c.textSecondary) } },
            trailingIcon = trailing,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = CtShapes.field,
            colors = ctTextFieldColors(),
        )
        val support = errorText ?: supportingText
        if (support != null || (maxLength != null && showCounter)) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    support ?: "", style = Ct.type.label, color = if (isError) c.onDangerContainer else c.textMuted,
                    modifier = Modifier.weight(1f),
                )
                if (maxLength != null && showCounter) {
                    val near = value.length >= (maxLength * 0.9f)
                    Text("${value.length}/$maxLength", style = Ct.type.monoS, color = if (near) c.accent else c.textMuted)
                }
            }
        }
    }
}

@Composable
private fun ctTextFieldColors() = Ct.colors.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.text, unfocusedTextColor = c.text, disabledTextColor = c.textMuted, errorTextColor = c.text,
        focusedContainerColor = c.surfaceRaised, unfocusedContainerColor = c.surfaceRaised, disabledContainerColor = c.surface, errorContainerColor = c.surfaceRaised,
        cursorColor = c.accent, errorCursorColor = c.stop,
        selectionColors = TextSelectionColors(handleColor = c.accent, backgroundColor = c.accent.copy(alpha = 0.3f)),
        focusedBorderColor = c.accent, unfocusedBorderColor = c.outline, disabledBorderColor = c.outline.copy(alpha = 0.5f), errorBorderColor = c.stop,
        focusedLeadingIconColor = c.textSecondary, unfocusedLeadingIconColor = c.textSecondary,
        focusedTrailingIconColor = c.textSecondary, unfocusedTrailingIconColor = c.textSecondary,
        focusedPlaceholderColor = c.textMuted, unfocusedPlaceholderColor = c.textMuted,
    )
}

// ───────────────────────── Segmented control ─────────────────────────

/** Pill segmented control ("Gigs | Crew up", "Mine | Joined", levels). Selected segment in Tungsten. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Ct.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CtShapes.pill)
            .background(c.surfaceRaised)
            .border(1.dp, c.outline, CtShapes.pill)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        val n = options.size.coerceAtLeast(1)
        val segW = maxWidth / n
        val x by animateDpAsState(segW * selectedIndex.coerceIn(0, n - 1), label = "segX")
        Box(Modifier.offset(x = x).width(segW).fillMaxHeight().clip(CtShapes.pill).background(if (enabled) c.accent else c.surfaceHigh))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                val fg by animateColorAsState(
                    when { selected && enabled -> c.onAccent; selected -> c.text; else -> c.textSecondary }, label = "segFg$i",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CtShapes.pill)
                        .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = Ct.type.bodyS.copy(fontSize = 14.sp, fontWeight = Ct.type.bodyStrong.fontWeight),
                        color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

// ───────────────────────── Toggle, slider ─────────────────────────

/** A row with a title, optional subtitle and a Tungsten switch. The whole row toggles. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val c = Ct.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CtShapes.field)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.body, color = if (enabled) c.text else c.textMuted)
            if (subtitle != null) Text(subtitle, style = Ct.type.bodyS, color = c.textSecondary)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, checkedBorderColor = c.accent,
                uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.outline,
                disabledCheckedTrackColor = c.accent.copy(alpha = 0.4f), disabledUncheckedTrackColor = c.surface,
            ),
        )
    }
}

/** Title + mono value + Tungsten slider (radius "15 km", hours cap "≤10h"). */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    subtitle: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = Ct.colors
    val shown = valueLabel(value)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Ct.type.body, color = c.text, modifier = Modifier.weight(1f))
            Text(shown, style = Ct.type.mono.copy(fontSize = 16.sp), color = c.accent)
        }
        if (subtitle != null) Text(subtitle, style = Ct.type.bodyS, color = c.textSecondary)
        Slider(
            value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = shown },
            colors = SliderDefaults.colors(
                thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surfaceHigh,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

// ───────────────────────── Choice cards ─────────────────────────

/** One option for [ChoiceCards]. */
data class ChoiceOption(val title: String, val body: String? = null, val icon: ImageVector? = null)

/** Big radio cards for a few weighty options (onboarding comp mode: Paid only / Paid + selective / Open to unpaid). */
@Composable
fun ChoiceCards(
    options: List<ChoiceOption>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ct.colors
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, o ->
            val selected = i == selectedIndex
            val border by animateColorAsState(if (selected) c.accent else c.outline, label = "choiceBorder")
            val bg by animateColorAsState(if (selected) c.accent.copy(alpha = 0.08f) else c.surface, label = "choiceBg")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(CtShapes.field)
                    .background(bg)
                    .border(if (selected) 1.5.dp else 1.dp, border, CtShapes.field)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (o.icon != null) {
                    Icon(o.icon, contentDescription = null, tint = if (selected) c.accent else c.textSecondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(o.title, style = Ct.type.bodyStrong, color = c.text)
                    if (o.body != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(o.body, style = Ct.type.bodyS, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                RadioDot(selected)
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val c = Ct.colors
    Box(
        Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) c.accent else c.textMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.accent))
    }
}

// ───────────────────────── Stepper ─────────────────────────

/** − value + stepper (role count, hours cap, footage weeks). Value in mono. */
@Composable
fun IntStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String? = null,
    suffix: String? = null,
) {
    val c = Ct.colors
    val name = label ?: ""
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, style = Ct.type.body, color = c.text, modifier = Modifier.weight(1f))
        StepButton(Icons.Rounded.Remove, stringResource(R.string.ws1_decrease, name), enabled = value > range.first) { onValueChange((value - 1).coerceIn(range)) }
        Text(
            if (suffix != null) "$value $suffix" else "$value",
            style = Ct.type.mono.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp).semantics { stateDescription = if (suffix != null) "$value $suffix" else "$value" },
        )
        StepButton(Icons.Rounded.Add, stringResource(R.string.ws1_increase, name), enabled = value < range.last) { onValueChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, cd: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Ct.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(1.dp, if (enabled) c.textMuted.copy(alpha = 0.7f) else c.outline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) c.text else c.textMuted, modifier = Modifier.size(20.dp))
    }
}

// ───────────────────────── Date & time ─────────────────────────

/** The field-like button behind [DateField] and [TimeField]. */
@Composable
private fun PickerField(
    label: String?,
    text: String?,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    errorText: String?,
    supportingText: String?,
) {
    val c = Ct.colors
    val isError = errorText != null
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, style = Ct.type.bodyS.copy(fontWeight = Ct.type.label.fontWeight), color = if (isError) c.onDangerContainer else c.textSecondary)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CtShapes.field)
                .background(if (enabled) c.surfaceRaised else c.surface)
                .border(1.dp, if (isError) c.stop else c.outline, CtShapes.field)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            if (text != null) Text(text, style = Ct.type.mono.copy(fontSize = 16.sp), color = if (enabled) c.text else c.textMuted)
            else Text(placeholder, style = Ct.type.body, color = c.textMuted)
        }
        val support = errorText ?: supportingText
        if (support != null) {
            Spacer(Modifier.height(6.dp))
            Text(support, style = Ct.type.label, color = if (isError) c.onDangerContainer else c.textMuted)
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Date field that opens the Material 3 date picker (themed darkroom). Shows "SAT 14 NOV" in mono
 * (with the year when it isn't this year). [minDate]/[maxDate] limit what can be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String?,
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    enabled: Boolean = true,
    placeholder: String = stringResource(R.string.ws1_pick_date),
    supportingText: String? = null,
    errorText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val today = AppClock.today()
    val text = value?.let { if (it.year == today.year) Dates.shortUpper(it) else "${Dates.shortUpper(it)} ${it.year}" }
    PickerField(label, text, placeholder, Icons.Rounded.CalendarMonth, { open = true }, modifier, enabled, errorText, supportingText)
    if (!open) return
    val c = Ct.colors
    val selectable = remember(minDate, maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = utcTimeMillis.toUtcDate()
                return (minDate == null || !d.isBefore(minDate)) && (maxDate == null || !d.isAfter(maxDate))
            }
            override fun isSelectableYear(year: Int): Boolean =
                (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = value?.toUtcMillis(),
        initialDisplayedMonthMillis = (value ?: minDate ?: today).toUtcMillis(),
        selectableDates = selectable,
    )
    val colors = DatePickerDefaults.colors(
        containerColor = c.surface,
        titleContentColor = c.textSecondary, headlineContentColor = c.text,
        weekdayContentColor = c.textMuted, subheadContentColor = c.textSecondary,
        navigationContentColor = c.text, yearContentColor = c.text, currentYearContentColor = c.accent,
        selectedYearContentColor = c.onAccent, selectedYearContainerColor = c.accent,
        dayContentColor = c.text, disabledDayContentColor = c.textMuted.copy(alpha = 0.5f),
        selectedDayContentColor = c.onAccent, selectedDayContainerColor = c.accent,
        todayContentColor = c.accent, todayDateBorderColor = c.accent, dividerColor = c.outline,
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onValueChange(it.toUtcDate()) }; open = false }, enabled = state.selectedDateMillis != null) {
                Text(stringResource(R.string.ws1_ok), color = if (state.selectedDateMillis != null) c.accent else c.textMuted, style = Ct.type.bodyStrong)
            }
        },
        dismissButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.ws1_cancel), color = c.textSecondary, style = Ct.type.bodyStrong) }
        },
        colors = colors,
    ) {
        DatePicker(state = state, colors = colors, showModeToggle = false)
    }
}

/**
 * Time field with a 24-hour Material 3 time picker ("07:30" in mono). [min]/[max] bound the pick,
 * e.g. in-person auditions 08:00–21:00 (F20); OK stays disabled outside the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String?,
    value: LocalTime?,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    min: LocalTime? = null,
    max: LocalTime? = null,
    enabled: Boolean = true,
    placeholder: String = stringResource(R.string.ws1_pick_time),
    supportingText: String? = null,
    errorText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, value?.let { Dates.time(it) }, placeholder, Icons.Rounded.Schedule, { open = true }, modifier, enabled, errorText, supportingText)
    if (!open) return
    val c = Ct.colors
    val initial = value ?: min ?: LocalTime.of(7, 0)
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    val picked = LocalTime.of(state.hour, state.minute)
    val inRange = (min == null || !picked.isBefore(min)) && (max == null || !picked.isAfter(max))
    AlertDialog(
        onDismissRequest = { open = false },
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.text,
        title = label?.let { { Text(it, style = Ct.type.titleM, color = c.text) } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.surfaceRaised, clockDialSelectedContentColor = c.onAccent, clockDialUnselectedContentColor = c.text,
                        selectorColor = c.accent, containerColor = c.surface,
                        timeSelectorSelectedContainerColor = c.accent.copy(alpha = 0.18f), timeSelectorUnselectedContainerColor = c.surfaceRaised,
                        timeSelectorSelectedContentColor = c.accent, timeSelectorUnselectedContentColor = c.text,
                    ),
                )
                if (!inRange && min != null && max != null) {
                    Text(
                        stringResource(R.string.ws1_time_out_of_range, Dates.time(min), Dates.time(max)),
                        style = Ct.type.bodyS, color = c.onDangerContainer, textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onValueChange(picked); open = false }, enabled = inRange) {
                Text(stringResource(R.string.ws1_ok), color = if (inRange) c.accent else c.textMuted, style = Ct.type.bodyStrong)
            }
        },
        dismissButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.ws1_cancel), color = c.textSecondary, style = Ct.type.bodyStrong) }
        },
    )
}

// ───────────────────────── Multi chip picker ─────────────────────────

/**
 * Wrapping multi-select chips (languages, perks, content flags). With [maxSelected], unselected chips
 * disable once the limit is reached and a hint says so.
 */
@Composable
fun <T> MultiChipPicker(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    maxSelected: Int? = null,
    icon: ((T) -> ImageVector?)? = null,
) {
    val full = maxSelected != null && selected.size >= maxSelected
    Column(modifier.fillMaxWidth()) {
        ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
            options.forEach { o ->
                val isSel = o in selected
                CtFilterChip(label(o), isSel, onClick = { onToggle(o) }, enabled = isSel || !full, icon = icon?.invoke(o))
            }
        }
        if (full) {
            Text(stringResource(R.string.ws1_max_selected, maxSelected!!), style = Ct.type.label, color = Ct.colors.textMuted)
        }
    }
}

/** Small "✓ Selected" mark for custom rows. */
@Composable
fun SelectedMark(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.ws1_selected)
    Box(
        modifier.size(24.dp).clip(CircleShape).background(Ct.colors.accent).clearAndSetSemantics { contentDescription = label; role = Role.Image },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Ct.colors.onAccent, modifier = Modifier.size(16.dp)) }
}
