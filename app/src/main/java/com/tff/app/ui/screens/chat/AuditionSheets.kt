package com.tff.app.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.domain.AuditionRules
import com.tff.app.domain.CardText
import com.tff.app.model.AuditionMode
import com.tff.app.model.AuditionRequest
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.DateField
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.TimeField
import com.tff.app.ui.theme.Ct
import java.time.LocalDate
import java.time.LocalTime

private val ALSO_PRESENT_SUGGESTIONS = listOf("Director", "Casting assistant", "Producer", "DP", "Co-actor")

/**
 * Owner's audition request (F20, S15). The mode is locked to the listing's `audition_mode`; in-person and
 * video-call times are limited to 08:00–21:00; studio auditions need someone else present. Validated by
 * [AuditionRules] before anything is sent.
 */
@Composable
fun AuditionRequestSheet(
    listingMode: AuditionMode,
    actorFirstName: String,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (AuditionRequest, (String?) -> Unit) -> Unit,
) {
    val c = Ct.colors
    val today = AppClock.today()
    var sides by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<LocalDate?>(today.plusDays(3)) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var time by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var link by rememberSaveable { mutableStateOf("") }
    var venue by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var present by rememberSaveable { mutableStateOf(listOf<String>()) }
    var custom by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    CtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.ws6_audition_sheet_title),
        subtitle = stringResource(R.string.ws6_audition_sheet_subtitle, actorFirstName),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ws6_audition_mode), style = Ct.type.bodyS, color = c.textSecondary)
            Spacer(Modifier.width(10.dp))
            CtChip(CardText.auditionModeLabel(listingMode), tone = ChipTone.ACCENT, filled = true, icon = Icons.Rounded.Lock)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.ws6_audition_mode_locked), style = Ct.type.label, color = c.textMuted)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (listingMode) {
                AuditionMode.SELF_TAPE -> {
                    CtTextField(sides, { sides = it; error = null }, stringResource(R.string.ws6_audition_sides_label),
                        placeholder = "https://", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        supportingText = stringResource(R.string.ws6_audition_sides_hint))
                    DateField(stringResource(R.string.ws6_audition_tape_due), due, { due = it; error = null }, minDate = today)
                }
                AuditionMode.VIDEO_CALL -> {
                    DateField(stringResource(R.string.ws6_audition_date), date, { date = it; error = null }, minDate = today)
                    TimeField(stringResource(R.string.ws6_audition_time), time, { time = it; error = null },
                        min = AuditionRules.EARLIEST, max = AuditionRules.LATEST, supportingText = stringResource(R.string.ws6_audition_hours))
                    CtTextField(link, { link = it; error = null }, stringResource(R.string.ws6_audition_link_label),
                        placeholder = "https://", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = stringResource(R.string.ws6_audition_link_hint))
                }
                AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> {
                    CtTextField(venue, { venue = it; error = null }, stringResource(R.string.ws6_audition_venue), singleLine = true, maxLength = 80, showCounter = false)
                    CtTextField(address, { address = it; error = null }, stringResource(R.string.ws6_audition_address), minLines = 2, maxLength = 200, showCounter = false,
                        supportingText = stringResource(R.string.ws6_audition_address_hint))
                    DateField(stringResource(R.string.ws6_audition_date), date, { date = it; error = null }, minDate = today)
                    TimeField(stringResource(R.string.ws6_audition_time), time, { time = it; error = null },
                        min = AuditionRules.EARLIEST, max = AuditionRules.LATEST, supportingText = stringResource(R.string.ws6_audition_hours))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ws6_audition_also_present_label), style = Ct.type.bodyS, color = c.text)
                        Text(stringResource(R.string.ws6_audition_also_present_hint), style = Ct.type.label, color = c.textMuted)
                        ChipFlow {
                            (ALSO_PRESENT_SUGGESTIONS + present.filter { it !in ALSO_PRESENT_SUGGESTIONS }).forEach { r ->
                                CtFilterChip(r, r in present, onClick = {
                                    present = if (r in present) present - r else present + r
                                    error = null
                                })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CtTextField(custom, { custom = it.take(30) }, null, Modifier.weight(1f), placeholder = stringResource(R.string.ws6_audition_add_role), singleLine = true)
                            IconButton(onClick = {
                                val t = custom.trim()
                                if (t.isNotEmpty() && t !in present) present = present + t
                                custom = ""
                                error = null
                            }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ws6_audition_add_role), tint = c.accent) }
                        }
                    }
                }
            }
            CtTextField(note, { note = it; error = null }, stringResource(R.string.ws6_audition_note), maxLength = 280, minLines = 2,
                placeholder = stringResource(R.string.ws6_audition_note_hint))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ws6_audition_carries), style = Ct.type.label, color = c.textMuted)
                SafetyLines()
            }
            error?.let {
                Text(it, style = Ct.type.bodyS, color = c.stop, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            PrimaryButton(stringResource(R.string.ws6_audition_send), {
                val req = AuditionRequest(
                    mode = listingMode,
                    sidesUrl = sides.trim().takeIf { listingMode == AuditionMode.SELF_TAPE && it.isNotEmpty() },
                    dueDate = due.takeIf { listingMode == AuditionMode.SELF_TAPE },
                    date = date.takeIf { listingMode != AuditionMode.SELF_TAPE },
                    time = time.takeIf { listingMode != AuditionMode.SELF_TAPE },
                    meetingLink = link.trim().takeIf { listingMode == AuditionMode.VIDEO_CALL && it.isNotEmpty() },
                    venue = venue.trim().takeIf { listingMode == AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS && it.isNotEmpty() },
                    address = address.trim().takeIf { listingMode == AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS && it.isNotEmpty() },
                    alsoPresent = if (listingMode == AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS) present else emptyList(),
                    note = note.trim().ifEmpty { null },
                )
                val err = AuditionRules.validate(req, listingMode, AppClock.today())
                if (err != null) error = err else onSend(req) { e -> error = e }
            }, Modifier.fillMaxWidth(), loading = sending)
        }
    }
}

/** Actor's [Send my tape]: one link field. Only the production sees the tape. */
@Composable
fun SendTapeSheet(sending: Boolean, onDismiss: () -> Unit, onSend: (String, (String?) -> Unit) -> Unit) {
    val c = Ct.colors
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val badLink = stringResource(R.string.ws6_tape_bad_link)
    CtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ws6_tape_title), subtitle = stringResource(R.string.ws6_tape_subtitle)) {
        CtTextField(url, { url = it; error = null }, stringResource(R.string.ws6_tape_label), placeholder = "https://", singleLine = true,
            errorText = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done))
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(R.string.ws6_audition_send_tape), {
            val t = url.trim()
            if (!Regex("^https?://\\S+\\.\\S+").containsMatchIn(t)) error = badLink
            else onSend(t) { e -> error = e }
        }, Modifier.fillMaxWidth(), loading = sending)
    }
}
