package com.tff.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.theme.Ct

/** Debug gallery of every design-system component with sample data. PLACEHOLDER — WS1. */
@Composable
fun ComponentGallery(nav: AppNavigator) {
    Box(Modifier.fillMaxSize().background(Ct.colors.bg), contentAlignment = Alignment.Center) {
        Text("ComponentGallery", color = Ct.colors.text)
    }
}
