package com.tff.app.ui.screens.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.Profile
import com.tff.app.model.ProfileCard
import com.tff.app.model.ProjectStatus
import com.tff.app.model.ProjectSummary
import com.tff.app.model.Reputation
import com.tff.app.model.RoleWithProject
import com.tff.app.model.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed interface MeUi {
    data object Loading : MeUi
    data object Guest : MeUi
    data object NoProfile : MeUi
    data class Error(val message: String?) : MeUi
    data class Ready(
        val profile: Profile,
        val card: ProfileCard?,
        val minRate: Int?,
        val keep: List<ProfileCard>,
        val saved: List<RoleWithProject>,
        val myProjects: List<ProjectSummary>,
        val email: String?,
        val currency: String = "INR",
    ) : MeUi {
        val reputation: Reputation get() = card?.reputation ?: Reputation(profile.id)
        val ownsProjects: Boolean get() = myProjects.any { it.project.status != ProjectStatus.DRAFT }
    }
}

/** S19: my profile + track record, Keep list, saved roles. Re-reads the card whenever the profile changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow<MeUi>(MeUi.Loading)
    val ui: StateFlow<MeUi> = _ui
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _ui.value = MeUi.Loading
        job = viewModelScope.launch {
            repo.session.map { it?.userId to it }.distinctUntilChanged { a, b -> a.first == b.first && a.second?.hasProfile == b.second?.hasProfile }
                .flatMapLatest { (uid, session) ->
                    if (uid == null) flowOf<MeUi>(MeUi.Guest)
                    else combine(repo.me(), repo.keepList(), repo.saved(), repo.myProjects()) { me, keep, saved, projects ->
                        Parts(me, keep, saved, projects)
                    }.map { parts -> toUi(parts, session) }
                }
                .catch { e -> _ui.value = MeUi.Error((e as? RepoException)?.userMessage) }
                .collect { _ui.value = it }
        }
    }

    private data class Parts(val me: Profile?, val keep: List<ProfileCard>, val saved: List<RoleWithProject>, val projects: List<ProjectSummary>)

    /** The card (reputation, confirmed credits, area name) and private rate are re-read on every change: cheap, and never stale. */
    private suspend fun toUi(p: Parts, session: Session?): MeUi {
        val me = p.me ?: return if (session?.hasProfile == true) MeUi.Loading else MeUi.NoProfile
        val card = runCatching { repo.profile(me.id) }.getOrNull()
        val rate = runCatching { repo.myPrivateMinRate() }.getOrNull()
        val currency = runCatching { repo.cities().firstOrNull { it.id == me.cityId }?.currency }.getOrNull() ?: "INR"
        return MeUi.Ready(me, card, rate, p.keep, p.saved, p.projects, session?.email, currency)
    }
}
