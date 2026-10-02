package com.tff.app.ui.screens.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.ApplicantView
import com.tff.app.model.ApplicationStatus
import com.tff.app.model.Decision
import com.tff.app.model.Match
import com.tff.app.model.Profile
import com.tff.app.model.ProfileCard
import com.tff.app.model.RoleWithProject
import com.tff.app.ui.screens.discover.Ws4Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the viewer is looking at this person. */
enum class PersonContext { NONE, APPLICANT, SCOUT }

data class PersonUi(
    val loading: Boolean = true,
    val error: String? = null,
    /** repo.profile() returned null: no relationship, blocked, or deleted. */
    val unavailable: Boolean = false,
    val card: ProfileCard? = null,
    val me: Profile? = null,
    val item: RoleWithProject? = null,
    val view: ApplicantView? = null,
    val context: PersonContext = PersonContext.NONE,
    val asksLeft: Int = 10,
    val busy: Boolean = false,
    /** The person's home currency, for their kit fee. */
    val currency: String = "INR",
) {
    val isSelf: Boolean get() = me != null && card?.profile?.id == me.id
    /** The owner is deciding on this person for [item]: show the S09 actions. */
    val canDecide: Boolean get() = context == PersonContext.APPLICANT && view?.application?.status == ApplicationStatus.PENDING
    val canAsk: Boolean get() = context == PersonContext.SCOUT
}

/** S10 Person detail. With a role id the owner sees the application (or Scout fit) and the same actions as S09. */
class PersonDetailViewModel(
    private val repo: Repo,
    private val profileId: String,
    private val roleId: String?,
    private val applicationId: String?,
) : ViewModel() {
    private val _ui = MutableStateFlow(PersonUi())
    val ui: StateFlow<PersonUi> = _ui.asStateFlow()
    private var contextJob: Job? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val me = if (repo.session.value != null) repo.me().first() else null
                val card = repo.profile(profileId)
                if (card == null) {
                    _ui.update { it.copy(loading = false, unavailable = true, me = me) }
                    return@launch
                }
                val item = roleId?.let { repo.roleWithProject(it) }
                val owner = item != null && me != null && item.project.ownerId == me.id
                val currency = repo.cities().firstOrNull { it.id == card.profile.cityId }?.currency ?: "INR"
                _ui.update { it.copy(loading = false, unavailable = false, card = card, me = me, item = item.takeIf { owner }, currency = currency) }
                if (owner) watchContext(item!!)
            } catch (e: RepoException) {
                _ui.update { it.copy(loading = false, error = e.userMessage) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = "") }
            }
        }
    }

    /** Live applicant view (so a decision made elsewhere shows), else a Scout match for crew roles. */
    private fun watchContext(item: RoleWithProject) {
        contextJob?.cancel()
        contextJob = viewModelScope.launch {
            launch { repo.asksLeftToday(item.project.id).collect { n -> _ui.update { it.copy(asksLeft = n) } } }
            var scoutTried = false
            repo.applicants(item.role.id).collect { list ->
                val v = list.firstOrNull { (applicationId != null && it.application?.id == applicationId) || it.card.profile.id == profileId }
                when {
                    v != null -> _ui.update { it.copy(view = v, context = PersonContext.APPLICANT) }
                    _ui.value.context == PersonContext.APPLICANT -> {
                        // No longer waiting (answered here or elsewhere, or withdrawn): keep the last view, drop the actions.
                        _ui.update { s ->
                            val a = s.view?.application
                            if (a != null && a.status == ApplicationStatus.PENDING) s.copy(view = s.view.copy(application = a.copy(status = ApplicationStatus.CLOSED))) else s
                        }
                    }
                    !scoutTried && !item.role.isCast -> {
                        scoutTried = true
                        val sv = runCatching { repo.scout(item.role.id) }.getOrDefault(emptyList())
                            .firstOrNull { it.card.profile.id == profileId && it.card.profile.id !in Ws4Prefs.scoutSkips(item.role.id) }
                        if (sv != null) _ui.update { it.copy(view = sv, context = PersonContext.SCOUT) }
                    }
                }
            }
        }
    }

    suspend fun decide(d: Decision): Result<Match?> {
        val app = _ui.value.view?.application ?: return Result.failure(RepoException("This person hasn't applied."))
        _ui.update { it.copy(busy = true) }
        val r = viewModelScope.async { runCatching { repo.decide(app.id, d, null) }.getOrElse { Result.failure(it) } }.await()
        _ui.update { s ->
            val status = when (d) { Decision.TALK -> ApplicationStatus.MATCHED; Decision.BACKUP -> ApplicationStatus.BACKUP; Decision.DECLINE -> ApplicationStatus.DECLINED }
            s.copy(busy = false, view = if (r.isSuccess) s.view?.copy(application = app.copy(status = status)) else s.view)
        }
        return r
    }

    suspend fun askCheck(): String? {
        val rid = roleId ?: return null
        return viewModelScope.async { runCatching { repo.askCheck(rid, profileId) }.getOrNull() }.await()
    }

    suspend fun sendAsk(note: String?): Result<Unit> {
        val rid = roleId ?: return Result.failure(RepoException("Pick a role first."))
        val r = viewModelScope.async { runCatching { repo.sendAsk(rid, profileId, note?.trim()?.ifBlank { null }) }.getOrElse { Result.failure(it) } }.await()
        if (r.isSuccess) _ui.update { it.copy(context = PersonContext.NONE) }
        return r
    }

    fun skip() {
        val rid = roleId ?: return
        Ws4Prefs.addScoutSkip(rid, profileId)
        _ui.update { it.copy(context = PersonContext.NONE) }
    }

    suspend fun block(): Result<Unit> = viewModelScope.async { runCatching { repo.block(profileId) }.getOrElse { Result.failure(it) } }.await()
}
