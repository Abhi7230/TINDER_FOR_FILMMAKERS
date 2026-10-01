package com.tff.app.ui.screens.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.domain.Severity
import com.tff.app.model.Ambition
import com.tff.app.model.AuditionMode
import com.tff.app.model.ContentFlag
import com.tff.app.model.DateCertainty
import com.tff.app.model.Format
import com.tff.app.model.LocationType
import com.tff.app.model.Meals
import com.tff.app.model.ScriptStatus
import com.tff.app.model.Stage
import com.tff.app.model.Transport
import com.tff.app.model.YesNoNa
import com.tff.app.ui.components.ChoiceCards
import com.tff.app.ui.components.ChoiceOption
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.DateField
import com.tff.app.ui.components.InfoTipCard
import com.tff.app.ui.components.KeyValueRow
import com.tff.app.ui.components.MultiChipPicker
import com.tff.app.ui.components.SegmentedControl
import com.tff.app.ui.components.SliderRow
import com.tff.app.ui.components.ToggleRow
import com.tff.app.ui.components.contentFlagIcon
import kotlin.math.roundToInt

private val GENRES = listOf("Drama", "Comedy", "Thriller", "Horror", "Romance", "Crime", "Mystery", "Sci-fi", "Fantasy", "Action", "Family", "Music", "Experimental")
private val INTIMATE = setOf(ContentFlag.NUDITY, ContentFlag.SIMULATED_SEX, ContentFlag.INTIMACY_SCENE)
private val RISKY = setOf(ContentFlag.STUNTS_FIGHTS, ContentFlag.FIREARMS_PROPS)

@Composable
private fun anchor(ui: WizardUi, vm: PostProjectViewModel, key: String): Modifier =
    Modifier.focusAnchor(key, ui.focusField, ui.focusSeq, vm::clearFocus)

// ─────────────────────────────── Step 1 · Basics ───────────────────────────────

@Composable
internal fun BasicsStep(ui: WizardUi, vm: PostProjectViewModel) {
    val p = ui.draft?.project ?: return
    val show = ui.showErrors(WizardStep.BASICS)
    var more by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        CtTextField(
            p.title, { t -> vm.updateProject { it.copy(title = t) } }, label = stringResource(R.string.ws5_title),
            placeholder = stringResource(R.string.ws5_title_ph), maxLength = 60, singleLine = true,
            errorText = blockText(ui.issues(Fields.TITLE), show), modifier = anchor(ui, vm, Fields.TITLE),
        )
        CtTextField(
            p.logline, { t -> vm.updateProject { it.copy(logline = t) } }, label = stringResource(R.string.ws5_logline),
            placeholder = stringResource(R.string.ws5_logline_ph), maxLength = 200, minLines = 3,
            supportingText = stringResource(R.string.ws5_logline_hint),
            errorText = blockText(ui.issues(Fields.LOGLINE), show), modifier = anchor(ui, vm, Fields.LOGLINE),
        )
        Column(Modifier.fillMaxWidth()) {
            FieldLabel(stringResource(R.string.ws5_format))
            ChoiceChips(Format.entries, p.format, { CardText.formatLabel(it) }, { f -> vm.updateProject { it.copy(format = f) } })
        }
        IntField(
            p.runtimeMin.takeIf { it > 0 }, { v -> vm.updateProject { it.copy(runtimeMin = v ?: 0) } },
            label = stringResource(R.string.ws5_runtime), supportingText = stringResource(R.string.ws5_runtime_hint),
            errorText = blockText(ui.issues(Fields.RUNTIME), show), modifier = anchor(ui, vm, Fields.RUNTIME),
        )
        Column(anchor(ui, vm, Fields.LANGUAGES).fillMaxWidth()) {
            val err = blockText(ui.issues(Fields.LANGUAGES), show)
            FieldLabel(stringResource(R.string.ws5_languages), error = err != null, hint = stringResource(R.string.ws5_languages_hint))
            MultiChipPicker(
                options = WizardLogic.languageOptions(ui.owner, p.languages), selected = p.languages.toSet(),
                onToggle = { code -> vm.updateProject { it.copy(languages = if (code in it.languages) it.languages - code else it.languages + code) } },
                label = CardText::languageName,
            )
            RuleMessages(ui.issues(Fields.LANGUAGES), show)
        }
        Column(Modifier.fillMaxWidth()) {
            FieldLabel(stringResource(R.string.ws5_script))
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = listOf(stringResource(R.string.ws5_script_idea), stringResource(R.string.ws5_script_draft), stringResource(R.string.ws5_script_locked)),
                selectedIndex = p.scriptStatus.ordinal,
                onSelect = { i -> vm.updateProject { it.copy(scriptStatus = ScriptStatus.entries[i]) } },
            )
        }
        ToggleRow(
            title = stringResource(R.string.ws5_post_only), subtitle = stringResource(R.string.ws5_post_only_hint),
            checked = ui.postOnly, onCheckedChange = vm::setPostOnly, enabled = !ui.isLive,
        )
        MoreDetails(more, { more = !more }, summary = stringResource(R.string.ws5_basics_more_summary)) {
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_genres), hint = stringResource(R.string.ws5_genres_hint))
                MultiChipPicker(
                    GENRES, p.genres.toSet(), { g -> vm.updateProject { it.copy(genres = if (g in it.genres) it.genres - g else it.genres + g) } },
                    label = { it }, maxSelected = 3,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_stage))
                ChoiceChips(Stage.entries, p.stage, { stageLabel(it) }, vm::setStage, enabled = !ui.isLive)
            }
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_ambition), hint = stringResource(R.string.ws5_ambition_hint))
                ChoiceChips(Ambition.entries, p.ambition, { ambitionLabel(it) }, { a -> vm.updateProject { it.copy(ambition = a) } })
            }
            ToggleRow(
                stringResource(R.string.ws5_readable), p.scriptReadable, { on -> vm.updateProject { it.copy(scriptReadable = on) } },
                subtitle = stringResource(R.string.ws5_readable_hint),
            )
            CtTextField(
                p.lookbookUrl ?: "", { t -> vm.updateProject { it.copy(lookbookUrl = t) } }, label = stringResource(R.string.ws5_lookbook),
                placeholder = stringResource(R.string.ws5_lookbook_ph), singleLine = true, maxLength = 200, showCounter = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            ToggleRow(
                stringResource(R.string.ws5_zero_budget), p.honestZeroBudget, { on -> vm.updateProject { it.copy(honestZeroBudget = on) } },
                subtitle = stringResource(R.string.ws5_zero_budget_hint),
            )
        }
    }
}

