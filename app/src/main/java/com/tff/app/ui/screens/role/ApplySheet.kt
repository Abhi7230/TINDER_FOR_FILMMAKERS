package com.tff.app.ui.screens.role

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.di.AppContainer
import com.tff.app.domain.CardText
import com.tff.app.model.Application
import com.tff.app.model.CompType
import com.tff.app.model.GearPolicy
import com.tff.app.model.LinkKind
import com.tff.app.model.Profile
import com.tff.app.model.Level
import com.tff.app.model.Dept
import com.tff.app.model.Taxonomy
import com.tff.app.model.RoleCode
import com.tff.app.model.RoleWithProject
import com.tff.app.model.WorkLink
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.PayLine
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.ToggleRow
import com.tff.app.ui.screens.discover.Ws4Prefs
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the person typed in the Apply sheet (S07). */
data class ApplyForm(val note: String?, val workLinkId: String?, val answer: String?, val kit: Boolean)

/** How an application attempt ended. Only [Invalid] keeps the sheet open. */
sealed interface ApplyOutcome {
    data class Sent(val application: Application) : ApplyOutcome
    /** Filled or capped meanwhile ("Just filled. Here's a similar one."): the card moves on. */
    data class Gone(val message: String) : ApplyOutcome
    /** 25 sent today. */
    data class DailyCap(val message: String) : ApplyOutcome
    /** Something to fix in the form, or a network failure ([message] blank = generic copy). */
    data class Invalid(val message: String) : ApplyOutcome
}

object ApplyActions {
    const val NOTE_MAX = 140
    const val ANSWER_MAX = 300

    suspend fun submit(repo: Repo, roleId: String, form: ApplyForm): ApplyOutcome {
        val r = runCatching { repo.apply(roleId, form.note?.trim()?.ifBlank { null }, form.workLinkId, form.answer?.trim()?.ifBlank { null }, form.kit) }
            .getOrElse { Result.failure(it) }
        return r.fold(
            onSuccess = { ApplyOutcome.Sent(it) },
            onFailure = { e ->
                val re = e as? RepoException
                when (re?.code) {
                    "filled", "cap" -> ApplyOutcome.Gone(re.userMessage)
                    "daily_cap" -> ApplyOutcome.DailyCap(re.userMessage)
                    else -> ApplyOutcome.Invalid(re?.userMessage ?: "")
                }
            },
        )
    }

    /** Link kind from the URL: YouTube / Vimeo are reels, IMDb is IMDB, everything else a portfolio link. */
    fun kindFor(url: String): LinkKind {
        val u = url.lowercase()
        return when {
            "youtu" in u || "vimeo" in u -> LinkKind.REEL
            "imdb." in u -> LinkKind.IMDB
            else -> LinkKind.PORTFOLIO
        }
    }

    fun isValidUrl(url: String): Boolean {
        val u = url.trim()
        return (u.startsWith("https://") || u.startsWith("http://")) && u.length > 10 && '.' in u.substringAfter("://") && ' ' !in u
    }

    /** "Add a link" inline: saves it to the profile (keeping the private rate) and returns the new link. */
    suspend fun addLink(repo: Repo, url: String, roleCode: RoleCode): Result<WorkLink> = runCatching {
        val me = repo.me().first() ?: throw RepoException("Finish your profile first, then add links.")
        val link = WorkLink(id = "", url = url.trim(), kind = kindFor(url), roleCode = roleCode)
        val saved = repo.saveProfile(me.copy(workLinks = me.workLinks + link), repo.myPrivateMinRate()).getOrThrow()
        saved.workLinks.lastOrNull { it.url == link.url } ?: throw RepoException("Couldn't add the link. Try again.")
    }

    /** Reels for this role first, then other reels, then everything else. Headshots only for cast roles. */
    fun orderedLinks(links: List<WorkLink>, roleCode: RoleCode, isCast: Boolean): List<WorkLink> =
        links.filter { it.kind != LinkKind.HEADSHOT || isCast }.sortedWith(
            compareBy<WorkLink>(
                { if (it.roleCode == roleCode && it.kind == LinkKind.REEL) 0 else if (it.roleCode == roleCode) 1 else if (it.kind == LinkKind.REEL) 2 else 3 },
            ),
        )

    fun isBringYourOwn(g: GearPolicy?): Boolean = g == GearPolicy.BYO_WITH_KIT_FEE || g == GearPolicy.BYO_NO_FEE
}

/**
 * Asks for POST_NOTIFICATIONS on Android 13+ the first time it's called (after the first application),
 * and never again on this device.
 */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context) {
        {
            if (Build.VERSION.SDK_INT >= 33 && !Ws4Prefs.notificationPromptShown) {
                Ws4Prefs.notificationPromptShown = true
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted) runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }
}

