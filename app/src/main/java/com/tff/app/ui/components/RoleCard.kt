package com.tff.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.domain.DeckCard
import com.tff.app.domain.ReputationCalc
import com.tff.app.model.AuditionMode
import com.tff.app.model.CompType
import com.tff.app.model.ContentFlag
import com.tff.app.model.GearPolicy
import com.tff.app.model.Level
import com.tff.app.model.LocationType
import com.tff.app.model.Project
import com.tff.app.model.ProjectRole
import com.tff.app.model.RoleWithProject
import com.tff.app.model.ScriptStatus
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlin.math.roundToInt

/**
 * The Gigs-deck Role Card (PRD §6.5), top to bottom: poster band · role label + badges · project title ·
 * pay line · when/where (mono) · credibility row · logline · flags row · footer. All copy comes from
 * domain [CardText] so the card, role detail and share text always agree.
 *
 * In a bounded height (a deck) the footer pins to the bottom and, if space runs short, rows drop in
 * this order: flags, logline, credibility. At font scale ≥ 1.3 the flags row is always dropped.
 */
@Composable
fun RoleCard(
    card: DeckCard,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val item = card.item
    val role = item.role
    val project = item.project
    val c = Ct.colors
    val fontScale = LocalDensity.current.fontScale
    val remoteLabel = stringResource(R.string.ws1_badge_remote)
    val outside = stringResource(R.string.ws1_outside_radius)
    val topRight = when {
        role.remoteOk && card.distanceKm == null -> remoteLabel
        card.distanceKm != null -> "${card.distanceKm.roundToInt().coerceAtLeast(1)} KM" + if (card.outsideRadius) " · $outside" else ""
        role.remoteOk -> remoteLabel
        else -> null
    }
    val openLabel = stringResource(R.string.ws1_open_details)
    PriorityColumn(
        modifier
            .clip(CtShapes.card)
            .background(c.surface)
            .border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick) else Modifier),
    ) {
        PosterBand(project, topRight = topRight, topRightTone = if (card.outsideRadius) ChipTone.WARN else ChipTone.NEUTRAL)

        // 2 · Role label + badges
        RoleLabelRow(role, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))

        // 3 · Project title
        Text(
            project.title,
            style = Ct.type.titleL, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
        )

        // 4 · Pay line, first and loudest after the title
        PayLine(
            text = payLineText(role, project),
            paid = role.isPaid,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )

        // 5 · When and where (mono)
        Text(
            whenLineText(role, project),
            style = Ct.type.monoS, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )
        Text(
            whereLineText(item),
            style = Ct.type.monoS, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
        )

        // 6 · Credibility row (never wraps; overflows into "+2")
        CredibilityRow(item, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).layoutId(PRIORITY_CRED))

        // 7 · Logline (2 lines)
        Text(
            project.logline,
            style = Ct.type.bodyS, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).layoutId(PRIORITY_LOGLINE),
        )

        // 8 · Flags row (only if any)
        val flags = flagChips(role, project)
        if (flags.isNotEmpty() && fontScale < 1.3f) {
            OverflowChipRow(flags, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp).layoutId(PRIORITY_FLAGS))
        }

        // 9 · Footer (muted), pinned to the bottom in a deck
        Text(
            monoNumbers(footerText(item), Ct.type.mono),
            style = Ct.type.label, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp).layoutId(FOOTER),
        )
    }
}

/** "SOUND MIXER ×1" in Barlow caps Tungsten, then STEP-UP OK / BEGINNERS WELCOME badges. */
@Composable
fun RoleLabelRow(role: ProjectRole, modifier: Modifier = Modifier) {
    val label = safeText({ "${shortRoleLabel(role.roleCode).uppercase()} ×${role.count}" }) { CardText.roleLabel(role) }
    val badges = buildList {
        if (role.stepUpFriendly) add(CardBadge.STEP_UP_OK)
        if (role.levelWanted == Level.FIRST_PROJECT || role.levelWanted == Level.BUILDING_REEL) add(CardBadge.BEGINNERS_WELCOME)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Ct.type.slate, color = Ct.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (badges.isNotEmpty()) OverflowChipRow(badges.map { b -> { BadgeChip(b) } })
    }
}

