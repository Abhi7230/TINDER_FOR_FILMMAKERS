package com.tff.app.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.domain.CardText
import com.tff.app.model.DateWindow
import com.tff.app.model.DeckFilters
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.MultiChipPicker
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SegmentedControl
import com.tff.app.ui.components.SliderRow
import com.tff.app.ui.components.ToggleRow
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlin.math.roundToInt

/**
 * S08 Filters sheet: the seven MVP filters (paid only · distance · remote only · dates · ID-verified
 * owners · footage by a date · languages), [Reset] [Apply], and the note that hard limits live in the
 * profile. [preview] counts what a filter set would show, for the "Show 3 roles" button.
 */
@Composable
fun FiltersSheet(
    current: DeckFilters,
    languages: List<String>,
    showDistance: Boolean,
    preview: (DeckFilters) -> Int,
    onApply: (DeckFilters) -> Unit,
    onDismiss: () -> Unit,
    onEditProfile: (() -> Unit)?,
) {
    val c = Ct.colors
    var f by remember { mutableStateOf(current) }
    val count = remember(f) { preview(f) }
    CtBottomSheet(onDismiss, title = stringResource(R.string.ws4_filters_title)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleRow(stringResource(R.string.ws4_filter_paid), f.paidOnly, { f = f.copy(paidOnly = it) }, subtitle = stringResource(R.string.ws4_filter_paid_sub))
            if (showDistance) {
                val max = DeckLogic.DISTANCE_SLIDER_MAX
                val any = stringResource(R.string.ws4_filter_distance_any)
                SliderRow(
                    title = stringResource(R.string.ws4_filter_distance),
                    value = (f.maxDistanceKm ?: max).toFloat(),
                    onValueChange = { v -> val km = v.roundToInt(); f = f.copy(maxDistanceKm = if (km >= max) null else km.coerceAtLeast(1)) },
                    valueRange = 1f..max.toFloat(),
                    valueLabel = { v -> if (v.roundToInt() >= max) any.uppercase() else "≤${v.roundToInt()} KM" },
                    modifier = Modifier.padding(vertical = 4.dp),
                    ticks = listOf(2f to "2", 10f to "10", 25f to "25", 50f to "50", max.toFloat() to any.uppercase()),
                )
            }
            ToggleRow(stringResource(R.string.ws4_filter_remote), f.remoteOnly, { f = f.copy(remoteOnly = it) }, subtitle = stringResource(R.string.ws4_filter_remote_sub))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ws4_filter_dates), style = Ct.type.body, color = c.text)
            Spacer(Modifier.height(8.dp))
            val windows = listOf(DateWindow.THIS_WEEK, DateWindow.NEXT_30_DAYS, DateWindow.ANY)
            SegmentedControl(
                listOf(stringResource(R.string.ws4_filter_dates_week), stringResource(R.string.ws4_filter_dates_30), stringResource(R.string.ws4_filter_dates_any)),
                windows.indexOf(f.dates).coerceAtLeast(0),
                { f = f.copy(dates = windows[it]) },
            )
            if (f.dates != DateWindow.ANY) {
                Text(stringResource(R.string.ws4_filter_dates_note), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(stringResource(R.string.ws4_filter_id), f.idVerifiedOwnersOnly, { f = f.copy(idVerifiedOwnersOnly = it) }, subtitle = stringResource(R.string.ws4_filter_id_sub))
            ToggleRow(stringResource(R.string.ws4_filter_footage), f.footageByDate, { f = f.copy(footageByDate = it) }, subtitle = stringResource(R.string.ws4_filter_footage_sub))
            if (languages.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ws4_filter_languages), style = Ct.type.body, color = c.text)
                Text(stringResource(R.string.ws4_filter_languages_sub), style = Ct.type.bodyS, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                MultiChipPicker(
                    options = languages, selected = f.languages,
                    onToggle = { l -> f = f.copy(languages = if (l in f.languages) f.languages - l else f.languages + l) },
                    label = { CardText.languageName(it) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (count == 0) {
            // Say it before they tap, and say what to change; applying anyway is allowed but not the loud button.
            Text(
                stringResource(R.string.ws4_filters_none_hint), style = Ct.type.bodyS, color = c.onWarnContainer,
                modifier = Modifier.fillMaxWidth().clip(CtShapes.field).background(c.warnContainer).padding(12.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(stringResource(R.string.ws4_filters_reset), { f = DeckFilters() }, Modifier.weight(1f), enabled = f != DeckFilters())
            if (count == 0) {
                SecondaryButton(stringResource(R.string.ws4_filters_apply_anyway), { onApply(f) }, Modifier.weight(1.4f))
            } else {
                PrimaryButton(pluralStringResource(R.plurals.ws4_filters_apply_n, count, count), { onApply(f) }, Modifier.weight(1.4f))
            }
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(CtShapes.field).background(c.surfaceRaised).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ws4_filters_hard_limits), style = Ct.type.bodyS, color = c.textSecondary, modifier = Modifier.weight(1f))
            if (onEditProfile != null) GhostButton(stringResource(R.string.ws4_edit), onEditProfile)
        }
    }
}
