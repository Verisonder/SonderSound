package com.verisonder.sondersound.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.verisonder.sondersound.Settings
import com.verisonder.sondersound.audio.ListenService

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

    /**
     * A microphone service can only be started while the app is on screen, and the system
     * does not restart it. So if the switch is on and nothing is listening, opening the
     * app is what brings it back.
     */
    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (Settings.listening(this) && granted && !ListenService.state.value.running) {
            ListenService.start(this)
        }
    }
}
