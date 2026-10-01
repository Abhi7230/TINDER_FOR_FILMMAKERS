package com.tff.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.tff.app.model.ApplicationStatus
import com.tff.app.model.MatchState
import com.tff.app.model.ProjectStatus
import com.tff.app.model.ReportTarget
import com.tff.app.model.RoleCode
import com.tff.app.model.RoleStatus
import com.tff.app.model.WrapStatus
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Every design-system component with local sample data ([GallerySamples]). Debug route "gallery"
 * (`calltime://gallery`). Section chips at the top jump to each group.
 */
@Composable
fun ComponentGallery(nav: AppNavigator) {
    val c = Ct.colors
    val sections = listOf("Type", "Buttons", "Chips", "Cards", "Deck", "Crew up", "Overlays", "States", "Rows", "Sheets", "Forms")
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var slate by remember { mutableStateOf(false) }
    var booked by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(
                title = "Components",
                subtitle = "Darkroom design system · 11 groups",
                onBack = { nav.back() },
                divider = true,
                actions = { TopBarAction(Icons.Rounded.Share, "Share", { nav.toast("Share sheet opens here") }) },
                bottom = {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(sections) { i, s ->
                            CtChip(s, onClick = { scope.launch { listState.scrollToItem(i) } }, filled = true)
                        }
                    }
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                item("type") { Group("Type & colour") { TypeSamples() } }
                item("buttons") { Group("Buttons") { ButtonSamples(nav) } }
                item("chips") { Group("Chips & badges") { ChipSamples() } }
                item("cards") { Group("Cards") { CardSamples(nav) } }
                item("deck") { Group("Swipe deck · Gigs") { DeckSample(nav) } }
                item("crewup") { Group("Swipe deck · Crew up") { CrewUpSample(nav, onTalk = { slate = true }) } }
                item("overlays") { Group("Overlays") { OverlaySamples(onSlate = { slate = true }, onBooked = { booked = true }) } }
                item("states") { Group("States") { StateSamples(nav) } }
                item("rows") { Group("Rows & labels") { RowSamples(nav) } }
                item("sheets") { Group("Sheets") { SheetSamples(nav) } }
                item("forms") { Group("Forms") { FormSamples() } }
                item("end") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
        SlateOverlay(
            visible = slate,
            subtitle = "Dana R. · Sound Mixer · Filter Coffee, 4 AM",
            onDismiss = { slate = false },
        )
        BookedOverlay(
            visible = booked,
            onDismiss = { booked = false },
            subtitle = "Sound Mixer · Filter Coffee, 4 AM",
            callLine = "CALL SAT 14 NOV · 07:30",
            actions = {
                SecondaryButton("Add to calendar", { nav.toast("Calendar") }, Modifier.weight(1f))
                PrimaryButton("Day sheet", { booked = false }, Modifier.weight(1f))
            },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
        Text(title.uppercase(), style = Ct.type.displayM, color = Ct.colors.text)
        Spacer(Modifier.height(4.dp))
        SprocketDivider(height = 10.dp)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Ct.type.monoS, color = Ct.colors.textMuted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

// ───────────────────────── Type & colour ─────────────────────────

@Composable
private fun TypeSamples() {
    val t = Ct.type
    val c = Ct.colors
    val rows: List<Pair<String, TextStyle>> = listOf(
        "displayL · IN TALKS" to t.displayL, "displayM · BOOKED" to t.displayM, "titleL · Filter Coffee, 4 AM" to t.titleL,
        "SLATE · SOUND MIXER ×1" to t.slate, "titleM · Who's posting" to t.titleM, "body · Every applicant gets an answer." to t.body,
        "bodyS · Approved clips of your scenes, graded." to t.bodyS, "label · Posted 2d ago" to t.label,
        "mono · SAT 14 NOV · 07:30" to t.mono, "monoL · 07:30" to t.monoL,
    )
    rows.forEach { (s, style) ->
        Text(s, style = style, color = if (style == t.slate) c.accent else c.text, modifier = Modifier.padding(vertical = 3.dp))
    }
    Label("COLOUR TOKENS")
    val swatches = listOf(
        "bg" to c.bg, "surface" to c.surface, "raised" to c.surfaceRaised, "high" to c.surfaceHigh, "outline" to c.outline,
        "Paper" to c.text, "Tungsten" to c.accent, "Greenlit" to c.go, "Safelight" to c.stop, "Daylight" to c.info,
        "danger" to c.dangerContainer, "info" to c.infoContainer, "warn" to c.warnContainer,
    )
    ChipFlow(spacing = 10.dp, lineSpacing = 10.dp) {
        swatches.forEach { (name, color) -> Swatch(name, color) }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CtShapes.chip).background(color).border(1.dp, Ct.colors.outline, CtShapes.chip))
        Text(name, style = Ct.type.label, color = Ct.colors.textSecondary)
    }
}

// ───────────────────────── Buttons ─────────────────────────

@Composable
private fun ButtonSamples(nav: AppNavigator) {
    var loading by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("Send booking card", { loading = !loading }, Modifier.fillMaxWidth(), loading = loading)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Ask to change", { nav.toast("Ask to change") }, Modifier.weight(1f))
            PrimaryButton("Accept & book", { nav.toast("Booked") }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("See saved", { nav.toast("Saved") })
            DangerButton("Withdraw", { nav.toast("Withdraw") }, Modifier.weight(1f))
        }
        PrimaryButton("Publish", {}, Modifier.fillMaxWidth(), enabled = false)
    }
    Label("DECK ACTIONS · GIGS")
    DeckActionBar(listOf(DeckAction.PASS, DeckAction.SAVE, DeckAction.INTERESTED), { nav.toast(it.name) })
    Label("DECK ACTIONS · APPLICANTS / SCOUT")
    DeckActionBar(listOf(DeckAction.NOT_THIS_TIME, DeckAction.BACKUP, DeckAction.LETS_TALK), { nav.toast(it.name) })
    DeckActionBar(listOf(DeckAction.SKIP, DeckAction.SEND_ASK), { nav.toast(it.name) })
}

