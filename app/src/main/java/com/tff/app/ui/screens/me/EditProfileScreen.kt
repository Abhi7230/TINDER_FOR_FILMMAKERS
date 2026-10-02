package com.tff.app.ui.screens.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.di.rememberVm
import com.tff.app.model.CompMode
import com.tff.app.model.ContentFlag
import com.tff.app.model.Format
import com.tff.app.model.Goal
import com.tff.app.model.Taxonomy
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.LoadingSkeleton
import com.tff.app.ui.components.MultiChipPicker
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SkeletonKind
import com.tff.app.ui.components.SliderRow
import com.tff.app.ui.components.ToggleRow
import com.tff.app.ui.components.shortRoleLabel
import com.tff.app.ui.screens.onboarding.AreaChips
import com.tff.app.ui.screens.onboarding.AvailabilityPicker
import com.tff.app.ui.screens.onboarding.CityField
import com.tff.app.ui.screens.onboarding.CompModePicker
import com.tff.app.ui.screens.onboarding.LanguagePicker
import com.tff.app.ui.screens.onboarding.LevelPicker
import com.tff.app.ui.screens.onboarding.LinkKindChips
import com.tff.app.ui.screens.onboarding.ProfileLogic
import com.tff.app.ui.screens.onboarding.rememberBuffered
import com.tff.app.ui.screens.onboarding.RoleChooser
import com.tff.app.ui.screens.onboarding.UnpaidPerksPicker
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

private val GENRES = listOf("Drama", "Comedy", "Thriller", "Horror", "Romance", "Documentary", "Sci-fi", "Fantasy", "Action", "Musical", "Experimental", "Animation")

/**
 * S20 Edit profile: sectioned forms mirroring onboarding, plus busy-dates calendar, gear, tools, taste
 * films, bio, affiliations, a work-links manager (reorder, role tag, kind) and the private min rate.
 * No photo upload in the MVP. Unsaved changes ask before leaving.
 */
@Composable
fun EditProfileScreen(nav: AppNavigator) {
    val vm = rememberVm { EditProfileViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Ct.colors
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.ws7_edit_saved)
    val fallback = stringResource(R.string.ws7_generic_error)
    val ready = ui as? EditUi.Ready

    LaunchedEffect(ready?.saved) { if (ready?.saved == true) { nav.toast(savedMsg); nav.back() } }
    BackHandler(enabled = ready?.dirty == true && !ready.saved) { confirmLeave = true }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        CtTopBar(
            title = stringResource(R.string.ws7_edit_title),
            onBack = { if (ready?.dirty == true) confirmLeave = true else nav.back() },
            divider = true,
        )
        when (val s = ui) {
            EditUi.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp), kind = SkeletonKind.DETAIL)
            is EditUi.Error -> ErrorState(onRetry = vm::load, message = s.message ?: stringResource(R.string.ws7_load_error), modifier = Modifier.weight(1f))
            EditUi.NoProfile -> EmptyState(stringResource(R.string.ws7_me_noprofile_title), stringResource(R.string.ws7_me_noprofile_body), Modifier.weight(1f)) {
                PrimaryButton(stringResource(R.string.ws7_me_noprofile_cta), nav::toOnboarding)
            }
            is EditUi.Ready -> {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EditForms(s, vm)
                    Spacer(Modifier.height(16.dp))
                }
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val err = s.error ?: if (s.showErrors && !s.form.valid) stringResource(R.string.ws7_edit_fix) else null
                    if (err != null) Text(err, style = Ct.type.bodyS, color = c.onDangerContainer, modifier = Modifier.padding(bottom = 8.dp))
                    PrimaryButton(stringResource(R.string.ws7_save), { vm.save(fallback) }, Modifier.fillMaxWidth(), enabled = s.dirty, loading = s.saving)
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.ws7_edit_discard_title), style = Ct.type.titleM, color = c.text) },
            text = { Text(stringResource(R.string.ws7_edit_discard_body), style = Ct.type.bodyS, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; nav.back() }) { Text(stringResource(R.string.ws7_edit_discard_yes), color = c.stop) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.ws7_edit_discard_no), color = c.accent) } },
        )
    }
}

