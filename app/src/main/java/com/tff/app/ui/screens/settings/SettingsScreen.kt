package com.tff.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.BuildConfig
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.model.DemoPersona
import com.tff.app.model.SimSpeed
import com.tff.app.model.TrustPage
import com.tff.app.nav.AppNavigator
import com.tff.app.notify.NotifyCategory
import com.tff.app.notify.SystemNotifier
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.ChipStyle
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.DangerButton
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.KeyValueRow
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SegmentedControl
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.ToggleRow
import com.tff.app.ui.screens.me.NavRow
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

/**
 * S22 Settings: account (email, sign out), notification switches (with the POST_NOTIFICATIONS
 * request), your data (export JSON via the share sheet, delete account with a double confirm), trust
 * pages, version, "Use demo mode" when LIVE is configured, and the DEMO CONTROLS (F18): persona switcher,
 * reset, speed 1×/3×/instant, clock +1/+7/+30, jump past next shoot / wrap +30d, inject a flagged
 * example, simulate a crew withdrawal, and the current demo date in mono.
 */
@Composable
fun SettingsScreen(nav: AppNavigator) {
    val vm = rememberVm { SettingsViewModel(it) }
    val session by vm.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()
    val c = Ct.colors
    val ctx = LocalContext.current

    val copy = SettingsCopy(
        generic = stringResource(R.string.ws7_generic_error),
        exportError = stringResource(R.string.ws7_set_export_error),
        switched = { n -> ctx.getString(R.string.ws7_demo_switched, n) },
        moved = { d -> ctx.getString(R.string.ws7_demo_moved, d) },
        injected = { t -> ctx.getString(R.string.ws7_demo_injected, t) },
        injectNone = stringResource(R.string.ws7_demo_inject_none),
        withdrawNone = stringResource(R.string.ws7_demo_withdraw_none),
        openChat = stringResource(R.string.ws7_demo_open_chat),
        openTeam = { t -> ctx.getString(R.string.ws7_demo_open_team, t) },
        resetDone = stringResource(R.string.ws7_demo_reset_done),
        deleted = stringResource(R.string.ws7_set_deleted),
    )
    val exportTitle = stringResource(R.string.ws7_set_export_title)
    var lastOpen by remember { mutableStateOf<SettingsEvent.Toast?>(null) }

    LaunchedEffect(event) {
        when (val e = event) {
            is SettingsEvent.Toast -> { lastOpen = e.takeIf { it.openRoute != null }; nav.toast(e.text) }
            is SettingsEvent.Share -> nav.share(e.text, exportTitle)
            is SettingsEvent.Restart -> {
                // clear the whole back stack so every tab rebuilds for the new persona / reset world
                nav.toWelcome()
                if (e.toOnboarding) nav.toOnboarding() else nav.toMain()
                e.toast?.let { nav.toast(it) }
            }
            is SettingsEvent.ToWelcome -> { nav.toWelcome(); e.toast?.let { nav.toast(it) } }
            null -> Unit
        }
        if (event != null) vm.consumed()
    }

    var confirmSignOut by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmMode by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        CtTopBar(title = stringResource(R.string.ws7_settings_title), onBack = nav::back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // ── Demo controls first in demo mode: that's what a demo-er is here for ──
            if (vm.isDemo) DemoControls(vm, busy, copy, lastOpen, nav, onReset = { confirmReset = true })

            // ── Account ──
            SectionHeader(stringResource(R.string.ws7_set_account), Modifier.padding(top = 20.dp))
            val s = session
            if (s == null) {
                Text(stringResource(R.string.ws7_set_guest), style = Ct.type.body, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                PrimaryButton(stringResource(R.string.ws7_set_sign_in), nav::toWelcome, Modifier.fillMaxWidth())
            } else {
                KeyValueRow(stringResource(R.string.ws7_set_email), s.email ?: stringResource(R.string.ws7_set_no_email), mono = s.email != null, divider = true)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.ws7_set_sign_out), { confirmSignOut = true }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Logout, loading = busy == "signout")
            }

            // ── Notifications ──
            NotificationSettings()

            // ── Data ──
            if (s != null) {
                SectionHeader(stringResource(R.string.ws7_set_data), Modifier.padding(top = 20.dp))
                NavRow(Icons.Rounded.Download, stringResource(R.string.ws7_set_export), stringResource(R.string.ws7_set_export_body), { vm.export(copy) })
                NavRow(Icons.Rounded.DeleteForever, stringResource(R.string.ws7_set_delete), stringResource(R.string.ws7_set_delete_body), { deleteStep = 1 })
            }

            // ── Trust pages ──
            SectionHeader(stringResource(R.string.ws7_set_trust), Modifier.padding(top = 20.dp))
            listOf(TrustPage.HOW_WE_MAKE_MONEY, TrustPage.NO_GEN_AI, TrustPage.COMMUNITY_RULES, TrustPage.PRIVACY, TrustPage.WHAT_NORMAL_LOOKS_LIKE, TrustPage.LICENCES).forEach { p ->
                NavRow(trustIcon(p), stringResource(trustTitle(p)), null, { nav.toTrustPage(p) })
            }
            KeyValueRow(stringResource(R.string.ws7_set_version), BuildConfig.VERSION_NAME, mono = true, divider = true)
            KeyValueRow(stringResource(R.string.ws7_set_mode), stringResource(if (vm.isDemo) R.string.ws7_set_mode_demo else R.string.ws7_set_mode_live))
            if (AppContainer.liveConfigured) {
                ToggleRow(
                    stringResource(R.string.ws7_set_use_demo), checked = vm.isDemo, onCheckedChange = { confirmMode = true },
                    subtitle = stringResource(R.string.ws7_set_use_demo_body),
                )
            }
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmSignOut) ConfirmDialog(
        title = stringResource(R.string.ws7_set_sign_out_title), body = stringResource(R.string.ws7_set_sign_out_body),
        confirm = stringResource(R.string.ws7_set_sign_out), onConfirm = { confirmSignOut = false; vm.signOut() }, onDismiss = { confirmSignOut = false },
    )
    if (confirmReset) ConfirmDialog(
        title = stringResource(R.string.ws7_demo_reset_title), body = stringResource(R.string.ws7_demo_reset_text),
        confirm = stringResource(R.string.ws7_demo_reset), danger = true, onConfirm = { confirmReset = false; vm.reset(copy) }, onDismiss = { confirmReset = false },
    )
    if (confirmMode) ConfirmDialog(
        title = stringResource(if (vm.isDemo) R.string.ws7_set_use_demo_title_off else R.string.ws7_set_use_demo_title_on),
        body = stringResource(R.string.ws7_set_use_demo_restart), confirm = stringResource(R.string.ws7_set_restart),
        onConfirm = {
            confirmMode = false
            AppContainer.setUseDemoMode(!vm.isDemo)
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            if (launch?.component != null) ctx.startActivity(Intent.makeRestartActivityTask(launch.component))
            exitProcess(0)
        },
        onDismiss = { confirmMode = false },
    )
    if (deleteStep == 1) ConfirmDialog(
        title = stringResource(R.string.ws7_set_delete_title1), body = stringResource(R.string.ws7_set_delete_text1),
        confirm = stringResource(R.string.ws7_set_delete_continue), danger = true, onConfirm = { deleteStep = 2 }, onDismiss = { deleteStep = 0 },
    )
    if (deleteStep == 2) {
        var typed by remember { mutableStateOf("") }
        val word = stringResource(R.string.ws7_set_delete_word)
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.ws7_set_delete_title2), style = Ct.type.titleM, color = c.text) },
            text = {
                Column {
                    Text(stringResource(R.string.ws7_set_delete_text2), style = Ct.type.bodyS, color = c.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    CtTextField(typed, { typed = it.uppercase().take(10) }, label = null, placeholder = word, singleLine = true, mono = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { deleteStep = 0; vm.deleteAccount(copy) }, enabled = typed.trim() == word) {
                    Text(stringResource(R.string.ws7_set_delete_confirm), color = if (typed.trim() == word) c.stop else c.textMuted)
                }
            },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text(stringResource(R.string.ws7_cancel), color = c.accent) } },
        )
    }
}

