package com.tff.app.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.model.AppNotification
import com.tff.app.model.AskDeclineReason
import com.tff.app.model.Inbox
import com.tff.app.model.Match
import com.tff.app.model.Session
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.userMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** S14 Inbox: asks, conversations and updates for the signed-in person. Guests get no data. */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val repo: Repo) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val session: StateFlow<Session?> = repo.session

    val state: StateFlow<Load<Inbox>> = retry.flatMapLatest {
        repo.session.flatMapLatest { s ->
            if (s == null) flowOf(Load.Ready(Inbox()))
            else repo.inbox()
                .map<Inbox, Load<Inbox>> { Load.Ready(it) }
                .onStart { emit(Load.Loading) }
                .catch { emit(Load.Failed(it.userMessage())) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

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
