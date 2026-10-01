package com.tff.app.ui.screens.wrap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.theme.Ct

/** PLACEHOLDER — replaced by its workstream. */
@Composable
fun WrapScreen(nav: AppNavigator, matchId: String?, projectId: String?) {
    Box(Modifier.fillMaxSize().background(Ct.colors.bg), contentAlignment = Alignment.Center) {
        Text("WrapScreen", color = Ct.colors.text)
    }
}
