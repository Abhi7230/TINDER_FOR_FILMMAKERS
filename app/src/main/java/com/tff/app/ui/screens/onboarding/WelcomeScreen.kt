package com.tff.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tff.app.R
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.di.rememberVm
import com.tff.app.model.AppMode
import com.tff.app.model.DemoPersona
import com.tff.app.model.TrustPage
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.DemoTag
import com.tff.app.ui.components.GeneratedPosterArt
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.posterTexture
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the Welcome screen is doing: idle, starting a persona (or "build my own"), or showing an error. */
data class WelcomeUi(val starting: DemoPersona? = null, val startingOwn: Boolean = false, val error: String? = null) {
    val busy: Boolean get() = starting != null || startingOwn
}

sealed interface WelcomeEvent {
    data object ToMain : WelcomeEvent
    data object ToSignIn : WelcomeEvent
}

class WelcomeViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow(WelcomeUi())
    val ui: StateFlow<WelcomeUi> = _ui
    private val _event = MutableStateFlow<WelcomeEvent?>(null)
    val event: StateFlow<WelcomeEvent?> = _event

    val isDemo: Boolean get() = repo.mode == AppMode.DEMO

    fun startPersona(p: DemoPersona, fallbackError: String) {
        if (_ui.value.busy) return
        _ui.value = WelcomeUi(starting = p)
        viewModelScope.launch {
            runCatching { repo.startDemo(p) }
                .onSuccess { _ui.value = WelcomeUi(); _event.value = WelcomeEvent.ToMain }
                .onFailure { e -> _ui.value = WelcomeUi(error = (e as? RepoException)?.userMessage ?: fallbackError) }
        }
    }

    fun consumed() { _event.value = null }
}

private data class PersonaTileSpec(val persona: DemoPersona, val name: String, val initials: String, val role: Int, val place: Int, val hint: Int)

private val personaTiles = listOf(
    PersonaTileSpec(DemoPersona.MEERA, "Meera", "MK", R.string.ws7_persona_meera_role, R.string.ws7_persona_meera_place, R.string.ws7_persona_meera_hint),
    PersonaTileSpec(DemoPersona.ROHAN, "Rohan", "RK", R.string.ws7_persona_rohan_role, R.string.ws7_persona_rohan_place, R.string.ws7_persona_rohan_hint),
    PersonaTileSpec(DemoPersona.KABIR, "Kabir", "KS", R.string.ws7_persona_kabir_role, R.string.ws7_persona_kabir_place, R.string.ws7_persona_kabir_hint),
    PersonaTileSpec(DemoPersona.DANA, "Dana", "DR", R.string.ws7_persona_dana_role, R.string.ws7_persona_dana_place, R.string.ws7_persona_dana_hint),
)

/**
 * S02 Welcome: value and trust in five seconds. A cinematic hero (generated poster, light leak, grain)
 * with the tagline, three value rows, then either the demo persona tiles + "Build my own profile"
 * (DEMO) or [Get started] (LIVE), [Look around first] for guests, and links to the trust pages.
 */
