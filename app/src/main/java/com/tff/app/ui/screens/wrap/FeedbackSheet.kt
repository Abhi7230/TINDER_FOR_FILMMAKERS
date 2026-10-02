package com.tff.app.ui.screens.wrap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.model.DeliveredAnswer
import com.tff.app.model.FeedbackAnswers
import com.tff.app.model.KeptAnswer
import com.tff.app.model.OnTimeAnswer
import com.tff.app.model.PaidAnswer
import com.tff.app.model.ReportReason
import com.tff.app.model.ReportTarget
import com.tff.app.model.ShowedUpAnswer
import com.tff.app.model.TriAnswer
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.ReportSheet
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

/** One forced-choice question: single-select chips. */
@Composable
private fun <T> Question(label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = Ct.type.bodyStrong, color = Ct.colors.text, modifier = Modifier.semantics { heading() })
        if (hint != null) Text(hint, style = Ct.type.label, color = Ct.colors.textMuted)
        Spacer(Modifier.height(8.dp))
        ChipFlow(Modifier.selectableGroup()) {
            options.forEach { (v, l) -> CtFilterChip(l, selected == v, onClick = { onSelect(v) }) }
        }
    }
}

/**
 * S18 private feedback, double-blind: answers unlock when both sides submit, or after 14 days, and only
 * totals of facts are ever shown (n ≥ 3). Questions depend on the side and on on-set vs post roles.
 */
