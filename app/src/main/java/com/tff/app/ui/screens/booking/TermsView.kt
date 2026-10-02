package com.tff.app.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.domain.BookingTermsFactory
import com.tff.app.domain.CardText
import com.tff.app.model.BookingTerms
import com.tff.app.model.CompType
import com.tff.app.model.OwedBy
import com.tff.app.model.Perk
import com.tff.app.model.Transport
import com.tff.app.model.WrapKind
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.screens.chat.summaryText
import com.tff.app.ui.screens.chat.wrapKindLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

// Plain-language values for each booking field, shared by the card view (with its diff) and the editor.

internal object TermsText {
    fun pay(t: BookingTerms): String {
        val a = t.amount?.let { Money.format(it, t.currency) }
        return when (t.compType) {
            CompType.PAID_DAY -> a?.let { "$it/day" } ?: "Paid per day"
            CompType.PAID_FLAT -> a?.let { "$it flat" } ?: "Paid flat fee"
            CompType.STIPEND -> a?.let { if (t.isPostRole) "Stipend $it" else "Stipend $it/day" } ?: "Stipend"
            CompType.UNPAID -> "Unpaid"
        }
    }
    fun kit(t: BookingTerms): String? = t.kitFeeDay?.takeIf { it > 0 }?.let { "${Money.format(it, t.currency)}/day" }
    fun terms(t: BookingTerms): String? = t.paymentTerms?.let { CardText.termsLabel(it, t.isPostRole).replaceFirstChar { c -> c.uppercase() } }
    fun payBy(t: BookingTerms): String? = (t.payByDate ?: BookingTermsFactory.payByDate(t))?.let { Dates.shortUpper(it) }
    fun dates(t: BookingTerms): String? = t.dates.takeIf { it.isNotEmpty() }?.let { CardText.dateList(it) }
    fun callWindow(t: BookingTerms): String? = t.callWindow?.takeIf { it.isNotBlank() }?.let { CardText.callWindowText(it).replaceFirstChar { c -> c.uppercase() } }
    fun delivery(t: BookingTerms): String? = t.deliveryDate?.let { Dates.shortUpper(it) }
    fun pictureLock(t: BookingTerms): String? = t.pictureLockDate?.let { Dates.shortUpper(it) }
    fun revisions(t: BookingTerms): String? = t.revisionRounds?.let { if (it == 1) "1 round" else "$it rounds" }
    fun perks(t: BookingTerms): String? {
        val out = t.perks.filter { it in STORED }.map { p ->
            when (p) {
                Perk.FOOTAGE_BY_DATE -> t.footageDueWeeks?.let { "Footage in $it wks" } ?: CardText.perkLabel(p)
                Perk.FINAL_FILM_BY_DATE -> t.finalFilmDueWeeks?.let { "Final film in $it wks" } ?: CardText.perkLabel(p)
                else -> CardText.perkLabel(p)
            }
        }
        return out.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    fun meals(t: BookingTerms): String? = t.meals?.let { CardText.mealsLabel(it) }
    fun transport(t: BookingTerms): String? = t.transport?.let { tr ->
        val base = CardText.transportLabel(tr)
        if (tr == Transport.MILEAGE_OR_TA_PAID) t.travelAllowancePerDay?.takeIf { it > 0 }?.let { "$base · ${Money.format(it, t.currency)}/day" } ?: base else base
    }
    val STORED = listOf(Perk.TRAVEL_COVERED, Perk.LODGING, Perk.FOOTAGE_BY_DATE, Perk.FINAL_FILM_BY_DATE, Perk.STEP_UP_CREDIT)
}

/** The plain-language summary ("You're booked as Sound Mixer …") in a raised card. */
@Composable
fun SummaryCard(t: BookingTerms, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val c = Ct.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(CtShapes.card)
            .background(c.surfaceRaised)
            .border(1.dp, c.accent.copy(alpha = 0.35f), CtShapes.card)
            .padding(16.dp),
    ) {
        Text(
            summaryText(CardText.bookingSummarySpans(t)),
            style = Ct.type.body.copy(fontSize = Ct.type.bodyS.fontSize * 1.07f), color = c.textSecondary,
            maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** One field, with the old value struck through under it when this version changed it. */
@Composable
private fun DiffRow(label: String, value: String?, old: String?, changed: Boolean, mono: Boolean = false) {
    val c = Ct.colors
    val shown = value ?: stringResource(R.string.ws6_terms_none)
    val oldShown = old ?: stringResource(R.string.ws6_terms_none)
    val cd = if (changed) stringResource(R.string.ws6_terms_changed_cd, label, shown, oldShown) else "$label: $shown"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = cd }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Text(label, style = Ct.type.bodyS, color = c.textSecondary, modifier = Modifier.widthIn(max = 140.dp).padding(top = 1.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    shown,
                    style = if (mono) Ct.type.mono.copy(fontSize = Ct.type.monoS.fontSize * 1.08f) else Ct.type.body.copy(fontSize = Ct.type.bodyS.fontSize * 1.07f),
                    color = if (changed) c.accent else if (value == null) c.textMuted else c.text,
                    textAlign = TextAlign.End,
                )
                if (changed) {
                    Text(
                        oldShown, style = Ct.type.bodyS.copy(textDecoration = TextDecoration.LineThrough), color = c.textMuted,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        HorizontalDivider(color = c.outline.copy(alpha = 0.5f), thickness = 1.dp)
    }
}

/**
 * Every term of one version, grouped like the editor (§6.4). [prev] is the version before it: fields in
 * [changed] show in Tungsten with the old value struck through.
 */
@Composable
fun TermsSections(t: BookingTerms, prev: BookingTerms?, changed: Set<String>) {
    val c = Ct.colors
    fun ch(key: String) = prev != null && key in changed
    SectionHeader(stringResource(R.string.ws6_terms_role))
    DiffRow(stringResource(R.string.ws6_terms_role_label), CardText.roleName(t.roleCode), prev?.let { CardText.roleName(it.roleCode) }, false)
    DiffRow(stringResource(R.string.ws6_terms_credit), t.creditTitle, prev?.creditTitle, ch("creditTitle"))
    Spacer(Modifier.height(12.dp))
    if (t.isPostRole) {
        SectionHeader(stringResource(R.string.ws6_terms_delivery))
        DiffRow(stringResource(R.string.ws6_terms_deliver_by), TermsText.delivery(t), prev?.let(TermsText::delivery), ch("deliveryDate"), mono = true)
        if (t.pictureLockDate != null || prev?.pictureLockDate != null) {
            DiffRow(stringResource(R.string.ws6_terms_picture_lock), TermsText.pictureLock(t), prev?.let(TermsText::pictureLock), ch("pictureLockDate"), mono = true)
        }
        DiffRow(stringResource(R.string.ws6_terms_revisions), TermsText.revisions(t), prev?.let(TermsText::revisions), ch("revisionRounds"))
    } else {
        SectionHeader(stringResource(R.string.ws6_terms_dates))
        DiffRow(stringResource(R.string.ws6_terms_shoot_days), TermsText.dates(t), prev?.let(TermsText::dates), ch("dates"), mono = true)
        DiffRow(stringResource(R.string.ws6_terms_call_window), TermsText.callWindow(t) ?: stringResource(R.string.ws6_terms_on_day_sheet),
            prev?.let { TermsText.callWindow(it) ?: stringResource(R.string.ws6_terms_on_day_sheet) }, ch("callWindow"))
    }
    Spacer(Modifier.height(12.dp))
    SectionHeader(stringResource(R.string.ws6_terms_pay))
    DiffRow(stringResource(R.string.ws6_terms_pay_label), TermsText.pay(t), prev?.let(TermsText::pay), ch("amount"), mono = t.compType != CompType.UNPAID)
    if (!t.isPostRole && (t.kitFeeDay != null || prev?.kitFeeDay != null)) {
        DiffRow(stringResource(R.string.ws6_terms_kit), TermsText.kit(t), prev?.let(TermsText::kit), ch("kitFeeDay"), mono = true)
    }
    if (t.compType != CompType.UNPAID) {
        DiffRow(stringResource(R.string.ws6_terms_terms), TermsText.terms(t), prev?.let(TermsText::terms), ch("paymentTerms"))
        DiffRow(stringResource(R.string.ws6_terms_pay_by), TermsText.payBy(t), prev?.let(TermsText::payBy), ch("paymentTerms") || ch("dates") || ch("deliveryDate"), mono = true)
    }
    Spacer(Modifier.height(12.dp))
    SectionHeader(stringResource(R.string.ws6_terms_perks))
    DiffRow(stringResource(R.string.ws6_terms_perks_label), TermsText.perks(t), prev?.let(TermsText::perks), ch("perks"))
    if (!t.isPostRole) {
        DiffRow(stringResource(R.string.ws6_terms_meals), TermsText.meals(t), prev?.let(TermsText::meals), ch("meals"))
        DiffRow(stringResource(R.string.ws6_terms_transport), TermsText.transport(t), prev?.let(TermsText::transport), ch("transport"))
    }
    val dels = t.deliverables
    if (dels.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.ws6_terms_deliverables))
        dels.forEach { d ->
            val who = stringResource(if (d.owedBy == OwedBy.PRODUCTION) R.string.ws6_owed_production else R.string.ws6_owed_you)
            DiffRow(wrapKindLabel(d.kind), (d.dueDate?.let { "${stringResource(R.string.ws6_due)} ${Dates.shortUpper(it)}" } ?: stringResource(R.string.ws6_terms_no_date)) + " · $who", null, false)
        }
        if (dels.any { it.kind == WrapKind.FOOTAGE }) {
            Text(stringResource(R.string.ws6_terms_footage_def, t.footageDefinition), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionHeader(stringResource(R.string.ws6_terms_notes))
    DiffRow(stringResource(R.string.ws6_terms_notes_label), t.notes, prev?.notes, ch("notes"))
    DiffRow(stringResource(R.string.ws6_terms_rights), t.rightsNote, null, false)
    DiffRow(stringResource(R.string.ws6_terms_payer), t.payerName, null, false)
}
