package com.tff.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.Dates
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.AppMode
import com.tff.app.model.AppNotification
import com.tff.app.model.DemoPersona
import com.tff.app.model.NotificationKind
import com.tff.app.model.SimSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One-shot things the Settings screen does after an action. */
sealed interface SettingsEvent {
    data class Toast(val text: String, val openRoute: String? = null) : SettingsEvent
    data class Share(val text: String) : SettingsEvent
    /** Rebuild Main from scratch (persona switched, demo reset), optionally after a toast. */
    data class Restart(val toOnboarding: Boolean, val toast: String?) : SettingsEvent
    data class ToWelcome(val toast: String?) : SettingsEvent
}

/** Copy the VM needs but can't read itself (string resources live in the composable). */
data class SettingsCopy(
    val generic: String,
    val exportError: String,
    val switched: (String) -> String,
    val moved: (String) -> String,
    val injected: (String) -> String,
    val injectNone: String,
    val withdrawNone: String,
    val resetDone: String,
    val deleted: String,
)

class SettingsViewModel(private val repo: Repo) : ViewModel() {
    val session = repo.session
    val isDemo: Boolean get() = repo.mode == AppMode.DEMO
    val demoDate: StateFlow<LocalDate>? get() = repo.demo?.demoDate
    val speed: StateFlow<SimSpeed>? get() = repo.demo?.speed

    private val _busy = MutableStateFlow<String?>(null)
    /** Which action is running (its key), so its button shows a spinner and the others wait. */
    val busy: StateFlow<String?> = _busy
    private val _event = MutableStateFlow<SettingsEvent?>(null)
    val event: StateFlow<SettingsEvent?> = _event

    fun consumed() { _event.value = null }

    private fun run(key: String, block: suspend () -> SettingsEvent?) {
        if (_busy.value != null) return
        _busy.value = key
        viewModelScope.launch {
            val ev = try { block() } catch (e: RepoException) { SettingsEvent.Toast(e.userMessage) } catch (e: Exception) { null }
            _busy.value = null
            if (ev != null) _event.value = ev
        }
    }

    // ── Account & data ──
    fun signOut() = run("signout") { repo.signOut(); SettingsEvent.ToWelcome(null) }

    fun export(copy: SettingsCopy) = run("export") {
        val json = runCatching { repo.exportMyData() }.getOrNull()
        if (json.isNullOrBlank() || json == "{}") SettingsEvent.Toast(copy.exportError) else SettingsEvent.Share(json)
    }

    fun deleteAccount(copy: SettingsCopy) = run("delete") {
        val r = repo.deleteAccount()
        r.fold({ SettingsEvent.ToWelcome(copy.deleted) }, { e -> SettingsEvent.Toast((e as? RepoException)?.userMessage ?: copy.generic) })
    }

    // ── Demo controls (F18) ──
    fun switchPersona(p: DemoPersona?, name: String, copy: SettingsCopy) = run("persona_${p?.name ?: "own"}") {
        val d = repo.demo ?: return@run null
        d.switchPersona(p)
        SettingsEvent.Restart(toOnboarding = p == null, toast = copy.switched(name))
    }

    fun setSpeed(s: SimSpeed) { repo.demo?.setSpeed(s) }

    fun advance(days: Int, copy: SettingsCopy) = run("plus$days") {
        repo.demo?.advanceDays(days)
        moved(copy)
    }

    fun jumpPastNextShoot(copy: SettingsCopy) = run("next_shoot") { repo.demo?.jumpPastNextShoot(); moved(copy) }
    fun jumpPastWrap(copy: SettingsCopy) = run("past_wrap") { repo.demo?.jumpPastWrap(); moved(copy) }

    private fun moved(copy: SettingsCopy): SettingsEvent? = repo.demo?.demoDate?.value?.let { SettingsEvent.Toast(copy.moved(Dates.shortUpper(it))) }

    /** Runs [action] and returns the notifications it produced for the signed-in person (newest first). */
    private suspend fun produced(action: suspend () -> Unit): List<AppNotification> {
        val before = runCatching { repo.notifications().first() }.getOrDefault(emptyList()).map { it.id to it.createdAt }.toSet()
        action()
        val after = runCatching { repo.notifications().first() }.getOrDefault(emptyList())
        return after.filter { (it.id to it.createdAt) !in before }
    }

    fun injectFlagged(copy: SettingsCopy) = run("inject") {
        val d = repo.demo ?: return@run null
        val new = produced { d.injectFlaggedExample() }
        val n = new.firstOrNull { it.kind == NotificationKind.MATCH_CREATED || it.kind == NotificationKind.ASK_RECEIVED } ?: new.firstOrNull()
        if (n == null) SettingsEvent.Toast(copy.injectNone) else SettingsEvent.Toast(copy.injected(n.title), n.deepLink)
    }

    fun simulateWithdrawal(copy: SettingsCopy) = run("withdraw") {
        val d = repo.demo ?: return@run null
        val new = produced { d.simulateWithdrawal() }
        val n = new.firstOrNull { it.kind == NotificationKind.WITHDRAWAL }
        if (n == null) SettingsEvent.Toast(copy.withdrawNone) else SettingsEvent.Toast("${n.title}. ${n.body}", n.deepLink)
    }

    fun reset(copy: SettingsCopy) = run("reset") {
        val d = repo.demo ?: return@run null
        d.reset()
        val s = repo.session.value
        if (s == null) SettingsEvent.ToWelcome(copy.resetDone) else SettingsEvent.Restart(toOnboarding = !s.hasProfile, toast = copy.resetDone)
    }
}
