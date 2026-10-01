package com.tff.app.ui.screens.discover

import android.content.Context
import android.content.SharedPreferences
import com.tff.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Small per-device UI memory for WS4 screens (never data that has to reach a server): the guest's
 * city, whether the notification prompt and the cast-role tip have been shown, and Scout skips.
 * Every access is guarded, so a missing context (unit tests) simply falls back to defaults.
 */
object Ws4Prefs {
    private const val FILE = "calltime_ws4"
    private const val KEY_GUEST_CITY = "guest_city"
    private const val KEY_NOTIF_ASKED = "notif_asked"
    private const val KEY_CAST_TIP = "cast_tip_seen"
    private const val KEY_SCOUT_SKIP = "scout_skip_"
    const val DEFAULT_GUEST_CITY = "mumbai"

    private fun prefs(): SharedPreferences? =
        runCatching { AppContainer.app.getSharedPreferences(FILE, Context.MODE_PRIVATE) }.getOrNull()

    var guestCityId: String
        get() = prefs()?.getString(KEY_GUEST_CITY, null) ?: DEFAULT_GUEST_CITY
        set(v) { prefs()?.edit()?.putString(KEY_GUEST_CITY, v)?.apply() }

    var notificationPromptShown: Boolean
        get() = prefs()?.getBoolean(KEY_NOTIF_ASKED, false) ?: true
        set(v) { prefs()?.edit()?.putBoolean(KEY_NOTIF_ASKED, v)?.apply() }

    var castTipSeen: Boolean
        get() = prefs()?.getBoolean(KEY_CAST_TIP, false) ?: false
        set(v) { prefs()?.edit()?.putBoolean(KEY_CAST_TIP, v)?.apply() }

    /**
     * Scout "Skip" hides a person for one role. The Repo contract has no call for it, so it is kept on
     * this device. TODO(LIVE): persist server-side so skips follow the owner across devices.
     */
    fun scoutSkips(roleId: String): Set<String> = prefs()?.getStringSet(KEY_SCOUT_SKIP + roleId, emptySet())?.toSet().orEmpty()

    fun addScoutSkip(roleId: String, profileId: String) {
        val p = prefs() ?: return
        p.edit().putStringSet(KEY_SCOUT_SKIP + roleId, scoutSkips(roleId) + profileId).apply()
    }

    fun removeScoutSkip(roleId: String, profileId: String) {
        val p = prefs() ?: return
        p.edit().putStringSet(KEY_SCOUT_SKIP + roleId, scoutSkips(roleId) - profileId).apply()
    }
}

/**
 * Role ids the person has just passed, saved or applied to from Role detail, so the Gigs deck (whose
 * order is frozen while you swipe) drops them without a rebuild. Cleared whenever the deck is rebuilt
 * from the Repo, which already excludes swiped roles.
 */
object DeckSignals {
    private val _gone = MutableStateFlow<Set<String>>(emptySet())
    val gone: StateFlow<Set<String>> = _gone.asStateFlow()

    fun hide(roleId: String) = _gone.update { it + roleId }
    fun unhide(roleId: String) = _gone.update { it - roleId }
    fun clear() { _gone.value = emptySet() }
}
