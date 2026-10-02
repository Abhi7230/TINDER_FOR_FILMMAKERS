package com.tff.app.ui.screens.onboarding

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tff.app.R
import com.tff.app.data.Repo
import com.tff.app.di.rememberVm
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.filmGrainBehind
import com.tff.app.ui.theme.lightLeak
import com.tff.app.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SplashTarget { MAIN, ONBOARDING, WELCOME }

/**
 * S01: restores the session (both repos answer "who is signed in?" from a local snapshot, instantly) and
 * routes: session + profile → Main; session without a profile → Onboarding; nobody → Welcome.
 * Holds the mark for a short beat so the launch doesn't flash.
 */
class SplashViewModel(private val repo: Repo) : ViewModel() {
    private val _target = MutableStateFlow<SplashTarget?>(null)
    val target: StateFlow<SplashTarget?> = _target

    init {
        viewModelScope.launch {
            delay(MIN_SPLASH_MS)
            val s = runCatching { repo.session.value }.getOrNull()
            _target.value = when {
                s == null -> SplashTarget.WELCOME
                s.hasProfile -> SplashTarget.MAIN
                else -> SplashTarget.ONBOARDING
            }
            runCatching { repo.log("app_open", mapOf("signed_in" to (s != null).toString())) }
        }
    }

    /** True once Splash left the screen (something navigated over it). Coming back means "don't replay the link". */
    var leftOnce = false

    companion object { const val MIN_SPLASH_MS = 650L }
}

/** Links a guest may open without an account: shared role cards (calltime://r/{id}, https://calltime.app/r/{id}). */
internal fun isPublicLink(route: String): Boolean {
    val r = route.removePrefix("calltime://").removePrefix("https://calltime.app/").removePrefix("http://calltime.app/")
    return r.startsWith("r/") || r.startsWith("role/")
}

@Composable
fun SplashScreen(nav: AppNavigator) {
    val vm = rememberVm { SplashViewModel(it) }
    val target by vm.target.collectAsStateWithLifecycle()
    // A cold start from a deep link or a notification tap: MainActivity queues it and the nav graph holds
    // it until Splash has routed (startupDone), then opens it. Splash only picks where to land first, so
    // the link opens once (opening it here too used to push the screen twice).
    val activity = LocalContext.current as? Activity
    val linked = remember { activity?.intent?.let { it.getStringExtra("route") ?: it.dataString } }
    DisposableEffect(Unit) { onDispose { vm.leftOnce = true } }
    LaunchedEffect(target) {
        val pending = linked.takeIf { !vm.leftOnce }
        when (target) {
            SplashTarget.MAIN -> nav.toMain()
            SplashTarget.ONBOARDING -> nav.toOnboarding()
            // guests may open shared role links (F17) from Main; the held link then opens over it
            SplashTarget.WELCOME -> if (pending != null && isPublicLink(pending)) nav.toMain() else nav.toWelcome()
            null -> Unit
        }
    }
    val c = Ct.colors
    val reduce = rememberReduceMotion()
    val clap = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (reduce) clap.snapTo(0f) else { delay(120); clap.animateTo(0f, tween(180)) }
    }
    val cd = stringResource(R.string.ws7_splash_cd)
    Box(
        Modifier.fillMaxSize().background(c.bg).lightLeak(c.accent, alpha = 0.12f).filmGrainBehind().semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            SlateMark(size = 88.dp, openFraction = clap.value)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.ws7_welcome_kicker), style = Ct.type.displayL, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tagline), style = Ct.type.bodyS, color = c.textSecondary)
        }
        LinearProgressIndicator(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp).width(96.dp),
            color = c.accent,
            trackColor = c.surfaceHigh,
        )
    }
}