@Composable
private fun EditForms(s: EditUi.Ready, vm: EditProfileViewModel) {
    val c = Ct.colors
    val f = s.form
    val showErr = s.showErrors
    val currency = s.city?.currency ?: "INR"

    // ── About you ──
    SectionHeader(stringResource(R.string.ws7_edit_about), Modifier.padding(top = 12.dp))
    val (name, setName) = rememberBuffered(f.name) { v -> vm.edit { it.copy(name = v.take(40)) } }
    val (pronouns, setPronouns) = rememberBuffered(f.pronouns) { v -> vm.edit { it.copy(pronouns = v.take(30)) } }
    val (bio, setBio) = rememberBuffered(f.bio) { v -> vm.edit { it.copy(bio = v) } }
    CtTextField(
        name, setName, label = stringResource(R.string.ws7_onb_name_label), singleLine = true, maxLength = 40, showCounter = false,
        supportingText = if (f.nameOk) stringResource(R.string.ws7_onb_name_support, ProfileLogic.shortName(f.name)) else null,
        errorText = if (!f.nameOk) stringResource(R.string.ws7_onb_name_short) else null,
    )
    CtTextField(pronouns, setPronouns, label = stringResource(R.string.ws7_edit_pronouns), placeholder = stringResource(R.string.ws7_edit_pronouns_hint), singleLine = true)
    CtTextField(
        bio, setBio, label = stringResource(R.string.ws7_edit_bio), placeholder = stringResource(R.string.ws7_edit_bio_hint),
        maxLength = 280, minLines = 3,
    )
    ChipListInput(stringResource(R.string.ws7_edit_affiliations), f.affiliations, { l -> vm.edit { it.copy(affiliations = l) } }, stringResource(R.string.ws7_edit_affiliations_hint), max = 6)

    // ── Roles and level ──
    SectionHeader(stringResource(R.string.ws7_edit_roles), Modifier.padding(top = 12.dp))
    LevelPicker(f.level, { l -> vm.edit { it.copy(level = l) } })
    // the full 29-role picker is long: show the summary and open the picker on demand
    var editRoles by rememberSaveable { mutableStateOf(false) }
    RoleChooser(f.roles, { sel -> vm.edit { it.copy(roles = sel) } }, showPicker = editRoles)
    if (!editRoles) GhostButton(stringResource(R.string.ws7_edit_change_roles), { editRoles = true }, icon = Icons.Rounded.Edit)

    // ── Where ──
    SectionHeader(stringResource(R.string.ws7_edit_where), Modifier.padding(top = 12.dp))
    val thanks = stringResource(R.string.ws7_onb_city_missing_thanks)
    val nav = com.tff.app.nav.LocalNavigator.current
    CityField(s.cities, f.cityId, onPick = vm::pickCity, onMissing = { name -> vm.missingCity(name); nav.toast(thanks.format(name)) })
    AreaChips(s.city, f.areaId, { a -> vm.edit { it.copy(areaId = a) } })
    SliderRow(
        stringResource(R.string.ws7_onb_radius_label), f.radiusKm.toFloat(), { v -> vm.edit { it.copy(radiusKm = v.toInt()) } }, 2f..150f,
        valueLabel = { "${it.toInt()} km" },
    )
    ToggleRow(stringResource(R.string.ws7_edit_travel_covered), f.travelIfCovered, { v -> vm.edit { it.copy(travelIfCovered = v) } }, subtitle = stringResource(R.string.ws7_edit_travel_covered_body))
    ToggleRow(stringResource(R.string.ws7_onb_remote_label), f.remoteOk, { v -> vm.edit { it.copy(remoteOk = v) } }, subtitle = stringResource(R.string.ws7_onb_remote_body))
    LanguagePicker(s.city?.country, f.languages, { code -> vm.edit { it.copy(languages = if (code in it.languages) it.languages - code else it.languages + code) } })

    // ── When ──
    SectionHeader(stringResource(R.string.ws7_edit_when), Modifier.padding(top = 12.dp))
    AvailabilityPicker(f.availability, { a -> vm.edit { it.copy(availability = a) } })
    Column {
        SubLabel(stringResource(R.string.ws7_edit_busy))
        Text(stringResource(R.string.ws7_edit_busy_body), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(bottom = 8.dp))
        BusyCalendar(f.busy, vm.today, vm::toggleBusy)
        val upcoming = f.busy.count { !it.isBefore(vm.today) }
        if (upcoming > 0) Text(stringResource(R.string.ws7_edit_busy_count, upcoming), style = Ct.type.monoS, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }

    // ── How you work ──
    SectionHeader(stringResource(R.string.ws7_edit_how), Modifier.padding(top = 12.dp))
    CompModePicker(f.compMode, { m -> vm.edit { it.copy(compMode = m) } })
    if (f.compMode != CompMode.PAID_ONLY) UnpaidPerksPicker(f.mustHaves, { p -> vm.edit { it.copy(mustHaves = if (p in it.mustHaves) it.mustHaves - p else it.mustHaves + p) } })
    Column {
        SubLabel(stringResource(R.string.ws7_edit_goals))
        val goalLabels = Goal.entries.associateWith { goalLabel(it) }
        MultiChipPicker(Goal.entries, f.goals, { g -> vm.edit { it.copy(goals = if (g in it.goals) it.goals - g else it.goals + g) } }, label = { goalLabels.getValue(it) })
    }
    Column {
        SubLabel(stringResource(R.string.ws7_edit_wont_do))
        Text(stringResource(R.string.ws7_edit_wont_do_body), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(bottom = 6.dp))
        MultiChipPicker(ContentFlag.entries, f.wontDo, { fl -> vm.edit { it.copy(wontDo = if (fl in it.wontDo) it.wontDo - fl else it.wontDo + fl) } }, label = { CardText.contentFlagLabel(it) })
    }
    val (rate, setRate) = rememberBuffered(f.minRate) { v -> vm.edit { it.copy(minRate = v.filter { ch -> ch.isDigit() }.take(9)) } }
    val (kit, setKit) = rememberBuffered(f.kitFee) { v -> vm.edit { it.copy(kitFee = v.filter { ch -> ch.isDigit() }.take(9)) } }
    CtTextField(
        rate, setRate, label = stringResource(R.string.ws7_onb_rate_label),
        placeholder = Money.symbol(currency), supportingText = stringResource(R.string.ws7_onb_rate_support),
        errorText = if (!f.rateOk) stringResource(R.string.ws7_onb_rate_invalid) else null, singleLine = true, mono = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    CtTextField(
        kit, setKit, label = stringResource(R.string.ws7_edit_kit_fee),
        placeholder = Money.symbol(currency), supportingText = stringResource(R.string.ws7_edit_kit_fee_body, Money.format(1500, currency)),
        errorText = if (!f.kitOk) stringResource(R.string.ws7_onb_rate_invalid) else null, singleLine = true, mono = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )

    // ── Work links ──
    SectionHeader(stringResource(R.string.ws7_edit_links), Modifier.padding(top = 12.dp), count = f.links.size)
    Text(stringResource(R.string.ws7_edit_links_body), style = Ct.type.bodyS, color = c.textSecondary)
    f.links.forEachIndexed { i, link -> key(link.key) { LinkEditor(link, i, f.links.size, f, vm, showErr) } }
    if (f.links.size < EditProfileViewModel.MAX_LINKS) {
        GhostButton(stringResource(R.string.ws7_edit_link_add), vm::addLink, icon = Icons.Rounded.Add)
    } else Text(stringResource(R.string.ws7_edit_links_max), style = Ct.type.label, color = c.textMuted)

    // ── Gear and tools ──
    SectionHeader(stringResource(R.string.ws7_edit_gear), Modifier.padding(top = 12.dp))
    ChipListInput(stringResource(R.string.ws7_edit_gear_label), f.gear, { l -> vm.edit { it.copy(gear = l) } }, stringResource(R.string.ws7_edit_gear_hint))
    ChipListInput(stringResource(R.string.ws7_edit_tools_label), f.tools, { l -> vm.edit { it.copy(tools = l) } }, stringResource(R.string.ws7_edit_tools_hint))

    // ── Taste ──
    SectionHeader(stringResource(R.string.ws7_edit_taste), Modifier.padding(top = 12.dp))
    ChipListInput(stringResource(R.string.ws7_edit_films), f.films, { l -> vm.edit { it.copy(films = l.take(5)) } }, stringResource(R.string.ws7_edit_films_hint), max = 5)
    Column {
        SubLabel(stringResource(R.string.ws7_edit_genres))
        MultiChipPicker((GENRES + f.genres).distinct(), f.genres, { g -> vm.edit { it.copy(genres = if (g in it.genres) it.genres - g else it.genres + g) } }, label = { it })
    }
    Column {
        SubLabel(stringResource(R.string.ws7_edit_formats))
        MultiChipPicker(Format.entries, f.formats, { fm -> vm.edit { it.copy(formats = if (fm in it.formats) it.formats - fm else it.formats + fm) } }, label = { CardText.formatLabel(it) })
    }
}

@Composable
private fun LinkEditor(link: LinkDraft, index: Int, total: Int, f: EditForm, vm: EditProfileViewModel, showErr: Boolean) {
    val c = Ct.colors
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.card).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", style = Ct.type.mono, color = c.textMuted)
            Spacer(Modifier.width(8.dp))
            Text(linkTitle(link, stringResource(R.string.ws7_edit_link_new)), style = Ct.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { vm.moveLink(link.key, -1) }, enabled = index > 0) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.ws7_edit_link_up), tint = if (index > 0) c.text else c.textMuted)
            }
            IconButton(onClick = { vm.moveLink(link.key, 1) }, enabled = index < total - 1) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.ws7_edit_link_down), tint = if (index < total - 1) c.text else c.textMuted)
            }
            IconButton(onClick = { vm.removeLink(link.key) }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.ws7_remove), tint = c.stop)
            }
        }
        val (url, setUrl) = rememberBuffered(link.url) { v -> vm.updateLink(link.key) { d -> d.copy(url = v.trim(), kind = if (d.url.isBlank()) ProfileLogic.detectLinkKind(v) else d.kind) } }
        val (title, setTitle) = rememberBuffered(link.title) { v -> vm.updateLink(link.key) { it.copy(title = v.take(60)) } }
        val (year, setYear) = rememberBuffered(link.year) { v -> vm.updateLink(link.key) { it.copy(year = v.filter { ch -> ch.isDigit() }.take(4)) } }
        CtTextField(
            url, setUrl,
            label = stringResource(R.string.ws7_edit_link_url), placeholder = stringResource(R.string.ws7_onb_link_hint), singleLine = true, leadingIcon = Icons.Rounded.Link,
            errorText = if ((showErr || link.url.length > 6) && !link.urlOk) stringResource(R.string.ws7_onb_link_invalid) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        LinkKindChips(link.kind, { k -> vm.updateLink(link.key) { it.copy(kind = k) } }, allowHeadshot = Taxonomy.isCast(f.roles.primary ?: f.base.primaryRole))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CtTextField(title, setTitle, label = stringResource(R.string.ws7_edit_link_title), singleLine = true, modifier = Modifier.weight(2f))
            CtTextField(
                year, setYear, label = stringResource(R.string.ws7_edit_link_year),
                singleLine = true, mono = true, modifier = Modifier.weight(1f),
                errorText = if (!link.yearOk) "1950–2100" else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Column {
            SubLabel(stringResource(R.string.ws7_edit_link_role))
            val roles = listOfNotNull(f.roles.primary) + f.roles.secondary + f.roles.stepUp
            ChipFlow(lineSpacing = 0.dp, spacing = 6.dp) {
                CtFilterChip(stringResource(R.string.ws7_edit_link_role_none), link.roleCode == null, { vm.updateLink(link.key) { it.copy(roleCode = null) } })
                roles.distinct().forEach { code -> CtFilterChip(shortRoleLabel(code), link.roleCode == code, { vm.updateLink(link.key) { it.copy(roleCode = code) } }) }
            }
        }
    }
}

private fun linkTitle(l: LinkDraft, fallback: String): String = l.title.ifBlank { l.url.substringAfter("://").substringBefore('?').ifBlank { fallback } }

@Composable
private fun goalLabel(g: Goal): String = stringResource(
    when (g) {
        Goal.PAID_WORK -> R.string.ws7_goal_paid
        Goal.REEL_FOOTAGE -> R.string.ws7_goal_reel
        Goal.STEP_UP -> R.string.ws7_goal_step
        Goal.FESTIVAL_CREDIT -> R.string.ws7_goal_festival
        Goal.BUILD_TEAM -> R.string.ws7_goal_team
        Goal.FUN_COMMUNITY -> R.string.ws7_goal_fun
    }
)
