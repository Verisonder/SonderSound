package com.verisonder.sondersound.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private enum class Screen { MAIN, SETTINGS, SOUNDS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SonderTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
                BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }
                when (screen) {
                    Screen.MAIN -> MainScreen(
                        onSettings = { screen = Screen.SETTINGS },
                        onSounds = { screen = Screen.SOUNDS },
                    )
                    Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.MAIN })
                    Screen.SOUNDS -> SoundsScreen(onBack = { screen = Screen.MAIN })
                }
            }
        }
    }
}
