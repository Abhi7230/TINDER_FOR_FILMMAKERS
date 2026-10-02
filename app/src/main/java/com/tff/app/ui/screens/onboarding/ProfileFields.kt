package com.tff.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.domain.CardText
import com.tff.app.model.AvailabilityPattern
import com.tff.app.model.City
import com.tff.app.model.CompMode
import com.tff.app.model.Level
import com.tff.app.model.LinkKind
import com.tff.app.model.Perk
import com.tff.app.model.RoleCode
import com.tff.app.ui.components.ChipFlow
import com.tff.app.ui.components.ChipStyle
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.ChoiceCards
import com.tff.app.ui.components.ChoiceOption
import com.tff.app.ui.components.CtBottomSheet
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtFilterChip
import com.tff.app.ui.components.CtTextField
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.MultiChipPicker
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.RolePicker
import com.tff.app.ui.components.RolePickerMode
import com.tff.app.ui.components.SegmentedControl
import com.tff.app.ui.components.shortRoleLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

// Form pieces shared by Onboarding (S04) and Edit profile (S20). All copy from strings_ws7.xml.

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Ct.type.bodyS.copy(fontWeight = Ct.type.label.fontWeight), color = Ct.colors.textSecondary, modifier = modifier)
}

/**
 * S04 step 1 roles, in explicit steps: "Your main role" is a single-select radio list with search; once it's picked
 * the list folds into one row ([Change]) and two optional chip rows follow: "Also do" (up to 4) and "Want to step up
 * to" (up to 2). Long-press on the list stays as a shortcut for step-up, with a TalkBack action of the same name.
 */
