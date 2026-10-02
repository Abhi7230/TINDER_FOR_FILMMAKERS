package com.tff.app.ui.screens.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.model.City
import com.tff.app.model.ProfileCard
import com.tff.app.model.Report
import com.tff.app.model.ReportStatus
import com.tff.app.model.ReportTarget
import com.tff.app.model.TrustPage
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.StatusChip
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.reportReasonLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface SafetyUi {
    data object Loading : SafetyUi
    data class Error(val message: String?) : SafetyUi
    data class Ready(
        val signedIn: Boolean,
        val reports: List<Report>,
        val blocked: List<ProfileCard>,
        val city: City?,
    ) : SafetyUi
}

/** S21: my reports with status, blocked people (unblock), emergency number for my country. */
@OptIn(ExperimentalCoroutinesApi::class)
class SafetyViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow<SafetyUi>(SafetyUi.Loading)
    val ui: StateFlow<SafetyUi> = _ui
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _ui.value = SafetyUi.Loading
        job = viewModelScope.launch {
            val cities = runCatching { repo.cities() }.getOrDefault(emptyList())
            repo.session.map { it?.userId }.flatMapLatest { uid ->
                if (uid == null) flowOf<SafetyUi>(SafetyUi.Ready(false, emptyList(), emptyList(), null))
                else combine(repo.myReports(), repo.blocked(), repo.me()) { reports, blocked, me ->
                    SafetyUi.Ready(true, reports, blocked, cities.firstOrNull { it.id == me?.cityId })
                }
            }
                .catch { e -> _ui.value = SafetyUi.Error((e as? RepoException)?.userMessage) }
                .collect { _ui.value = it }
        }
    }

    fun unblock(card: ProfileCard, done: String, fallback: String) {
        viewModelScope.launch {
            val r = runCatching { repo.unblock(card.profile.id) }.getOrElse { Result.failure(it) }
            _message.value = if (r.isSuccess) done else (r.exceptionOrNull() as? RepoException)?.userMessage ?: fallback
        }
    }

    fun messageShown() { _message.value = null }
}

/**
 * S21 Safety Center: emergency number for my country, the 6 community rules, what normal looks like,
 * My reports (RECEIVED → REVIEWING → ACTION TAKEN / NO ACTION), Blocked people with Unblock, and how
 * reports are handled. Works for guests (reports and blocks need sign-in).
 */
@Composable
fun SafetyCenterScreen(nav: AppNavigator) {
    val vm = rememberVm { SafetyViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(msg) { msg?.let { nav.toast(it); vm.messageShown() } }
    val c = Ct.colors
    var unblocking by remember { mutableStateOf<ProfileCard?>(null) }
    val fallback = stringResource(R.string.ws7_generic_error)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        CtTopBar(title = stringResource(R.string.ws7_safety_title), onBack = nav::back)
        when (val s = ui) {
            SafetyUi.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp), rows = 5)
            is SafetyUi.Error -> ErrorState(onRetry = vm::load, message = s.message, modifier = Modifier.fillMaxSize())
            is SafetyUi.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                item("emergency") { EmergencyCard(s.city, nav) }
                item("rules") {
                    SectionHeader(stringResource(R.string.ws7_safety_rules), Modifier.padding(top = 20.dp), action = stringResource(R.string.ws7_safety_read_more), onAction = { nav.toTrustPage(TrustPage.COMMUNITY_RULES) })
                    val rules = stringArrayResource(R.array.ws7_rules_short)
                    rules.forEachIndexed { i, r -> NumberedLine(i + 1, r) }
                }
                item("normal") {
                    SectionHeader(stringResource(R.string.ws7_safety_normal), Modifier.padding(top = 20.dp), action = stringResource(R.string.ws7_safety_read_more), onAction = { nav.toTrustPage(TrustPage.WHAT_NORMAL_LOOKS_LIKE) })
                    Text(stringResource(R.string.ws7_safety_normal_body), style = Ct.type.body, color = c.text)
                }
                item("reports") {
                    SectionHeader(stringResource(R.string.ws7_safety_reports), Modifier.padding(top = 20.dp), count = s.reports.size.takeIf { s.signedIn })
                    when {
                        !s.signedIn -> {
                            Text(stringResource(R.string.ws7_safety_reports_guest), style = Ct.type.bodyS, color = c.textSecondary)
                            GhostButton(stringResource(R.string.ws7_set_sign_in), nav::toWelcome)
                        }
                        s.reports.isEmpty() -> Text(stringResource(R.string.ws7_safety_reports_empty), style = Ct.type.bodyS, color = c.textSecondary)
                    }
                }
                items(s.reports, key = { "rep_" + it.id }) { r -> ReportRow(r, Modifier.padding(top = 8.dp)) }
                if (s.signedIn) {
                    item("blocked") {
                        SectionHeader(stringResource(R.string.ws7_safety_blocked), Modifier.padding(top = 20.dp), count = s.blocked.size)
                        if (s.blocked.isEmpty()) Text(stringResource(R.string.ws7_safety_blocked_empty), style = Ct.type.bodyS, color = c.textSecondary)
                    }
                    items(s.blocked, key = { "blk_" + it.profile.id }) { b -> BlockedRow(b, onUnblock = { unblocking = b }, Modifier.padding(top = 8.dp)) }
                }
                item("how") {
                    SectionHeader(stringResource(R.string.ws7_safety_how), Modifier.padding(top = 20.dp))
                    listOf(R.string.ws7_safety_how_1, R.string.ws7_safety_how_2, R.string.ws7_safety_how_3, R.string.ws7_safety_how_4, R.string.ws7_safety_how_5)
                        .forEachIndexed { i, id -> NumberedLine(i + 1, stringResource(id)) }
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }

    unblocking?.let { card ->
        val done = stringResource(R.string.ws7_safety_unblocked, card.profile.shortName)
        AlertDialog(
            onDismissRequest = { unblocking = null },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.ws7_safety_unblock_title, card.profile.shortName), style = Ct.type.titleM, color = c.text) },
            text = { Text(stringResource(R.string.ws7_safety_unblock_body), style = Ct.type.bodyS, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { vm.unblock(card, done, fallback); unblocking = null }) { Text(stringResource(R.string.ws7_safety_unblock), color = c.accent) } },
            dismissButton = { TextButton(onClick = { unblocking = null }) { Text(stringResource(R.string.ws7_cancel), color = c.textSecondary) } },
        )
    }
}

