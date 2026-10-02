package com.tff.app.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.model.AppNotification
import com.tff.app.model.AskDeclineReason
import com.tff.app.model.Inbox
import com.tff.app.model.Match
import com.tff.app.model.Message
import com.tff.app.model.Report
import com.tff.app.model.Session
import com.tff.app.ui.screens.chat.ENDED_STATES
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.isOrgName
import com.tff.app.ui.screens.chat.userMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Max live threads read for safety markers (the list itself is not capped). */
private const val THREADS_WATCHED = 30

/**
 * S14 Inbox: asks, conversations and updates for the signed-in person, plus what the list needs on top:
 * safety markers per conversation (red flags in the thread, the viewer's reports), the "Needs you" group and
 * the ordering. Guests get no data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val repo: Repo) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val session: StateFlow<Session?> = repo.session

    /** Full names for organisation-style handles ("Casting Hub Official"), keyed by profile id. */
    val fullNames = MutableStateFlow<Map<String, String>>(emptyMap())

    private val inboxLoad = retry.flatMapLatest {
        repo.session.flatMapLatest { s ->
            if (s == null) flowOf<Load<Inbox>>(Load.Ready(Inbox()))
            else repo.inbox()
                .map<Inbox, Load<Inbox>> { Load.Ready(it) }
                .onStart { emit(Load.Loading) }
                .catch { emit(Load.Failed(it.userMessage())) }
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Messages of the live conversations, re-subscribed only when the set of live conversations changes. */
    private val threads: Flow<Map<String, List<Message>>> = inboxLoad
        .map { l -> (l as? Load.Ready)?.data?.conversations.orEmpty().filter { it.match.state !in ENDED_STATES }.map { it.match.id }.take(THREADS_WATCHED) }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyMap())
            else combine(ids.map { id -> repo.messages(id).map { id to it }.onStart { emit(id to emptyList()) }.catch { emit(id to emptyList()) } }) { it.toMap() }
        }
        .onStart { emit(emptyMap()) }

    private val reports: Flow<List<Report>> = repo.session.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else repo.myReports().onStart { emit(emptyList()) }.catch { emit(emptyList()) }
    }

    val state: StateFlow<Load<InboxUi>> = combine(inboxLoad, threads, reports, repo.session) { l, t, r, s ->
        when (l) {
            Load.Loading -> Load.Loading
            is Load.Failed -> l
            is Load.Ready -> Load.Ready(buildInboxUi(l.data, t, r, s?.userId)).also { resolveNames(l.data) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    /** Rows carry short names ("Casting O."); for organisation-style handles, look the full name up once. */
    private fun resolveNames(inbox: Inbox) {
        val missing = inbox.conversations.filter { isOrgName(it.counterpartName) }.map { counterpartIdOf(it) }
            .filter { it !in fullNames.value }.distinct()
        if (missing.isEmpty()) return
        fullNames.value = fullNames.value + missing.associateWith { "" }       // in flight; don't ask twice
        viewModelScope.launch {
            val found = missing.mapNotNull { id ->
                runCatching { repo.profile(id) }.getOrNull()?.profile?.displayName?.trim()?.takeIf { it.isNotBlank() }?.let { id to it }
            }
            fullNames.value = fullNames.value + found
        }
    }

    /** Ask ids being answered right now (buttons show a spinner, double taps are ignored). */
    val busy = MutableStateFlow<Set<String>>(emptySet())

    fun retry() { retry.value++ }

    fun acceptAsk(appId: String, onDone: (Result<Match?>) -> Unit) = answer(appId, onDone) { repo.respondAsk(appId, true, null) }

    fun declineAsk(appId: String, reason: AskDeclineReason, onDone: (Result<Match?>) -> Unit) =
        answer(appId, onDone) { repo.respondAsk(appId, false, reason) }

    private fun answer(appId: String, onDone: (Result<Match?>) -> Unit, call: suspend () -> Result<Match?>) {
        if (appId in busy.value) return
        busy.value = busy.value + appId
        viewModelScope.launch {
            val r = runCatching { call() }.getOrElse { Result.failure(it) }
            busy.value = busy.value - appId
            onDone(r)
        }
    }

    fun markRead(n: AppNotification) {
        if (n.readAt != null) return
        viewModelScope.launch { runCatching { repo.markNotificationRead(n.id) } }
    }
}
