package com.tff.app.di

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tff.app.BuildConfig
import com.tff.app.data.Repo
import com.tff.app.data.demo.DemoRepo
import com.tff.app.data.remote.SupabaseRepo
import com.tff.app.model.AppMode

/**
 * Manual DI (PRD §10.1). DEMO when SUPABASE_URL is blank or the user switched "Use demo mode" on;
 * otherwise LIVE (Supabase). The mode is fixed for the life of the process.
 */
object AppContainer {
    lateinit var app: Application
        private set
    lateinit var repo: Repo
        private set

    val shareBaseUrl: String get() = BuildConfig.SHARE_BASE_URL
    val liveConfigured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank()

    fun init(app: Application) {
        this.app = app
        val prefs = app.getSharedPreferences("calltime", Context.MODE_PRIVATE)
        val forceDemo = prefs.getBoolean("use_demo_mode", false)
        val mode = if (!liveConfigured || forceDemo) AppMode.DEMO else AppMode.LIVE
        repo = when (mode) {
            AppMode.DEMO -> DemoRepo(app)
            AppMode.LIVE -> SupabaseRepo(app, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        }
    }

    /** Takes effect on next launch (Settings restarts the app). */
    fun setUseDemoMode(on: Boolean) {
        app.getSharedPreferences("calltime", Context.MODE_PRIVATE).edit().putBoolean("use_demo_mode", on).apply()
    }
}

/** `val vm = rememberVm { repo -> DiscoverViewModel(repo) }` — one VM per destination (pass [key] for per-id VMs). */
@Composable
inline fun <reified VM : ViewModel> rememberVm(key: String? = null, crossinline create: (Repo) -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create(AppContainer.repo) } })