@Composable
fun FeedbackSheet(
    isOwner: Boolean,
    isPostRole: Boolean,
    counterpartFirst: String,
    counterpartId: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (FeedbackAnswers, (String?) -> Unit, () -> Unit) -> Unit,
) {
    val c = Ct.colors
    var paid by rememberSaveable { mutableStateOf<PaidAnswer?>(null) }
    var delivered by rememberSaveable { mutableStateOf<DeliveredAnswer?>(null) }
    var kept by rememberSaveable { mutableStateOf<KeptAnswer?>(null) }
    var safe by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showedUp by rememberSaveable { mutableStateOf<ShowedUpAnswer?>(null) }
    var prepared by rememberSaveable { mutableStateOf<TriAnswer?>(null) }
    var onTime by rememberSaveable { mutableStateOf<OnTimeAnswer?>(null) }
    var revisions by rememberSaveable { mutableStateOf<TriAnswer?>(null) }
    var again by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }

    if (sent) {
        CtBottomSheet(onDismiss = onDismiss, scrollable = false) {
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.go, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.ws6_fb_thanks), style = Ct.type.titleM, color = c.text)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.ws6_fb_thanks_body), style = Ct.type.bodyS, color = c.textSecondary)
                Spacer(Modifier.height(24.dp))
                PrimaryButton(stringResource(R.string.ws1_done), onDismiss, Modifier.fillMaxWidth())
            }
        }
        return
    }

    val yes = stringResource(R.string.ws6_fb_yes)
    val no = stringResource(R.string.ws6_fb_no)
    val complete = when {
        !isOwner -> paid != null && delivered != null && kept != null && safe != null && again != null
        isPostRole -> onTime != null && revisions != null && again != null
        else -> showedUp != null && prepared != null && again != null
    }

    CtBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ws6_fb_title, counterpartFirst)) {
        Row(
            Modifier.fillMaxWidth().clip(CtShapes.field).background(c.infoContainer).padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.info, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.ws6_fb_private), style = Ct.type.bodyS, color = c.onInfoContainer)
        }
        Spacer(Modifier.height(8.dp))
        if (!isOwner) {
            Question(stringResource(R.string.ws6_fb_q_paid), listOf(
                PaidAnswer.ON_TIME to stringResource(R.string.ws6_fb_on_time), PaidAnswer.LATE to stringResource(R.string.ws6_fb_late),
                PaidAnswer.NO to no, PaidAnswer.NOT_PAID_ROLE to stringResource(R.string.ws6_fb_not_paid_role),
            ), paid, { paid = it })
            Question(stringResource(R.string.ws6_fb_q_delivered), listOf(
                DeliveredAnswer.YES to yes, DeliveredAnswer.PARTLY to stringResource(R.string.ws6_fb_partly),
                DeliveredAnswer.NOT_YET to stringResource(R.string.ws6_fb_not_yet), DeliveredAnswer.NO to no,
            ), delivered, { delivered = it })
            Question(stringResource(R.string.ws6_fb_q_kept), listOf(
                KeptAnswer.YES to yes, KeptAnswer.CUT_LATE to stringResource(R.string.ws6_fb_cut_late),
                KeptAnswer.CANCELLED_LATE to stringResource(R.string.ws6_fb_cancelled_late),
            ), kept, { kept = it })
            Question(stringResource(R.string.ws6_fb_q_safe), listOf(true to yes, false to no), safe, { safe = it })
            if (safe == false) {
                Text(stringResource(R.string.ws6_fb_unsafe), style = Ct.type.bodyS, color = c.onDangerContainer)
                GhostButton(stringResource(R.string.ws6_fb_report_privately), { report = true }, color = c.stop)
            }
        } else if (isPostRole) {
            Question(stringResource(R.string.ws6_fb_q_on_time), listOf(
                OnTimeAnswer.YES to yes, OnTimeAnswer.LATE to stringResource(R.string.ws6_fb_late), OnTimeAnswer.DIDNT_DELIVER to stringResource(R.string.ws6_fb_didnt_deliver),
            ), onTime, { onTime = it })
            Question(stringResource(R.string.ws6_fb_q_revisions), listOf(
                TriAnswer.YES to yes, TriAnswer.SOMEWHAT to stringResource(R.string.ws6_fb_somewhat), TriAnswer.NO to no,
            ), revisions, { revisions = it })
        } else {
            Question(stringResource(R.string.ws6_fb_q_showed), listOf(
                ShowedUpAnswer.YES to yes, ShowedUpAnswer.LATE to stringResource(R.string.ws6_fb_late), ShowedUpAnswer.NO_SHOW to stringResource(R.string.ws6_fb_no_show),
            ), showedUp, { showedUp = it })
            Question(stringResource(R.string.ws6_fb_q_prepared), listOf(
                TriAnswer.YES to yes, TriAnswer.SOMEWHAT to stringResource(R.string.ws6_fb_somewhat), TriAnswer.NO to no,
            ), prepared, { prepared = it })
        }
        Question(stringResource(R.string.ws6_fb_q_again), listOf(true to yes, false to no), again, { again = it },
            hint = stringResource(R.string.ws6_fb_again_hint))
        Spacer(Modifier.height(8.dp))
        // Both sides are accountable: say what the other side is asked about you.
        val theirs = when {
            isOwner -> stringResource(R.string.ws6_fb_they_ask_owner)
            isPostRole -> stringResource(R.string.ws6_fb_they_ask_post)
            else -> stringResource(R.string.ws6_fb_they_ask_onset)
        }
        Text(stringResource(R.string.ws6_fb_they_answer, counterpartFirst, theirs), style = Ct.type.label, color = c.textMuted)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Ct.type.bodyS, color = c.stop)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.ws6_fb_submit), {
            val answers = FeedbackAnswers(
                paid = paid.takeIf { !isOwner }, delivered = delivered.takeIf { !isOwner }, feltSafe = safe.takeIf { !isOwner },
                kept = kept.takeIf { !isOwner },
                showedUp = showedUp.takeIf { isOwner && !isPostRole }, prepared = prepared.takeIf { isOwner && !isPostRole },
                deliveredOnTime = onTime.takeIf { isOwner && isPostRole }, revisions = revisions.takeIf { isOwner && isPostRole },
                workAgain = again,
            )
            onSubmit(answers, { e -> error = e }, { sent = true })
        }, Modifier.fillMaxWidth(), enabled = complete, loading = busy)
        if (!complete) {
            Text(stringResource(R.string.ws6_fb_answer_all), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
    }
    if (report) ReportSheet(ReportTarget.PROFILE, counterpartId, onDismiss = { report = false }, prefill = ReportReason.UNSAFE_SET)
}
