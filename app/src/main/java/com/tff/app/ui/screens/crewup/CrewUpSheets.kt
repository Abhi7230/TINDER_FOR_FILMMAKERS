package com.tff.app.ui.screens.crewup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tff.app.R
import com.tff.app.data.RepoException
import com.tff.app.domain.CardText
import com.tff.app.domain.MessageSafety
import com.tff.app.model.ApplicantView
import com.tff.app.model.RoleWithProject
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SlateOverlay
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.launch

const val ASK_NOTE_MAX = 140

/** First name for copy ("Dana", "Kabir"). */
fun firstName(v: ApplicantView): String = v.card.profile.displayName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { v.card.profile.shortName }

/** "Dana R. · Sound Mixer · Filter Coffee, 4 AM" under IN TALKS. */
fun inTalksLine(v: ApplicantView, item: RoleWithProject): String =
    "${v.card.profile.shortName} · ${CardText.roleName(item.role)} · ${item.project.title}"

/**
 * The IN TALKS moment (PRD §3.3, §12.4) covering the whole screen (bottom nav included), with
 * [Open chat] [Keep swiping]. Never "It's a match".
 */
@Composable
fun InTalksOverlay(subtitle: String, onOpenChat: () -> Unit, onKeepSwiping: () -> Unit) {
    Dialog(
        onDismissRequest = onKeepSwiping,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        SlateOverlay(
            visible = true,
            subtitle = subtitle,
            onDismiss = onKeepSwiping,
            actions = {
                SecondaryButton(stringResource(R.string.ws4_keep_swiping), onKeepSwiping, Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.ws4_open_chat), onOpenChat, Modifier.weight(1f))
            },
        )
    }
}

/**
 * Ask composer (S09 Scout → Send ask): the pre-filled Ask card ("Would you be Sound Mixer on Filter
 * Coffee, 4 AM? Sat 14 – Sun 15 Nov · ₹2,500/day + kit · 2 meals"), an optional note (≤140), and the
 * "10 asks left today" counter. The reverse G4/G5 check runs first: if it fails, the sheet says why
 * instead of sending. [onCancel] = the card returns; [onSkip] hides the person for this role.
 */
@Composable
fun AskComposerSheet(
    view: ApplicantView,
    item: RoleWithProject,
    asksLeft: Int,
    check: suspend () -> String?,
    send: suspend (String?) -> Result<Unit>,
    onCancel: () -> Unit,
    onSent: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = Ct.colors
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(true) }
    var blocked by remember { mutableStateOf<String?>(null) }
    var note by rememberSaveable(view.card.profile.id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val genericError = stringResource(R.string.ws4_generic_error)
    val name = firstName(view)

    LaunchedEffect(view.card.profile.id) {
        blocked = check()
        checking = false
    }

    CtBottomSheet(
        onDismiss = { if (!sending) onCancel() },
        title = stringResource(R.string.ws4_ask_title, name),
        subtitle = "${view.card.profile.shortName} · ${CardText.roleName(view.card.profile.primaryRole)} · ${CardText.levelLabel(view.card.profile.level)}",
    ) {
        when {
            checking -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            blocked != null -> {
                Row(
                    Modifier.fillMaxWidth().clip(CtShapes.field).background(c.warnContainer)
                        .border(1.dp, c.accent.copy(alpha = 0.3f), CtShapes.field).padding(14.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(Icons.Rounded.Block, contentDescription = null, tint = c.onWarnContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(blocked!!, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize), color = c.onWarnContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ws4_ask_blocked_body), style = Ct.type.bodyS, color = c.onWarnContainer)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(stringResource(R.string.ws4_ask_back), onCancel, Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.ws4_ask_skip_person, name), onSkip, Modifier.weight(1f))
                }
            }
            else -> {
                // The Ask card exactly as the person will see it in their Inbox.
                Column(
                    Modifier.fillMaxWidth().clip(CtShapes.card).background(c.surfaceRaised)
                        .border(1.dp, c.outline, CtShapes.card).padding(16.dp),
                ) {
                    Text(stringResource(R.string.ws4_ask_card_label), style = Ct.type.slate.copy(fontSize = Ct.type.label.fontSize * 1.1f), color = c.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(CardText.askLine(item), style = Ct.type.body, color = c.text)
                    if (note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("“${note.trim()}”", style = Ct.type.bodyS, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                val warn = remember(note) { runCatching { MessageSafety.draftWarning(note, booked = false) }.getOrNull() }
                CtTextField(
                    value = note, onValueChange = { note = it.take(ASK_NOTE_MAX); error = null },
                    label = stringResource(R.string.ws4_ask_note_label),
                    placeholder = stringResource(R.string.ws4_ask_note_hint),
                    maxLength = ASK_NOTE_MAX, minLines = 2,
                    errorText = warn,
                )
                Spacer(Modifier.height(12.dp))
                TimecodeText(
                    pluralStringResource(R.plurals.ws4_asks_left, asksLeft, asksLeft),
                    style = Ct.type.monoS, color = if (asksLeft <= 2) c.accent else c.textSecondary,
                )
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        error!!, style = Ct.type.bodyS, color = c.onDangerContainer,
                        modifier = Modifier.fillMaxWidth().clip(CtShapes.field).background(c.dangerContainer).padding(12.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    stringResource(R.string.ws4_ask_send),
                    onClick = {
                        sending = true
                        scope.launch {
                            send(note).onSuccess { onSent() }.onFailure { e -> error = (e as? RepoException)?.userMessage ?: genericError }
                            sending = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = asksLeft > 0 && warn == null,
                    loading = sending,
                    icon = Icons.AutoMirrored.Rounded.Send,
                )
                if (asksLeft <= 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ws4_asks_none), style = Ct.type.label, color = c.textMuted)
                }
                Spacer(Modifier.height(4.dp))
                GhostButton(stringResource(R.string.ws1_cancel), onCancel, Modifier.fillMaxWidth(), color = c.textSecondary)
            }
        }
    }
}
