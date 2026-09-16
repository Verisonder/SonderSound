package com.verisonder.sondersound.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.verisonder.sondersound.Settings
import com.verisonder.sondersound.audio.ListenService

class MainActivity : ComponentActivity() {

    private enum class Screen { SETUP, MAIN, SETTINGS, SOUNDS, SAVED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SonderTheme {
                // Every screen sits on this Surface. Without it Compose has no content
                // colour to inherit and draws text and icons black on the dark background.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Palette.background,
                    contentColor = Palette.text,
                ) {
                    val first = if (Settings.setupDone(this)) Screen.MAIN else Screen.SETUP
                    var screen by rememberSaveable { mutableStateOf(first) }
                    var pendingTranscribe by remember { mutableStateOf<ShortArray?>(null) }
                    BackHandler(enabled = screen == Screen.SETTINGS || screen == Screen.SOUNDS || screen == Screen.SAVED) {
                        screen = Screen.MAIN
                    }
                    when (screen) {
                        Screen.SETUP -> SetupScreen(onFinished = {
                            Settings.setSetupDone(this, true)
                            screen = Screen.MAIN
                        })
                        Screen.MAIN -> MainScreen(
                            onSettings = { screen = Screen.SETTINGS },
                            onSounds = { screen = Screen.SOUNDS },
                            onSaved = { screen = Screen.SAVED },
                            pendingTranscribe = pendingTranscribe,
                            onPendingHandled = { pendingTranscribe = null },
                        )
                        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.MAIN })
                        Screen.SOUNDS -> SoundsScreen(onBack = { screen = Screen.MAIN })
                        Screen.SAVED -> SavedScreen(
                            onBack = { screen = Screen.MAIN },
                            onTranscribe = { pcm ->
                                // Handed to the main screen, which shows the transcript and asks
                                // for the one-time notice if it has not been accepted yet.
                                pendingTranscribe = pcm
                                screen = Screen.MAIN
                            },
                        )
                    }
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