// ───────────────────────── Chips ─────────────────────────

@Composable
private fun ChipSamples() {
    Label("CtChip · tones, outline and filled")
    ChipFlow {
        ChipTone.entries.forEach { CtChip(it.name.lowercase(), tone = it) }
    }
    Spacer(Modifier.height(8.dp))
    ChipFlow {
        ChipTone.entries.forEach { CtChip(it.name.lowercase(), tone = it, filled = true) }
    }
    Spacer(Modifier.height(8.dp))
    ChipFlow {
        CtChip("Kept bookings 6/6", filled = true, monoNumbers = true)
        CtChip("18 left today", style = ChipStyle.MONO)
        CtChip("2 meals", icon = Icons.Rounded.Restaurant)
        CtChip("Filters", icon = Icons.Rounded.Tune, onClick = {})
    }
    Label("StatusChip · match / application / wrap / project / role")
    ChipFlow {
        MatchState.entries.forEach { StatusChip(it) }
    }
    Spacer(Modifier.height(8.dp))
    ChipFlow {
        ApplicationStatus.entries.forEach { StatusChip(it) }
    }
    Spacer(Modifier.height(8.dp))
    ChipFlow {
        WrapStatus.entries.forEach { StatusChip(it, overdue = false) }
        StatusChip(WrapStatus.OPEN, overdue = true)
    }
    Spacer(Modifier.height(8.dp))
    ChipFlow {
        ProjectStatus.entries.forEach { StatusChip(it) }
        RoleStatus.entries.forEach { StatusChip(it) }
    }
    Label("BadgeChip · VerificationBadges (tap for the exact wording)")
    ChipFlow { CardBadge.entries.forEach { BadgeChip(it) } }
    Spacer(Modifier.height(8.dp))
    VerificationBadges(emailVerified = true, phoneVerified = true, idVerified = true)
    Label("TrackRecordChips · owner / new / weak number")
    TrackRecordChips(GallerySamples.rohan.reputation, asOwner = true)
    Spacer(Modifier.height(8.dp))
    TrackRecordChips(GallerySamples.newOwner.reputation, asOwner = true)
    Spacer(Modifier.height(8.dp))
    TrackRecordChips(GallerySamples.lateOwner.reputation, asOwner = true, singleLine = true)
    Label("FilterChip · OverflowChipRow")
    var sel by remember { mutableStateOf(setOf("Kannada")) }
    ChipFlow(lineSpacing = 0.dp) {
        listOf("Kannada", "English", "Hindi").forEach { l -> CtFilterChip(l, l in sel, { sel = if (l in sel) sel - l else sel + l }) }
    }
    OverflowChipRow(
        List(9) { i -> { CtChip(listOf("Night shoot", "Water", "Stunts", "Friend welcome", "Self-tape audition", "Animals", "Gore", "Minors", "Firearms")[i], tone = ChipTone.WARN) } },
        Modifier.fillMaxWidth(),
    )
}

