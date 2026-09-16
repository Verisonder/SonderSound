package com.verisonder.sondersound.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Step { WELCOME, MICROPHONE, SOUND }

/** First launch only. Ends by calling [onFinished]; the caller marks setup done. */
@Composable
fun SetupScreen(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var refused by rememberSaveable { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            refused = false
            step = Step.SOUND
        } else {
            refused = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(72.dp))
        when (step) {
            Step.WELCOME -> {
                Title("SonderSound")
                Line("Tells you when someone calls your name.")
                Spacer(Modifier.height(48.dp))
                Primary("Start") { step = Step.MICROPHONE }
            }

            Step.MICROPHONE -> {
                Title("Microphone")
                Line("Listens with the phone's mic while on.")
                Line("Audio stays in memory. Nothing is sent unless you ask.")
                if (refused) {
                    Spacer(Modifier.height(16.dp))
                    Text("Needed to work. Allow it to continue.", color = Palette.muted)
                }
                Spacer(Modifier.height(48.dp))
                Primary("Allow microphone") {
                    val wanted = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissions.launch(wanted.toTypedArray())
                }
            }

            Step.SOUND -> {
                Title("Your sound")
                Line("Teach it what to listen for.")
                Spacer(Modifier.height(32.dp))
                RecordSound(
                    onDone = onFinished,
                    secondary = "Skip for now",
                    onSecondary = onFinished,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 40.sp, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun Line(text: String) {
    Text(text, color = Palette.muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.pillOn, contentColor = Palette.onPill),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(label, fontSize = 18.sp) }
}