@Composable
fun RoleChooser(sel: RoleSelection, onChange: (RoleSelection) -> Unit, modifier: Modifier = Modifier, showPicker: Boolean = true) {
    val c = Ct.colors
    var hint by remember { mutableStateOf<String?>(null) }
    var changing by rememberSaveable { mutableStateOf(false) }
    val stepMax = stringResource(R.string.ws7_onb_step_up_max)
    val alsoMax = stringResource(R.string.ws7_onb_also_max)
    val tagAlso = stringResource(R.string.ws7_onb_tag_also)
    val tagStep = stringResource(R.string.ws7_onb_tag_step)
    Column(modifier.fillMaxWidth()) {
        if (!showPicker) { RoleSummary(sel); return@Column }
        val main = sel.primary
        if (main == null || changing) {
            Text(stringResource(R.string.ws7_onb_main_help), style = Ct.type.bodyS, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            RolePicker(
                selected = setOfNotNull(main),
                onToggle = { code -> onChange(sel.withPrimary(code)); changing = false; hint = null },
                mode = RolePickerMode.SINGLE,
                tagFor = { code -> when (sel.tagOf(code)) { RoleTag.ALSO -> tagAlso; RoleTag.STEP_UP -> tagStep; else -> null } },
                onLongPress = { code ->
                    val next = sel.toggleStepUp(code)
                    hint = if (next == sel && code != sel.primary) stepMax else null
                    onChange(next)
                },
                longPressLabel = stringResource(R.string.ws7_onb_step_up_action),
            )
        } else {
            MainRoleRow(main, onChange = { changing = true })
        }
        if (main != null && !changing) {
            val others = remember(main) { otherRoles(main) }
            Spacer(Modifier.height(18.dp))
            FieldLabel(stringResource(R.string.ws7_onb_also_label))
            Text(stringResource(R.string.ws7_onb_also_help), style = Ct.type.label, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            ChipFlow(spacing = 6.dp, lineSpacing = 0.dp) {
                others.forEach { code ->
                    CtFilterChip(shortRoleLabel(code), code in sel.secondary, {
                        val next = sel.toggleAlso(code)
                        hint = if (next == sel) alsoMax else null
                        onChange(next)
                    })
                }
            }
            Spacer(Modifier.height(18.dp))
            FieldLabel(stringResource(R.string.ws7_onb_step_label))
            Text(stringResource(R.string.ws7_onb_step_help), style = Ct.type.label, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            ChipFlow(spacing = 6.dp, lineSpacing = 0.dp) {
                others.filter { it !in sel.secondary }.forEach { code ->
                    CtFilterChip(shortRoleLabel(code), code in sel.stepUp, {
                        val next = sel.toggleStepUp(code)
                        hint = if (next == sel) stepMax else null
                        onChange(next)
                    })
                }
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint!!, style = Ct.type.label, color = c.onWarnContainer)
        }
    }
}

/** Other roles for the chip rows: the main role's department first, then the rest in taxonomy order. */
private fun otherRoles(main: RoleCode): List<RoleCode> {
    val dept = com.tff.app.model.Taxonomy.dept(main)
    val all = com.tff.app.model.Taxonomy.all.map { it.code }.filter { it != main }
    return all.filter { com.tff.app.model.Taxonomy.dept(it) == dept } + all.filter { com.tff.app.model.Taxonomy.dept(it) != dept }
}

/** The picked main role, folded into one row with [Change]. */
@Composable
private fun MainRoleRow(main: RoleCode, onChange: () -> Unit) {
    val c = Ct.colors
    val info = com.tff.app.model.Taxonomy.info(main)
    Row(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(c.accent.copy(alpha = 0.08f))
            .border(1.5.dp, c.accent, CtShapes.field).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ws7_onb_main_kicker), style = Ct.type.slate.copy(fontSize = 13.sp, lineHeight = 16.sp), color = c.textMuted)
            Text(info.label, style = Ct.type.bodyStrong, color = c.accent)
            Text(info.duties, style = Ct.type.label, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        com.tff.app.ui.components.GhostButton(stringResource(R.string.ws7_onb_main_change), onChange)
    }
}

@Composable
private fun RoleSummary(sel: RoleSelection) {
    val c = Ct.colors
    if (sel.primary == null && sel.secondary.isEmpty() && sel.stepUp.isEmpty()) {
        Text(stringResource(R.string.ws7_onb_roles_none), style = Ct.type.label, color = c.textMuted)
        return
    }
    ChipFlow(lineSpacing = 6.dp, spacing = 6.dp) {
        sel.primary?.let { CtChip(stringResource(R.string.ws7_onb_roles_summary_main, shortRoleLabel(it)), tone = ChipTone.ACCENT, filled = true) }
        if (sel.primary == null) CtChip(stringResource(R.string.ws7_onb_roles_none), tone = ChipTone.WARN)
        if (sel.secondary.isNotEmpty()) CtChip(stringResource(R.string.ws7_onb_roles_summary_also, sel.secondary.joinToString(", ") { shortRoleLabel(it) }))
        if (sel.stepUp.isNotEmpty()) CtChip(stringResource(R.string.ws7_onb_roles_summary_step, sel.stepUp.joinToString(", ") { shortRoleLabel(it) }), tone = ChipTone.INFO)
    }
}

/** Level: four radio cards, each with its name and one-line description ("First project / Building a reel / ..."). */
@Composable
fun LevelPicker(level: Level?, onSelect: (Level) -> Unit, modifier: Modifier = Modifier) {
    ChoiceCards(
        listOf(
            ChoiceOption(levelName(Level.FIRST_PROJECT), stringResource(R.string.ws7_onb_level_first_body)),
            ChoiceOption(levelName(Level.BUILDING_REEL), stringResource(R.string.ws7_onb_level_reel_body)),
            ChoiceOption(levelName(Level.CREDITED_INDIE), stringResource(R.string.ws7_onb_level_credited_body)),
            ChoiceOption(levelName(Level.WORKING_PRO), stringResource(R.string.ws7_onb_level_pro_body)),
        ),
        selectedIndex = level?.ordinal,
        onSelect = { onSelect(Level.entries[it]) },
        modifier = modifier,
    )
}

/** Field-like button that opens the city sheet. */
@Composable
fun CityField(cities: List<City>, cityId: String?, onPick: (City) -> Unit, onMissing: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val city = cities.firstOrNull { it.id == cityId }
    val early = stringResource(R.string.ws7_onb_city_early_label)
    Column(modifier.fillMaxWidth()) {
        FieldLabel(stringResource(R.string.ws7_onb_city_label))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(CtShapes.field)
                .background(c.surfaceRaised)
                .border(1.dp, if (city != null) c.outline else c.accent.copy(alpha = 0.6f), CtShapes.field)
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.LocationCity, contentDescription = null, tint = c.textSecondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(city?.name ?: stringResource(R.string.ws7_onb_city_pick), style = Ct.type.body, color = if (city != null) c.text else c.textMuted)
                if (city?.isEarly == true) Text(early, style = Ct.type.label, color = c.onWarnContainer)
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary)
        }
    }
    if (open) CitySheet(cities, cityId, onPick = { open = false; onPick(it) }, onMissing = { name -> open = false; onMissing(name) }, onDismiss = { open = false })
}