// ───────────────────────── Cards ─────────────────────────

@Composable
private fun CardSamples(nav: AppNavigator) {
    Label("PosterBand · generated (3 looks) and YouTube thumbnail")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PosterBand(GallerySamples.filterCoffee, topRight = "4 KM", shape = CtShapes.card)
        PosterBand(GallerySamples.lastFerry, topRight = "REMOTE", shape = CtShapes.card)
        PosterBand(GallerySamples.chaiBreak, topRight = "18 KM", shape = CtShapes.card)
        PosterBand(GallerySamples.monsoon, topRight = "3 KM", shape = CtShapes.card)
    }
    Label("RoleCard · paid on-set")
    RoleCard(GallerySamples.deck[0], onClick = { nav.toast("Open role") })
    Label("RoleCard · unpaid cast, beginners welcome")
    RoleCard(GallerySamples.deck[1])
    Label("RoleCard · remote post role")
    RoleCard(GallerySamples.deck[2])
    Label("RoleCard · stipend, warnings, outside radius")
    RoleCard(GallerySamples.deck[3])
    Label("PersonCard · crew applicant")
    PersonCard(GallerySamples.dana, RoleCode.SOUND_MIXER, currency = "INR")
    Label("PersonCard · actor applicant (no headshot shared)")
    PersonCard(GallerySamples.meera, RoleCode.ACTOR, unpaidRole = true)
    Label("PersonCard · scout, step-up")
    PersonCard(GallerySamples.kabirScout, RoleCode.DP, currency = "INR")
    Label("PayLine")
    PayLine("₹2,500/day + ₹1,000 kit · paid on wrap", paid = true)
    PayLine("Stipend ₹800/day · 2 meals · TA ₹300", paid = false)
    PayLine("Unpaid · 2 meals · your scenes in 6 wks · step-up credit", paid = false)
}

// ───────────────────────── Decks ─────────────────────────

@Composable
private fun DeckSample(nav: AppNavigator) {
    val state = rememberSwipeDeckState<com.tff.app.domain.DeckCard>()
    val items = remember { mutableStateListOf(*GallerySamples.longDeck.toTypedArray()) }
    var likes by remember { mutableIntStateOf(18) }
    var last by remember { mutableStateOf("Drag a card, or use the buttons") }
    Column {
        SwipeDeck(
            items = items,
            key = { it.item.role.id },
            state = state,
            onSwiped = { card, dir ->
                if (dir == SwipeDirection.RIGHT) likes--
                last = "${dir.name} · ${card.item.project.title}"
            },
            onTap = { nav.toast("Open ${it.item.project.title}") },
            modifier = Modifier.fillMaxWidth().height(600.dp),
            empty = {
                EmptyState(
                    title = "You've seen everything near you.",
                    body = "We'll ping you when new roles land.",
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    PrimaryButton("See saved", { state.reset() })
                }
            },
        ) { card -> RoleCard(card, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(8.dp))
        DeckActionBar(
            listOf(DeckAction.PASS, DeckAction.SAVE, DeckAction.INTERESTED),
            onAction = { state.swipeAsync(it.direction) },
            state = state,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimecodeText("$likes left today", style = Ct.type.monoS, color = Ct.colors.textSecondary)
            Spacer(Modifier.weight(1f))
            if (state.canUndo) {
                CtChip("Undo", icon = Icons.AutoMirrored.Rounded.Undo, onClick = {
                    state.undoAsync { _, d -> if (d == SwipeDirection.RIGHT) likes++ }
                })
            }
        }
        Text(last, style = Ct.type.label, color = Ct.colors.textMuted)
        Label("Stamps (fade in past 8% of the width while dragging)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SwipeStamp("INTERESTED", Ct.colors.go)
            SwipeStamp("PASS", Ct.colors.stop)
            SwipeStamp("SAVE", Ct.colors.accent)
        }
    }
}

@Composable
private fun CrewUpSample(nav: AppNavigator, onTalk: () -> Unit) {
    val state = rememberSwipeDeckState<com.tff.app.model.ApplicantView>()
    Column {
        SwipeDeck(
            items = GallerySamples.people,
            key = { it.card.profile.id },
            state = state,
            labels = SwipeLabels.applicants(),
            onSwiped = { _, dir -> if (dir == SwipeDirection.RIGHT) onTalk() },
            modifier = Modifier.fillMaxWidth().height(560.dp),
            empty = {
                EmptyState("No applicants yet.", "Share the role to your WhatsApp groups.", Modifier.align(Alignment.Center), icon = Icons.Rounded.Inbox) {
                    PrimaryButton("Share this role", { state.reset() })
                }
            },
        ) { v -> PersonCard(v, if (v.card.profile.primaryRole == RoleCode.ACTOR) RoleCode.ACTOR else RoleCode.SOUND_MIXER, Modifier.fillMaxSize(), currency = "INR") }
        Spacer(Modifier.height(8.dp))
        DeckActionBar(
            listOf(DeckAction.NOT_THIS_TIME, DeckAction.BACKUP, DeckAction.LETS_TALK),
            onAction = { state.swipeAsync(it.direction) },
            state = state,
        )
    }
}

// ───────────────────────── Overlays ─────────────────────────

@Composable
private fun OverlaySamples(onSlate: () -> Unit, onBooked: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("IN TALKS", onSlate, Modifier.weight(1f))
        SecondaryButton("BOOKED", onBooked, Modifier.weight(1f))
    }
}

