package com.tff.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Money
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.model.CompMode
import com.tff.app.model.Level
import com.tff.app.model.Taxonomy
import com.tff.app.nav.AppNavigator
import com.tff.app.nav.MainTab
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.DemoTag
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SliderRow
import com.tff.app.ui.components.StepHeader
import com.tff.app.ui.components.ToggleRow
import com.tff.app.model.AppMode
import com.tff.app.ui.theme.Ct
import kotlinx.coroutines.launch

/**
 * S04 Onboarding, exactly 3 steps: (1) what you do — name, roles (tap = main / also, long-press = step
 * up to), level; (2) where — city (live cities first, early cities labelled, "My city isn't listed"),
 * area, radius with a live "≈ N people and M projects within X km", Remote OK, languages; (3) how you
 * work (skippable) — comp mode, "I'll do unpaid if…", availability, private min rate, one work link.
 * [Finish] → Discover. Built for a first swipe within 90 s of install.
 */
@Composable
fun OnboardingScreen(nav: AppNavigator) {
    val vm = rememberVm { OnboardingViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Ct.colors
    val scope = rememberCoroutineScope()
    var leaving by rememberSaveable { mutableStateOf(false) }
    val fallback = stringResource(R.string.ws7_generic_error)

    LaunchedEffect(ui.done) { if (ui.done) nav.toMain(MainTab.DISCOVER) }
    LaunchedEffect(ui.toast) { ui.toast?.let { nav.toast(it); vm.toastShown() } }
    BackHandler { if (!vm.back()) leaving = true }

    val titles = listOf(stringResource(R.string.ws7_onb_step1), stringResource(R.string.ws7_onb_step2), stringResource(R.string.ws7_onb_step3))
    val scroll = rememberScrollState()
    LaunchedEffect(ui.step) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!vm.back()) leaving = true }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ws7_back), tint = c.text)
                }
                Spacer(Modifier.weight(1f))
                if (AppContainer.repo.mode == AppMode.DEMO) DemoTag()
            }
            StepHeader(current = ui.step, total = 3, title = titles[ui.step - 1], modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            when (ui.step) {
                1 -> StepWhatYouDo(ui, vm)
                2 -> StepWhere(ui, vm)
                else -> StepHowYouWork(ui, vm)
            }
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (ui.error != null) {
                Text(ui.error!!, style = Ct.type.bodyS, color = c.onDangerContainer, modifier = Modifier.padding(bottom = 8.dp))
            }
            when (ui.step) {
                1 -> PrimaryButton(stringResource(R.string.ws7_next), vm::next, Modifier.fillMaxWidth(), enabled = ui.form.step1Ok)
                2 -> PrimaryButton(stringResource(R.string.ws7_next), vm::next, Modifier.fillMaxWidth(), enabled = ui.form.step2Ok)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(stringResource(R.string.ws7_onb_skip), { vm.finish(skip = true, fallback) }, Modifier.weight(1f), enabled = !ui.saving)
                    PrimaryButton(
                        if (ui.saving) stringResource(R.string.ws7_onb_finishing) else stringResource(R.string.ws7_onb_finish),
                        { vm.finish(skip = false, fallback) }, Modifier.weight(1f), enabled = ui.form.step3Ok, loading = ui.saving,
                    )
                }
            }
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.ws7_onb_leave_title), style = Ct.type.titleM, color = c.text) },
            text = { Text(stringResource(R.string.ws7_onb_leave_body), style = Ct.type.bodyS, color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    scope.launch { runCatching { AppContainer.repo.signOut() }; nav.toWelcome() }
                }) { Text(stringResource(R.string.ws7_onb_leave_yes), color = c.stop) }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.ws7_onb_leave_no), color = c.accent) } },
        )
    }
}