/**
 * S07 Apply sheet: one line (≤140), a reel picker (role-tagged first, "Add a link" inline), the
 * screening answer (required when there's a question), "I'll bring my kit" on bring-your-own roles,
 * and for unpaid roles a plain summary of what you get. [onCancel] = the card returns to the deck.
 * [onFinished] runs for every outcome except [ApplyOutcome.Invalid], which is shown in the sheet.
 */
@Composable
fun ApplySheet(
    item: RoleWithProject,
    onCancel: () -> Unit,
    onFinished: (ApplyOutcome) -> Unit,
) {
    val repo = AppContainer.repo
    val c = Ct.colors
    val role = item.role
    val project = item.project
    val scope = rememberCoroutineScope()
    val me by remember { repo.me() }.collectAsStateWithLifecycle(initialValue = null as Profile?)
    var note by rememberSaveable(role.id) { mutableStateOf("") }
    var answer by rememberSaveable(role.id) { mutableStateOf("") }
    var linkId by rememberSaveable(role.id) { mutableStateOf<String?>(null) }
    var linkTouched by rememberSaveable(role.id) { mutableStateOf(false) }
    var kit by rememberSaveable(role.id) { mutableStateOf<Boolean?>(null) }
    var addingLink by rememberSaveable(role.id) { mutableStateOf(false) }
    var newUrl by rememberSaveable(role.id) { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var savingLink by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var answerError by remember { mutableStateOf(false) }

    val links = remember(me?.workLinks, role.roleCode) {
        me?.let { ApplyActions.orderedLinks(it.workLinks, role.roleCode, role.isCast) }.orEmpty()
    }
    // Pre-pick the best link once the profile arrives (role-tagged reel first).
    LaunchedEffect(links) { if (!linkTouched && linkId == null) linkId = links.firstOrNull()?.id }
    val byo = ApplyActions.isBringYourOwn(role.gearPolicy)
    val kitOn = kit ?: (me?.gear?.isNotEmpty() == true)
    val question = role.screeningQuestion?.takeIf { it.isNotBlank() }
    val genericError = stringResource(R.string.ws4_apply_error)
    val badUrl = stringResource(R.string.ws4_apply_link_invalid)
    val needAnswer = stringResource(R.string.ws4_apply_answer_required)

    CtBottomSheet(
        onDismiss = { if (!sending) onCancel() },
        title = stringResource(R.string.ws4_apply_title, CardText.roleName(role)),
        subtitle = project.title,
    ) {
        PayLine(CardText.payLine(role, project), paid = role.isPaid)
        Spacer(Modifier.height(16.dp))

        if (role.compType == CompType.UNPAID) {
            val perks = CardText.perksDetailed(role, project)
            val text = if (perks.isEmpty()) stringResource(R.string.ws4_apply_unpaid_none)
            else stringResource(R.string.ws4_apply_unpaid_summary, perks.joinToString("; ") { it.replaceFirstChar { ch -> ch.lowercase() } })
            Text(
                text, style = Ct.type.bodyS, color = c.onWarnContainer,
                modifier = Modifier.fillMaxWidth().clip(CtShapes.field).background(c.warnContainer)
                    .border(1.dp, c.accent.copy(alpha = 0.25f), CtShapes.field).padding(14.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        // Writers are judged on the page: a writing sample, and for first-timers a logline in place of a reel.
        val writing = Taxonomy.dept(role.roleCode) == Dept.WRITING
        val loglineInstead = writing && me?.level == Level.FIRST_PROJECT
        CtTextField(
            value = note, onValueChange = { note = it.take(ApplyActions.NOTE_MAX); error = null },
            label = stringResource(if (loglineInstead) R.string.ws4_apply_logline_label else R.string.ws4_apply_note_label),
            placeholder = stringResource(if (loglineInstead) R.string.ws4_apply_logline_hint else R.string.ws4_apply_note_hint),
            maxLength = ApplyActions.NOTE_MAX, minLines = 2,
        )
        Spacer(Modifier.height(18.dp))

        // Reel picker (writing sample picker for writing roles)
        Text(
            stringResource(if (writing) R.string.ws4_apply_link_label_writing else R.string.ws4_apply_link_label),
            style = Ct.type.bodyS.copy(fontWeight = Ct.type.label.fontWeight), color = c.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            links.forEach { l ->
                LinkRow(
                    title = l.title ?: linkHost(l.url),
                    subtitle = linkKindLabel(l.kind) + (l.roleCode?.let { " · " + CardText.roleName(it) } ?: ""),
                    selected = linkId == l.id,
                    onClick = { linkId = l.id; linkTouched = true },
                )
            }
            LinkRow(
                title = stringResource(R.string.ws4_apply_link_none), subtitle = null,
                selected = linkId == null, onClick = { linkId = null; linkTouched = true },
            )
        }
        if (addingLink) {
            Spacer(Modifier.height(8.dp))
            CtTextField(
                value = newUrl, onValueChange = { newUrl = it.trim(); urlError = null },
                label = null, placeholder = stringResource(if (writing) R.string.ws4_apply_link_hint_writing else R.string.ws4_apply_link_hint), singleLine = true,
                errorText = urlError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    stringResource(R.string.ws4_apply_link_add), loading = savingLink, enabled = newUrl.isNotBlank(),
                    onClick = {
                        if (!ApplyActions.isValidUrl(newUrl)) { urlError = badUrl; return@SecondaryButton }
                        savingLink = true
                        scope.launch {
                            ApplyActions.addLink(repo, newUrl, role.roleCode)
                                .onSuccess { l -> linkId = l.id; linkTouched = true; addingLink = false; newUrl = "" }
                                .onFailure { e -> urlError = (e as? RepoException)?.userMessage ?: genericError }
                            savingLink = false
                        }
                    },
                )
                GhostButton(stringResource(R.string.ws1_cancel), { addingLink = false; newUrl = ""; urlError = null }, color = c.textSecondary)
            }
        } else {
            GhostButton(
                stringResource(if (writing) R.string.ws4_apply_link_add_writing else R.string.ws4_apply_link_add_cta),
                { addingLink = true }, icon = Icons.Rounded.Add, modifier = Modifier.padding(start = 0.dp),
            )
        }

        if (question != null) {
            Spacer(Modifier.height(16.dp))
            CtTextField(
                value = answer, onValueChange = { answer = it.take(ApplyActions.ANSWER_MAX); answerError = false; error = null },
                label = question,
                placeholder = stringResource(R.string.ws4_apply_answer_hint),
                maxLength = ApplyActions.ANSWER_MAX, minLines = 2,
                errorText = if (answerError) needAnswer else null,
                supportingText = stringResource(R.string.ws4_apply_answer_support),
            )
        }

        if (byo) {
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                stringResource(R.string.ws4_apply_kit), kitOn, { kit = it },
                subtitle = if (role.gearPolicy == GearPolicy.BYO_WITH_KIT_FEE) stringResource(R.string.ws4_apply_kit_fee) else stringResource(R.string.ws4_apply_kit_no_fee),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error!!, style = Ct.type.bodyS, color = c.onDangerContainer,
                modifier = Modifier.fillMaxWidth().clip(CtShapes.field).background(c.dangerContainer).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(20.dp))
        // The promise sits above Send so it is read before tapping (and stays visible on shorter screens).
        Text(
            stringResource(R.string.ws4_apply_promise), style = Ct.type.label, color = c.textMuted,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            stringResource(R.string.ws4_apply_send),
            onClick = {
                if (question != null && answer.isBlank()) { answerError = true; return@PrimaryButton }
                sending = true
                error = null
                scope.launch {
                    val out = ApplyActions.submit(repo, role.id, ApplyForm(note, linkId, answer.takeIf { question != null }, byo && kitOn))
                    sending = false
                    if (out is ApplyOutcome.Invalid) error = out.message.ifBlank { genericError } else onFinished(out)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            loading = sending,
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val c = Ct.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CtShapes.field)
            .background(if (selected) c.accent.copy(alpha = 0.08f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.textMuted))
        Spacer(Modifier.width(8.dp))
        Icon(if (subtitle != null) Icons.Rounded.Link else Icons.Rounded.LinkOff, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.body.copy(fontSize = Ct.type.bodyS.fontSize), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Ct.type.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun linkKindLabel(k: LinkKind): String = stringResource(
    when (k) {
        LinkKind.REEL -> R.string.ws4_link_reel
        LinkKind.CREDIT -> R.string.ws4_link_credit
        LinkKind.HEADSHOT -> R.string.ws4_link_headshot
        LinkKind.PORTFOLIO -> R.string.ws4_link_portfolio
        LinkKind.IMDB -> R.string.ws4_link_imdb
        LinkKind.OTHER -> R.string.ws4_link_other
    }
)

/** "youtube.com" from a URL, for links without a title. */
internal fun linkHost(url: String): String =
    url.substringAfter("://").substringBefore('/').removePrefix("www.").ifBlank { url }