@Composable
fun WelcomeScreen(nav: AppNavigator) {
    val vm = rememberVm { WelcomeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(event) {
        when (event) {
            WelcomeEvent.ToMain -> { vm.consumed(); nav.toMain() }
            WelcomeEvent.ToSignIn -> { vm.consumed(); nav.toSignIn() }
            null -> Unit
        }
    }
    val c = Ct.colors
    val startError = stringResource(R.string.ws7_welcome_start_error)

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()),
    ) {
        WelcomeHero(demo = vm.isDemo)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            ValueRow(Icons.Rounded.Payments, stringResource(R.string.ws7_welcome_value_pay), stringResource(R.string.ws7_welcome_value_pay_body))
            ValueRow(Icons.Rounded.EventAvailable, stringResource(R.string.ws7_welcome_value_booked), stringResource(R.string.ws7_welcome_value_booked_body))
            ValueRow(Icons.Rounded.MoneyOff, stringResource(R.string.ws7_welcome_value_free), stringResource(R.string.ws7_welcome_value_free_body))
            Spacer(Modifier.height(20.dp))

            if (vm.isDemo) {
                Text(
                    stringResource(R.string.ws7_welcome_try_demo), style = Ct.type.slate, color = c.accent,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.ws7_welcome_demo_note), style = Ct.type.label, color = c.textMuted)
                Spacer(Modifier.height(12.dp))
                personaTiles.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { spec ->
                            PersonaTile(
                                spec = spec,
                                loading = ui.starting == spec.persona,
                                enabled = !ui.busy,
                                onClick = { vm.startPersona(spec.persona, startError) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                SecondaryButton(
                    stringResource(R.string.ws7_welcome_build_own), { nav.toSignIn() }, Modifier.fillMaxWidth(),
                    enabled = !ui.busy, icon = Icons.Rounded.Add,
                )
            } else {
                PrimaryButton(stringResource(R.string.ws7_welcome_get_started), { nav.toSignIn() }, Modifier.fillMaxWidth())
            }
            if (ui.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(ui.error!!, style = Ct.type.bodyS, color = c.onDangerContainer)
            }
            Spacer(Modifier.height(4.dp))
            GhostButton(
                stringResource(R.string.ws7_welcome_look_around), { nav.toMain() }, Modifier.fillMaxWidth(),
                enabled = !ui.busy, color = c.text,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                LinkText(stringResource(R.string.ws7_welcome_how_money)) { nav.toTrustPage(TrustPage.HOW_WE_MAKE_MONEY) }
                Dot()
                LinkText(stringResource(R.string.ws7_welcome_rules)) { nav.toTrustPage(TrustPage.COMMUNITY_RULES) }
                Dot()
                LinkText(stringResource(R.string.ws7_welcome_privacy)) { nav.toTrustPage(TrustPage.PRIVACY) }
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun WelcomeHero(demo: Boolean) {
    val c = Ct.colors
    Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).aspectRatio(1.8f)) {
        GeneratedPosterArt(seed = "Calltime · Last Local", Modifier.fillMaxSize().posterTexture())
        // fade the picture into the darkroom so the copy below reads as one piece
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to c.bg.copy(alpha = 0.55f), 0.35f to Color.Transparent, 0.62f to c.bg.copy(alpha = 0.55f), 1f to c.bg),
            ),
        )
        if (demo) DemoTag(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 16.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            SlateMark(size = 52.dp)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.ws7_welcome_kicker), style = Ct.type.displayL, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.tagline), style = Ct.type.titleL, color = c.accent)
        }
    }
}

@Composable
private fun ValueRow(icon: ImageVector, title: String, body: String) {
    val c = Ct.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.bodyStrong, color = c.text)
            Text(body, style = Ct.type.bodyS, color = c.textSecondary)
        }
    }
}

@Composable
private fun PersonaTile(spec: PersonaTileSpec, loading: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val role = stringResource(spec.role)
    val cd = stringResource(R.string.ws7_persona_cd, spec.name, role)
    Column(
        modifier
            .clip(CtShapes.card)
            .background(c.surface)
            .border(1.dp, if (loading) c.accent else c.outline, CtShapes.card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cd }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(spec.initials, size = 38.dp, highlight = loading)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.name, style = Ct.type.titleM, color = c.text, maxLines = 1)
                Text(role.uppercase(), style = Ct.type.slate.copy(fontSize = Ct.type.label.fontSize, lineHeight = Ct.type.label.lineHeight), color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (loading) stringResource(R.string.ws7_welcome_starting) else stringResource(spec.hint),
            style = Ct.type.bodyS, color = c.textSecondary, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(spec.place).uppercase(), style = Ct.type.monoS, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LinkText(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(CtShapes.chip).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ct.type.label, color = Ct.colors.info)
    }
}

@Composable
private fun Dot() {
    Text("·", style = Ct.type.label, color = Ct.colors.textMuted)
}