/**
 * The pay line (PRD §5.4, §6.5 item 4): Inter 600, Greenlit when the role is PAID. Pass the text from
 * [CardText.payLine]: "₹2,500/day + ₹1,000 kit · paid on wrap", "Stipend ₹800/day · 2 meals",
 * "Unpaid · 2 meals · your scenes in 6 wks". Amounts render in mono.
 */
@Composable
fun PayLine(
    text: String,
    paid: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = Ct.type.bodyStrong,
    maxLines: Int = 2,
) {
    Text(
        monoNumbers(text, Ct.type.mono),
        style = style,
        color = if (paid) Ct.colors.go else Ct.colors.text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// ───────────────────────── Card text (CardText with safe fallbacks) ─────────────────────────

internal fun payLineText(role: ProjectRole, project: Project): String = safeText({
    when (role.compType) {
        CompType.UNPAID -> "Unpaid"
        CompType.STIPEND -> "Stipend " + Money.range(role.amountMin, role.amountMax, role.currency) + "/day"
        CompType.PAID_DAY -> Money.range(role.amountMin, role.amountMax, role.currency) + "/day"
        CompType.PAID_FLAT -> Money.range(role.amountMin, role.amountMax, role.currency) + " flat"
    }
}) { CardText.payLine(role, project) }

internal fun whenLineText(role: ProjectRole, project: Project): String = safeText({
    val s = project.shootStart
    val e = project.shootEnd
    when {
        role.isPostRole -> "REMOTE · DUE " + (role.postDeadline?.let { Dates.dayMonth(it).uppercase() } ?: "")
        s != null && e != null -> Dates.span(s, e).uppercase() + " · ${project.shootDays} DAYS"
        else -> "DATES TBC"
    }
}) { CardText.whenLine(role, project) }

internal fun whereLineText(item: RoleWithProject): String =
    safeText({ "${item.areaName}, ${item.cityName}" }) { CardText.whereLine(item) }

internal fun footerText(item: RoleWithProject): String = safeText({
    "${item.role.status.name.lowercase().replaceFirstChar { it.uppercase() }} · ${item.role.applicationsCount}/${item.role.applicationCap} applied"
}) { CardText.footer(item) }

internal fun contentFlagText(f: ContentFlag): String =
    safeText({ f.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } }) { CardText.contentFlagLabel(f) }

// ───────────────────────── Rows ─────────────────────────

@Composable
private fun CredibilityRow(item: RoleWithProject, modifier: Modifier) {
    val owner = item.owner
    val rep = owner.reputation
    val idLabel = stringResource(R.string.ws1_badge_id)
    val idTip = stringResource(R.string.ws1_tip_id)
    val newLabel = stringResource(R.string.ws1_badge_new)
    val headline = safeText({ if (rep.isNew) newLabel else "" }) { ReputationCalc.headlineChip(rep, asOwner = true) }
    val attached = item.team.filterNot { it.isOwner }
    val chips = buildList<@Composable () -> Unit> {
        if (owner.idVerified) add { CtChip(idLabel, tone = ChipTone.INFO, filled = true, icon = Icons.Rounded.Badge, contentDescription = "$idLabel. $idTip") }
        if (rep.shootsCompleted > 0) {
            val n = rep.shootsCompleted
            add { CtChip(pluralStringResource(R.plurals.ws1_shoots_done, n, n), filled = true, monoNumbers = true) }
        }
        if (headline.isNotBlank()) add { TrackRecordChip(headline, newLabel) }
        if (item.project.scriptStatus == ScriptStatus.LOCKED) add { CtChip(stringResource(R.string.ws1_script_locked), filled = true, icon = Icons.Rounded.Description) }
        if (item.project.honestZeroBudget) add { CtChip(stringResource(R.string.ws1_honest_zero), filled = true) }
        if (attached.isNotEmpty()) add { TeamMonograms(attached, compact = true) }
    }
    if (chips.isNotEmpty()) OverflowChipRow(chips, modifier)
}

@Composable
private fun flagChips(role: ProjectRole, project: Project): List<@Composable () -> Unit> = buildList<@Composable () -> Unit> {
    project.contentFlags.sortedBy { it.ordinal }.forEach { f ->
        val text = contentFlagText(f)
        add { CtChip(text, tone = ChipTone.WARN, icon = contentFlagIcon(f)) }
    }
    if (project.shootLocationType == LocationType.PRIVATE_HOME) {
        add { CtChip(stringResource(R.string.ws1_private_home), tone = ChipTone.WARN, icon = Icons.Rounded.Home) }
    } else if (project.bringFriendWelcome) {
        add { CtChip(stringResource(R.string.ws1_friend_welcome), icon = Icons.Rounded.Home) }
    }
    if (role.isCast) {
        project.auditionMode?.let { m ->
            val res = when (m) {
                AuditionMode.SELF_TAPE -> R.string.ws1_audition_self_tape
                AuditionMode.VIDEO_CALL -> R.string.ws1_audition_video
                AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> R.string.ws1_audition_studio
            }
            add { CtChip(stringResource(res), icon = Icons.Rounded.Videocam) }
        }
    }
    if (role.gearPolicy == GearPolicy.BYO_NO_FEE) add { CtChip(stringResource(R.string.ws1_own_gear), tone = ChipTone.WARN, icon = Icons.Rounded.Work) }
    if (project.rescheduleCount >= 2) add { CtChip(stringResource(R.string.ws1_rescheduled, project.rescheduleCount), tone = ChipTone.STOP, icon = Icons.Rounded.EventRepeat, monoNumbers = true) }
}

/** Icon for a content flag (Face icons, §6.2). */
fun contentFlagIcon(f: ContentFlag): ImageVector = when (f) {
    ContentFlag.NUDITY, ContentFlag.SIMULATED_SEX, ContentFlag.INTIMACY_SCENE -> Icons.Rounded.PrivacyTip
    ContentFlag.STUNTS_FIGHTS -> Icons.Rounded.SportsMma
    ContentFlag.FIREARMS_PROPS -> Icons.Rounded.GppMaybe
    ContentFlag.MINORS_ON_SET -> Icons.Rounded.ChildCare
    ContentFlag.NIGHT_SHOOT -> Icons.Rounded.DarkMode
    ContentFlag.WATER -> Icons.Rounded.Water
    ContentFlag.ANIMALS -> Icons.Rounded.Pets
    ContentFlag.GORE -> Icons.Rounded.Bloodtype
}

// ───────────────────────── Priority layout ─────────────────────────

internal const val FOOTER = "footer"
internal const val PRIORITY_CRED = 1
internal const val PRIORITY_LOGLINE = 2
internal const val PRIORITY_FLAGS = 3

/**
 * A column for card faces. Unbounded: a plain column. Bounded (inside a deck): the [FOOTER] child is
 * pinned to the bottom and children tagged with an Int priority are dropped, highest number first,
 * until everything fits. Untagged children are never dropped.
 */
@Composable
internal fun PriorityColumn(modifier: Modifier = Modifier, spacing: Dp = 0.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val ids = measurables.map { it.layoutId }
        val gap = spacing.roundToPx()
        val keep = BooleanArray(placeables.size) { true }
        fun total(): Int {
            var h = 0
            var n = 0
            placeables.forEachIndexed { i, p -> if (keep[i]) { h += p.height; n++ } }
            return h + gap * (n - 1).coerceAtLeast(0)
        }
        if (constraints.hasBoundedHeight) {
            val order = ids.indices.filter { ids[it] is Int }.sortedByDescending { ids[it] as Int }
            for (i in order) {
                if (total() <= constraints.maxHeight) break
                keep[i] = false
            }
        }
        val content = total()
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight.coerceAtLeast(constraints.minHeight) else content.coerceAtLeast(constraints.minHeight)
        val width = constraints.maxWidth
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { i, p ->
                if (!keep[i]) return@forEachIndexed
                val isFooter = ids[i] == FOOTER
                val py = if (isFooter && constraints.hasBoundedHeight) maxOf(y, height - p.height) else y
                p.placeRelative(0, py)
                y = py + p.height + gap
            }
        }
    }
}
