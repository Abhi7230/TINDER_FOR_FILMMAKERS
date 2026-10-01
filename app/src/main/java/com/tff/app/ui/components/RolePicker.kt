package com.tff.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tff.app.R
import com.tff.app.model.Dept
import com.tff.app.model.RoleCode
import com.tff.app.model.RoleInfo
import com.tff.app.model.Taxonomy
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes

/** Single = radio (a role for a listing); Multi = checkboxes (roles I do, filters). */
enum class RolePickerMode { SINGLE, MULTI }

/**
 * Role picker (F3): the Appendix A taxonomy grouped by department, every row with its plain-language
 * duties line, plus a search field. Not lazy (29 rows) so it can sit inside a scrolling screen.
 *
 * @param tagFor optional tag on a row ("PRIMARY", "STEP UP") for onboarding's tap / long-press rules.
 * @param onLongPress optional long-press (onboarding: "step up to").
 */
@Composable
fun RolePicker(
    selected: Set<RoleCode>,
    onToggle: (RoleCode) -> Unit,
    modifier: Modifier = Modifier,
    mode: RolePickerMode = RolePickerMode.SINGLE,
    roles: List<RoleInfo> = Taxonomy.all,
    showSearch: Boolean = true,
    tagFor: (RoleCode) -> String? = { null },
    onLongPress: ((RoleCode) -> Unit)? = null,
    longPressLabel: String? = null,
) {
    val c = Ct.colors
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val deptNames = Dept.entries.associateWith { deptLabel(it) }
    val matches = if (q.isEmpty()) roles else roles.filter { r ->
        r.label.lowercase().contains(q) || r.duties.lowercase().contains(q) ||
            shortRoleLabel(r.code).lowercase().contains(q) || deptNames.getValue(r.dept).lowercase().contains(q) ||
            r.scopeSuggestions.any { it.lowercase().contains(q) }
    }
    val groups = Dept.entries.mapNotNull { d -> matches.filter { it.dept == d }.takeIf { it.isNotEmpty() }?.let { d to it } }

    Column(modifier.fillMaxWidth()) {
        if (showSearch) {
            CtTextField(
                value = query,
                onValueChange = { query = it },
                label = null,
                placeholder = stringResource(R.string.ws1_role_search),
                singleLine = true,
                leadingIcon = Icons.Rounded.Search,
                maxLength = 40,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailing = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ws1_clear), tint = c.textSecondary) }
                }) else null,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (groups.isEmpty()) {
            Text(
                stringResource(R.string.ws1_role_none, query.trim()), style = Ct.type.bodyS, color = c.textSecondary,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
        groups.forEach { (dept, list) ->
            Text(
                deptNames.getValue(dept).uppercase(),
                style = Ct.type.slate.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = c.textMuted,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
            )
            Column(
                Modifier.fillMaxWidth().clip(CtShapes.field).background(c.surface).border(1.dp, c.outline.copy(alpha = 0.8f), CtShapes.field),
            ) {
                list.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp, modifier = Modifier.padding(start = 52.dp))
                    RoleRow(r, r.code in selected, mode, tagFor(r.code), onClick = { onToggle(r.code) }, onLongPress = onLongPress?.let { { it(r.code) } }, longPressLabel)
                }
            }
        }
    }
}

@Composable
private fun RoleRow(
    info: RoleInfo,
    selected: Boolean,
    mode: RolePickerMode,
    tag: String?,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    longPressLabel: String?,
) {
    val c = Ct.colors
    val bg by animateColorAsState(if (selected) c.accent.copy(alpha = 0.08f) else c.surface, label = "roleBg")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(bg)
            .combinedClickable(
                role = if (mode == RolePickerMode.SINGLE) Role.RadioButton else Role.Checkbox,
                onLongClick = onLongPress,
                onLongClickLabel = longPressLabel,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Indicator(selected, mode)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(info.label, style = Ct.type.bodyStrong.copy(fontSize = 15.sp, lineHeight = 20.sp), color = if (selected) c.accent else c.text)
            Text(info.duties, style = Ct.type.bodyS.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (tag != null) {
            Spacer(Modifier.width(8.dp))
            CtChip(tag, tone = ChipTone.ACCENT, filled = true, style = ChipStyle.CAPS)
        }
    }
}

@Composable
private fun Indicator(selected: Boolean, mode: RolePickerMode) {
    val c = Ct.colors
    val shape = if (mode == RolePickerMode.SINGLE) CircleShape else RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(22.dp)
            .clip(shape)
            .background(if (selected && mode == RolePickerMode.MULTI) c.accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(2.dp, if (selected) c.accent else c.textMuted, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            if (mode == RolePickerMode.SINGLE) Box(Modifier.size(11.dp).clip(CircleShape).background(c.accent))
            else Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}
