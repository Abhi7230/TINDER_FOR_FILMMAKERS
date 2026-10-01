package com.tff.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tff.app.R
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.di.rememberVm
import com.tff.app.model.AppMode
import com.tff.app.model.TrustPage
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUi(
    val email: String = "",
    val adult: Boolean = false,
    val codeStep: Boolean = false,
    val code: String = "",
    val sending: Boolean = false,
    val verifying: Boolean = false,
    val resendIn: Int = 0,
    val error: String? = null,
    val info: String? = null,
    val done: Boolean? = null,          // non-null when signed in: true = has a profile
)

class SignInViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow(SignInUi())
    val ui: StateFlow<SignInUi> = _ui
    private var timer: Job? = null
    val isDemo: Boolean get() = repo.mode == AppMode.DEMO

    fun setEmail(v: String) = _ui.update { it.copy(email = v.replace(" ", ""), error = null) }
    fun setAdult(v: Boolean) = _ui.update { it.copy(adult = v, error = null) }
    fun editEmail() { timer?.cancel(); _ui.update { it.copy(codeStep = false, code = "", error = null, info = null, resendIn = 0) } }

    fun sendCode(fallback: String, resent: String? = null) {
        val s = _ui.value
        if (s.sending || !EMAIL.matches(s.email.trim()) || !s.adult) return
        _ui.update { it.copy(sending = true, error = null, info = null) }
        viewModelScope.launch {
            val r = runCatching { repo.sendOtp(s.email.trim()) }.getOrElse { Result.failure(it) }
            r.onSuccess {
                _ui.update { it.copy(sending = false, codeStep = true, code = "", info = resent) }
                startTimer()
            }.onFailure { e -> _ui.update { it.copy(sending = false, error = (e as? RepoException)?.userMessage ?: fallback) } }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (left in RESEND_SECONDS downTo 0) {
                _ui.update { it.copy(resendIn = left) }
                if (left > 0) delay(1000)
            }
        }
    }

    fun setCode(v: String, fallback: String) {
        val digits = v.filter { it.isDigit() }.take(6)
        _ui.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) verify(fallback)
    }

    fun verify(fallback: String) {
        val s = _ui.value
        if (s.verifying || s.code.length != 6) return
        _ui.update { it.copy(verifying = true, error = null) }
        viewModelScope.launch {
            val r = runCatching { repo.verifyOtp(s.email.trim(), s.code) }.getOrElse { Result.failure(it) }
            r.onSuccess { sess ->
                runCatching { repo.log("signup_done", mapOf("has_profile" to sess.hasProfile.toString())) }
                _ui.update { it.copy(verifying = false, done = sess.hasProfile) }
            }.onFailure { e -> _ui.update { it.copy(verifying = false, code = "", error = (e as? RepoException)?.userMessage ?: fallback) } }
        }
    }

    companion object {
        const val RESEND_SECONDS = 30
        val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}

/**
 * S03 Sign in: email → [Send code] → 6-digit code (auto-advances and signs in on the 6th digit). The
 * "I'm 18 or older" box is required. Wrong and expired codes say how to fix it; resend unlocks after 30 s.
 * Signed in with a profile → Main, otherwise → Onboarding.
 */
