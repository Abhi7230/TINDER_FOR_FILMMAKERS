package com.tff.app.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tff.app.R
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.di.rememberVm
import com.tff.app.model.RoleWithProject
import com.tff.app.nav.AppNavigator
import com.tff.app.nav.MainTab
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.theme.Ct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface SavedUi {
    data object Loading : SavedUi
    data object Guest : SavedUi
    data class Error(val message: String?) : SavedUi
    data class Ready(val items: List<RoleWithProject>) : SavedUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow<SavedUi>(SavedUi.Loading)
    val ui: StateFlow<SavedUi> = _ui
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _ui.value = SavedUi.Loading
        job = viewModelScope.launch {
            repo.session.map { it?.userId }.flatMapLatest { uid ->
                if (uid == null) flowOf<SavedUi>(SavedUi.Guest) else repo.saved().map<List<RoleWithProject>, SavedUi> { SavedUi.Ready(it) }
            }
                .catch { e -> _ui.value = SavedUi.Error((e as? RepoException)?.userMessage) }
                .collect { _ui.value = it }
        }
    }

    /** Un-save = undo the SAVE swipe (Repo.undoSwipe); the role can come back to the deck. */
    fun remove(roleId: String, done: String, fallback: String) {
        viewModelScope.launch {
            val r = runCatching { repo.undoSwipe(roleId) }.getOrElse { Result.failure(it) }
            _message.value = if (r.isSuccess) done else (r.exceptionOrNull() as? RepoException)?.userMessage ?: fallback
        }
    }

    fun messageShown() { _message.value = null }
}

/** Saved roles (S19 "Saved", route `saved`): roles saved from the deck, newest first. Tap opens Role detail. */
@Composable
fun SavedScreen(nav: AppNavigator) {
    val vm = rememberVm { SavedViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(msg) { msg?.let { nav.toast(it); vm.messageShown() } }
    val c = Ct.colors
    val removed = stringResource(R.string.ws7_saved_removed)
    val fallback = stringResource(R.string.ws7_generic_error)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        CtTopBar(title = stringResource(R.string.ws7_saved_title), onBack = nav::back)
        when (val s = ui) {
            SavedUi.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp), rows = 4)
            is SavedUi.Error -> ErrorState(onRetry = vm::load, message = s.message, modifier = Modifier.fillMaxSize())
            SavedUi.Guest -> EmptyState(stringResource(R.string.ws7_saved_guest_title), stringResource(R.string.ws7_me_guest_body), Modifier.fillMaxSize(), icon = Icons.Rounded.BookmarkBorder) {
                PrimaryButton(stringResource(R.string.ws7_me_guest_cta), nav::toWelcome)
            }
            is SavedUi.Ready -> if (s.items.isEmpty()) {
                EmptyState(stringResource(R.string.ws7_saved_empty_title), stringResource(R.string.ws7_saved_empty_body), Modifier.fillMaxSize(), icon = Icons.Rounded.BookmarkBorder) {
                    PrimaryButton(stringResource(R.string.ws7_saved_find), { nav.toMain(MainTab.DISCOVER) })
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.items, key = { it.role.id }) { item ->
                        SavedRoleRow(item, onClick = { nav.toRoleDetail(item.role.id) }, trailing = {
                            IconButton(onClick = { vm.remove(item.role.id, removed, fallback) }) {
                                Icon(Icons.Rounded.BookmarkRemove, contentDescription = stringResource(R.string.ws7_saved_remove_cd, item.project.title), tint = c.textSecondary)
                            }
                        })
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}
