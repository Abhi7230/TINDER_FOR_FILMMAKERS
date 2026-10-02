package com.tff.app.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.di.AppContainer
import com.tff.app.domain.CardText
import com.tff.app.domain.Notice
import com.tff.app.model.ApplicantView
import com.tff.app.model.BookingTerms
import com.tff.app.model.MatchState
import com.tff.app.model.MatchView
import com.tff.app.model.ReleaseReason
import com.tff.app.model.ReportReason
import com.tff.app.model.ReportTarget
import com.tff.app.model.WithdrawReason
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.DangerButton
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.screens.chat.releaseReasonLabel
import com.tff.app.ui.screens.chat.userMessage
import com.tff.app.ui.screens.chat.withdrawReasonLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Notice for an exit, in hours to day 1 (on-set) or the delivery date (post role); null when not booked. */
private fun noticeHours(v: MatchView): Pair<BookingTerms, Int>? {
    if (v.match.state != MatchState.BOOKED) return null
    val terms = v.acceptedCard?.terms ?: return null
    return terms to Notice.hoursUntil(terms, AppClock.nowMillis(), AppClock.zone)
}

/** Notice line in Greenlit (neutral) or the amber container (late). */
@Composable
private fun NoticeBox(text: String, late: Boolean) {
    val c = Ct.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CtShapes.field)
            .background(if (late) c.dangerContainer else c.go.copy(alpha = 0.10f))
            .border(1.dp, if (late) c.stop.copy(alpha = 0.45f) else c.go.copy(alpha = 0.35f), CtShapes.field)
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (late) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle, contentDescription = null,
            tint = if (late) c.stop else c.go, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Ct.type.bodyS, color = if (late) c.onDangerContainer else c.text)
    }
}

/**
 * S16W Withdraw sheet: reason chips, the computed notice ("4 days' notice: this won't count against
 * you"; post roles measure against the delivery date with a 7-day threshold), [Withdraw]. "Safety
 * concern" also offers [Report]. Calls the Repo itself so any screen can show it.
 */
@Composable
fun WithdrawSheet(
    view: MatchView,
    nav: AppNavigator,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    preselect: WithdrawReason? = null,
) {
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    var reason by rememberSaveable { mutableStateOf(preselect) }
    var note by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf(false) }
    val role = CardText.roleName(view.item.role)
    val notice = noticeHours(view)

    CtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.ws6_withdraw_title, view.item.project.title),
        subtitle = stringResource(R.string.ws6_withdraw_subtitle, role),
    ) {
        Text(stringResource(R.string.ws6_withdraw_why), style = Ct.type.bodyStrong, color = Ct.colors.text)
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            WithdrawReason.entries.forEach { r ->
                CtFilterChip(withdrawReasonLabel(r), reason == r, onClick = { reason = r; error = null })
            }
        }
        if (reason == WithdrawReason.SAFETY) {
            Spacer(Modifier.height(14.dp))
            InfoTipCard(
                text = stringResource(R.string.ws6_withdraw_safety_tip),
                actionLabel = stringResource(R.string.ws1_safety_report),
                onAction = { report = true },
            )
        }
        Spacer(Modifier.height(16.dp))
        CtTextField(note, { note = it }, stringResource(R.string.ws6_withdraw_note), maxLength = 200, minLines = 2,
            placeholder = stringResource(R.string.ws6_withdraw_note_hint))
        Spacer(Modifier.height(16.dp))
        if (notice != null) {
            val (terms, hours) = notice
            NoticeBox(Notice.withdrawLine(terms, hours), late = !Notice.isNeutral(terms, hours))
        } else {
            NoticeBox(stringResource(R.string.ws6_withdraw_not_booked), late = false)
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Ct.type.bodyS, color = Ct.colors.stop)
        }
        Spacer(Modifier.height(20.dp))
        DangerButton(stringResource(R.string.ws6_withdraw_cta), {
            val r = reason ?: return@DangerButton
            busy = true
            scope.launch {
                val result = runCatching { AppContainer.repo.withdraw(view.match.id, r, note.trim().ifEmpty { null }) }.getOrElse { Result.failure(it) }
                busy = false
                result.onSuccess {
                    nav.toast(res.getString(R.string.ws6_withdraw_done, view.counterpart.profile.shortName))
                    onDone()
                }.onFailure { error = it.userMessage() ?: res.getString(R.string.ws6_generic_error) }
            }
        }, Modifier.fillMaxWidth(), enabled = reason != null, loading = busy)
        if (reason == null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ws6_pick_reason), style = Ct.type.label, color = Ct.colors.textMuted, modifier = Modifier.padding(start = 4.dp))
        }
    }
    if (report) {
        ReportSheet(ReportTarget.PROFILE, view.counterpart.profile.id, onDismiss = { report = false }, prefill = ReportReason.UNSAFE_SET)
    }
}

