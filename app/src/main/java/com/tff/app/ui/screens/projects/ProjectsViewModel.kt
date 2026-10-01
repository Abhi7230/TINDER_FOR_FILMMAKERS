package com.tff.app.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.AppClock
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.domain.WrapItemsFactory
import com.tff.app.model.City
import com.tff.app.model.ProjectStatus
import com.tff.app.model.ProjectSummary
import com.tff.app.model.WrapItem
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

/** One row on the Joined tab: the summary plus my own wrap items (so "Footage overdue" is about me, not the whole project). */
data class JoinedRow(val summary: ProjectSummary, val myItems: List<WrapItem>) {
    val myOverdue: Int get() = myItems.count { WrapItemsFactory.isOverdue(it, AppClock.today()) }
}

sealed interface ProjectsUi {
    data object Loading : ProjectsUi
    data object Guest : ProjectsUi
    data class Error(val message: String?) : ProjectsUi
    data class Ready(val mine: List<ProjectSummary>, val joined: List<JoinedRow>, val places: Map<String, String>) : ProjectsUi
}

/** S12 Projects tab: Mine (owner pipeline) and Joined (where I'm booked). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModel(private val repo: Repo) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val places = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        viewModelScope.launch { runCatching { repo.cities() }.onSuccess { places.value = placeNames(it) } }
    }

    val ui: StateFlow<ProjectsUi> = retry.flatMapLatest {
        repo.session.flatMapLatest { session ->
            if (session == null) flowOf<ProjectsUi>(ProjectsUi.Guest)
            else combine(repo.myProjects(), joinedRows(), places) { mine, joined, pl ->
                // A deleted draft is a cancelled project that was never published: it leaves the list.
                val visible = mine.filterNot { it.project.status == ProjectStatus.CANCELLED && it.project.publishedAt == null }
                ProjectsUi.Ready(visible, joined, pl) as ProjectsUi
            }
        }.onStart { emit(ProjectsUi.Loading) }
            .catch { e -> emit(ProjectsUi.Error((e as? RepoException)?.userMessage)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUi.Loading)

    private fun joinedRows(): Flow<List<JoinedRow>> = repo.joinedProjects().flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { s ->
            val mid = s.myMatchId
            if (mid == null || !repo.features.wrap) flowOf(JoinedRow(s, emptyList()))
            else repo.wrapItems(mid).map { JoinedRow(s, it) }
        }) { it.toList() }
    }

    fun retry() { retry.value++ }

    companion object {
        /** areaId → "Jayanagar, Bengaluru". */
        fun placeNames(cities: List<City>): Map<String, String> = cities.flatMap { c ->
            c.areas.map { a -> a.id to (if (a.name.equals(c.name, true) || a.name == "City centre") c.name else "${a.name}, ${c.name}") }
        }.toMap()
    }
}
