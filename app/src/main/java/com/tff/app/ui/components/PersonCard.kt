package com.tff.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.model.ApplicantView
import com.tff.app.model.ApplicationOrigin
import com.tff.app.model.LinkKind
import com.tff.app.model.Level
import com.tff.app.model.RoleCode
import com.tff.app.model.Taxonomy
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import kotlin.math.roundToInt

/**
 * Crew-up Person Card (PRD §6.5): reel band · role + level (+ step-up chip) · name + badges · fit line ·
 * reason chips · their note and screening answer · footer. Work first: crew are never shown by face.
 * For cast roles ([roleCode] in CAST) the band carries a 30% portrait inset — the applicant's headshot
 * when [ApplicantView.headshotUrl] is present (Applicants only), else a silhouette.
 *
 * [currency] formats the kit fee ("Kit ₹1,500/day"; without it the line says "Has kit").
 * [unpaidRole] switches the money part of the fit line to "Open to unpaid · conditions met".
 */
@Composable
fun PersonCard(
    view: ApplicantView,
    roleCode: RoleCode,
    modifier: Modifier = Modifier,
    currency: String? = null,
    unpaidRole: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val p = view.card.profile
    val c = Ct.colors
    val isCast = Taxonomy.isCast(roleCode)
    val openLabel = stringResource(R.string.ws1_open_details)
    PriorityColumn(
        modifier
            .clip(CtShapes.card)
            .background(c.surface)
            .border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick) else Modifier),
    ) {
        ReelBand(view, isCast)

        // 2 · ROLE · LEVEL + step-up chip
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val level = safeText({ levelFallback(p.level) }) { CardText.levelLabel(p.level) }
            Text(
                "${shortRoleLabel(p.primaryRole).uppercase()} · ${level.uppercase()}",
                style = Ct.type.slate, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            p.stepUpRoles.firstOrNull()?.let { up ->
                CtChip("→ ${shortRoleLabel(up)}", tone = ChipTone.GO, style = ChipStyle.CAPS, contentDescription = stringResource(R.string.ws1_step_up_cd, Taxonomy.label(up)))
            }
        }

        // 3 · Name + badges
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.shortName, style = Ct.type.titleL, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (AppClock.nowMillis() - p.lastActiveAt in 0 until 24 * 3_600_000L) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(c.go))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.ws1_active_today), style = Ct.type.label, color = c.textSecondary, maxLines = 1)
            }
        }
        VerificationBadges(p, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).layoutId(PRIORITY_CRED), interactive = false)

        // 4 · Fit line (mono)
        Text(
            monoNumbers(fitLine(view, currency, unpaidRole), Ct.type.mono),
            style = Ct.type.bodyS, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )

        // 5 · Reason chips (top 3 from the ranker)
        if (view.reasons.isNotEmpty()) {
            OverflowChipRow(
                view.reasons.take(3).map { r -> { TrackRecordChip(r, interactive = false, small = true) } },
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                maxLines = 2, small = true,
            )
        }

        // 6 · Their note and screening answer
        val note = view.application?.note?.takeIf { it.isNotBlank() }
        if (note != null) {
            Text(
                "“$note”", style = Ct.type.bodyS.copy(fontSize = 15.sp, lineHeight = 21.sp), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).layoutId(PRIORITY_LOGLINE),
            )
        }
        val answer = view.application?.screeningAnswer?.takeIf { it.isNotBlank() }
        if (answer != null) {
            Text(
                monoNumbers("A: $answer", Ct.type.mono), style = Ct.type.bodyS, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp).layoutId(PRIORITY_FLAGS),
            )
        }

        // 7 · Footer
        val app = view.application
        val footer = when {
            app == null -> stringResource(R.string.ws1_scout_not_applied)
            app.origin == ApplicationOrigin.INVITED -> stringResource(R.string.ws1_asked_ago, Dates.ago(app.createdAt, AppClock.nowMillis()))
            else -> stringResource(R.string.ws1_applied_ago, Dates.ago(app.createdAt, AppClock.nowMillis()))
        }
        CardFooter(footer, Modifier.layoutId(FOOTER))
    }
}

