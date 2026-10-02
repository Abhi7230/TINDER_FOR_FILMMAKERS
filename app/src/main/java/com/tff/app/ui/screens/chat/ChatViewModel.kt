package com.tff.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.model.AuditionRequest
import com.tff.app.model.AuditionResponse
import com.tff.app.model.BookingCard
import com.tff.app.model.Features
import com.tff.app.model.MatchView
import com.tff.app.model.Message
import com.tff.app.model.Report
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the chat renders. [view] is null when the conversation doesn't exist or isn't the viewer's. */
data class ChatData(
    val view: MatchView?,
    val messages: List<Message>,
    val cards: List<BookingCard>,
    val myId: String?,
    val reports: List<Report>,
)

/** S15 Chat for one match. Messages update live (the demo simulator replies through the same flows). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val repo: Repo, val matchId: String) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val features: Features = repo.features

    val state: StateFlow<Load<ChatData>> = retry.flatMapLatest {
        combine(repo.matchView(matchId), repo.messages(matchId), repo.bookingCards(matchId), repo.myReports(), repo.session) { v, msgs, cards, reports, s ->
            ChatData(v, msgs, cards, s?.userId, reports)
        }
            .map<ChatData, Load<ChatData>> { Load.Ready(it) }
            .onStart { emit(Load.Loading) }
            .catch { emit(Load.Failed(it.userMessage())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    val sending = MutableStateFlow(false)

    init { ChatBridge.chatOpened(matchId) }

    override fun onCleared() { ChatBridge.chatClosed(matchId) }

    fun retry() { retry.value++ }

    fun markRead() { viewModelScope.launch { runCatching { repo.markRead(matchId) } } }

    fun send(text: String, onDone: (Result<Unit>) -> Unit) {
        if (sending.value || text.isBlank()) return
        sending.value = true
        viewModelScope.launch {
            val r = runCatching { repo.sendMessage(matchId, text.trim()) }.getOrElse { Result.failure(it) }
            sending.value = false
            onDone(r)
        }
    }

    fun sendAudition(req: AuditionRequest, onDone: (Result<Unit>) -> Unit) = call(onDone) { repo.sendAuditionRequest(matchId, req) }

    fun respondAudition(messageId: String, response: AuditionResponse, tapeUrl: String?, onDone: (Result<Unit>) -> Unit) =
        call(onDone) { repo.respondAudition(messageId, response, tapeUrl) }

    fun shareContacts(onDone: (Result<Unit>) -> Unit) = call(onDone) { repo.shareContacts(matchId) }

    private fun call(onDone: (Result<Unit>) -> Unit, block: suspend () -> Result<Unit>) {
        viewModelScope.launch { onDone(runCatching { block() }.getOrElse { Result.failure(it) }) }
    }
}