@Composable
private fun CitySheet(cities: List<City>, selected: String?, onPick: (City) -> Unit, onMissing: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Ct.colors
    var asking by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    if (asking) {
        CtBottomSheet(onDismiss, title = stringResource(R.string.ws7_onb_city_missing_title), subtitle = stringResource(R.string.ws7_onb_city_missing_body)) {
            CtTextField(typed, { typed = it }, label = stringResource(R.string.ws7_onb_city_missing_label), placeholder = stringResource(R.string.ws7_onb_city_missing_hint), singleLine = true, maxLength = 40)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.ws7_onb_city_missing_send), { onMissing(typed.trim()); asking = false; typed = "" }, Modifier.fillMaxWidth(), enabled = typed.trim().length >= 2)
        }
        return
    }
    CtBottomSheet(onDismiss, title = stringResource(R.string.ws7_onb_city_sheet)) {
        val live = cities.filter { it.isLive }
        val early = cities.filter { it.isEarly && !it.isLive }
        val listed = cities.filter { !it.isLive && !it.isEarly }
        CityGroup(stringResource(R.string.ws7_onb_city_live), live, selected, onPick, chip = stringResource(R.string.ws7_onb_city_live_chip))
        CityGroup(stringResource(R.string.ws7_onb_city_listed), listed, selected, onPick)
        CityGroup(stringResource(R.string.ws7_onb_city_early), early, selected, onPick, note = stringResource(R.string.ws7_onb_city_early_label))
        Spacer(Modifier.height(8.dp))
        GhostButton(stringResource(R.string.ws7_onb_city_missing), { asking = true }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CityGroup(title: String, cities: List<City>, selected: String?, onPick: (City) -> Unit, chip: String? = null, note: String? = null) {
    if (cities.isEmpty()) return
    val c = Ct.colors
    Text(title, style = Ct.type.slate.copy(fontSize = 14.sp, lineHeight = 18.sp), color = c.textMuted, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    if (note != null) Text(note, style = Ct.type.label, color = c.onWarnContainer, modifier = Modifier.padding(bottom = 4.dp))
    Column(Modifier.fillMaxWidth().clip(CtShapes.field).background(c.surfaceRaised)) {
        cities.forEachIndexed { i, city ->
            if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
            val isSel = city.id == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(role = Role.RadioButton) { onPick(city) }
                    .semantics { stateDescription = if (isSel) "Selected" else "" }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(city.name, style = Ct.type.body, color = if (isSel) c.accent else c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chip != null) CtChip(chip, tone = ChipTone.GO, style = ChipStyle.CAPS, small = true)
                Spacer(Modifier.width(8.dp))
                Text(city.country, style = Ct.type.monoS, color = c.textMuted)
            }
        }
    }
}

/** Area chips for the chosen city (single choice). */
@Composable
fun AreaChips(city: City?, areaId: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (city == null) return
    Column(modifier.fillMaxWidth()) {
        FieldLabel(stringResource(R.string.ws7_onb_area_label))
        if (areaId == null) Text(stringResource(R.string.ws7_onb_area_pick), style = Ct.type.label, color = Ct.colors.textMuted)
        Spacer(Modifier.height(6.dp))
        ChipFlow(lineSpacing = 0.dp) {
            city.areas.forEach { a -> CtFilterChip(a.name, a.id == areaId, { onPick(a.id) }) }
        }
    }
}

/** Language chips (≥1). */
@Composable
fun LanguagePicker(country: String?, selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val options = (ProfileLogic.languageOptions(country) + selected).distinct()
    Column(modifier.fillMaxWidth()) {
        FieldLabel(stringResource(R.string.ws7_onb_languages_label))
        if (selected.isEmpty()) Text(stringResource(R.string.ws7_onb_languages_none), style = Ct.type.label, color = Ct.colors.onWarnContainer)
        Spacer(Modifier.height(6.dp))
        MultiChipPicker(options, selected, onToggle, label = { CardText.languageName(it) })
    }
}

/** Comp mode: three weighty cards (S04 step 3). */
@Composable
fun CompModePicker(mode: CompMode?, onSelect: (CompMode) -> Unit, modifier: Modifier = Modifier) {
    val order = listOf(CompMode.PAID_ONLY, CompMode.PAID_AND_SELECTIVE, CompMode.OPEN_TO_UNPAID)
    ChoiceCards(
        listOf(
            ChoiceOption(stringResource(R.string.ws7_onb_comp_paid), stringResource(R.string.ws7_onb_comp_paid_body)),
            ChoiceOption(stringResource(R.string.ws7_onb_comp_selective), stringResource(R.string.ws7_onb_comp_selective_body)),
            ChoiceOption(stringResource(R.string.ws7_onb_comp_open), stringResource(R.string.ws7_onb_comp_open_body)),
        ),
        selectedIndex = mode?.let { order.indexOf(it) },
        onSelect = { onSelect(order[it]) },
        modifier = modifier,
    )
}

/** "I'll do unpaid if…" checklist. */
@Composable
fun UnpaidPerksPicker(selected: Set<Perk>, onToggle: (Perk) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        FieldLabel(stringResource(R.string.ws7_onb_unpaid_label))
        Text(stringResource(R.string.ws7_onb_unpaid_body), style = Ct.type.label, color = Ct.colors.textMuted)
        Spacer(Modifier.height(6.dp))
        MultiChipPicker(ProfileLogic.unpaidPerkOptions, selected, onToggle, label = { CardText.perkLabel(it) })
    }
}

@Composable
fun availabilityLabel(a: AvailabilityPattern): String = stringResource(
    when (a) {
        AvailabilityPattern.ANY_DAY -> R.string.ws7_onb_avail_any
        AvailabilityPattern.WEEKENDS_ONLY -> R.string.ws7_onb_avail_weekends
        AvailabilityPattern.EVENINGS_AND_WEEKENDS -> R.string.ws7_onb_avail_evenings
        AvailabilityPattern.WEEKDAYS_ONLY -> R.string.ws7_onb_avail_weekdays
    }
)

/** Availability pattern: single-choice chips. */
@Composable
fun AvailabilityPicker(value: AvailabilityPattern?, onSelect: (AvailabilityPattern) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        FieldLabel(stringResource(R.string.ws7_onb_avail_label))
        Spacer(Modifier.height(6.dp))
        ChipFlow(lineSpacing = 0.dp) {
            ProfileLogic.availabilityOptions.forEach { a -> CtFilterChip(availabilityLabel(a), a == value, { onSelect(a) }) }
        }
    }
}

