package com.tff.app.notify

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tff.app.MainActivity
import com.tff.app.R
import com.tff.app.di.AppContainer
import com.tff.app.model.AppNotification
import com.tff.app.model.NotificationKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** The notification groups people can switch off in Settings (S22). */
enum class NotifyCategory(val prefKey: String) {
    APPLICATIONS("notify_applications"),     // applicants, closures, backups, asks, IN TALKS
    BOOKINGS("notify_bookings"),             // messages, booking cards, changes, withdrawals
    SHOOTS("notify_shoots"),                 // day sheets, "Still coming?"
    WRAP("notify_wrap"),                     // wrap items, overdue nudges, feedback unlocked
    SAFETY("notify_safety");                 // report updates, account notices

    companion object {
        fun of(kind: NotificationKind): NotifyCategory = when (kind) {
            NotificationKind.APPLICATION_RECEIVED, NotificationKind.APPLICATION_CLOSED, NotificationKind.APPLICATION_BACKUP,
            NotificationKind.ASK_RECEIVED, NotificationKind.MATCH_CREATED -> APPLICATIONS
            NotificationKind.MESSAGE, NotificationKind.BOOKING_CARD, NotificationKind.BOOKING_ACCEPTED, NotificationKind.BOOKING_CHANGED,
            NotificationKind.WITHDRAWAL, NotificationKind.RELEASE, NotificationKind.PROJECT_CHANGED -> BOOKINGS
            NotificationKind.DAY_SHEET, NotificationKind.STILL_COMING -> SHOOTS
            NotificationKind.WRAP_ITEM, NotificationKind.WRAP_OVERDUE, NotificationKind.FEEDBACK_UNLOCKED -> WRAP
            NotificationKind.REPORT_UPDATE, NotificationKind.SYSTEM -> SAFETY
        }
    }
}

/**
 * F21 (demo half): observes [com.tff.app.data.Repo.notifications] for the signed-in person and posts an
 * Android notification for every NEW item while Calltime is in the background (in the foreground the
 * Inbox already shows it). Tapping one opens MainActivity with the extra "route" = the item's deep link
 * (calltime://m/{matchId} …), which MainActivity turns into a navigation.
 *
 * "New" means not seen by this process before: the first list after start (or after a persona switch)
 * is the baseline and is never posted, so old items don't flood the shade. Respects the
 * POST_NOTIFICATIONS permission and the per-category switches in Settings.
 */
object SystemNotifier {
    const val CHANNEL_ID = "calltime_updates"
    const val EXTRA_ROUTE = "route"
    private const val PREFS = "calltime_ws7"
    private const val KEY_ENABLED = "notify_enabled"
    private const val TAG = "CalltimeNotify"

    @Volatile private var started = false
    private val startedActivities = AtomicInteger(0)
    private val tracker = NotificationTracker()

    /** True while any Calltime activity is visible. */
    val isForeground: Boolean get() = startedActivities.get() > 0

    fun start(app: Application) {
        if (started) return
        started = true
        runCatching { createChannel(app) }.onFailure { Log.w(TAG, "channel", it) }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities.incrementAndGet() }
            override fun onActivityStopped(activity: Activity) { startedActivities.updateAndGet { (it - 1).coerceAtLeast(0) } }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            val repo = AppContainer.repo
            repo.session.map { it?.userId }.distinctUntilChanged().collectLatest { uid ->
                if (uid == null) return@collectLatest
                tracker.reset(uid)
                repo.notifications()
                    .catch { Log.w(TAG, "notifications flow failed", it) }
                    .collect { list -> tracker.onList(uid, list, isForeground).forEach { post(app, uid, it) } }
            }
        }
    }

    private fun key(uid: String, n: AppNotification) = NotificationTracker.key(uid, n)

    // ── Settings (S22) ──

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The in-app master switch (the system permission is checked separately). */
    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, true)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_ENABLED, on).apply()
    fun isCategoryOn(ctx: Context, c: NotifyCategory): Boolean = prefs(ctx).getBoolean(c.prefKey, true)
    fun setCategory(ctx: Context, c: NotifyCategory, on: Boolean) = prefs(ctx).edit().putBoolean(c.prefKey, on).apply()

    /** Whether Android will actually show our notifications (runtime permission on 13+, app not blocked). */
    fun systemAllows(ctx: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    // ── Posting ──

    private fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.ws7_notify_channel), NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = ctx.getString(R.string.ws7_notify_channel_desc)
        nm.createNotificationChannel(ch)
    }

    private fun post(ctx: Context, uid: String, n: AppNotification) {
        if (!isEnabled(ctx) || !isCategoryOn(ctx, NotifyCategory.of(n.kind)) || !systemAllows(ctx)) return
        val id = key(uid, n).hashCode()
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, n.deepLink ?: "inbox")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val category = when (NotifyCategory.of(n.kind)) {
            NotifyCategory.BOOKINGS -> if (n.kind == NotificationKind.MESSAGE) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT
            NotifyCategory.SHOOTS -> NotificationCompat.CATEGORY_REMINDER
            else -> NotificationCompat.CATEGORY_SOCIAL
        }
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_calltime)
            .setColor(TUNGSTEN)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setCategory(category)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notif)
            Log.i(TAG, "posted ${n.kind} ${n.id} → ${n.deepLink}")
        } catch (e: SecurityException) {
            Log.w(TAG, "notification permission missing", e)
        }
    }

    private const val TUNGSTEN = 0xFFFFB547.toInt()
}