@Composable
fun stageLabel(s: Stage): String = stringResource(
    when (s) {
        Stage.DEVELOPMENT -> R.string.ws5_stage_dev
        Stage.PRE -> R.string.ws5_stage_pre
        Stage.PRODUCTION -> R.string.ws5_stage_prod
        Stage.POST -> R.string.ws5_stage_post
    },
)

@Composable
fun ambitionLabel(a: Ambition): String = stringResource(
    when (a) {
        Ambition.FOR_FUN -> R.string.ws5_amb_fun
        Ambition.PORTFOLIO -> R.string.ws5_amb_portfolio
        Ambition.FESTIVAL -> R.string.ws5_amb_festival
        Ambition.DISTRIBUTION -> R.string.ws5_amb_distribution
        Ambition.CLIENT_PAID -> R.string.ws5_amb_client
    },
)

@Composable
fun locationLabel(l: LocationType): String = stringResource(
    when (l) {
        LocationType.STUDIO -> R.string.ws5_loc_studio
        LocationType.OUTDOOR_PUBLIC -> R.string.ws5_loc_outdoor
        LocationType.PRIVATE_HOME -> R.string.ws5_loc_home
        LocationType.OFFICE_COMMERCIAL -> R.string.ws5_loc_office
        LocationType.MIXED -> R.string.ws5_loc_mixed
    },
)

@Composable
fun yesNoNaLabel(v: YesNoNa): String = stringResource(
    when (v) {
        YesNoNa.YES -> R.string.ws5_yes
        YesNoNa.NO -> R.string.ws5_no
        YesNoNa.NOT_YET -> R.string.ws5_not_yet
        YesNoNa.NA -> R.string.ws5_not_needed
    },
)

// ─────────────────────────────── Step 2 · When and where ───────────────────────────────