// ───────────────────────── States ─────────────────────────

@Composable
private fun StateSamples(nav: AppNavigator) {
    Label("EmptyState")
    Box(Modifier.fillMaxWidth().clip(CtShapes.card).background(Ct.colors.surface)) {
        EmptyState("Nothing in range yet.", "3 roles are just outside 15 km, and 12 remote roles are open.") {
            PrimaryButton("Widen radius", { nav.toast("Widen") })
            SecondaryButton("Include remote roles", { nav.toast("Remote") })
        }
    }
    Label("ErrorState")
    Box(Modifier.fillMaxWidth().clip(CtShapes.card).background(Ct.colors.surface)) {
        ErrorState(onRetry = { nav.toast("Retrying") })
    }
    Label("LoadingSkeleton · CARD / LIST")
    LoadingSkeleton(Modifier.fillMaxWidth().height(440.dp), kind = SkeletonKind.CARD)
    LoadingSkeleton(Modifier.fillMaxWidth(), kind = SkeletonKind.LIST, rows = 3)
}

// ───────────────────────── Rows ─────────────────────────

@Composable
private fun RowSamples(nav: AppNavigator) {
    SectionHeader("What you get", count = 3, action = "Edit", onAction = { nav.toast("Edit") })
    KeyValueRow("Shoot days", "SAT 14 – SUN 15 NOV · 2 DAYS", mono = true, divider = true)
    KeyValueRow("Hours cap", "≤10h a day", divider = true)
    KeyValueRow("Payer", "Rohan K.", divider = true, icon = Icons.Rounded.Payments)
    KeyValueRow("Kit fee", "₹1,000/day", mono = true, valueColor = Ct.colors.go, icon = Icons.Rounded.Work)
    Spacer(Modifier.height(12.dp))
    InfoTipCard(GallerySamples.TIP_CAST, title = "Before your audition", actionLabel = "Safety Center", onAction = { nav.toast("Safety") }, onDismiss = {})
    Spacer(Modifier.height(10.dp))
    SafetyBanner(GallerySamples.bannerMoney, onReport = { nav.toast("Report") }, onBlock = { nav.toast("Block") })
    Spacer(Modifier.height(10.dp))
    SafetyBanner(GallerySamples.bannerMeeting, {}, {})
    Spacer(Modifier.height(10.dp))
    SafetyBanner(GallerySamples.bannerOffPlatform, {}, {}, onLearnMore = { nav.toast("Why") })
    Label("Monogram · TeamMonograms")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Monogram("MK")
        Monogram("RK", highlight = true)
        Monogram("DR", size = 56.dp)
        Spacer(Modifier.width(6.dp))
        TeamMonograms(GallerySamples.soundMixer.team.filterNot { it.isOwner })
    }
    Label("TimecodeText · CallTimeDisplay")
    TimecodeText("SAT 14 NOV · GENERAL CALL 07:00 · WRAP BY 17:00", color = Ct.colors.textSecondary)
    Spacer(Modifier.height(8.dp))
    CallTimeDisplay("Your call", LocalTime.of(7, 30), caption = "SAT 14 NOV · DAY 1 OF 2")
    Label("StepHeader")
    StepHeader(2, 5, title = "When and where")
    Label("SprocketDivider")
    SprocketDivider()
}

