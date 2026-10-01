package com.tff.app.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.domain.AuditionRules
import com.tff.app.domain.CardText
import com.tff.app.domain.MessageSafety
import com.tff.app.domain.SafetyBannerSpec
import com.tff.app.domain.SpanStyleKind
import com.tff.app.domain.TextSpan
import com.tff.app.model.AuditionMode
import com.tff.app.model.AuditionPayload
import com.tff.app.model.AuditionResponse
import com.tff.app.model.BookingCard
import com.tff.app.model.BookingStatus
import com.tff.app.model.MessageFlag
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SafetyBanner
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

/** Bubble text with masked contact tokens ("•••• shared contact hidden") set apart in muted mono. */
@Composable
fun maskedText(body: String): AnnotatedString {
    val c = Ct.colors
    val mono = Ct.type.monoS
    if (!body.contains(MessageSafety.MASK)) return AnnotatedString(body)
    return buildAnnotatedString {
        var i = 0
        while (true) {
            val at = body.indexOf(MessageSafety.MASK, i)
            if (at < 0) { append(body.substring(i)); break }
            append(body.substring(i, at))
            withStyle(SpanStyle(color = c.textMuted, background = c.surfaceHigh, fontFamily = mono.fontFamily, fontSize = mono.fontSize)) {
                append(" ${MessageSafety.MASK} ")
            }
            i = at + MessageSafety.MASK.length
        }
    }
}

/** Booking summary spans → bold role, italic project title (CardText.bookingSummarySpans). */
@Composable
fun summaryText(spans: List<TextSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        when (s.style) {
            SpanStyleKind.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Ct.colors.text)) { append(s.text) }
            SpanStyleKind.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.text) }
            SpanStyleKind.NORMAL -> append(s.text)
        }
    }
}

@Composable
fun DaySeparatorRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Ct.colors.outline.copy(alpha = 0.5f))
        Text(text, style = Ct.type.monoS, color = Ct.colors.textMuted, modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f), color = Ct.colors.outline.copy(alpha = 0.5f))
    }
}

/** System message: centred, muted. Multi-line ones (the role's terms summary) get a quiet card. */
@Composable
fun SystemMessage(body: String) {
    val c = Ct.colors
    if (body.contains('\n')) {
        val lines = body.lines()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(CtShapes.field)
                .background(c.surface)
                .border(1.dp, c.outline.copy(alpha = 0.6f), CtShapes.field)
                .padding(14.dp),
        ) {
            Text(stringResource(R.string.ws6_chat_terms_label), style = Ct.type.slate.copy(fontSize = Ct.type.label.fontSize), color = c.accent)
            Spacer(Modifier.height(6.dp))
            Text(lines.first(), style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize, lineHeight = Ct.type.bodyS.lineHeight), color = c.text)
            lines.drop(1).forEach { l ->
                Spacer(Modifier.height(4.dp))
                Text(l, style = Ct.type.bodyS, color = c.textSecondary)
            }
        }
    } else {
        Text(
            body, style = Ct.type.label.copy(lineHeight = Ct.type.bodyS.lineHeight), color = c.textMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

/** Banners above a flagged bubble (MessageSafety.bannersFor, direction rule applied by the caller). */
@Composable
fun BannersFor(banners: List<SafetyBannerSpec>, onReport: () -> Unit, onBlock: () -> Unit, onLearnMore: () -> Unit) {
    banners.forEach { b ->
        SafetyBanner(b, onReport = onReport, onBlock = onBlock, onLearnMore = onLearnMore, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** A text bubble. Theirs on the left in surface; mine on the right, Tungsten-tinted. Long-press reports. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextBubble(body: String, mine: Boolean, time: String?, onLongPress: (() -> Unit)?) {
    val c = Ct.colors
    val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    val reportLabel = stringResource(R.string.ws6_chat_report_message)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) c.accent.copy(alpha = 0.16f) else c.surfaceRaised)
                .border(1.dp, if (mine) c.accent.copy(alpha = 0.32f) else c.outline.copy(alpha = 0.6f), shape)
                .then(
                    if (onLongPress != null) Modifier
                        .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = reportLabel)
                        .semantics { customActions = listOf(CustomAccessibilityAction(reportLabel) { onLongPress(); true }) }
                    else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(maskedText(body), style = Ct.type.body.copy(fontSize = Ct.type.bodyS.fontSize * 1.07f), color = c.text)
        }
        if (time != null) {
            Text(time, style = Ct.type.monoS, color = c.textMuted, modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp))
        }
    }
}