@Composable
internal fun WhenWhereStep(ui: WizardUi, vm: PostProjectViewModel) {
    val p = ui.draft?.project ?: return
    val show = ui.showErrors(WizardStep.WHEN_WHERE)
    var cityOpen by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    val city = ui.city
    val needsLock = WizardLogic.needsLock(ui.activeRoles)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        CityPickerRow(city, { cityOpen = true }, enabled = !ui.isLive)
        if (city != null && city.areas.size > 1) {
            Column(anchor(ui, vm, Fields.AREA).fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_area), hint = stringResource(R.string.ws5_area_hint))
                ChoiceChips(city.areas, ui.area, { it.name }, { a -> vm.updateProject { it.copy(areaId = a.id) } }, enabled = !ui.isLive)
            }
        }
        if (ui.postOnly) {
            InfoTipCard(stringResource(R.string.ws5_post_only_tip), title = stringResource(R.string.ws5_post_only_tip_title), icon = Icons.Rounded.Movie)
            if (needsLock) PictureLockField(ui, vm, show)
            return@Column
        }
        Column(anchor(ui, vm, Fields.LOCATION).fillMaxWidth()) {
            val err = blockText(ui.issues(Fields.LOCATION), show)
            FieldLabel(stringResource(R.string.ws5_location), error = err != null)
            ChoiceChips(LocationType.entries, p.shootLocationType, { locationLabel(it) }, { l ->
                vm.updateProject { it.copy(shootLocationType = l, bringFriendWelcome = it.bringFriendWelcome || l == LocationType.PRIVATE_HOME) }
            })
            RuleMessages(ui.issues(Fields.LOCATION), show)
        }
        if (p.shootLocationType == LocationType.PRIVATE_HOME) {
            InfoTipCard(stringResource(R.string.ws5_home_tip), icon = Icons.Rounded.Home)
        }
        Column(anchor(ui, vm, Fields.SHOOT_DATES).fillMaxWidth()) {
            StepSubhead(stringResource(R.string.ws5_shoot_dates))
            if (ui.datesLocked) {
                KeyValueRow(stringResource(R.string.ws5_shoot_dates), datesText(p.shootDates), mono = true)
                InfoTipCard(stringResource(R.string.ws5_dates_locked_tip), icon = Icons.Rounded.Lock)
            } else {
                ShootDatesField(
                    p.shootDates, { d -> vm.updateProject { it.copy(shootDates = d, lodging = if (d.size > 1) it.lodging ?: false else it.lodging) } },
                    today = ui.today, errorText = blockText(ui.issues(Fields.SHOOT_DATES), show),
                )
            }
        }
        Column(anchor(ui, vm, Fields.CERTAINTY).fillMaxWidth()) {
            val err = blockText(ui.issues(Fields.CERTAINTY), show)
            FieldLabel(stringResource(R.string.ws5_certainty), error = err != null, hint = stringResource(R.string.ws5_certainty_hint))
            ChoiceChips(DateCertainty.entries, p.dateCertainty, {
                stringResource(if (it == DateCertainty.LOCKED) R.string.ws5_dates_locked else R.string.ws5_dates_tentative)
            }, { c -> vm.updateProject { it.copy(dateCertainty = c) } }, enabled = !ui.datesLocked)
            RuleMessages(ui.issues(Fields.CERTAINTY), show)
        }
        Column(anchor(ui, vm, Fields.HOURS).fillMaxWidth()) {
            SliderRow(
                title = stringResource(R.string.ws5_hours_cap), value = (p.hoursCap ?: WizardLogic.DEFAULT_HOURS).toFloat(),
                onValueChange = { v -> vm.updateProject { it.copy(hoursCap = v.roundToInt()) } },
                valueRange = 2f..16f, steps = 13, valueLabel = { "≤${it.roundToInt()}h" },
                subtitle = stringResource(R.string.ws5_hours_hint),
            )
            RuleMessages(ui.issues(Fields.HOURS), show)
        }
        Column(anchor(ui, vm, Fields.MEALS).fillMaxWidth()) {
            val mealIssues = ui.rules.filter { it.field == Fields.MEALS }.distinctBy { it.message }
            val err = blockText(mealIssues, show)
            FieldLabel(stringResource(R.string.ws5_meals), error = err != null, hint = stringResource(R.string.ws5_meals_hint))
            ChoiceChips(Meals.entries, p.meals, { CardText.mealsLabel(it) }, { m -> vm.updateProject { it.copy(meals = m) } })
            RuleMessages(mealIssues, show)
        }
        Column(anchor(ui, vm, Fields.TRANSPORT).fillMaxWidth()) {
            val err = blockText(ui.issues(Fields.TRANSPORT), show)
            FieldLabel(stringResource(R.string.ws5_transport), error = err != null)
            ChoiceChips(Transport.entries, p.transport, { CardText.transportLabel(it) }, { t -> vm.updateProject { it.copy(transport = t) } })
            RuleMessages(ui.issues(Fields.TRANSPORT), show)
        }
        if (p.transport == Transport.MILEAGE_OR_TA_PAID) {
            MoneyField(
                p.travelAllowancePerDay, { v -> vm.updateProject { it.copy(travelAllowancePerDay = v) } },
                label = stringResource(R.string.ws5_ta), currency = ui.currency,
                errorText = blockText(ui.issues(Fields.TA), show), modifier = anchor(ui, vm, Fields.TA),
            )
        }
        if (p.shootDates.size > 1) {
            ToggleRow(
                stringResource(R.string.ws5_lodging), p.lodging == true, { on -> vm.updateProject { it.copy(lodging = on) } },
                subtitle = stringResource(R.string.ws5_lodging_hint),
            )
        }
        if (needsLock) PictureLockField(ui, vm, show)
        MoreDetails(more || ui.focusField in Fields.WHEN_MORE, { more = !more }, summary = stringResource(R.string.ws5_when_more_summary)) {
            CtTextField(
                p.storySetting ?: "", { t -> vm.updateProject { it.copy(storySetting = t) } }, label = stringResource(R.string.ws5_story_setting),
                placeholder = stringResource(R.string.ws5_story_setting_ph), maxLength = 80, singleLine = true,
                errorText = blockText(ui.issues(Fields.STORY), true), modifier = anchor(ui, vm, Fields.STORY),
            )
        }
    }
    if (cityOpen) {
        CityPickerSheet(ui.cities, p.cityId, onPick = { vm.setCity(it.id); cityOpen = false }, onDismiss = { cityOpen = false })
    }
}

