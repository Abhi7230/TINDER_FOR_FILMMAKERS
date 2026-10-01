package com.tff.app.ui.screens.me

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
fun MeScreen(nav: AppNavigator) {
    Box(Modifier.fillMaxSize().background(Ct.colors.bg), contentAlignment = Alignment.Center) {
        Text("MeScreen", color = Ct.colors.text)
    }
}