/**
 * Owner's Release sheet: reason chips, computed notice, and the honest line under 72h ("Less than 72
 * hours' notice. This will show in your Kept bookings."). Before booking it ends the talks instead.
 * After a booked release, offers the slot to backup #1 when there is one.
 */
@Composable
fun ReleaseSheet(view: MatchView, nav: AppNavigator, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    var reason by rememberSaveable { mutableStateOf<ReleaseReason?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var backup by remember { mutableStateOf<ApplicantView?>(null) }
    var offering by remember { mutableStateOf(false) }
    val booked = view.match.state == MatchState.BOOKED
    val first = view.counterpart.profile.shortName
    val notice = noticeHours(view)

    val b = backup
    if (b != null) {
        CtBottomSheet(onDismiss = onDone, title = stringResource(R.string.ws6_release_slot_open), subtitle = stringResource(R.string.ws6_release_offer_body, b.card.profile.shortName)) {
            PrimaryButton(stringResource(R.string.ws6_release_offer_cta), {
                offering = true
                scope.launch {
                    val r = runCatching { AppContainer.repo.offerSlotToBackup(view.item.role.id) }.getOrElse { Result.failure(it) }
                    offering = false
                    r.onSuccess { nav.toast(res.getString(R.string.ws6_release_offered, b.card.profile.shortName.removeSuffix("."))) }
                        .onFailure { nav.toast(it.userMessage() ?: res.getString(R.string.ws6_generic_error)) }
                    onDone()
                }
            }, Modifier.fillMaxWidth(), loading = offering)
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.ws1_not_now), onDone, Modifier.fillMaxWidth())
        }
        return
    }

    CtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(if (booked) R.string.ws6_release_title else R.string.ws6_end_talks_title, first),
        subtitle = stringResource(R.string.ws6_release_subtitle, CardText.roleName(view.item.role), view.item.project.title),
    ) {
        Text(stringResource(R.string.ws6_release_why), style = Ct.type.bodyStrong, color = Ct.colors.text)
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            ReleaseReason.entries.forEach { r ->
                CtFilterChip(releaseReasonLabel(r), reason == r, onClick = { reason = r; error = null })
            }
        }
        Spacer(Modifier.height(16.dp))
        if (notice != null) {
            val (_, hours) = notice
            val late = Notice.releaseLine(hours)
            NoticeBox(late ?: res.getString(R.string.ws6_release_neutral, Notice.noticeText(hours)), late = late != null)
        } else {
            NoticeBox(stringResource(R.string.ws6_release_not_booked), late = false)
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Ct.type.bodyS, color = Ct.colors.stop)
        }
        Spacer(Modifier.height(20.dp))
        DangerButton(stringResource(if (booked) R.string.ws6_release_cta else R.string.ws6_end_talks_cta), {
            val r = reason ?: return@DangerButton
            busy = true
            scope.launch {
                val repo = AppContainer.repo
                val result = runCatching { repo.release(view.match.id, r) }.getOrElse { Result.failure(it) }
                result.onSuccess {
                    nav.toast(if (booked) res.getString(R.string.ws6_release_done, first.removeSuffix(".")) else res.getString(R.string.ws6_end_talks_done, first))
                    val bench = if (booked) runCatching { repo.project(view.item.project.id).first()?.backups?.get(view.item.role.id) }.getOrNull() else null
                    busy = false
                    val top = bench?.firstOrNull()
                    if (top != null) backup = top else onDone()
                }.onFailure {
                    busy = false
                    error = it.userMessage() ?: res.getString(R.string.ws6_generic_error)
                }
            }
        }, Modifier.fillMaxWidth(), enabled = reason != null, loading = busy)
        if (reason == null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ws6_pick_reason), style = Ct.type.label, color = Ct.colors.textMuted, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
