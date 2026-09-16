package com.verisonder.sondersound.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sondersound.KeyVault
import com.verisonder.sondersound.R
import com.verisonder.sondersound.Settings
import java.text.DateFormat
import java.util.Date

/** One thing the app heard. Filled in once detection exists. */
data class Detection(val sound: String, val atMillis: Long)

private const val SNOOZE_MS = 60 * 60 * 1000L

@Composable
fun MainScreen(onSettings: () -> Unit, onSounds: () -> Unit) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(Settings.listening(context)) }
    var snoozeUntil by remember { mutableLongStateOf(Settings.snoozeUntil(context)) }
    val snoozed = snoozeUntil > System.currentTimeMillis()
    val seconds = remember { Settings.bufferSeconds(context) }
    val hasKey = remember { KeyVault.hasGeminiKey(context) }
    val detections = remember { emptyList<Detection>() }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = {
                snoozeUntil = if (snoozed) 0L else System.currentTimeMillis() + SNOOZE_MS
                Settings.setSnoozeUntil(context, snoozeUntil)
            }) {
                SnoozeIcon(snoozed)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.SettingsIcon, contentDescription = "Settings", modifier = Modifier.size(28.dp))
            }
        }

        Spacer(Modifier.height(72.dp))
        Text(
            "SonderSound",
            fontSize = 44.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            if (snoozed) "Snoozed until ${time.format(Date(snoozeUntil))}" else " ",
            color = Palette.muted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))

        // The big toggle.
        Surface(
            shape = RoundedCornerShape(50),
            color = if (listening) Palette.pillOn else Palette.pillOff,
            modifier = Modifier.fillMaxWidth().clickable {
                listening = !listening
                Settings.setListening(context, listening)
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (listening) "On" else "Off",
                    color = Palette.onPill,
                    fontSize = 26.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = listening,
                    onCheckedChange = {
                        listening = it
                        Settings.setListening(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Palette.onPill,
                        checkedThumbColor = Palette.pillOn,
                        uncheckedTrackColor = Palette.onPill,
                        uncheckedThumbColor = Palette.muted,
                        uncheckedBorderColor = Palette.muted,
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // The buffer. Play and Transcribe are wired in later steps.
        Surface(shape = RoundedCornerShape(50), color = Palette.card, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play back")
                }
                Text("Last $seconds s", fontSize = 20.sp, modifier = Modifier.weight(1f))
                if (hasKey) {
                    TextButton(onClick = {}, enabled = false) { Text("Transcribe") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = Palette.card,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSounds),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_bars), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(20.dp))
                Text("My sounds", fontSize = 20.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (detections.isEmpty()) {
            Text(
                "Nothing heard yet.",
                color = Palette.muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn {
                items(detections) { d -> DetectionRow(d, time.format(Date(d.atMillis))) }
            }
        }
    }
}

@Composable
private fun DetectionRow(detection: Detection, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = Palette.accent, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(detection.sound, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(time, color = Palette.muted)
        }
        IconButton(onClick = {}, enabled = false) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
        }
    }
}

/** A bell, struck through while snoozed. Core icons have no "notifications off". */
@Composable
private fun SnoozeIcon(snoozed: Boolean) {
    val tint = MaterialTheme.colorScheme.onSurface
    Icon(
        Icons.Filled.Notifications,
        contentDescription = if (snoozed) "Resume alerts" else "Snooze for an hour",
        tint = tint,
        modifier = Modifier
            .size(28.dp)
            .drawWithContent {
                drawContent()
                if (snoozed) {
                    drawLine(
                        color = Color.Black,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 6f,
                    )
                    drawLine(
                        color = tint,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
            },
    )
}
