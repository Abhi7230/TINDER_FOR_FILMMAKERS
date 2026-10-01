package com.tff.app.ui.screens.crewup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.data.RepoException
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.ShareText
import com.tff.app.model.ApplicantView
import com.tff.app.model.CompType
import com.tff.app.model.Decision
import com.tff.app.model.RoleWithProject
import com.tff.app.model.Taxonomy
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.DeckAction
import com.tff.app.ui.components.DeckActionBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PersonCard
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SegmentedControl
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.SwipeDeck
import com.tff.app.ui.components.SwipeDirection
import com.tff.app.ui.components.SwipeLabels
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.direction
import com.tff.app.ui.components.rememberSwipeDeckState
import com.tff.app.ui.theme.Ct
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** S09 Crew up as a full-screen destination (route crewup/{roleId}), e.g. from a dashboard or a notification. */
@Composable
fun CrewUpScreen(nav: AppNavigator, roleId: String?) {
    Column(Modifier.fillMaxSize().background(Ct.colors.bg)) {
        CtTopBar(title = stringResource(R.string.ws4_crewup_title), onBack = { nav.back() })
        Box(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
            CrewUpPane(nav, initialRoleId = roleId, inline = false)
        }
    }
}

/** A deck decision that can still be shown as an IN TALKS overlay. */
private data class TalkMoment(val matchId: String, val subtitle: String)

/**
 * The Crew up deck: role selector chips with counts, Applicants | Scout (no Scout for cast roles),
 * Person Cards with Not this time · Backup · Let's talk (Scout: Skip · Send ask), the Ask composer,
 * the IN TALKS overlay, and the empty states. Used inline in Discover and by [CrewUpScreen].
 */
@Composable
fun CrewUpPane(nav: AppNavigator, initialRoleId: String?, inline: Boolean) {
    val vm = rememberVm(key = "crewup_" + (initialRoleId ?: if (inline) "inline" else "all")) { CrewUpViewModel(it, initialRoleId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshScout() }

    when {
        ui.loading -> Column(Modifier.fillMaxSize()) {
            LoadingSkeleton(Modifier.fillMaxWidth().weight(1f).padding(16.dp), kind = SkeletonKind.CARD)
        }
        ui.error != null -> ErrorState(onRetry = vm::retry, message = ui.error!!.ifBlank { null }, modifier = Modifier.fillMaxSize())
        ui.isGuest -> CenteredScroll {
            EmptyState(stringResource(R.string.ws4_crewup_guest_title), stringResource(R.string.ws4_crewup_guest_body), icon = Icons.Rounded.Groups) {
                PrimaryButton(stringResource(R.string.ws1_signin_cta), { nav.toWelcome() })
            }
        }
        ui.roles.isEmpty() -> CenteredScroll {
            EmptyState(stringResource(R.string.ws4_crewup_none_title), stringResource(R.string.ws4_crewup_none_body), icon = Icons.Rounded.Groups) {
                PrimaryButton(stringResource(R.string.ws4_post_project), { nav.toPostProject() })
            }
        }
        else -> CrewUpBody(nav, vm, ui)
    }
}

@Composable
private fun CenteredScroll(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 480.dp)) { content() }
    }
}