@Composable
fun linkKindLabel(k: LinkKind): String = stringResource(
    when (k) {
        LinkKind.REEL -> R.string.ws7_kind_reel
        LinkKind.CREDIT -> R.string.ws7_kind_credit
        LinkKind.HEADSHOT -> R.string.ws7_kind_headshot
        LinkKind.PORTFOLIO -> R.string.ws7_kind_portfolio
        LinkKind.IMDB -> R.string.ws7_kind_imdb
        LinkKind.OTHER -> R.string.ws7_kind_other
    }
)

/** Kind chips for a work link; [allowHeadshot] only for actors. */
@Composable
fun LinkKindChips(kind: LinkKind, onSelect: (LinkKind) -> Unit, allowHeadshot: Boolean, modifier: Modifier = Modifier) {
    val kinds = listOfNotNull(LinkKind.REEL, LinkKind.PORTFOLIO, LinkKind.IMDB, LinkKind.CREDIT, LinkKind.HEADSHOT.takeIf { allowHeadshot }, LinkKind.OTHER)
    ChipFlow(modifier, lineSpacing = 0.dp, spacing = 6.dp) {
        kinds.forEach { k -> CtFilterChip(linkKindLabel(k), k == kind, { onSelect(k) }) }
    }
}

@Composable
fun levelName(l: Level): String = CardText.levelLabel(l)

/** "Writer", "DP"… for summaries. */
fun roleShort(code: RoleCode): String = shortRoleLabel(code)

/**
 * Text bound to a ViewModel, buffered locally so fast typing never loses characters while the VM's
 * StateFlow catches up. Values we sent ourselves are treated as echoes; anything else the VM says
 * (a reset, a filtered value) is adopted.
 */
@Composable
fun rememberBuffered(external: String, onChange: (String) -> Unit): Pair<String, (String) -> Unit> {
    val local = remember { mutableStateOf(external) }
    val sent = remember { mutableListOf<String>() }
    if (external == local.value) sent.clear()
    else if (external !in sent) { local.value = external; sent.clear() }
    return local.value to { v: String ->
        local.value = v
        sent += v
        if (sent.size > 64) sent.removeAt(0)
        onChange(v)
    }
}
