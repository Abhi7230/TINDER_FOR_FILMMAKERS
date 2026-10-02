package com.tff.app.ui.screens.wrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.model.BookingTerms
import com.tff.app.model.FeedbackAnswers
import com.tff.app.model.FeedbackState
import com.tff.app.model.Features
import com.tff.app.model.MatchView
import com.tff.app.model.Message
import com.tff.app.model.ProjectFull
import com.tff.app.model.WrapGroup
import com.tff.app.model.WrapItem
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.userMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WrapData {
    /** wrap/m/{matchId}: one booking, seen from either side. [view] null = not the viewer's. [mine] = the viewer's own chat messages (reminder cooldown). */
    data class Single(val view: MatchView?, val items: List<WrapItem>, val feedback: FeedbackState, val mine: List<Message> = emptyList()) : WrapData
    /** wrap/p/{projectId}: the owner's per-person groups; [extras] by match id. */
    data class Groups(
        val project: ProjectFull?,
        val groups: List<WrapGroup>,
        val feedback: Map<String, FeedbackState>,
        val extras: Map<String, GroupExtra> = emptyMap(),
    ) : WrapData
}

/** Per-person extras on the owner's wrap view: the accepted terms (pay owed), the card to report, and the owner's own messages. */
data class GroupExtra(val terms: BookingTerms?, val cardId: String?, val mine: List<Message>)

/** S17 Wrap checklist + S18 feedback submission. */
@OptIn(ExperimentalCoroutinesApi::class)
class WrapViewModel(private val repo: Repo, private val matchId: String?, private val projectId: String?) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val features: Features = repo.features

    /** The viewer's own messages in one chat (empty when the chat can't be read). */
    private fun mine(id: String): Flow<List<Message>> =
        combine(repo.messages(id).catch { emit(emptyList()) }, repo.session) { msgs, s -> msgs.filter { s != null && it.senderId == s.userId } }

    private fun source(): Flow<WrapData> = when {
        matchId != null -> combine(repo.matchView(matchId), repo.wrapItems(matchId), repo.feedbackState(matchId), mine(matchId)) { v, items, fb, my ->
            WrapData.Single(v, items, fb, my)
        }
        projectId != null -> combine(repo.project(projectId), repo.projectWrap(projectId)) { p, g -> p to g }
            .flatMapLatest { (p, groups) ->
                if (groups.isEmpty()) flowOf(WrapData.Groups(p, groups, emptyMap()))
                else combine(groups.map { g ->
                    val id = g.match.id
                    combine(repo.feedbackState(id), repo.matchView(id).catch { emit(null) }, mine(id)) { fb, v, my ->
                        Triple(id, fb, GroupExtra(v?.acceptedCard?.terms, v?.acceptedCard?.id, my))
                    }
                }) { arr ->
                    WrapData.Groups(p, groups, arr.associate { it.first to it.second }, arr.associate { it.first to it.third })
                }
            }
        else -> flowOf(WrapData.Single(null, emptyList(), FeedbackState(false, false, false, false)))
    }

    val state: StateFlow<Load<WrapData>> = retry.flatMapLatest {
        source()
            .map<WrapData, Load<WrapData>> { Load.Ready(it) }
            .onStart { emit(Load.Loading) }
            .catch { emit(Load.Failed(it.userMessage())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    /** Item ids with a request in flight. */
    val busy = MutableStateFlow<Set<String>>(emptySet())

    fun retry() { retry.value++ }

    fun mark(itemId: String, link: String?, note: String?, onDone: (Result<Unit>) -> Unit) = call(itemId, onDone) { repo.markWrapItem(itemId, link, note) }

    fun confirm(itemId: String, received: Boolean, onDone: (Result<Unit>) -> Unit) = call(itemId, onDone) { repo.confirmWrapItem(itemId, received) }

    /** A polite reminder in the chat about an overdue item (the screen enforces the 7-day cooldown). */
    fun remind(forMatch: String, itemId: String, body: String, onDone: (Result<Unit>) -> Unit) =
        call("remind_$itemId", onDone) { repo.sendMessage(forMatch, body) }

    fun submitFeedback(forMatch: String, answers: FeedbackAnswers, onDone: (Result<Unit>) -> Unit) =
        call("fb_$forMatch", onDone) { repo.submitFeedback(forMatch, answers) }

    private fun call(key: String, onDone: (Result<Unit>) -> Unit, block: suspend () -> Result<Unit>) {
        if (key in busy.value) return
        busy.value = busy.value + key
        viewModelScope.launch {
            val r = runCatching { block() }.getOrElse { Result.failure(it) }
            busy.value = busy.value - key
            onDone(r)
        }
    }
}