@Composable
private fun CrewUpBody(nav: AppNavigator, vm: CrewUpViewModel, ui: CrewUpUi) {
    val c = Ct.colors
    val sel = ui.selected ?: return
    val item = sel.item
    val role = item.role
    val scope = rememberCoroutineScope()
    val scoutAllowed = CrewUpLogic.hasScout(role)
    val tab = if (scoutAllowed) ui.tab else CrewTab.APPLICANTS
    val deck = rememberSwipeDeckState<ApplicantView>()
    var talk by remember { mutableStateOf<TalkMoment?>(null) }
    var askFor by remember { mutableStateOf<ApplicantView?>(null) }
    var undoSkip by remember { mutableStateOf<ApplicantView?>(null) }
    val genericError = stringResource(R.string.ws4_generic_error)
    val declinedMsg = stringResource(R.string.ws4_declined_toast)
    val backupMsg = stringResource(R.string.ws4_backup_toast)
    val askSentMsg = stringResource(R.string.ws4_ask_sent)
    val shareTitle = stringResource(R.string.ws4_share_title)

    // A new role or sub-deck starts with a clean stack.
    LaunchedEffect(sel.item.role.id, tab) { deck.reset(); undoSkip = null }
    LaunchedEffect(undoSkip) { if (undoSkip != null) { delay(5_000); undoSkip = null } }

    fun share() = nav.share(ShareText.roleCard(item, AppContainer.shareBaseUrl), shareTitle)
    fun fail(e: Throwable) { nav.toast((e as? RepoException)?.userMessage ?: genericError) }

    Column(Modifier.fillMaxSize()) {
        // Role selector chips with counts ("Sound Mixer · 3 new"); the selected one is always in view.
        val chipState = rememberLazyListState()
        val selIndex = ui.roles.indexOfFirst { it.item.role.id == ui.selectedId }
        LaunchedEffect(selIndex) { if (selIndex >= 0) chipState.animateScrollToItem(selIndex) }
        LazyRow(
            state = chipState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(ui.roles, key = { it.item.role.id }) { t ->
                val label = CardText.roleName(t.item.role) + if (t.pending > 0) " · " + stringResource(R.string.ws4_chip_new, t.pending) else ""
                CtFilterChip(label, selected = t.item.role.id == ui.selectedId, onClick = { vm.selectRole(t.item.role.id) })
            }
        }
        // Which project and on what terms, so the owner never has to guess.
        Text(
            item.project.title + " · " + CardText.payLine(role, item.project),
            style = Ct.type.bodyS, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
        )
        if (scoutAllowed) {
            SegmentedControl(
                listOf(stringResource(R.string.ws4_tab_applicants, sel.pending), stringResource(R.string.ws4_tab_scout)),
                if (tab == CrewTab.SCOUT) 1 else 0,
                { vm.selectTab(if (it == 1) CrewTab.SCOUT else CrewTab.APPLICANTS) },
                Modifier.padding(horizontal = 16.dp),
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = c.info, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.ws4_cast_no_scout), style = Ct.type.bodyS, color = c.onInfoContainer)
            }
        }
        Spacer(Modifier.height(8.dp))

        val people = if (tab == CrewTab.SCOUT) ui.scout else ui.applicants
        val loadingScout = tab == CrewTab.SCOUT && ui.scoutLoading
        if (loadingScout) {
            LoadingSkeleton(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).padding(bottom = 28.dp), kind = SkeletonKind.CARD)
        } else if (tab == CrewTab.SCOUT && ui.scoutError) {
            Box(Modifier.fillMaxWidth().weight(1f)) { ErrorState(onRetry = vm::refreshScout, modifier = Modifier.align(Alignment.Center)) }
        } else {
            SwipeDeck(
                items = people,
                key = CrewUpLogic::key,
                state = deck,
                labels = if (tab == CrewTab.SCOUT) SwipeLabels.scout() else SwipeLabels.applicants(),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                onTap = { v -> nav.toPerson(v.card.profile.id, role.id, v.application?.id) },
                onSwiped = { v, dir ->
                    if (tab == CrewTab.SCOUT) {
                        when (dir) {
                            SwipeDirection.RIGHT -> askFor = v
                            else -> { vm.skip(v); undoSkip = v }
                        }
                    } else {
                        val d = when (dir) { SwipeDirection.RIGHT -> Decision.TALK; SwipeDirection.LEFT -> Decision.DECLINE; SwipeDirection.UP -> Decision.BACKUP }
                        scope.launch {
                            vm.decide(v, d)
                                .onSuccess { m ->
                                    when (d) {
                                        Decision.TALK -> m?.let { talk = TalkMoment(it.id, inTalksLine(v, item)) }
                                        Decision.DECLINE -> nav.toast(declinedMsg.format(firstName(v)))
                                        Decision.BACKUP -> nav.toast(backupMsg.format(firstName(v)))
                                    }
                                }
                                .onFailure { e -> fail(e); deck.undo() }
                        }
                    }
                },
                empty = {
                    CenteredScroll {
                        if (tab == CrewTab.SCOUT) ScoutEmpty(nav, item, ::share)
                        else ApplicantsEmpty(ui.answered > 0, scoutAllowed, ::share) { vm.selectTab(CrewTab.SCOUT) }
                    }
                },
            ) { v ->
                PersonCard(v, role.roleCode, Modifier.fillMaxSize(), currency = role.currency, unpaidRole = role.compType == CompType.UNPAID)
            }
        }

        // Counters and the Skip undo.
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tab == CrewTab.SCOUT) {
                TimecodeText(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.ws4_asks_left, ui.asksLeft, ui.asksLeft),
                    style = Ct.type.monoS, color = if (ui.asksLeft <= 2) c.accent else c.textSecondary,
                )
            } else {
                Text(stringResource(R.string.ws4_every_applicant), style = Ct.type.label, color = c.textMuted, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            undoSkip?.let { v ->
                val label = stringResource(R.string.ws4_undo_skip)
                CtChip(label, icon = Icons.AutoMirrored.Rounded.Undo, contentDescription = label, onClick = {
                    undoSkip = null
                    scope.launch { deck.undo(); vm.unskip(v) }
                })
            }
        }
        if (people.isNotEmpty() && !loadingScout) {
            DeckActionBar(
                if (tab == CrewTab.SCOUT) listOf(DeckAction.SKIP, DeckAction.SEND_ASK) else listOf(DeckAction.NOT_THIS_TIME, DeckAction.BACKUP, DeckAction.LETS_TALK),
                onAction = { a -> deck.swipeAsync(a.direction) },
                state = deck,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else Spacer(Modifier.height(8.dp))
    }

    askFor?.let { v ->
        AskComposerSheet(
            view = v, item = item, asksLeft = ui.asksLeft,
            check = { vm.askCheck(v) },
            send = { note -> vm.sendAsk(v, note) },
            onCancel = { askFor = null; scope.launch { deck.undo() } },
            onSent = { askFor = null; deck.clearUndo(); nav.toast(askSentMsg.format(firstName(v))) },
            onSkip = { askFor = null; vm.skip(v); undoSkip = v },
        )
    }
    talk?.let { t ->
        InTalksOverlay(
            subtitle = t.subtitle,
            onOpenChat = { talk = null; nav.toChat(t.matchId) },
            onKeepSwiping = { talk = null },
        )
    }
}

@Composable
private fun ApplicantsEmpty(answeredSome: Boolean, scoutAllowed: Boolean, share: () -> Unit, openScout: () -> Unit) {
    EmptyState(
        title = stringResource(if (answeredSome) R.string.ws4_applicants_done_title else R.string.ws4_applicants_empty_title),
        body = stringResource(if (answeredSome) R.string.ws4_applicants_done_body else R.string.ws4_applicants_empty_body),
        icon = Icons.Rounded.Inbox,
    ) {
        PrimaryButton(stringResource(R.string.ws4_share_role), share, icon = Icons.Rounded.Share)
        if (scoutAllowed) SecondaryButton(stringResource(R.string.ws4_open_scout), openScout, icon = Icons.Rounded.PersonSearch)
    }
}

@Composable
private fun ScoutEmpty(nav: AppNavigator, item: RoleWithProject, share: () -> Unit) {
    val role = item.role
    val canGoRemote = !role.remoteOk && (Taxonomy.info(role.roleCode).remoteDefault || Taxonomy.dept(role.roleCode) in Taxonomy.POST_DEPTS)
    EmptyState(
        title = stringResource(R.string.ws4_scout_empty_title),
        body = stringResource(R.string.ws4_scout_empty_body),
        icon = Icons.Rounded.PersonSearch,
    ) {
        PrimaryButton(stringResource(R.string.ws4_share_whatsapp), share, icon = Icons.Rounded.Share)
        if (canGoRemote) SecondaryButton(stringResource(R.string.ws4_make_remote), { nav.toPostProject(item.project.id) })
        // TODO(WS4): [Widen radius] for Scout needs a search radius on Repo.scout(); the frozen contract has none.
    }
}
