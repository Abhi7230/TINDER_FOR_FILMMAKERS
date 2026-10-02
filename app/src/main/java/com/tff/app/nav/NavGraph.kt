package com.tff.app.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tff.app.di.AppContainer
import com.tff.app.model.Inbox
import com.tff.app.model.TrustPage
import com.tff.app.ui.components.ComponentGallery
import com.tff.app.ui.screens.booking.BookingCardScreen
import com.tff.app.ui.screens.chat.ChatScreen
import com.tff.app.ui.screens.crewup.CrewUpScreen
import com.tff.app.ui.screens.discover.DiscoverScreen
import com.tff.app.ui.screens.inbox.InboxScreen
import com.tff.app.ui.screens.me.EditProfileScreen
import com.tff.app.ui.screens.me.MeScreen
import com.tff.app.ui.screens.me.SavedScreen
import com.tff.app.ui.screens.onboarding.OnboardingScreen
import com.tff.app.ui.screens.onboarding.SignInScreen
import com.tff.app.ui.screens.onboarding.SplashScreen
import com.tff.app.ui.screens.onboarding.WelcomeScreen
import com.tff.app.ui.screens.person.PersonDetailScreen
import com.tff.app.ui.screens.post.PostProjectScreen
import com.tff.app.ui.screens.projects.ProjectDashboardScreen
import com.tff.app.ui.screens.projects.ProjectsScreen
import com.tff.app.ui.screens.projects.ShootDayEditorScreen
import com.tff.app.ui.screens.role.RoleDetailScreen
import com.tff.app.ui.screens.safety.SafetyCenterScreen
import com.tff.app.ui.screens.settings.SettingsScreen
import com.tff.app.ui.screens.settings.TrustPageScreen
import com.tff.app.ui.screens.shootday.ShootDayViewScreen
import com.tff.app.ui.screens.wrap.WrapScreen
import com.tff.app.ui.theme.Ct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CalltimeNavHost(startAtMain: Boolean, pendingRoute: MutableState<String?>) {
    val nc = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navigator = remember(nc) { NavigatorImpl(nc, context, snackbar, scope) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Box(Modifier.fillMaxSize().background(Ct.colors.bg)) {
            NavHost(nc, startDestination = if (startAtMain) MainRoute() else SplashRoute) {
                composable<SplashRoute> { SplashScreen(navigator) }
                composable<WelcomeRoute> { WelcomeScreen(navigator) }
                composable<SignInRoute> { SignInScreen(navigator) }
                composable<OnboardingRoute> { OnboardingScreen(navigator) }
                composable<MainRoute> { MainScreen(navigator) }
                composable<RoleDetailRoute> { RoleDetailScreen(navigator, it.toRoute<RoleDetailRoute>().roleId) }
                composable<CrewUpRoute> { CrewUpScreen(navigator, it.toRoute<CrewUpRoute>().roleId) }
                composable<PersonDetailRoute> {
                    val r = it.toRoute<PersonDetailRoute>()
                    PersonDetailScreen(navigator, r.profileId, r.roleId, r.applicationId)
                }
                composable<PostProjectRoute> { PostProjectScreen(navigator, it.toRoute<PostProjectRoute>().projectId) }
                composable<ProjectDashboardRoute> { ProjectDashboardScreen(navigator, it.toRoute<ProjectDashboardRoute>().projectId) }
                composable<ShootDayEditorRoute> {
                    val r = it.toRoute<ShootDayEditorRoute>()
                    ShootDayEditorScreen(navigator, r.projectId, r.shootDayId)
                }
                composable<ChatRoute> { ChatScreen(navigator, it.toRoute<ChatRoute>().matchId) }
                composable<BookingCardRoute> { BookingCardScreen(navigator, it.toRoute<BookingCardRoute>().matchId) }
                composable<ShootDayViewRoute> {
                    val r = it.toRoute<ShootDayViewRoute>()
                    ShootDayViewScreen(navigator, r.projectId, r.shootDayId)
                }
                composable<WrapRoute> {
                    val r = it.toRoute<WrapRoute>()
                    WrapScreen(navigator, r.matchId, r.projectId)
                }
                composable<EditProfileRoute> { EditProfileScreen(navigator) }
                composable<SafetyCenterRoute> { SafetyCenterScreen(navigator) }
                composable<SettingsRoute> { SettingsScreen(navigator) }
                composable<SavedRoute> { SavedScreen(navigator) }
                composable<TrustPageRoute> { TrustPageScreen(navigator, TrustPage.valueOf(it.toRoute<TrustPageRoute>().page)) }
                composable<GalleryRoute> { ComponentGallery(navigator) }
            }
            // Top placement: never covers deck buttons, sticky action bars or the chat composer.
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp))
        }
    }

    // Deep links / notification taps wait until Splash has routed (session restore), otherwise Splash's
    // own navigation (popUpTo 0) would replace the linked screen on a cold start.
    if (startAtMain) navigator.startupDone.value = true
    LaunchedEffect(pendingRoute.value, navigator.startupDone.value) {
        val r = pendingRoute.value ?: return@LaunchedEffect
        if (!navigator.startupDone.value) return@LaunchedEffect
        pendingRoute.value = null
        navigator.go(r)
    }
}