@Composable
fun SignInScreen(nav: AppNavigator) {
    val vm = rememberVm { SignInViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Ct.colors
    val fallback = stringResource(R.string.ws7_generic_error)
    val resentMsg = stringResource(R.string.ws7_signin_resent)

    LaunchedEffect(ui.done) {
        when (ui.done) {
            true -> nav.toMain()
            false -> nav.toOnboarding()
            null -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        CtTopBar(
            title = stringResource(R.string.ws7_signin_title),
            onBack = { if (ui.codeStep) vm.editEmail() else nav.back() },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (!ui.codeStep) EmailStep(ui, vm, nav, fallback) else CodeStep(ui, vm, fallback, resentMsg)
            if (vm.isDemo) {
                Spacer(Modifier.height(24.dp))
                InfoTipCard(stringResource(R.string.ws7_signin_demo_tip))
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (!ui.codeStep) {
                PrimaryButton(
                    stringResource(R.string.ws7_signin_send), { vm.sendCode(fallback) }, Modifier.fillMaxWidth(),
                    enabled = SignInViewModel.EMAIL.matches(ui.email.trim()) && ui.adult, loading = ui.sending,
                )
            } else {
                PrimaryButton(
                    stringResource(R.string.ws7_signin_verify), { vm.verify(fallback) }, Modifier.fillMaxWidth(),
                    enabled = ui.code.length == 6, loading = ui.verifying,
                )
            }
        }
    }
}

@Composable
private fun EmailStep(ui: SignInUi, vm: SignInViewModel, nav: AppNavigator, fallback: String) {
    val c = Ct.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Text(stringResource(R.string.ws7_signin_email_heading), style = Ct.type.titleL, color = c.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.ws7_signin_email_body), style = Ct.type.bodyS, color = c.textSecondary)
    Spacer(Modifier.height(20.dp))
    val showInvalid = ui.email.length > 3 && ui.email.contains("@") && !SignInViewModel.EMAIL.matches(ui.email.trim())
    val (email, setEmail) = rememberBuffered(ui.email, vm::setEmail)
    CtTextField(
        value = email,
        onValueChange = setEmail,
        label = stringResource(R.string.ws7_signin_email_label),
        placeholder = stringResource(R.string.ws7_signin_email_hint),
        singleLine = true,
        leadingIcon = Icons.Rounded.Email,
        errorText = ui.error ?: if (showInvalid) stringResource(R.string.ws7_signin_email_invalid) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.sendCode(fallback) }),
        modifier = Modifier.focusRequester(focus),
    )
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CtShapes.field)
            .background(c.surface)
            .border(1.dp, if (ui.adult) c.accent.copy(alpha = 0.6f) else c.outline, CtShapes.field)
            .toggleable(value = ui.adult, role = Role.Checkbox, onValueChange = vm::setAdult)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = ui.adult, onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.textMuted),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ws7_signin_adult), style = Ct.type.bodyStrong, color = c.text)
            Text(stringResource(R.string.ws7_signin_adult_body), style = Ct.type.bodyS, color = c.textSecondary)
        }
    }
    if (!ui.adult && SignInViewModel.EMAIL.matches(ui.email.trim())) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ws7_signin_adult_needed), style = Ct.type.label, color = c.textMuted)
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.ws7_signin_agree), style = Ct.type.label, color = c.textMuted)
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinkText(stringResource(R.string.ws7_welcome_rules)) { nav.toTrustPage(TrustPage.COMMUNITY_RULES) }
        Text("·", style = Ct.type.label, color = c.textMuted)
        LinkText(stringResource(R.string.ws7_welcome_privacy)) { nav.toTrustPage(TrustPage.PRIVACY) }
    }
}

@Composable
private fun CodeStep(ui: SignInUi, vm: SignInViewModel, fallback: String, resentMsg: String) {
    val c = Ct.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Text(stringResource(R.string.ws7_signin_code_heading), style = Ct.type.titleL, color = c.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.ws7_signin_code_body, ui.email.trim()), style = Ct.type.bodyS, color = c.textSecondary)
    Spacer(Modifier.height(24.dp))
    val (code, setCode) = rememberBuffered(ui.code) { vm.setCode(it, fallback) }
    OtpField(code, setCode, Modifier.focusRequester(focus), error = ui.error != null, enabled = !ui.verifying)
    if (ui.error != null) {
        Spacer(Modifier.height(10.dp))
        Text(ui.error, style = Ct.type.bodyS, color = c.onDangerContainer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (ui.info != null) {
        Spacer(Modifier.height(10.dp))
        Text(ui.info, style = Ct.type.bodyS, color = c.go)
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (ui.resendIn > 0) {
            TimecodeText(
                stringResource(R.string.ws7_signin_resend_in, "0:%02d".format(ui.resendIn)),
                style = Ct.type.monoS, color = c.textMuted, modifier = Modifier.padding(start = 4.dp),
            )
        } else {
            GhostButton(stringResource(R.string.ws7_signin_resend), { vm.sendCode(fallback, resentMsg) })
        }
        Spacer(Modifier.weight(1f))
        GhostButton(stringResource(R.string.ws7_signin_change_email), vm::editEmail, color = c.textSecondary)
    }
}

/** Six boxes over one hidden text field: typing fills the next box (auto-advance), paste works too. */
@Composable
private fun OtpField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: Boolean, enabled: Boolean) {
    val c = Ct.colors
    val cd = stringResource(R.string.ws7_signin_code_cd, value.length)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
        cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = cd },
        decorationBox = { inner ->
            Box {
                Box(Modifier.size(1.dp)) { inner() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { i ->
                        val ch = value.getOrNull(i)?.toString() ?: ""
                        val active = i == value.length && enabled
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 60.dp)
                                .clip(CtShapes.field)
                                .background(c.surfaceRaised)
                                .border(
                                    if (active) 2.dp else 1.dp,
                                    when { error -> c.stop; active -> c.accent; ch.isNotEmpty() -> c.textSecondary; else -> c.outline },
                                    CtShapes.field,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch, style = Ct.type.monoL.copy(fontSize = 26.sp), color = c.text, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
    )
}