@Composable
private fun DemoControls(vm: SettingsViewModel, busy: String?, copy: SettingsCopy, lastOpen: SettingsEvent.Toast?, nav: AppNavigator, onReset: () -> Unit) {
    val c = Ct.colors
    val date by (vm.demoDate ?: return).collectAsStateWithLifecycle()
    val speed by (vm.speed ?: return).collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val idle = busy == null

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(CtShapes.card).background(c.surface)
            .border(1.dp, c.accent.copy(alpha = 0.45f), CtShapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.ws7_demo), style = Ct.type.slate, color = c.accent)
        // date
        Column {
            Text(stringResource(R.string.ws7_demo_date), style = Ct.type.label, color = c.textMuted)
            TimecodeText("${Dates.shortUpper(date)} ${date.year}", style = Ct.type.monoL.copy(fontSize = Ct.type.titleL.fontSize, lineHeight = Ct.type.titleL.lineHeight), color = c.text)
            val offset = ChronoUnit.DAYS.between(LocalDate.now(), date)
            TimecodeText(
                if (offset == 0L) stringResource(R.string.ws7_demo_date_today).uppercase() else pluralStringResource(R.plurals.ws7_demo_date_offset, offset.toInt(), offset.toInt()).uppercase(),
                style = Ct.type.monoS, color = if (offset == 0L) c.textMuted else c.accent,
            )
        }

        // persona
        Text(stringResource(R.string.ws7_demo_persona), style = Ct.type.bodyStrong, color = c.text)
        Text(stringResource(R.string.ws7_demo_persona_body), style = Ct.type.label, color = c.textMuted)
        val personas = listOf(DemoPersona.MEERA to "Meera", DemoPersona.ROHAN to "Rohan", DemoPersona.KABIR to "Kabir", DemoPersona.DANA to "Dana")
        val ownLabel = stringResource(R.string.ws7_demo_own)
        ChipFlow(lineSpacing = 0.dp, spacing = 6.dp) {
            personas.forEach { (p, name) ->
                CtFilterChip(name, session?.persona == p, { if (session?.persona != p) vm.switchPersona(p, name, copy) }, enabled = idle)
            }
            CtFilterChip(ownLabel, session != null && session?.persona == null, { vm.switchPersona(null, ownLabel, copy) }, enabled = idle)
        }

        // speed
        Text(stringResource(R.string.ws7_demo_speed), style = Ct.type.bodyStrong, color = c.text)
        Text(stringResource(R.string.ws7_demo_speed_body), style = Ct.type.label, color = c.textMuted)
        val speeds = listOf(SimSpeed.NORMAL, SimSpeed.FAST, SimSpeed.INSTANT)
        SegmentedControl(
            listOf(stringResource(R.string.ws7_demo_speed_1), stringResource(R.string.ws7_demo_speed_3), stringResource(R.string.ws7_demo_speed_instant)),
            speeds.indexOf(speed).coerceAtLeast(0), { vm.setSpeed(speeds[it]) },
        )

        // clock
        Text(stringResource(R.string.ws7_demo_clock), style = Ct.type.bodyStrong, color = c.text)
        Text(stringResource(R.string.ws7_demo_clock_body), style = Ct.type.label, color = c.textMuted)
        // mono chips (48dp targets) so "+30 days" never truncates three-across
        ChipFlow(spacing = 8.dp, lineSpacing = 0.dp) {
            listOf(1 to R.string.ws7_demo_plus1, 7 to R.string.ws7_demo_plus7, 30 to R.string.ws7_demo_plus30).forEach { (d, label) ->
                CtChip(
                    stringResource(label).uppercase(), style = ChipStyle.MONO, filled = true, tone = ChipTone.ACCENT,
                    icon = Icons.Rounded.FastForward, onClick = { if (idle) vm.advance(d, copy) },
                )
            }
        }
        SecondaryButton(stringResource(R.string.ws7_demo_next_shoot), { vm.jumpPastNextShoot(copy) }, Modifier.fillMaxWidth(), enabled = idle, loading = busy == "next_shoot", icon = Icons.Rounded.FastForward)
        SecondaryButton(stringResource(R.string.ws7_demo_past_wrap), { vm.jumpPastWrap(copy) }, Modifier.fillMaxWidth(), enabled = idle, loading = busy == "past_wrap", icon = Icons.Rounded.FastForward)

        // examples
        Text(stringResource(R.string.ws7_demo_examples), style = Ct.type.bodyStrong, color = c.text)
        SecondaryButton(stringResource(R.string.ws7_demo_inject), { vm.injectFlagged(copy) }, Modifier.fillMaxWidth(), enabled = idle && session != null, loading = busy == "inject", icon = Icons.Rounded.Flag)
        Text(stringResource(R.string.ws7_demo_inject_body), style = Ct.type.label, color = c.textMuted)
        OpenResult(lastOpen, "inject", nav)
        SecondaryButton(stringResource(R.string.ws7_demo_withdraw), { vm.simulateWithdrawal(copy) }, Modifier.fillMaxWidth(), enabled = idle && session != null, loading = busy == "withdraw", icon = Icons.Rounded.PersonRemove)
        Text(stringResource(R.string.ws7_demo_withdraw_body), style = Ct.type.label, color = c.textMuted)
        OpenResult(lastOpen, "withdraw", nav)

        // reset
        DangerButton(stringResource(R.string.ws7_demo_reset), onReset, Modifier.fillMaxWidth(), enabled = idle, loading = busy == "reset", icon = Icons.Rounded.RestartAlt)
        Text(stringResource(R.string.ws7_demo_reset_body), style = Ct.type.label, color = c.textMuted)
    }
}