@Composable
private fun StepWhatYouDo(ui: OnboardingUi, vm: OnboardingViewModel) {
    val f = ui.form
    val short = ProfileLogic.shortName(f.name)
    val (name, setName) = rememberBuffered(f.name, vm::setName)
    CtTextField(
        value = name,
        onValueChange = setName,
        label = stringResource(R.string.ws7_onb_name_label),
        placeholder = stringResource(R.string.ws7_onb_name_hint),
        singleLine = true,
        maxLength = 40,
        showCounter = false,
        supportingText = if (short.isNotEmpty() && f.nameOk) stringResource(R.string.ws7_onb_name_support, short) else null,
        errorText = if (f.name.isNotEmpty() && !f.nameOk) stringResource(R.string.ws7_onb_name_short) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
    Column {
        FieldLabel(stringResource(R.string.ws7_onb_level_label))
        Spacer(Modifier.height(6.dp))
        LevelPicker(f.level, vm::setLevel)
    }
    Column {
        FieldLabel(stringResource(R.string.ws7_onb_roles_label))
        Spacer(Modifier.height(6.dp))
        RoleChooser(f.roles, vm::setRoles)
    }
}

@Composable
private fun StepWhere(ui: OnboardingUi, vm: OnboardingViewModel) {
    val c = Ct.colors
    val f = ui.form
    if (ui.cities.isEmpty()) {
        if (ui.citiesError) ErrorState(onRetry = vm::loadCities) else LoadingSkeleton(rows = 3)
        return
    }
    val thanksFmt = stringResource(R.string.ws7_onb_city_missing_thanks)
    CityField(ui.cities, f.cityId, onPick = vm::pickCity, onMissing = { name -> vm.missingCity(name, thanksFmt.format(name)) })
    val city = ui.city
    if (city?.isEarly == true) InfoTipCard(stringResource(R.string.ws7_onb_early_tip, city.name))
    AreaChips(city, f.areaId, vm::pickArea)
    if (city != null) {
        val n = ui.nearby
        val live = if (n?.people != null) {
            val people = if (n.people == 1) "1 person" else "${n.people} people"
            val projects = if (n.projects == 1) "1 project" else "${n.projects} projects"
            stringResource(R.string.ws7_onb_radius_live, people, projects, f.radiusKm)
        } else stringResource(R.string.ws7_onb_radius_counting)
        SliderRow(
            title = stringResource(R.string.ws7_onb_radius_label),
            value = f.radiusKm.toFloat(),
            onValueChange = { vm.setRadius(it.toInt()) },
            valueRange = 2f..50f,
            valueLabel = { "${it.toInt()} km" },
            subtitle = live,
        )
    }
    ToggleRow(
        title = stringResource(R.string.ws7_onb_remote_label),
        checked = f.remoteOk,
        onCheckedChange = vm::setRemote,
        subtitle = stringResource(R.string.ws7_onb_remote_body),
    )
    LanguagePicker(city?.country, f.languages, vm::toggleLanguage)
}

@Composable
private fun StepHowYouWork(ui: OnboardingUi, vm: OnboardingViewModel) {
    val c = Ct.colors
    val f = ui.form
    Text(stringResource(R.string.ws7_onb_skip_note), style = Ct.type.bodyS, color = c.textSecondary)
    Column {
        FieldLabel(stringResource(R.string.ws7_onb_comp_label), Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        CompModePicker(f.compMode, vm::setComp)
    }
    if (f.compMode != CompMode.PAID_ONLY) UnpaidPerksPicker(f.mustHaves, vm::togglePerk)
    AvailabilityPicker(f.availability, vm::setAvailability)
    val currency = ui.city?.currency ?: "INR"
    val (rate, setRate) = rememberBuffered(f.minRate, vm::setRate)
    val (link, setLink) = rememberBuffered(f.link, vm::setLink)
    CtTextField(
        value = rate,
        onValueChange = setRate,
        label = stringResource(R.string.ws7_onb_rate_label),
        placeholder = Money.symbol(currency) + if (currency == "INR") "2,000" else "300",
        supportingText = stringResource(R.string.ws7_onb_rate_support),
        errorText = if (!f.rateOk) stringResource(R.string.ws7_onb_rate_invalid) else null,
        singleLine = true,
        mono = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
    Column {
        CtTextField(
            value = link,
            onValueChange = setLink,
            label = stringResource(R.string.ws7_onb_link_label),
            placeholder = stringResource(R.string.ws7_onb_link_hint),
            supportingText = if (f.level == Level.FIRST_PROJECT) stringResource(R.string.ws7_onb_link_beginner) else stringResource(R.string.ws7_onb_link_support),
            errorText = if (!f.linkOk) stringResource(R.string.ws7_onb_link_invalid) else null,
            singleLine = true,
            leadingIcon = Icons.Rounded.Link,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        if (f.link.isNotBlank() && f.linkOk) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ws7_onb_link_kind, linkKindLabel(f.effectiveLinkKind)), style = Ct.type.label, color = c.textMuted)
            Spacer(Modifier.height(4.dp))
            LinkKindChips(f.effectiveLinkKind, vm::setLinkKind, allowHeadshot = f.roles.primary?.let { Taxonomy.isCast(it) } == true)
        }
    }
}