/** Bottom-nav shell: Discover · Projects · Inbox · Me. Tab screens own their top bars and status-bar insets. */
@Composable
private fun MainScreen(navigator: NavigatorImpl) {
    val repo = AppContainer.repo
    val inbox by remember { repo.inbox() }.collectAsStateWithLifecycle(Inbox())
    val tab = navigator.selectedTab.value
    Scaffold(
        containerColor = Ct.colors.bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Ct.colors.surface, tonalElevation = 0.dp) {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { navigator.selectedTab.value = t },
                        icon = {
                            val icon = when (t) {
                                MainTab.DISCOVER -> Icons.Rounded.Style
                                MainTab.PROJECTS -> Icons.Rounded.Movie
                                MainTab.INBOX -> Icons.Rounded.ChatBubble
                                MainTab.ME -> Icons.Rounded.Person
                            }
                            if (t == MainTab.INBOX && inbox.unreadCount > 0) {
                                BadgedBox(badge = { Badge(containerColor = Ct.colors.accent, contentColor = Ct.colors.onAccent) { Text("${inbox.unreadCount}") } }) {
                                    Icon(icon, contentDescription = null)
                                }
                            } else Icon(icon, contentDescription = null)
                        },
                        label = {
                            Text(when (t) { MainTab.DISCOVER -> "Discover"; MainTab.PROJECTS -> "Projects"; MainTab.INBOX -> "Inbox"; MainTab.ME -> "Me" })
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ct.colors.accent, selectedTextColor = Ct.colors.accent,
                            indicatorColor = Ct.colors.surfaceHigh,
                            unselectedIconColor = Ct.colors.textSecondary, unselectedTextColor = Ct.colors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                MainTab.DISCOVER -> DiscoverScreen(navigator)
                MainTab.PROJECTS -> ProjectsScreen(navigator)
                MainTab.INBOX -> InboxScreen(navigator)
                MainTab.ME -> MeScreen(navigator)
            }
        }
    }
}

