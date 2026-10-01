package com.tff.app.nav

import androidx.compose.runtime.staticCompositionLocalOf
import com.tff.app.model.TrustPage

/**
 * Screens navigate ONLY through this interface (never NavController directly), so every workstream can
 * build its screens in isolation. Implemented in NavGraph.kt.
 */
interface AppNavigator {
    fun back()
    fun toWelcome()
    fun toSignIn()
    fun toOnboarding()
    fun toMain(tab: MainTab = MainTab.DISCOVER)
    fun toRoleDetail(roleId: String)
    fun toCrewUp(roleId: String? = null)
    fun toPerson(profileId: String, roleId: String? = null, applicationId: String? = null)
    fun toPostProject(projectId: String? = null)
    fun toProjectDashboard(projectId: String)
    fun toShootDayEditor(projectId: String, shootDayId: String? = null)
    fun toChat(matchId: String)
    fun toBookingCard(matchId: String)
    fun toShootDayView(projectId: String, shootDayId: String)
    fun toWrap(matchId: String? = null, projectId: String? = null)
    fun toEditProfile()
    fun toSafetyCenter()
    fun toSettings()
    fun toSaved()
    fun toTrustPage(page: TrustPage)
    /** QA / deep-link string routes ("chat/m_001", "role/r_001_1", "calltime://m/m_001"). */
    fun go(route: String)
    /** External links. demo.calltime.app links show the "Demo link" toast instead of opening. */
    fun openUrl(url: String)
    /** Android share sheet with plain text (WhatsApp, Instagram, FB groups...). */
    fun share(text: String, title: String? = null)
    /** Short snackbar-style message. */
    fun toast(message: String)
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("No AppNavigator provided") }