@Composable
private fun ReelBand(view: ApplicantView, isCast: Boolean) {
    val p = view.card.profile
    val c = Ct.colors
    val reel = remember(p.workLinks) { p.workLinks.firstOrNull { it.kind == LinkKind.REEL } }
    val thumb = remember(reel) { youTubeThumbnail(reel?.url) }
    val reelCd = stringResource(R.string.ws1_reel_cd, p.shortName)
    Row(Modifier.fillMaxWidth().aspectRatio(SCOPE_RATIO)) {
        if (isCast) {
            val headshot = view.headshotUrl
            val cd = if (headshot != null) stringResource(R.string.ws1_headshot_cd, p.shortName) else stringResource(R.string.ws1_no_headshot_cd)
            Box(
                Modifier.fillMaxHeight().weight(0.3f).background(c.surfaceHigh).semantics { contentDescription = cd },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(56.dp))
                if (headshot != null) {
                    AsyncImage(model = headshot, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(c.bg))
        }
        Box(Modifier.fillMaxHeight().weight(if (isCast) 0.7f else 1f).semantics { contentDescription = if (reel != null) reelCd else "" }) {
            GeneratedPosterArt(seed = p.displayName, Modifier.fillMaxSize())
            if (thumb != null) {
                AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().posterTexture())
                Box(
                    Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.6f)).border(1.dp, c.text.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.text, modifier = Modifier.size(26.dp)) }
                reel?.title?.let { t ->
                    PosterChip(t.uppercase(), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Monogram(p.initials, size = 52.dp, ring = c.bg.copy(alpha = 0.5f))
                    Spacer(Modifier.size(8.dp))
                    // A reel that isn't on YouTube has no thumbnail we can fetch, but it is still linked.
                    val label = when {
                        reel == null -> stringResource(R.string.ws1_no_reel)
                        reel.title != null -> reel.title
                        else -> stringResource(R.string.ws1_reel_linked, reelHost(reel.url))
                    }
                    Text(label.uppercase(), style = Ct.type.monoS, color = c.text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val km = view.card.distanceKm
            val chip = when {
                km != null -> "${km.roundToInt().coerceAtLeast(1)} KM"
                p.remoteOk -> stringResource(R.string.ws1_badge_remote)
                else -> null
            }
            if (chip != null) PosterChip(chip, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

@Composable
private fun fitLine(view: ApplicantView, currency: String?, unpaidRole: Boolean): String {
    val p = view.card.profile
    val parts = mutableListOf<String>()
    if (view.totalDays > 0) parts += pluralStringResource(R.plurals.ws1_days_fit, view.totalDays, view.availDays, view.totalDays)
    view.card.distanceKm?.let { parts += "${it.roundToInt().coerceAtLeast(1)} km" } ?: run { if (p.remoteOk) parts += stringResource(R.string.ws1_remote_ok) }
    val kit = p.kitFeeDay
    if (p.gear.isNotEmpty()) {
        parts += if (kit != null && currency != null) stringResource(R.string.ws1_kit_day, Money.format(kit, currency)) else stringResource(R.string.ws1_has_kit)
    }
    if (unpaidRole) parts += stringResource(R.string.ws1_open_to_unpaid_ok)
    else if (view.rateFit >= 0.999) parts += stringResource(R.string.ws1_rate_in_budget)
    return parts.joinToString(" · ")
}

private fun levelFallback(l: Level): String = when (l) {
    Level.FIRST_PROJECT -> "First project"
    Level.BUILDING_REEL -> "Building a reel"
    Level.CREDITED_INDIE -> "Credited indie"
    Level.WORKING_PRO -> "Working pro"
}

/** "vimeo.com" from a work link, for the reel band when there's no thumbnail to show. */
private fun reelHost(url: String): String =
    url.substringAfter("://").substringBefore("/").removePrefix("www.").ifBlank { "link" }