@Composable
private fun EmergencyCard(city: City?, nav: AppNavigator) {
    val c = Ct.colors
    val number = city?.emergencyNumber ?: "112"
    val country = when (city?.country) {
        "US" -> stringResource(R.string.ws7_country_us)
        "GB" -> stringResource(R.string.ws7_country_gb)
        "IN" -> stringResource(R.string.ws7_country_in)
        else -> null
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(CtShapes.card).background(c.dangerContainer)
            .border(1.dp, c.stop.copy(alpha = 0.5f), CtShapes.card).padding(16.dp),
    ) {
        Text(stringResource(R.string.ws7_safety_emergency, number), style = Ct.type.titleM, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(
            if (country != null) stringResource(R.string.ws7_safety_emergency_body, country) else stringResource(R.string.ws7_safety_other_numbers),
            style = Ct.type.bodyS, color = c.onDangerContainer,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(stringResource(R.string.ws7_safety_call, number), { nav.openUrl("tel:$number") }, icon = Icons.Rounded.Call)
        if (city != null) {
            Spacer(Modifier.height(10.dp))
            TimecodeText(stringResource(R.string.ws7_safety_other_numbers).uppercase(), style = Ct.type.monoS, color = c.textSecondary)
        }
    }
}

@Composable
private fun NumberedLine(n: Int, text: String) {
    val c = Ct.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
            Text("$n", style = Ct.type.monoS, color = c.accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = Ct.type.body, color = c.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReportRow(r: Report, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val (label, tone) = when (r.status) {
        ReportStatus.RECEIVED -> stringResource(R.string.ws7_report_status_received) to ChipTone.INFO
        ReportStatus.REVIEWING -> stringResource(R.string.ws7_report_status_reviewing) to ChipTone.WARN
        ReportStatus.ACTION_TAKEN -> stringResource(R.string.ws7_report_status_action) to ChipTone.GO
        ReportStatus.NO_ACTION -> stringResource(R.string.ws7_report_status_none) to ChipTone.NEUTRAL
    }
    val target = stringResource(
        when (r.targetKind) {
            ReportTarget.PROFILE -> R.string.ws7_report_target_profile
            ReportTarget.PROJECT -> R.string.ws7_report_target_project
            ReportTarget.ROLE -> R.string.ws7_report_target_role
            ReportTarget.MESSAGE -> R.string.ws7_report_target_message
            ReportTarget.BOOKING -> R.string.ws7_report_target_booking
        }
    )
    val date = Instant.ofEpochMilli(r.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    Row(
        modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Flag, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(reportReasonLabel(r.reason), style = Ct.type.bodyStrong, color = c.text)
            Text(target, style = Ct.type.bodyS, color = c.textSecondary)
            if (!r.details.isNullOrBlank()) Text("“${r.details}”", style = Ct.type.bodyS, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            TimecodeText(stringResource(R.string.ws7_report_filed, Dates.shortUpper(date)), style = Ct.type.monoS, color = c.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(label, tone)
    }
}

@Composable
private fun BlockedRow(card: ProfileCard, onUnblock: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val p = card.profile
    Row(
        modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(p.initials, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.shortName, style = Ct.type.bodyStrong, color = c.text)
            Text(CardText.roleName(p.primaryRole), style = Ct.type.bodyS, color = c.textSecondary)
        }
        SecondaryButton(stringResource(R.string.ws7_safety_unblock), onUnblock)
    }
}