/** The follow-up of an example control, right under it and named ("Open Filter Coffee, 4 AM · Team"), never a bare "Open". */
@Composable
private fun OpenResult(last: SettingsEvent.Toast?, from: String, nav: AppNavigator) {
    val route = last?.openRoute ?: return
    if (last.from != from) return
    SecondaryButton(last.openLabel ?: stringResource(R.string.ws7_demo_open), { nav.go(route) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.OpenInNew)
}

@Composable
private fun NotificationSettings() {
    val c = Ct.colors
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked = true
        if (granted) SystemNotifier.setEnabled(ctx, true)
        tick++
    }
    val allowed = remember(tick) { SystemNotifier.systemAllows(ctx) }
    var enabled by remember(tick) { mutableStateOf(SystemNotifier.isEnabled(ctx)) }
    fun openSystemSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    SectionHeader(stringResource(R.string.ws7_set_notifications), Modifier.padding(top = 20.dp))
    ToggleRow(
        stringResource(R.string.ws7_set_notify_master),
        checked = enabled && allowed,
        onCheckedChange = { on ->
            if (!on) { SystemNotifier.setEnabled(ctx, false); enabled = false }
            else {
                SystemNotifier.setEnabled(ctx, true); enabled = true
                if (!allowed) {
                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission && !asked) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else { asked = true; openSystemSettings() }
                }
            }
        },
        subtitle = stringResource(R.string.ws7_set_notify_master_body),
    )
    if (enabled && !allowed && asked) {
        Text(stringResource(R.string.ws7_set_notify_blocked), style = Ct.type.bodyS, color = c.onWarnContainer)
        GhostButton(stringResource(R.string.ws7_set_notify_open_settings), { openSystemSettings() })
    }
    if (enabled && allowed) {
        val rows = listOf(
            NotifyCategory.APPLICATIONS to (R.string.ws7_set_notify_apps to R.string.ws7_set_notify_apps_body),
            NotifyCategory.BOOKINGS to (R.string.ws7_set_notify_bookings to R.string.ws7_set_notify_bookings_body),
            NotifyCategory.SHOOTS to (R.string.ws7_set_notify_shoots to R.string.ws7_set_notify_shoots_body),
            NotifyCategory.WRAP to (R.string.ws7_set_notify_wrap to R.string.ws7_set_notify_wrap_body),
            NotifyCategory.SAFETY to (R.string.ws7_set_notify_safety to R.string.ws7_set_notify_safety_body),
        )
        rows.forEach { (cat, labels) ->
            var on by remember(cat) { mutableStateOf(SystemNotifier.isCategoryOn(ctx, cat)) }
            ToggleRow(stringResource(labels.first), on, { v -> on = v; SystemNotifier.setCategory(ctx, cat, v) }, subtitle = stringResource(labels.second))
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    val c = Ct.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = Ct.type.titleM, color = c.text) },
        text = { Text(body, style = Ct.type.bodyS, color = c.textSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) c.stop else c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ws7_cancel), color = c.textSecondary) } },
    )
}