class NavigatorImpl(
    private val nc: NavHostController,
    private val context: Context,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) : AppNavigator {
    val selectedTab = mutableStateOf(MainTab.DISCOVER)
    /** True once Splash has sent the user to Welcome / Onboarding / Main (or the app started at Main). */
    val startupDone = mutableStateOf(false)

    override fun back() { if (!nc.popBackStack()) toMain() }
    override fun toWelcome() { nc.navigate(WelcomeRoute) { popUpTo(0) { inclusive = true } }; startupDone.value = true }
    override fun toSignIn() = nc.navigate(SignInRoute)
    override fun toOnboarding() { nc.navigate(OnboardingRoute) { popUpTo(0) { inclusive = true } }; startupDone.value = true }
    override fun toMain(tab: MainTab) {
        selectedTab.value = tab
        val popped = runCatching { nc.popBackStack<MainRoute>(inclusive = false) }.getOrDefault(false)
        if (!popped) nc.navigate(MainRoute(tab.name)) { popUpTo(0) { inclusive = true } }
        startupDone.value = true
    }
    override fun toRoleDetail(roleId: String) = nc.navigate(RoleDetailRoute(roleId))
    override fun toCrewUp(roleId: String?) = nc.navigate(CrewUpRoute(roleId))
    override fun toPerson(profileId: String, roleId: String?, applicationId: String?) = nc.navigate(PersonDetailRoute(profileId, roleId, applicationId))
    override fun toPostProject(projectId: String?) = nc.navigate(PostProjectRoute(projectId))
    override fun toProjectDashboard(projectId: String) = nc.navigate(ProjectDashboardRoute(projectId))
    override fun toShootDayEditor(projectId: String, shootDayId: String?) = nc.navigate(ShootDayEditorRoute(projectId, shootDayId))
    override fun toChat(matchId: String) = nc.navigate(ChatRoute(matchId)) { launchSingleTop = true }
    override fun toBookingCard(matchId: String) = nc.navigate(BookingCardRoute(matchId))
    override fun toShootDayView(projectId: String, shootDayId: String) = nc.navigate(ShootDayViewRoute(projectId, shootDayId))
    override fun toWrap(matchId: String?, projectId: String?) = nc.navigate(WrapRoute(matchId, projectId))
    override fun toEditProfile() = nc.navigate(EditProfileRoute)
    override fun toSafetyCenter() = nc.navigate(SafetyCenterRoute)
    override fun toSettings() = nc.navigate(SettingsRoute)
    override fun toSaved() = nc.navigate(SavedRoute)
    override fun toTrustPage(page: TrustPage) = nc.navigate(TrustPageRoute(page.name))

    /**
     * String routes for QA intents, deep links and notifications:
     * discover | projects | inbox | me | role/{id} | crewup[/{roleId}] | person/{id} | post[/{projectId}] |
     * dashboard/{projectId} | shootday-edit/{projectId}[/{dayId}] | chat/{matchId} | booking/{matchId} |
     * shootday/{projectId}/{dayId} | wrap/m/{matchId} | wrap/p/{projectId} | editprofile | safety | settings |
     * saved | trust/{PAGE} | welcome | onboarding | signin ; also calltime://r|m|p/{id} and https://calltime.app/r/{id}
     */
    override fun go(route: String) {
        var r = route.trim()
        r = r.removePrefix("calltime://").removePrefix("https://calltime.app/").removePrefix("http://calltime.app/")
        val p = r.split("/").filter { it.isNotEmpty() }
        if (p.isEmpty()) return toMain()
        when (p[0]) {
            "discover" -> toMain(MainTab.DISCOVER)
            "projects" -> toMain(MainTab.PROJECTS)
            "inbox" -> toMain(MainTab.INBOX)
            "me" -> toMain(MainTab.ME)
            "role", "r" -> p.getOrNull(1)?.let(::toRoleDetail)
            "crewup" -> toCrewUp(p.getOrNull(1))
            "person" -> p.getOrNull(1)?.let { toPerson(it) }
            "post" -> toPostProject(p.getOrNull(1))
            "dashboard", "p" -> p.getOrNull(1)?.let(::toProjectDashboard)
            "shootday-edit" -> p.getOrNull(1)?.let { toShootDayEditor(it, p.getOrNull(2)) }
            "chat", "m" -> p.getOrNull(1)?.let(::toChat)
            "booking" -> p.getOrNull(1)?.let(::toBookingCard)
            "shootday", "d" -> if (p.size >= 3) toShootDayView(p[1], p[2])
            "wrap" -> when (p.getOrNull(1)) { "m" -> toWrap(matchId = p.getOrNull(2)); "p" -> toWrap(projectId = p.getOrNull(2)); else -> toWrap() }
            "editprofile" -> toEditProfile()
            "safety" -> toSafetyCenter()
            "settings" -> toSettings()
            "saved" -> toSaved()
            "trust" -> p.getOrNull(1)?.let { runCatching { TrustPage.valueOf(it) }.getOrNull() }?.let(::toTrustPage)
            "welcome" -> toWelcome()
            "onboarding" -> toOnboarding()
            "signin" -> toSignIn()
            "gallery" -> nc.navigate(GalleryRoute)
            else -> toMain()
        }
    }

    override fun openUrl(url: String) {
        if (url.contains("demo.calltime.app")) return toast("Demo link")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast("No app can open this link")
        }
    }

    override fun share(text: String, title: String?) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (title != null) send.putExtra(Intent.EXTRA_SUBJECT, title)
        context.startActivity(Intent.createChooser(send, title ?: "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun toast(message: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) }
    }
}
