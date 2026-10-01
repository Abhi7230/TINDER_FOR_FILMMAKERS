package com.tff.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.di.AppContainer
import com.tff.app.model.AppMode
import com.tff.app.ui.theme.Ct

/**
 * The app's top bar. Draws behind the status bar and pads itself ([statusBarInsets] = false when a
 * parent Scaffold already did). Title in Inter 600 with an optional mono-friendly [subtitle] (density
 * header "Bengaluru · 128 people · 19 open projects"), a back arrow when [onBack] is set, [actions]
 * (use [TopBarAction]) and, in demo mode, the thin Tungsten "DEMO" tag at the far right (§8.0).
 * [bottom] hosts a segmented control or tabs under the title row.
 */
@Composable
fun CtTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    statusBarInsets: Boolean = true,
    divider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
) {
    val c = Ct.colors
    val demo = !LocalInspectionMode.current && runCatching { AppContainer.repo.mode == AppMode.DEMO }.getOrDefault(false)
    Column(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .then(if (statusBarInsets) Modifier.statusBarsPadding() else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ws1_back), tint = c.text)
                }
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    title, style = Ct.type.titleM, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        monoNumbers(subtitle, Ct.type.mono), style = Ct.type.bodyS.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
            if (demo) {
                Spacer(Modifier.width(4.dp))
                DemoTag(Modifier.padding(end = 8.dp))
            }
        }
        if (bottom != null) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { bottom() }
        }
        if (divider) HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
    }
}

/** 48dp icon action for [CtTopBar] with a required content description. */
@Composable
fun TopBarAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = Ct.colors.text, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else Ct.colors.textMuted)
    }
}

/** The thin Tungsten "DEMO" tag (§8.0 demo ribbon). */
@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    val c = Ct.colors
    val cd = stringResource(R.string.ws1_demo_tag_cd)
    val shape = RoundedCornerShape(4.dp)
    Text(
        stringResource(R.string.ws1_demo_tag),
        style = Ct.type.monoS.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.5.sp),
        color = c.accent,
        modifier = modifier
            .clearAndSetSemantics { contentDescription = cd }
            .border(1.dp, c.accent.copy(alpha = 0.7f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
