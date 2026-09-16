package com.verisonder.sondersound.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Enrolled sounds and the setup flow. Filled in with detection. */
@Composable
fun SoundsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        TopBar("My sounds", onBack)
        Text("No sounds yet.", color = Palette.muted, modifier = Modifier.padding(16.dp))
    }
}
