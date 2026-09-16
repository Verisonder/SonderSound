package com.verisonder.sondersound.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sondersound.audio.ClipPlayer
import com.verisonder.sondersound.sound.SoundStore
import com.verisonder.sondersound.sound.TakeCheck
import com.verisonder.sondersound.sound.TakeRecorder
import com.verisonder.sondersound.Settings
import com.verisonder.sondersound.detect.Features
import com.verisonder.sondersound.detect.Sounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Teaching the app one sound: a name, then 5 to 8 takes. Used by setup and by My sounds.
 * A take that is too quiet or clipped is refused and not counted.
 */
@Composable
fun RecordSound(onDone: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf<String?>(null) }
    var takes by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf<ShortArray?>(null) }

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { if (id == null) name = it },
            enabled = id == null,
            singleLine = true,
            label = { Text("Name it") },
            placeholder = { Text("Your name, doorbell…") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Say it after tapping Record. $takes of ${SoundStore.MIN_TAKES}.",
            fontSize = 18.sp,
        )
        Text(
            "Vary the distance and how loud.",
            color = Palette.muted,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !recording && name.isNotBlank() && takes < SoundStore.MAX_TAKES,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.pillOn, contentColor = Palette.onPill),
                onClick = {
                    recording = true
                    line = "Say it now."
                    scope.launch {
                        val pcm = runCatching { TakeRecorder.record() }.getOrNull()
                        recording = false
                        if (pcm == null) {
                            line = "Microphone unavailable."
                            return@launch
                        }
                        when (TakeCheck.judge(pcm)) {
                            TakeCheck.Verdict.TOO_QUIET -> line = "Too quiet. Try again."
                            TakeCheck.Verdict.TOO_LOUD -> line = "Too loud. Try again."
                            TakeCheck.Verdict.OK -> {
                                val soundId = id ?: SoundStore.create(context, name).also { id = it }
                                SoundStore.addTake(context, soundId, pcm)
                                takes++
                                last = pcm
                                line = "Take $takes kept."
                            }
                        }
                    }
                },
            ) { Text(if (recording) "Listening…" else "Record") }

            last?.let { pcm ->
                IconButton(onClick = { ClipPlayer.play(context, pcm) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Hear the last take")
                }
                TextButton(enabled = !recording, onClick = {
                    id?.let { SoundStore.removeLastTake(context, it) }
                    takes--
                    last = null
                    line = "Last take removed."
                }) { Text("Undo") }
            }
        }

        line?.let {
            Text(it, color = Palette.muted, modifier = Modifier.padding(top = 12.dp))
        }

        if (takes >= SoundStore.MIN_TAKES) {
            Spacer(Modifier.height(24.dp))
            Text("Test it", fontSize = 18.sp)
            Text("Say it once more, or say something else.", color = Palette.muted, fontSize = 14.sp)
            TextButton(
                enabled = !recording,
                onClick = {
                    val soundId = id ?: return@TextButton
                    recording = true
                    line = "Say it now."
                    scope.launch {
                        val pcm = runCatching { TakeRecorder.record() }.getOrNull()
                        if (pcm == null) {
                            recording = false
                            line = "Microphone unavailable."
                            return@launch
                        }
                        line = "Checking…"
                        val result = withContext(Dispatchers.Default) {
                            runCatching {
                                val enrolled = Sounds.enrolled(context).filter { it.id == soundId }
                                Sounds.matcher(context).scoreRecording(pcm, enrolled)
                            }
                        }
                        recording = false
                        val needed = Features.threshold(Settings.sensitivity(context))
                        line = result.fold(
                            onSuccess = { best ->
                                when {
                                    best == null -> "Heard nothing to compare."
                                    best.score >= needed -> String.format(Locale.US, "Match. %.2f, needs %.2f.", best.score, needed)
                                    else -> String.format(Locale.US, "No match. %.2f, needs %.2f.", best.score, needed)
                                }
                            },
                            onFailure = { "Detector failed to load." },
                        )
                    }
                },
            ) { Text("Test") }
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                // Abandoning a half-taught sound leaves nothing behind.
                if (takes < SoundStore.MIN_TAKES) id?.let { SoundStore.delete(context, it) }
                onSecondary()
            }) { Text(secondary) }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = takes >= SoundStore.MIN_TAKES && !recording,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.pillOn, contentColor = Palette.onPill),
                onClick = onDone,
            ) { Text("Done") }
        }
    }
}
