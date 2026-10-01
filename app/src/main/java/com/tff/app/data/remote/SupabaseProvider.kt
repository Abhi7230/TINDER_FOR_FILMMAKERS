package com.tff.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * One SupabaseClient per process (Auth with persisted + auto-refreshed session, Postgrest, Realtime).
 * URL and anon key come from local.properties via BuildConfig (never committed).
 */
object SupabaseProvider {
    /** Wire JSON: tolerant of new server fields, emits defaults so RPC payloads are complete. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    @Volatile private var instance: SupabaseClient? = null

    fun client(url: String, anonKey: String): SupabaseClient =
        instance ?: synchronized(this) {
            instance ?: createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
                defaultSerializer = KotlinXSerializer(json)
                install(Auth) {
                    alwaysAutoRefresh = true
                    autoLoadFromStorage = true     // session restore on app start
                    autoSaveToStorage = true
                }
                install(Postgrest)
                install(Realtime)
            }.also { instance = it }
        }
}
