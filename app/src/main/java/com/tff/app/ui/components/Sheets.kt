package com.tff.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.data.RepoException
import com.tff.app.di.AppContainer
import com.tff.app.model.ReportReason
import com.tff.app.model.ReportTarget
import com.tff.app.model.Session
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.launch

/**
 * The app's modal bottom sheet: surface colour, 28dp top corners, a slim handle, 24dp side padding.
 * Insets (navigation bar, keyboard) are handled by the sheet. Content scrolls if it's tall.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ct.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = CtShapes.sheet,
        containerColor = c.surface,
        contentColor = c.text,
        tonalElevation = 0.dp,
        scrimColor = c.bg.copy(alpha = 0.72f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.outline))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        ) {
            if (title != null) {
                Text(title, style = Ct.type.titleL, color = c.text, modifier = Modifier.semantics { heading() })
                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, style = Ct.type.bodyS, color = c.textSecondary)
                }
                Spacer(Modifier.height(20.dp))
            }
            content()
        }
    }
}

/**
 * Guest prompt (S05 guest, F1): "Free forever. No fees to apply." with [Sign in] and [Not now].
 * Usually shown for you by [rememberGuestGate].
 */
@Composable
fun SignInPromptSheet(onDismiss: () -> Unit, onSignIn: () -> Unit) {
    val c = Ct.colors
    CtBottomSheet(onDismiss, scrollable = false) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Movie, contentDescription = null, tint = c.accent, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ws1_signin_title), style = Ct.type.titleL, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ws1_signin_body), style = Ct.type.bodyS, color = c.textSecondary)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.ws1_signin_cta), onSignIn, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        GhostButton(stringResource(R.string.ws1_not_now), onDismiss, Modifier.fillMaxWidth(), color = c.textSecondary)
    }
}

/**
 * Guest gate (F1, BUILD_GUIDE rule 5). `gate.require { apply() }` runs the action for signed-in users
 * and shows [SignInPromptSheet] for guests. [check] is the same as a Boolean (for `SwipeDeck.canSwipe`).
 */
@Stable
class GuestGate internal constructor(private val session: State<Session?>) {
    internal var prompting by mutableStateOf(false)

    /** True when nobody is signed in (reactive). */
    val isGuest: Boolean get() = session.value == null

    /** Run [action] if signed in, otherwise show the sign-in prompt. */
    fun require(action: () -> Unit) {
        if (AppContainer.repo.session.value != null) action() else prompting = true
    }

    /** True if signed in; otherwise shows the prompt and returns false. */
    fun check(): Boolean {
        if (AppContainer.repo.session.value != null) return true
        prompting = true
        return false
    }

    /** Show the prompt now. */
    fun prompt() { prompting = true }
}

/** Remember a [GuestGate] for this screen; it shows the sign-in sheet itself. [Sign in] → `nav.toWelcome()`. */
@Composable
fun rememberGuestGate(nav: AppNavigator): GuestGate {
    val session = AppContainer.repo.session.collectAsStateWithLifecycle()
    val gate = remember { GuestGate(session) }
    if (gate.prompting) {
        SignInPromptSheet(
            onDismiss = { gate.prompting = false },
            onSignIn = { gate.prompting = false; nav.toWelcome() },
        )
    }
    return gate
}

/** Friendly label for a report reason (§5.5). */
@Composable
fun reportReasonLabel(r: ReportReason): String = stringResource(
    when (r) {
        ReportReason.MONEY_REQUEST -> R.string.ws1_report_reason_money
        ReportReason.SEXUAL_INAPPROPRIATE -> R.string.ws1_report_reason_sexual
        ReportReason.FAKE_IMPERSONATION -> R.string.ws1_report_reason_fake
        ReportReason.UNSAFE_SET -> R.string.ws1_report_reason_unsafe
        ReportReason.HARASSMENT -> R.string.ws1_report_reason_harassment
        ReportReason.NOT_PAID -> R.string.ws1_report_reason_not_paid
        ReportReason.SPAM -> R.string.ws1_report_reason_spam
        ReportReason.OTHER -> R.string.ws1_report_reason_other
    }
)

/**
 * Report sheet (S23, §5.5): pick a reason, add details (≤500, required for "Other"), send. Calls
 * `repo.report(...)` and confirms with "Thanks. We aim to review within 24 hours." [prefill] picks a
 * reason up front (e.g. UNSAFE_SET from an audition card).
 */
@Composable
fun ReportSheet(
    target: ReportTarget,
    targetId: String,
    onDismiss: () -> Unit,
    prefill: ReportReason? = null,
) {
    val c = Ct.colors
    val scope = rememberCoroutineScope()
    var reason by rememberSaveable { mutableStateOf(prefill) }
    var details by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val genericError = stringResource(R.string.ws1_report_error)
    val needDetails = reason == ReportReason.OTHER && details.trim().length < 3

    if (sent) {
        CtBottomSheet(onDismiss, scrollable = false) {
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.go, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.ws1_report_thanks), style = Ct.type.titleM, color = c.text)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.ws1_report_thanks_body), style = Ct.type.bodyS, color = c.textSecondary)
                Spacer(Modifier.height(24.dp))
                PrimaryButton(stringResource(R.string.ws1_done), onDismiss, Modifier.fillMaxWidth())
            }
        }
        return
    }

    CtBottomSheet(onDismiss, title = stringResource(R.string.ws1_report_title), subtitle = stringResource(R.string.ws1_report_subtitle)) {
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            ReportReason.entries.forEach { r ->
                val selected = reason == r
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(CtShapes.field)
                        .background(if (selected) c.accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { reason = r; error = null })
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected, onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.textMuted),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(reportReasonLabel(r), style = Ct.type.body, color = c.text)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CtTextField(
            value = details,
            onValueChange = { details = it; error = null },
            label = stringResource(R.string.ws1_report_details_label),
            placeholder = stringResource(R.string.ws1_report_details_hint),
            maxLength = 500,
            minLines = 3,
            errorText = if (needDetails && details.isNotEmpty()) stringResource(R.string.ws1_report_details_required) else null,
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, style = Ct.type.bodyS, color = c.onDangerContainer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Spacer(Modifier.height(20.dp))
        if (reason == null) {
            Text(stringResource(R.string.ws1_report_pick_reason), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(bottom = 8.dp))
        }
        PrimaryButton(
            stringResource(R.string.ws1_report_send),
            onClick = {
                val r = reason ?: return@PrimaryButton
                sending = true
                error = null
                scope.launch {
                    val result = runCatching { AppContainer.repo.report(target, targetId, r, details.trim().ifBlank { null }) }
                        .getOrElse { Result.failure(it) }
                    sending = false
                    result.onSuccess { sent = true }.onFailure { e -> error = (e as? RepoException)?.userMessage ?: genericError }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = reason != null && !needDetails,
            loading = sending,
        )
    }
}

/** A titled group of controls inside a sheet or form, with 12dp between children. */
@Composable
fun FormSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) SectionHeader(title)
        content()
    }
}
