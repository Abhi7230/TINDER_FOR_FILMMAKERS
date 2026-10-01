package com.tff.app.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tff.app.R
import com.tff.app.domain.CardText
import com.tff.app.model.LinkKind
import com.tff.app.model.RoleStatus
import com.tff.app.model.RoleWithProject
import com.tff.app.model.WorkLink
import com.tff.app.ui.components.GeneratedPosterArt
import com.tff.app.ui.components.PayLine
import com.tff.app.ui.components.PosterChip
import com.tff.app.ui.components.RoleLabelRow
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.posterTexture
import com.tff.app.ui.components.youTubeThumbnail
import com.tff.app.ui.screens.onboarding.linkKindLabel
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

/** A reel / work-link tile: YouTube thumbnail via Coil, else a generated poster (demo links need no network). */
@Composable
fun WorkLinkTile(link: WorkLink, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    val thumb = youTubeThumbnail(link.url)
    val label = link.title ?: link.url.substringAfter("://").substringBefore('?').take(40)
    val cd = stringResource(R.string.ws7_me_open_link_cd, label)
    Column(modifier.width(220.dp).clip(CtShapes.card).background(c.surface).clickable(role = Role.Button, onClick = onOpen).semantics(mergeDescendants = true) { contentDescription = cd }) {
        Box(Modifier.fillMaxWidth().height(124.dp)) {
            GeneratedPosterArt(seed = link.url, Modifier.fillMaxSize())
            if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().posterTexture())
            PosterChip(linkKindLabel(link.kind).uppercase(), modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            if (link.kind == LinkKind.REEL) {
                Box(
                    Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.6f)).border(1.dp, c.text.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.text) }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(link.roleCode?.let { CardText.roleName(it) }, link.year?.toString()).joinToString(" · ")
            if (meta.isNotEmpty()) TimecodeText(meta.uppercase(), style = Ct.type.monoS, color = c.textMuted)
        }
    }
}

/** Compact saved-role row: role label, project, pay line, dates (mono). */
@Composable
fun SavedRoleRow(item: RoleWithProject, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = Ct.colors
    val closed = item.role.status != RoleStatus.OPEN
    Row(
        modifier
            .fillMaxWidth()
            .clip(CtShapes.card)
            .background(c.surface)
            .border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RoleLabelRow(item.role)
            Text(item.project.title, style = Ct.type.titleM, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            PayLine(runCatching { CardText.payLine(item.role, item.project) }.getOrDefault(""), paid = item.role.isPaid, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            TimecodeText(runCatching { CardText.whenLine(item.role, item.project) }.getOrDefault(""), style = Ct.type.monoS, color = c.textSecondary)
            Text(
                if (closed) stringResource(R.string.ws7_saved_closed) else runCatching { CardText.whereLine(item) }.getOrDefault(item.cityName),
                style = Ct.type.label, color = if (closed) c.onWarnContainer else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
    }
}

/** A navigation row (Safety Center, Settings, trust pages). 56dp tall. */
@Composable
fun NavRow(icon: ImageVector, title: String, body: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CtShapes.field).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ct.type.bodyStrong, color = c.text)
            if (body != null) Text(body, style = Ct.type.bodyS, color = c.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
    }
}

/** A tappable stat tile ("Saved roles · 3"). */
@Composable
fun StatTile(value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ct.colors
    Column(
        modifier.clip(CtShapes.field).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.7f), CtShapes.field)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(value, style = Ct.type.monoL.copy(fontSize = Ct.type.titleL.fontSize, lineHeight = Ct.type.titleL.lineHeight), color = c.text)
        Text(label, style = Ct.type.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
