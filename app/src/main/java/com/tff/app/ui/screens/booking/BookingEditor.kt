package com.tff.app.ui.screens.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.model.BookingTerms
import com.tff.app.model.CompType
import com.tff.app.model.Meals
import com.tff.app.model.PaymentTerms
import com.tff.app.model.Perk
import com.tff.app.model.ProjectRole
import com.tff.app.model.Transport
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.DateField
import com.tff.app.ui.components.IntStepper
import com.tff.app.ui.components.KeyValueRow
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.theme.Ct
import java.time.LocalDate

/**
 * Owner's booking-card editor (S16): pre-filled fields grouped as Role & credit · Dates & call window
 * (post roles: Delivery) · Pay · Perks & logistics · Deliverables · Notes. Every change goes through
 * [onUpdate], which re-normalises the terms so the preview, pay-by date and deliverables stay true.
 */
@Composable
fun BookingEditor(
    t: BookingTerms,
    projectDates: List<LocalDate>,
    onUpdate: ((BookingTerms) -> BookingTerms) -> Unit,
    role: ProjectRole? = null,
    cpFirst: String = "",
) {
    val c = Ct.colors
    val today = AppClock.today()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Role & credit ──
        SectionHeader(stringResource(R.string.ws6_terms_role))
        KeyValueRow(stringResource(R.string.ws6_terms_role_label), BookingCopy.roleLine(t, role))
        CtTextField(t.creditTitle, { v -> onUpdate { it.copy(creditTitle = v.take(60)) } }, stringResource(R.string.ws6_terms_credit),
            singleLine = true, maxLength = 60, showCounter = false, supportingText = stringResource(R.string.ws6_editor_credit_hint))

        // ── Dates & call window / Delivery ──
        if (t.isPostRole) {
            // Picture lock → deliver by → revisions, the order the work happens in.
            SectionHeader(stringResource(R.string.ws6_terms_delivery))
            if (CardText.needsPictureLock(t.roleCode) || t.pictureLockDate != null) {
                DateField(stringResource(R.string.ws6_terms_picture_lock), t.pictureLockDate, { d -> onUpdate { it.copy(pictureLockDate = d) } },
                    supportingText = stringResource(R.string.ws6_editor_lock_hint))
            }
            DateField(stringResource(R.string.ws6_terms_deliver_by), t.deliveryDate, { d -> onUpdate { it.copy(deliveryDate = d) } }, minDate = today)
            IntStepper((t.revisionRounds ?: 0), { n -> onUpdate { it.copy(revisionRounds = n.takeIf { r -> r > 0 }) } }, 0..10,
                label = stringResource(R.string.ws6_terms_revisions))
        } else {
            SectionHeader(stringResource(R.string.ws6_terms_dates))
            val all = (projectDates + t.dates).distinct().sorted()
            Text(stringResource(R.string.ws6_editor_days_hint), style = Ct.type.label, color = c.textMuted)
            ChipFlow {
                all.forEach { d ->
                    val on = d in t.dates
                    CtFilterChip(Dates.shortUpper(d), on, onClick = {
                        onUpdate { cur -> cur.copy(dates = if (on) cur.dates - d else (cur.dates + d).distinct().sorted()) }
                    })
                }
            }
            DateField(stringResource(R.string.ws6_editor_add_day), null, { d -> onUpdate { cur -> cur.copy(dates = (cur.dates + d).distinct().sorted()) } }, minDate = today,
                placeholder = stringResource(R.string.ws6_editor_add_day_hint))
            // Local text: normalize() trims, which would eat spaces while typing.
            var cwText by remember { mutableStateOf(t.callWindow ?: "") }
            CtTextField(cwText, { v -> cwText = v.take(40); onUpdate { it.copy(callWindow = v.take(40)) } }, stringResource(R.string.ws6_terms_call_window),
                singleLine = true, placeholder = stringResource(R.string.ws6e_call_placeholder),
                supportingText = t.callWindow?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ws6_editor_reads_as, CardText.callWindowText(it)) }
                    ?: stringResource(R.string.ws6e_editor_call_missing, cpFirst.ifBlank { "they" }))
        }

        // ── Pay ──
        SectionHeader(stringResource(R.string.ws6_terms_pay))
        ChipFlow {
            CompType.entries.forEach { ct ->
                CtFilterChip(CardText.compTypeLabel(ct), t.compType == ct, onClick = {
                    onUpdate { cur ->
                        cur.copy(
                            compType = ct,
                            paymentTerms = if (ct == CompType.UNPAID) null else cur.paymentTerms ?: PaymentTerms.ON_WRAP,
                        )
                    }
                })
            }
        }
        if (t.compType != CompType.UNPAID) {
            var amountText by remember(t.compType) { mutableStateOf(t.amount?.toString() ?: "") }
            val unit = when (t.compType) {
                CompType.PAID_FLAT -> stringResource(R.string.ws6_editor_unit_flat)
                else -> if (t.isPostRole) stringResource(R.string.ws6_editor_unit_flat) else stringResource(R.string.ws6_editor_unit_day)
            }
            CtTextField(amountText, { v ->
                val digits = v.filter { it.isDigit() }.take(8)
                amountText = digits
                onUpdate { it.copy(amount = digits.toIntOrNull()) }
            }, stringResource(R.string.ws6_editor_amount, Money.symbol(t.currency).trim(), unit), singleLine = true, mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                errorText = if ((t.amount ?: 0) <= 0) stringResource(R.string.ws6_editor_amount_error) else null)
            if (!t.isPostRole) {
                var kitText by remember { mutableStateOf(t.kitFeeDay?.toString() ?: "") }
                CtTextField(kitText, { v ->
                    val digits = v.filter { it.isDigit() }.take(7)
                    kitText = digits
                    onUpdate { it.copy(kitFeeDay = digits.toIntOrNull()) }
                }, stringResource(R.string.ws6_editor_kit, Money.symbol(t.currency).trim()), singleLine = true, mono = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = stringResource(R.string.ws6e_kit_placeholder))
            }
            Text(stringResource(R.string.ws6_terms_terms), style = Ct.type.bodyS, color = c.textSecondary)
            ChipFlow {
                PaymentTerms.entries.forEach { pt ->
                    CtFilterChip(CardText.termsLabel(pt, t.isPostRole).replaceFirstChar { it.uppercase() }, t.paymentTerms == pt, onClick = {
                        onUpdate { it.copy(paymentTerms = pt) }
                    })
                }
            }
            TermsText.payBy(t)?.let { KeyValueRow(stringResource(R.string.ws6_terms_pay_by), it, mono = true) }
        } else {
            Text(stringResource(R.string.ws6_editor_unpaid_hint), style = Ct.type.label, color = c.textMuted)
        }

        // ── Perks & logistics ──
        SectionHeader(stringResource(R.string.ws6_terms_perks))
        val perkOptions = if (t.isPostRole) listOf(Perk.FINAL_FILM_BY_DATE, Perk.STEP_UP_CREDIT) else TermsText.STORED
        ChipFlow {
            perkOptions.forEach { p ->
                val on = p in t.perks
                CtFilterChip(CardText.perkLabel(p), on, onClick = {
                    onUpdate { cur ->
                        val perks = if (on) cur.perks - p else cur.perks + p
                        cur.copy(
                            perks = perks,
                            footageDueWeeks = if (p == Perk.FOOTAGE_BY_DATE && !on) cur.footageDueWeeks ?: 8 else cur.footageDueWeeks,
                            finalFilmDueWeeks = if (p == Perk.FINAL_FILM_BY_DATE && !on) cur.finalFilmDueWeeks ?: 12 else cur.finalFilmDueWeeks,
                        )
                    }
                })
            }
        }
        if (!t.isPostRole && Perk.FOOTAGE_BY_DATE in t.perks) {
            IntStepper(t.footageDueWeeks ?: 8, { n -> onUpdate { it.copy(footageDueWeeks = n) } }, 1..26,
                label = stringResource(R.string.ws6_editor_footage_weeks), suffix = stringResource(R.string.ws6_editor_weeks))
        }
        if (Perk.FINAL_FILM_BY_DATE in t.perks) {
            IntStepper(t.finalFilmDueWeeks ?: 12, { n -> onUpdate { it.copy(finalFilmDueWeeks = n) } }, 1..52,
                label = stringResource(R.string.ws6_editor_final_weeks), suffix = stringResource(R.string.ws6_editor_weeks))
        }
        if (!t.isPostRole) {
            Text(stringResource(R.string.ws6_terms_meals), style = Ct.type.bodyS, color = c.textSecondary)
            ChipFlow {
                Meals.entries.forEach { m ->
                    CtFilterChip(CardText.mealsLabel(m), t.meals == m, onClick = { onUpdate { it.copy(meals = m) } })
                }
            }
            Text(stringResource(R.string.ws6_terms_transport), style = Ct.type.bodyS, color = c.textSecondary)
            ChipFlow {
                Transport.entries.forEach { tr ->
                    CtFilterChip(CardText.transportLabel(tr), t.transport == tr, onClick = { onUpdate { it.copy(transport = tr) } })
                }
            }
            if (t.transport == Transport.MILEAGE_OR_TA_PAID) {
                var taText by remember { mutableStateOf(t.travelAllowancePerDay?.toString() ?: "") }
                CtTextField(taText, { v ->
                    val digits = v.filter { it.isDigit() }.take(6)
                    taText = digits
                    onUpdate { it.copy(travelAllowancePerDay = digits.toIntOrNull()) }
                }, stringResource(R.string.ws6_editor_ta, Money.symbol(t.currency).trim()), singleLine = true, mono = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }

        // ── Deliverables (auto) ──
        if (t.deliverables.isNotEmpty()) {
            SectionHeader(stringResource(R.string.ws6_terms_deliverables))
            Text(stringResource(R.string.ws6_editor_deliverables_hint), style = Ct.type.label, color = c.textMuted)
            t.deliverables.forEach { d ->
                KeyValueRow(
                    com.tff.app.ui.screens.chat.wrapKindLabel(d.kind),
                    d.dueDate?.let { "${stringResource(R.string.ws6_due)} ${Dates.shortUpper(it)}" } ?: stringResource(R.string.ws6_terms_no_date),
                    mono = true,
                )
            }
        }

        // ── Notes ──
        SectionHeader(stringResource(R.string.ws6_terms_notes))
        var notesText by remember { mutableStateOf(t.notes ?: "") }
        CtTextField(notesText, { v -> notesText = v.take(280); onUpdate { it.copy(notes = v.take(280)) } }, stringResource(R.string.ws6_terms_notes_label),
            maxLength = 280, minLines = 2, placeholder = stringResource(R.string.ws6_editor_notes_hint))
        Spacer(Modifier.height(8.dp))
    }
}