// ───────────────────────── Sheets ─────────────────────────

@Composable
private fun SheetSamples(nav: AppNavigator) {
    val gate = rememberGuestGate(nav)
    var sheet by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("CtBottomSheet", { sheet = true }, Modifier.fillMaxWidth())
        SecondaryButton(if (gate.isGuest) "Guest action → sign-in prompt" else "Signed in: runs the action", { gate.require { nav.toast("Action ran") } }, Modifier.fillMaxWidth())
        SecondaryButton("ReportSheet", { report = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.MoreVert)
    }
    if (sheet) {
        CtBottomSheet(onDismiss = { sheet = false }, title = "Apply to Sound Mixer", subtitle = "Filter Coffee, 4 AM · ₹2,500/day + ₹1,000 kit") {
            var note by remember { mutableStateOf("") }
            CtTextField(note, { note = it }, label = "One line for the production (optional)", placeholder = "Why you, in a sentence", maxLength = 140)
            Spacer(Modifier.height(12.dp))
            ToggleRow("I'll bring my kit", checked = true, onCheckedChange = {})
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Send", { sheet = false; nav.toast("Sent. Every applicant gets an answer within 14 days.") }, Modifier.fillMaxWidth())
        }
    }
    if (report) ReportSheet(ReportTarget.ROLE, "r_fc_sound", onDismiss = { report = false })
}

// ───────────────────────── Forms ─────────────────────────

@Composable
private fun FormSamples() {
    var title by remember { mutableStateOf("Filter Coffee, 4 AM") }
    var logline by remember { mutableStateOf("") }
    var seg by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var remote by remember { mutableStateOf(true) }
    var radius by remember { mutableStateOf(15f) }
    var comp by remember { mutableStateOf<Int?>(1) }
    var count by remember { mutableIntStateOf(1) }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.of(2026, 11, 14)) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var langs by remember { mutableStateOf(setOf("Kannada", "English")) }
    var role by remember { mutableStateOf(setOf(RoleCode.SOUND_MIXER)) }
    var multi by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CtTextField(title, { title = it }, label = "Title", maxLength = 60, singleLine = true)
        CtTextField(logline, { logline = it }, label = "Logline", placeholder = "One or two sentences: who wants what", maxLength = 200, minLines = 2,
            errorText = if (logline.isNotEmpty() && logline.length < 20) "Add a little more: at least 20 characters." else null)
        SegmentedControl(listOf("Gigs", "Crew up"), seg, { seg = it })
        SegmentedControl(listOf("First project", "Reel", "Credited", "Pro"), level, { level = it })
        ToggleRow("Remote OK", remote, { remote = it }, subtitle = "Editors, composers and colorists work from anywhere.")
        SliderRow("Travel radius", radius, { radius = it }, 2f..50f, valueLabel = { "${it.toInt()} km" }, subtitle = "≈ 214 people and 31 projects within ${radius.toInt()} km")
        ChoiceCards(
            listOf(
                ChoiceOption("Paid only", "Only paid roles reach your deck."),
                ChoiceOption("Paid, and selective unpaid", "Unpaid roles only when they give you what you need."),
                ChoiceOption("Open to unpaid", "Show me everything that fits."),
            ),
            comp, { comp = it },
        )
        IntStepper(count, { count = it }, 1..10, label = "How many")
        DateField("First shoot day", date, { date = it }, minDate = LocalDate.of(2026, 10, 2))
        TimeField("Audition time", time, { time = it }, min = LocalTime.of(8, 0), max = LocalTime.of(21, 0), supportingText = "In-person auditions run 08:00–21:00.")
        MultiChipPicker(listOf("Kannada", "English", "Hindi", "Tamil", "Telugu", "Marathi", "Malayalam"), langs, { l -> langs = if (l in langs) langs - l else langs + l }, label = { it }, maxSelected = 4)
        SegmentedControl(listOf("Single", "Multi"), if (multi) 1 else 0, { multi = it == 1 })
        RolePicker(
            selected = role,
            onToggle = { r -> role = if (multi) (if (r in role) role - r else role + r) else setOf(r) },
            mode = if (multi) RolePickerMode.MULTI else RolePickerMode.SINGLE,
            tagFor = { if (it == role.firstOrNull()) "PRIMARY" else null },
        )
    }
}
