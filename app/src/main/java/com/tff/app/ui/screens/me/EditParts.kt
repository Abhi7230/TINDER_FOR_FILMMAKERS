package com.tff.app.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.screens.onboarding.FieldLabel
import com.tff.app.ui.theme.Ct
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A list of short free-text items (gear, tools, films, groups) with an Add field and removable chips. */
@Composable
fun ChipListInput(label: String, items: List<String>, onChange: (List<String>) -> Unit, hint: String, max: Int = 12, modifier: Modifier = Modifier) {
    var typed by rememberSaveable(label) { mutableStateOf("") }
    val full = items.size >= max
    fun add() {
        val t = typed.trim()
        if (t.isNotEmpty() && !full && items.none { it.equals(t, true) }) onChange(items + t)
        typed = ""
    }
    Column(modifier.fillMaxWidth()) {
        CtTextField(
            value = typed, onValueChange = { typed = it }, label = label, placeholder = hint, singleLine = true, maxLength = 60, showCounter = false,
            enabled = !full,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
            trailing = {
                IconButton(onClick = { add() }, enabled = typed.isNotBlank() && !full) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ws7_add) + " " + label, tint = if (typed.isNotBlank()) Ct.colors.accent else Ct.colors.textMuted)
                }
            },
        )
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ChipFlow(spacing = 6.dp, lineSpacing = 6.dp) {
                items.forEach { item ->
                    CtChip(
                        item, icon = Icons.Rounded.Close, onClick = { onChange(items - item) },
                        contentDescription = stringResource(R.string.ws7_edit_remove_item_cd, item),
                    )
                }
            }
        }
    }
}

/** Busy-dates month calendar (S20): tap a day to mark it busy. Past days are disabled. Dates in mono. */
@Composable
fun BusyCalendar(busy: Set<LocalDate>, today: LocalDate, onToggle: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val title = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)).uppercase()
    Column(modifier.fillMaxWidth().clip(com.tff.app.ui.theme.CtShapes.field).background(c.surface).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }, enabled = month.isAfter(YearMonth.from(today))) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.ws7_edit_busy_prev), tint = c.text)
            }
            TimecodeText(title, modifier = Modifier.weight(1f), color = c.text)
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month.isBefore(YearMonth.from(today).plusMonths(11))) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.ws7_edit_busy_next), tint = c.text)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(d, style = Ct.type.monoS, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
        val days = month.lengthOfMonth()
        val cells = lead + days
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0 until 7) {
                    val idx = r * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (idx in 0 until days) {
                            val date = month.atDay(idx + 1)
                            val isBusy = date in busy
                            val past = date.isBefore(today)
                            val isToday = date == today
                            val label = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)) + if (isBusy) ", busy" else ""
                            Box(
                                Modifier
                                    .padding(2.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(if (isBusy) c.stop.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(1.dp, when { isBusy -> c.stop; isToday -> c.accent; else -> androidx.compose.ui.graphics.Color.Transparent }, CircleShape)
                                    .clickable(enabled = !past, role = Role.Checkbox) { onToggle(date) }
                                    .semantics { contentDescription = label; selected = isBusy },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${idx + 1}", style = Ct.type.monoS,
                                    color = when { past -> c.textMuted.copy(alpha = 0.5f); isBusy -> c.onDangerContainer; else -> c.text },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubLabel(text: String) {
    FieldLabel(text, Modifier.padding(bottom = 6.dp))
}
