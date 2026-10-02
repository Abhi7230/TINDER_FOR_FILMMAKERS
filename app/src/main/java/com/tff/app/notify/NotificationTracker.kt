package com.tff.app.notify

import com.tff.app.model.AppNotification

/**
 * Pure bookkeeping behind [SystemNotifier] (unit-tested): which notifications are NEW and should be
 * posted to the system tray. The first list seen for a person is the baseline and is never posted;
 * after that, an item is new the first time its (person, id, createdAt) appears. Items that arrive
 * while the app is in the foreground are marked seen (the Inbox shows them) and never posted later.
 */
class NotificationTracker {
    private val seen = HashSet<String>()
    private val baselined = HashSet<String>()

    @Synchronized
    fun onList(userId: String, list: List<AppNotification>, foreground: Boolean): List<AppNotification> {
        val keyed = list.map { it to key(userId, it) }
        val fresh = keyed.filter { (_, k) -> k !in seen }.map { it.first }
        keyed.forEach { seen += it.second }
        if (baselined.add(userId)) return emptyList()
        if (foreground) return emptyList()
        return fresh.filter { it.readAt == null }.sortedBy { it.createdAt }
    }

    /** A persona switch or sign-out: the next list for [userId] is a fresh baseline. */
    @Synchronized
    fun reset(userId: String) { baselined.remove(userId) }

    companion object {
        fun key(userId: String, n: AppNotification) = "$userId|${n.id}|${n.createdAt}"
    }
}
