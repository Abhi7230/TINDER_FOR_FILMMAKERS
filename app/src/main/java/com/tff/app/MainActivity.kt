package com.tff.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.tff.app.di.AppContainer
import com.tff.app.model.DemoPersona
import com.tff.app.model.SimSpeed
import com.tff.app.nav.CalltimeNavHost
import com.tff.app.ui.theme.CalltimeTheme
import com.tff.app.ui.theme.Ct
import kotlinx.coroutines.launch

/**
 * Single activity. Deep links (calltime://r|m|p/{id}, https://calltime.app/r/{id}) become pending routes.
 *
 * QA hooks (debug builds only), used by agents and qa/run_qa.py:
 *   adb shell am start -n com.tff.app/.MainActivity --es qa_persona MEERA --es qa_route chat/m_meera_scam \
 *       [--ez qa_reset true] [--es qa_speed INSTANT]
 * qa_persona: MEERA | ROHAN | KABIR | DANA | NEW (fresh "build my own" user → use qa_route onboarding).
 */
class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)
    private val qaReady = mutableStateOf(true)
    private var qaMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // On recreation (rotation, font size…) the launch intent was already handled: re-handling it would push its
        // deep link a second time, or re-run the QA hook (and its reset) over the restored state. Only a link not
        // yet opened, or a QA hook cut short, carries over.
        if (savedInstanceState == null || savedInstanceState.getBoolean(KEY_QA_PENDING)) handleIntent(intent)
        else {
            qaMode = savedInstanceState.getBoolean(KEY_QA_MODE)
            pendingRoute.value = savedInstanceState.getString(KEY_PENDING_ROUTE)
        }
        setContent {
            CalltimeTheme {
                if (qaReady.value) CalltimeNavHost(startAtMain = qaMode, pendingRoute = pendingRoute)
                else Box(Modifier.fillMaxSize().background(Ct.colors.bg))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_QA_MODE, qaMode)
        outState.putBoolean(KEY_QA_PENDING, !qaReady.value)
        pendingRoute.value?.let { outState.putString(KEY_PENDING_ROUTE, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        i.data?.let { pendingRoute.value = it.toString() }
        i.getStringExtra("route")?.let { pendingRoute.value = it }          // notification taps
        val persona = i.getStringExtra("qa_persona")
        if (BuildConfig.DEBUG && persona != null) {
            qaMode = true
            qaReady.value = false
            val repo = AppContainer.repo
            lifecycleScope.launch {
                if (i.getBooleanExtra("qa_reset", false)) repo.demo?.reset()
                i.getStringExtra("qa_speed")?.let { s -> runCatching { SimSpeed.valueOf(s) }.getOrNull()?.let { repo.demo?.setSpeed(it) } }
                repo.startDemo(if (persona == "NEW") null else DemoPersona.valueOf(persona))
                pendingRoute.value = i.getStringExtra("qa_route") ?: "discover"
                qaReady.value = true
            }
        }
    }

    private companion object {
        const val KEY_QA_MODE = "calltime.qa_mode"
        const val KEY_QA_PENDING = "calltime.qa_pending"
        const val KEY_PENDING_ROUTE = "calltime.pending_route"
    }
}
