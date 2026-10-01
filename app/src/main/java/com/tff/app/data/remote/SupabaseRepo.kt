package com.tff.app.data.remote

import android.content.Context
import com.tff.app.data.Repo
import com.tff.app.data.StubRepo
import com.tff.app.model.AppMode

/** PLACEHOLDER — replaced by WS8 (Supabase LIVE mode, L0). */
class SupabaseRepo(context: Context, url: String, anonKey: String) : Repo by StubRepo(AppMode.LIVE)