// ───────────────────────── Booking card message ─────────────────────────

@Composable
fun BookingMessageCard(card: BookingCard?, fallback: String, mine: Boolean, onOpen: () -> Unit) {
    val c = Ct.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(CtShapes.field)
                .background(c.surface)
                .border(1.dp, if (card?.status == BookingStatus.SENT) c.accent.copy(alpha = 0.5f) else c.outline, CtShapes.field)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (card != null) stringResource(R.string.ws6_chat_booking_card_v, card.version) else fallback.uppercase(),
                    style = Ct.type.slate, color = c.text, modifier = Modifier.weight(1f),
                )
                if (card != null) BookingStatusChip(card.status)
            }
            if (card != null) {
                if (card.changedFields.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.ws6_changed_list, changedFieldsText(card.changedFields)), style = Ct.type.label, color = c.accent)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    summaryText(CardText.bookingSummarySpans(card.terms)), style = Ct.type.bodyS, color = c.textSecondary,
                    maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            GhostButton(stringResource(R.string.ws6_chat_open_booking_card), onOpen, Modifier.offset(x = (-16).dp))
        }
    }
}

@Composable
fun BookingStatusChip(status: BookingStatus) {
    val (res, tone) = when (status) {
        BookingStatus.SENT -> R.string.ws6_card_status_sent to ChipTone.INFO
        BookingStatus.ACCEPTED -> R.string.ws6_card_status_accepted to ChipTone.GO
        BookingStatus.DECLINED -> R.string.ws6_card_status_declined to ChipTone.STOP
        BookingStatus.SUPERSEDED -> R.string.ws6_card_status_replaced to ChipTone.NEUTRAL
        BookingStatus.CANCELLED -> R.string.ws6_card_status_cancelled to ChipTone.NEUTRAL
    }
    StatusChip(stringResource(res), tone)
}

// ───────────────────────── Audition card (F20) ─────────────────────────

