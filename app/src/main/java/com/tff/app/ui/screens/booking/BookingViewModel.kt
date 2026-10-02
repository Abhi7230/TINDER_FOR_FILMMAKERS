package com.tff.app.ui.screens.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.domain.BookingTermsFactory
import com.tff.app.model.BookingCard
import com.tff.app.model.BookingTerms
import com.tff.app.model.Features
import com.tff.app.model.MatchView
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.userMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class BookingData(val view: MatchView?, val cards: List<BookingCard>, val myId: String?)

/** S16 booking card: every version (oldest first), the owner's working copy, and the collaborator's answer. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModel(private val repo: Repo, val matchId: String) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val features: Features = repo.features

    val state: StateFlow<Load<BookingData>> = retry.flatMapLatest {
        combine(repo.matchView(matchId), repo.bookingCards(matchId), repo.session) { v, cards, s -> BookingData(v, cards.sortedBy { it.version }, s?.userId) }
            .map<BookingData, Load<BookingData>> { Load.Ready(it) }
            .onStart { emit(Load.Loading) }
            .catch { emit(Load.Failed(it.userMessage())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    /** Owner's working copy while editing (null = not editing). Survives rotation. */
    val editing = MutableStateFlow<BookingTerms?>(null)
    /** The terms the working copy started from (the latest version), for the live diff. */
    var editBase: BookingTerms? = null
        private set
    val busy = MutableStateFlow(false)
    val loadingDraft = MutableStateFlow(false)

    fun retry() { retry.value++ }

    /** Start editing from the latest version, or the role's pre-filled terms when there's none. */
    fun startEditing(latest: BookingTerms?, onError: (String?) -> Unit) {
        if (latest != null) { editBase = latest; editing.value = latest; return }
        if (loadingDraft.value) return
        loadingDraft.value = true
        viewModelScope.launch {
            runCatching { withCallWindow(repo.draftBookingTerms(matchId)) }
                .onSuccess { editBase = null; editing.value = BookingTermsFactory.normalize(it) }
                .onFailure { onError(it.userMessage()) }
            loadingDraft.value = false
        }
    }

    /**
     * A fresh on-set draft with no call window gets one from the published day sheets, the night-shoot flag
     * or the project's hours cap (BookingCopy.suggestCallWindow), so nobody pledges notice without knowing their hours.
     */
    private suspend fun withCallWindow(t: BookingTerms): BookingTerms {
        if (t.isPostRole || !t.callWindow.isNullOrBlank()) return t
        val view = (state.value as? Load.Ready)?.data?.view ?: withTimeoutOrNull(1_500) { repo.matchView(matchId).first() }
        val project = view?.item?.project ?: return t
        val days = runCatching { withTimeoutOrNull(1_500) { repo.shootDays(project.id).first() } }.getOrNull().orEmpty().map { it.day }
        return BookingCopy.suggestCallWindow(project, days)?.let { t.copy(callWindow = it) } ?: t
    }

    fun update(f: (BookingTerms) -> BookingTerms) {
        val t = editing.value ?: return
        editing.value = runCatching { BookingTermsFactory.normalize(f(t)) }.getOrElse { f(t) }
    }

    fun stopEditing() { editing.value = null; editBase = null }

    fun send(onDone: (Result<Unit>) -> Unit) {
        val t = editing.value ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val r = runCatching { repo.sendBookingCard(matchId, BookingTermsFactory.normalize(t)) }.getOrElse { Result.failure(it) }
            busy.value = false
            if (r.isSuccess) stopEditing()
            onDone(r)
        }
    }

    fun respond(cardId: String, accept: Boolean, onDone: (Result<Unit>) -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val r = runCatching { repo.respondBookingCard(cardId, accept) }.getOrElse { Result.failure(it) }
            busy.value = false
            onDone(r)
        }
    }
}