/** Picture lock (R20): asked whenever the project has a composer or sound designer. A target is fine. */
@Composable
internal fun PictureLockField(ui: WizardUi, vm: PostProjectViewModel, show: Boolean) {
    val p = ui.draft?.project ?: return
    val issues = ui.rules.filter { it.field == Fields.LOCK }.distinctBy { it.message }
    DateField(
        label = stringResource(R.string.ws5_picture_lock), value = p.pictureLockDate,
        onValueChange = { d -> vm.updateProject { it.copy(pictureLockDate = d) } },
        minDate = ui.today.minusDays(365), supportingText = stringResource(R.string.ws5_picture_lock_hint),
        errorText = blockText(issues, show), modifier = anchor(ui, vm, Fields.LOCK),
    )
}

@Composable
private fun datesText(dates: List<java.time.LocalDate>): String {
    val s = dates.minOrNull() ?: return "—"
    val e = dates.maxOrNull()!!
    return pluralDays(dates.size) + " · " + (if (s == e) com.tff.app.core.Dates.short(s) else com.tff.app.core.Dates.span(s, e))
}

// ─────────────────────────────── Step 4 · Safety ───────────────────────────────

@Composable
internal fun SafetyStep(ui: WizardUi, vm: PostProjectViewModel) {
    val p = ui.draft?.project ?: return
    val show = ui.showErrors(WizardStep.SAFETY)
    var more by rememberSaveable { mutableStateOf(false) }
    val intimate = p.contentFlags.any { it in INTIMATE }
    val risky = p.contentFlags.any { it in RISKY }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(anchor(ui, vm, Fields.CONTENT).fillMaxWidth()) {
            val err = blockText(ui.issues(Fields.CONTENT), show)
            FieldLabel(stringResource(R.string.ws5_content), error = err != null, hint = stringResource(R.string.ws5_content_hint))
            MultiChipPicker(
                options = ContentFlag.entries, selected = p.contentFlags,
                onToggle = { f -> vm.updateProject { it.copy(contentFlags = if (f in it.contentFlags) it.contentFlags - f else it.contentFlags + f, contentNoneConfirmed = false) } },
                label = CardText::contentFlagLabel, icon = { contentFlagIcon(it) },
            )
            CtFilterChip(
                stringResource(R.string.ws5_none_of_these), selected = p.contentNoneConfirmed && p.contentFlags.isEmpty(),
                onClick = { vm.updateProject { it.copy(contentFlags = emptySet(), contentNoneConfirmed = !(it.contentNoneConfirmed && it.contentFlags.isEmpty())) } },
            )
            RuleMessages(ui.issues(Fields.CONTENT), show)
        }
        if (intimate) {
            Column(anchor(ui, vm, Fields.IC).fillMaxWidth()) {
                val err = blockText(ui.issues(Fields.IC), show)
                FieldLabel(stringResource(R.string.ws5_ic_q), error = err != null)
                YesNoChips(p.intimacyCoordinator, { v -> vm.updateProject { it.copy(intimacyCoordinator = v) } })
                RuleMessages(ui.issues(Fields.IC), show)
            }
            KeyValueRow(stringResource(R.string.ws5_no_nudity_auditions), stringResource(R.string.ws5_fixed), icon = Icons.Rounded.Lock, valueColor = com.tff.app.ui.theme.Ct.colors.textSecondary)
        }
        if (risky) {
            Column(anchor(ui, vm, Fields.STUNT).fillMaxWidth()) {
                val err = blockText(ui.issues(Fields.STUNT), show)
                FieldLabel(stringResource(R.string.ws5_stunt_q), error = err != null)
                YesNoChips(p.stuntOrArmourer, { v -> vm.updateProject { it.copy(stuntOrArmourer = v) } })
                RuleMessages(ui.issues(Fields.STUNT), show)
            }
        }
        if (intimate || risky || !p.safetyMeasures.isNullOrBlank()) {
            CtTextField(
                p.safetyMeasures ?: "", { t -> vm.updateProject { it.copy(safetyMeasures = t) } }, label = stringResource(R.string.ws5_safety_measures),
                placeholder = stringResource(if (intimate) R.string.ws5_safety_measures_ph_ic else R.string.ws5_safety_measures_ph), maxLength = 400, minLines = 3,
                errorText = blockText(ui.issues(Fields.SAFETY), show), modifier = anchor(ui, vm, Fields.SAFETY),
            )
        }
        if (ContentFlag.MINORS_ON_SET in p.contentFlags) {
            Column(anchor(ui, vm, Fields.GUARDIAN).fillMaxWidth()) {
                val err = blockText(ui.issues(Fields.GUARDIAN), show)
                FieldLabel(stringResource(R.string.ws5_guardian_q), error = err != null, hint = stringResource(R.string.ws5_guardian_hint))
                YesNoChips(p.guardianPresent, { v -> vm.updateProject { it.copy(guardianPresent = v) } })
                RuleMessages(ui.issues(Fields.GUARDIAN), show)
            }
        }
        if (WizardLogic.hasCast(ui.activeRoles)) {
            Column(anchor(ui, vm, Fields.AUDITION).fillMaxWidth()) {
                val err = blockText(ui.issues(Fields.AUDITION), show)
                FieldLabel(stringResource(R.string.ws5_audition), error = err != null, hint = stringResource(R.string.ws5_audition_hint))
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                val modes = AuditionMode.entries
                ChoiceCards(
                    options = modes.map { m ->
                        ChoiceOption(
                            CardText.auditionModeLabel(m),
                            stringResource(when (m) {
                                AuditionMode.SELF_TAPE -> R.string.ws5_aud_tape_body
                                AuditionMode.VIDEO_CALL -> R.string.ws5_aud_call_body
                                AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> R.string.ws5_aud_studio_body
                            }),
                            when (m) { AuditionMode.SELF_TAPE -> Icons.Rounded.Movie; AuditionMode.VIDEO_CALL -> Icons.Rounded.Videocam; else -> Icons.Rounded.Groups },
                        )
                    },
                    selectedIndex = p.auditionMode?.let { modes.indexOf(it) },
                    onSelect = { i -> vm.updateProject { it.copy(auditionMode = modes[i]) } },
                )
                RuleMessages(ui.issues(Fields.AUDITION), show)
            }
        }
        val home = p.shootLocationType == LocationType.PRIVATE_HOME && !ui.postOnly
        ToggleRow(
            stringResource(R.string.ws5_bring_friend), p.bringFriendWelcome || home, { on -> vm.updateProject { it.copy(bringFriendWelcome = on) } },
            subtitle = stringResource(if (home) R.string.ws5_bring_friend_forced else R.string.ws5_bring_friend_hint), enabled = !home,
        )
        MoreDetails(more, { more = !more }, summary = stringResource(R.string.ws5_safety_more_summary)) {
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_insurance))
                ChoiceChips(YesNoNa.entries, p.insurance, { yesNoNaLabel(it) }, { v -> vm.updateProject { it.copy(insurance = v) } })
            }
            Column(Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.ws5_permits))
                ChoiceChips(YesNoNa.entries, p.permits, { yesNoNaLabel(it) }, { v -> vm.updateProject { it.copy(permits = v) } })
            }
        }
    }
}

/** Money label helper for steps: "₹2,000". */
internal fun money(amount: Int?, currency: String): String = amount?.let { Money.format(it, currency) } ?: ""

/** True when [results] has a WARN or AUTO note (used for amber markers on role rows). */
internal fun hasNotes(results: List<com.tff.app.domain.RuleResult>) = results.any { it.severity != Severity.BLOCK }
