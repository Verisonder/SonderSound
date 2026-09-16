package com.verisonder.sondersound.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sondersound.KeyVault
import com.verisonder.sondersound.Settings

private const val GET_KEY_URL = "https://aistudio.google.com/apikey"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var sensitivity by remember { mutableFloatStateOf(Settings.sensitivity(context)) }
    var onMatch by remember { mutableStateOf(Settings.onMatch(context)) }
    var seconds by remember { mutableIntStateOf(Settings.bufferSeconds(context)) }
    var saveClips by remember { mutableStateOf(Settings.saveClips(context)) }
    var keepHours by remember { mutableIntStateOf(Settings.keepHours(context)) }
    var hasKey by remember { mutableStateOf(KeyVault.hasGeminiKey(context)) }
    var keyDraft by remember { mutableStateOf("") }
    var script by remember { mutableStateOf(Settings.darijaScript(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar("Settings", onBack)

        Section("Listening")
        Option("Sensitivity", "Higher catches more, and more by mistake.")
        Slider(
            value = sensitivity,
            onValueChange = { sensitivity = it },
            onValueChangeFinished = { Settings.setSensitivity(context, sensitivity) },
        )
        Option("When it hears it", null)
        Choices(
            labels = listOf("Lower music", "Pause music"),
            selected = onMatch.ordinal,
        ) {
            onMatch = Settings.OnMatch.entries[it]
            Settings.setOnMatch(context, onMatch)
        }

        Section("Last clip")
        Option("Length", "Only this much is kept in memory.")
        Choices(
            labels = Settings.BUFFER_CHOICES.map { "$it s" },
            selected = Settings.BUFFER_CHOICES.indexOf(seconds),
        ) {
            seconds = Settings.BUFFER_CHOICES[it]
            Settings.setBufferSeconds(context, seconds)
        }

        Section("Clips")
        SwitchOption("Save detection clips", "Keep clips after the app closes.", saveClips) {
            saveClips = it
            Settings.setSaveClips(context, it)
        }
        if (saveClips) {
            Option("Delete after", null)
            Choices(
                labels = Settings.KEEP_CHOICES.map {
                    when (it) {
                        0 -> "Never"
                        1 -> "1 h"
                        24 -> "24 h"
                        else -> "${it / 24} days"
                    }
                },
                selected = Settings.KEEP_CHOICES.indexOf(keepHours),
            ) {
                keepHours = Settings.KEEP_CHOICES[it]
                Settings.setKeepHours(context, keepHours)
            }
        }

        Section("Transcription")
        if (hasKey) {
            Option("Gemini key saved", "Clips you transcribe are sent to Google.")
            OutlinedButton(onClick = {
                KeyVault.clearGeminiKey(context)
                hasKey = false
            }) { Text("Remove key") }

            Spacer(Modifier.height(8.dp))
            Option("Darija script", null)
            Choices(labels = listOf("Arabic", "Latin"), selected = script.ordinal) {
                script = Settings.Script.entries[it]
                Settings.setDarijaScript(context, script)
            }
        } else {
            Option("Gemini key", "Optional. Needed only to transcribe.")
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                singleLine = true,
                placeholder = { Text("Paste key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = keyDraft.isNotBlank(),
                    onClick = {
                        KeyVault.saveGeminiKey(context, keyDraft)
                        keyDraft = ""
                        hasKey = KeyVault.hasGeminiKey(context)
                    },
                ) { Text("Save") }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(GET_KEY_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Get a key") }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = Palette.pillOn,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Option(title: String, line: String?) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(title, fontSize = 18.sp)
        if (line != null) Text(line, color = Palette.muted, fontSize = 14.sp)
    }
}

@Composable
private fun SwitchOption(title: String, line: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) { Option(title, line) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}