/**
 * The AUDITION message card: mode, the details for that mode, the fixed safety lines, and the actor's
 * answers. [isActor] decides which actions show; [active] is false once the conversation has ended.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuditionCard(
    payload: AuditionPayload,
    isActor: Boolean,
    active: Boolean,
    otherFirstName: String,
    onWillDo: () -> Unit,
    onCantMake: () -> Unit,
    onSendTape: () -> Unit,
    onReport: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val c = Ct.colors
    val req = payload.request
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CtShapes.card)
            .background(c.surface)
            .border(1.dp, c.accent.copy(alpha = 0.45f), CtShapes.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Theaters, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ws6_audition_title), style = Ct.type.slate, color = c.accent, modifier = Modifier.weight(1f))
            CtChip(CardText.auditionModeLabel(req.mode), small = true)
        }
        Spacer(Modifier.height(12.dp))
        when (req.mode) {
            AuditionMode.SELF_TAPE -> {
                req.dueDate?.let { DetailLine(stringResource(R.string.ws6_audition_tape_due), Dates.shortUpper(it), mono = true) }
                req.sidesUrl?.let { url ->
                    DetailLine(stringResource(R.string.ws6_audition_sides), url.removePrefix("https://").removePrefix("http://"))
                    GhostButton(stringResource(R.string.ws6_audition_open_sides), { onOpenUrl(url) }, Modifier.offset(x = (-16).dp), color = c.info)
                }
            }
            AuditionMode.VIDEO_CALL -> {
                if (req.date != null && req.time != null) DetailLine(stringResource(R.string.ws6_audition_when), "${Dates.shortUpper(req.date)} · ${Dates.time(req.time)}", mono = true)
                req.meetingLink?.let { url -> GhostButton(stringResource(R.string.ws6_audition_join_call), { onOpenUrl(url) }, Modifier.offset(x = (-16).dp), color = c.info) }
            }
            AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> {
                if (req.date != null && req.time != null) DetailLine(stringResource(R.string.ws6_audition_when), "${Dates.shortUpper(req.date)} · ${Dates.time(req.time)}", mono = true)
                DetailLine(stringResource(R.string.ws6_audition_where), listOfNotNull(req.venue, req.address).joinToString("\n"))
                if (req.alsoPresent.isNotEmpty()) DetailLine(stringResource(R.string.ws6_audition_also_present), req.alsoPresent.joinToString(" · "))
            }
        }
        req.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text("“${MessageSafety.mask(it)}”", style = Ct.type.bodyS.copy(fontStyle = FontStyle.Italic), color = c.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        SafetyLines()
        Spacer(Modifier.height(12.dp))
        val first = otherFirstName
        when (payload.response) {
            null -> if (isActor && active) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (req.mode == AuditionMode.SELF_TAPE) PrimaryButton(stringResource(R.string.ws6_audition_send_tape), onSendTape)
                    if (req.mode == AuditionMode.SELF_TAPE) SecondaryButton(stringResource(R.string.ws6_audition_will_do), onWillDo)
                    else PrimaryButton(stringResource(R.string.ws6_audition_will_do), onWillDo)
                    SecondaryButton(stringResource(R.string.ws6_audition_cant), onCantMake)
                    GhostButton(stringResource(R.string.ws1_safety_report), onReport, color = c.stop)
                }
            } else if (!isActor) {
                ResponseLine(stringResource(R.string.ws6_audition_waiting, first), c.textSecondary)
            }
            AuditionResponse.WILL_DO -> {
                ResponseLine(if (isActor) stringResource(R.string.ws6_audition_you_will) else stringResource(R.string.ws6_audition_they_will, first), c.go)
                if (isActor && active && req.mode == AuditionMode.SELF_TAPE) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(stringResource(R.string.ws6_audition_send_tape), onSendTape)
                }
            }
            AuditionResponse.TAPE_SENT -> {
                ResponseLine(if (isActor) stringResource(R.string.ws6_audition_you_sent) else stringResource(R.string.ws6_audition_they_sent, first), c.go)
                payload.tapeUrl?.let { url -> GhostButton(stringResource(R.string.ws6_audition_open_tape), { onOpenUrl(url) }, Modifier.offset(x = (-16).dp), color = c.info) }
            }
            AuditionResponse.CANT_MAKE_IT ->
                ResponseLine(if (isActor) stringResource(R.string.ws6_audition_you_cant) else stringResource(R.string.ws6_audition_they_cant, first), c.textSecondary)
        }
        if (isActor && payload.response != null && active) {
            GhostButton(stringResource(R.string.ws1_safety_report), onReport, Modifier.offset(x = (-16).dp), color = c.stop)
        }
    }
}

/** The fixed lines every audition card carries (F20). */
@Composable
fun SafetyLines(modifier: Modifier = Modifier) {
    val c = Ct.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(CtShapes.field)
            .background(c.infoContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.info, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ws6_audition_every), style = Ct.type.label, color = c.onInfoContainer)
        }
        AuditionRules.SAFETY_LINES.split(" · ").forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.go, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(line, style = Ct.type.bodyS, color = c.text)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = Ct.type.bodyS, color = Ct.colors.textSecondary, modifier = Modifier.width(104.dp))
        Text(value, style = if (mono) Ct.type.mono else Ct.type.bodyS, color = Ct.colors.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ResponseLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize), color = color)
    }
}

/** Which banners a message shows to the viewer (PRD §5.2, direction rule in Appendix C). */
fun bannersForMessage(
    flags: Set<MessageFlag>,
    mine: Boolean,
    senderIsOwnerSide: Boolean,
    bookedLike: Boolean,
): List<SafetyBannerSpec> {
    val all = MessageSafety.bannersFor(flags, senderIsOwnerSide)
    return all.filter { b ->
        when {
            b.flag == MessageFlag.OFF_PLATFORM -> !bookedLike            // to both, until booked
            mine -> false                                               // red/amber banners are for the recipient
            else -> true
        }
    }
}
