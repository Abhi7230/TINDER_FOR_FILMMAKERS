package com.tff.app.ui.screens.settings

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tff.app.R
import com.tff.app.model.TrustPage
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SprocketDivider
import com.tff.app.ui.screens.me.NavRow
import com.tff.app.ui.theme.Ct

private data class TrustSection(@StringRes val heading: Int, @StringRes val body: Int? = null, @ArrayRes val bullets: Int? = null, val numbered: Boolean = false, @StringRes val after: Int? = null)
private data class TrustLink(@StringRes val label: Int, val url: String)
private data class TrustContent(@StringRes val intro: Int, val sections: List<TrustSection>, val links: List<TrustLink> = emptyList())

private fun content(page: TrustPage): TrustContent = when (page) {
    TrustPage.HOW_WE_MAKE_MONEY -> TrustContent(
        R.string.ws7_tp_money_intro,
        listOf(
            TrustSection(R.string.ws7_tp_money_free_h, body = R.string.ws7_tp_money_free_b),
            TrustSection(R.string.ws7_tp_money_later_h, bullets = R.array.ws7_tp_money_later),
            TrustSection(R.string.ws7_tp_money_never_h, bullets = R.array.ws7_tp_money_never),
        ),
    )
    TrustPage.NO_GEN_AI -> TrustContent(
        R.string.ws7_tp_ai_intro,
        listOf(
            TrustSection(R.string.ws7_tp_ai_pledge_h, bullets = R.array.ws7_tp_ai_pledge),
            TrustSection(R.string.ws7_tp_ai_checks_h, body = R.string.ws7_tp_ai_checks_b),
            TrustSection(R.string.ws7_tp_ai_change_h, body = R.string.ws7_tp_ai_change_b),
        ),
    )
    TrustPage.COMMUNITY_RULES -> TrustContent(
        R.string.ws7_tp_rules_intro,
        listOf(
            TrustSection(R.string.ws7_tp_rules_h, bullets = R.array.ws7_rules_short, numbered = true),
            TrustSection(R.string.ws7_tp_rules_break_h, body = R.string.ws7_tp_rules_break_b),
        ),
    )
    TrustPage.PRIVACY -> TrustContent(
        R.string.ws7_tp_privacy_intro,
        listOf(
            TrustSection(R.string.ws7_tp_privacy_see_h, body = R.string.ws7_tp_privacy_see_b),
            TrustSection(R.string.ws7_tp_privacy_hidden_h, bullets = R.array.ws7_tp_privacy_hidden),
            TrustSection(R.string.ws7_tp_privacy_places_h, body = R.string.ws7_tp_privacy_places_b),
            TrustSection(R.string.ws7_tp_privacy_controls_h, body = R.string.ws7_tp_privacy_controls_b),
        ),
    )
    TrustPage.LICENCES -> TrustContent(
        R.string.ws7_tp_lic_intro,
        listOf(
            TrustSection(R.string.ws7_tp_lic_fonts_h, body = R.string.ws7_tp_lic_fonts_b),
            TrustSection(R.string.ws7_tp_lic_code_h, bullets = R.array.ws7_tp_lic_code),
        ),
        links = listOf(
            TrustLink(R.string.ws7_tp_lic_ofl_link, "https://openfontlicense.org"),
            TrustLink(R.string.ws7_tp_lic_apache_link, "https://www.apache.org/licenses/LICENSE-2.0"),
        ),
    )
    TrustPage.WHAT_NORMAL_LOOKS_LIKE -> TrustContent(
        R.string.ws7_tp_normal_intro,
        listOf(
            TrustSection(R.string.ws7_tp_normal_aud_h, body = R.string.ws7_tp_normal_aud_b),
            TrustSection(R.string.ws7_tp_normal_money_h, body = R.string.ws7_tp_normal_money_b),
            TrustSection(R.string.ws7_tp_normal_talks_h, body = R.string.ws7_tp_normal_talks_b),
            TrustSection(R.string.ws7_tp_normal_day_h, body = R.string.ws7_tp_normal_day_b),
            TrustSection(R.string.ws7_tp_normal_flags_h, bullets = R.array.ws7_tp_normal_flags, after = R.string.ws7_tp_normal_flags_b),
        ),
    )
}

@StringRes
fun trustTitle(page: TrustPage): Int = when (page) {
    TrustPage.HOW_WE_MAKE_MONEY -> R.string.ws7_trust_money
    TrustPage.NO_GEN_AI -> R.string.ws7_trust_ai
    TrustPage.COMMUNITY_RULES -> R.string.ws7_trust_rules
    TrustPage.PRIVACY -> R.string.ws7_trust_privacy
    TrustPage.LICENCES -> R.string.ws7_trust_licences
    TrustPage.WHAT_NORMAL_LOOKS_LIKE -> R.string.ws7_trust_normal
}

fun trustIcon(page: TrustPage): ImageVector = when (page) {
    TrustPage.HOW_WE_MAKE_MONEY -> Icons.Rounded.MoneyOff
    TrustPage.NO_GEN_AI -> Icons.Rounded.Block
    TrustPage.COMMUNITY_RULES -> Icons.Rounded.Gavel
    TrustPage.PRIVACY -> Icons.Rounded.Lock
    TrustPage.LICENCES -> Icons.Rounded.Verified
    TrustPage.WHAT_NORMAL_LOOKS_LIKE -> Icons.Rounded.Shield
}

/** S24 Trust pages: static, markdown-like composables (no WebView). Reachable from Welcome and Settings. */
@Composable
fun TrustPageScreen(nav: AppNavigator, page: TrustPage) {
    val c = Ct.colors
    val doc = content(page)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        // the big heading below carries the page name; the bar just says where you are
        CtTopBar(title = stringResource(R.string.ws7_tp_bar), onBack = nav::back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ws7_tp_kicker), style = Ct.type.monoS, color = c.accent)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(trustTitle(page)).uppercase(), style = Ct.type.displayM, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            SprocketDivider(height = 10.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(doc.intro), style = Ct.type.titleM.copy(fontWeight = Ct.type.body.fontWeight), color = c.text)
            doc.sections.forEach { sec ->
                SectionHeader(stringResource(sec.heading), Modifier.padding(top = 24.dp))
                sec.body?.let { Text(stringResource(it), style = Ct.type.body, color = c.text) }
                sec.bullets?.let { arr ->
                    stringArrayResource(arr).forEachIndexed { i, line -> Bullet(line, if (sec.numbered) i + 1 else null) }
                }
                sec.after?.let { Text(stringResource(it), style = Ct.type.body, color = c.text, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (doc.links.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                doc.links.forEach { l -> GhostButton(stringResource(l.label), { nav.openUrl(l.url) }, icon = Icons.AutoMirrored.Rounded.OpenInNew) }
            }
            SectionHeader(stringResource(R.string.ws7_tp_also), Modifier.padding(top = 28.dp))
            TrustPage.entries.filter { it != page }.forEach { other ->
                NavRow(trustIcon(other), stringResource(trustTitle(other)), null, { nav.toTrustPage(other) })
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Bullet(text: String, number: Int?) {
    val c = Ct.colors
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        if (number != null) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
                Text("$number", style = Ct.type.monoS, color = c.accent)
            }
            Spacer(Modifier.width(12.dp))
        } else {
            Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(14.dp))
        }
        Text(text, style = Ct.type.body, color = c.text, modifier = Modifier.weight(1f))
    }
}
