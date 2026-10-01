package com.tff.app.nav

import kotlinx.serialization.Serializable

// Type-safe navigation-compose routes (PRD §8.0). Enum-valued params travel as String names.
@Serializable object SplashRoute
@Serializable object WelcomeRoute
@Serializable object SignInRoute
@Serializable object OnboardingRoute
@Serializable data class MainRoute(val tab: String = "DISCOVER")
@Serializable data class RoleDetailRoute(val roleId: String)
@Serializable data class CrewUpRoute(val roleId: String? = null)
@Serializable data class PersonDetailRoute(val profileId: String, val roleId: String? = null, val applicationId: String? = null)
@Serializable data class PostProjectRoute(val projectId: String? = null)
@Serializable data class ProjectDashboardRoute(val projectId: String)
@Serializable data class ShootDayEditorRoute(val projectId: String, val shootDayId: String? = null)
@Serializable data class ChatRoute(val matchId: String)
@Serializable data class BookingCardRoute(val matchId: String)
@Serializable data class ShootDayViewRoute(val projectId: String, val shootDayId: String)
@Serializable data class WrapRoute(val matchId: String? = null, val projectId: String? = null)
@Serializable object EditProfileRoute
@Serializable object SafetyCenterRoute
@Serializable object SettingsRoute
@Serializable object SavedRoute
@Serializable data class TrustPageRoute(val page: String)
/** Debug-only component gallery (WS1) — reach it with qa_route "gallery". */
@Serializable object GalleryRoute

enum class MainTab { DISCOVER, PROJECTS, INBOX, ME }
