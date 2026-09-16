package com.verisonder.sondersound.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours taken from Google's Sound Notifications screen, which this layout follows. */
object Palette {
    val background = Color(0xFF1B1B1B)
    val card = Color(0xFF474747)
    val pillOff = Color(0xFFE3E3E3)
    val pillOn = Color(0xFFA8C7FA)
    val onPill = Color(0xFF1B1B1B)
    val text = Color(0xFFE6E6E6)
    val muted = Color(0xFFBDBDBD)
    val accent = Color(0xFF146C2E)
}

@Composable
fun SonderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.background,
            surface = Palette.background,
            primary = Palette.pillOn,
            onBackground = Palette.text,
            onSurface = Palette.text,
        ),
        content = content,
    )
}
