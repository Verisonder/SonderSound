package com.verisonder.sondersound.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.verisonder.sondersound.audio.ClipPlayer
import com.verisonder.sondersound.audio.ListenService
import com.verisonder.sondersound.clips.DetectionLog
import com.verisonder.sondersound.transcribe.Transcriber
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val SNOOZE_MS = 60 * 60 * 1000L

@Composable
fun MainScreen(onSettings: () -> Unit, onSounds: () -> Unit) {
    val context = LocalContext.current
    val service by ListenService.state.collectAsState()
    val playing by ClipPlayer.playing.collectAsState()
    val detections by DetectionLog.items.collectAsState()
    val transcript by Transcriber.state.collectAsState()
    var refused by remember { mutableStateOf(false) }
    var snoozeUntil by remember { mutableLongStateOf(Settings.snoozeUntil(context)) }
    val snoozed = snoozeUntil > System.currentTimeMillis()
    val seconds = remember { Settings.bufferSeconds(context) }
    val hasKey = remember { KeyVault.hasGeminiKey(context) }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    var askDisclosure by remember { mutableStateOf<ShortArray?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    // Drop ids that are no longer in the list, for instance trimmed away by a new detection.
    val liveSelection = selected.filterTo(mutableSetOf()) { id -> detections.any { it.id == id } }

    BackHandler(enabled = liveSelection.isNotEmpty()) { selected = emptySet() }

    LaunchedEffect(Unit) { DetectionLog.load(context) }

    // The pill shows what the service is doing, not the saved switch.
    val listening = service.running

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            refused = false
            Settings.setListening(context, true)
            ListenService.start(context)
        } else {
            refused = true
        }
    }

    fun setListening(on: Boolean) {
        if (on) {
            val wanted = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.launch(wanted.toTypedArray())
        } else {
            Settings.setListening(context, false)
            ClipPlayer.stop()
            ListenService.stop(context)
        }
    }

    fun transcribe(pcm: ShortArray) {
        if (Settings.transcribeDisclosed(context)) Transcriber.start(context, pcm) else askDisclosure = pcm
    }

    askDisclosure?.let { pcm ->
        AlertDialog(
            onDismissRequest = { askDisclosure = null },
            title = { Text("Transcribe") },
            text = { Text("Clips you transcribe are sent to Google using your key.") },
            confirmButton = {
                TextButton(onClick = {
                    Settings.setTranscribeDisclosed(context)
                    askDisclosure = null
                    Transcriber.start(context, pcm)
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { askDisclosure = null }) { Text("Cancel") } },
        )
    }

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

        Spacer(Modifier.height(40.dp))
        Text("SonderSound", fontSize = 44.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            when {
                snoozed -> "Snoozed until ${time.format(Date(snoozeUntil))}"
                listening && service.sounds == 0 -> "No sounds yet. Add one in My sounds."
                listening && service.score != null ->
                    String.format(Locale.US, "Hearing %.2f · needs %.2f", service.score, service.needed)
                else -> " "
            },
            color = Palette.muted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = if (listening) Palette.pillOn else Palette.pillOff,
            modifier = Modifier.fillMaxWidth().clickable { setListening(!listening) },
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
                    onCheckedChange = { setListening(it) },
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

        Surface(shape = RoundedCornerShape(50), color = Palette.card, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = playing || service.heldSeconds > 0,
                    onClick = {
                        if (playing) ClipPlayer.stop()
                        else ListenService.freeze()?.let { ClipPlayer.play(context, it) }
                    },
                ) {
                    Icon(
                        if (playing) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Stop" else "Play back",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Last $seconds s", fontSize = 20.sp)
                    if (listening && service.heldSeconds < seconds) {
                        Text("${service.heldSeconds} s so far", color = Palette.muted, fontSize = 13.sp)
                    }
                }
                if (hasKey) {
                    TextButton(
                        enabled = service.heldSeconds > 0 && transcript != Transcriber.State.Working,
                        onClick = { ListenService.freeze()?.let { transcribe(it) } },
                    ) { Text("Transcribe") }
                }
            }
        }

        val problem = if (refused) "Microphone permission is needed." else service.note
        if (problem != null) {
            Text(problem, color = Palette.muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        TranscriptCard(transcript, onClose = { Transcriber.dismiss(context) }, onCopy = { text ->
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Transcript", text))
        })

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

        Spacer(Modifier.height(12.dp))

        if (liveSelection.isNotEmpty()) {
            val chosen = detections.filter { it.id in liveSelection }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                }
                Text("${liveSelection.size}", fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selected = if (liveSelection.size == detections.size) emptySet() else detections.map { it.id }.toSet()
                }) { Text(if (liveSelection.size == detections.size) "None" else "All") }
                if (chosen.any { !it.pinned }) {
                    TextButton(onClick = {
                        DetectionLog.pin(context, liveSelection)
                        selected = emptySet()
                    }) { Text("Save") }
                }
                if (hasKey && chosen.size == 1) {
                    TextButton(onClick = {
                        transcribe(chosen.first().pcm)
                        selected = emptySet()
                    }) { Text("Transcribe") }
                }
                TextButton(onClick = {
                    DetectionLog.delete(context, liveSelection)
                    selected = emptySet()
                }) { Text("Delete") }
            }
        }

        if (detections.isEmpty()) {
            Text(
                "Nothing heard yet.",
                color = Palette.muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn {
                items(detections, key = { it.id }) { d ->
                    val isSelected = d.id in liveSelection
                    DetectionRow(
                        detection = d,
                        time = time.format(Date(d.atMillis)),
                        selected = isSelected,
                        onClick = {
                            if (liveSelection.isNotEmpty()) {
                                selected = if (isSelected) liveSelection - d.id else liveSelection + d.id
                            } else {
                                ClipPlayer.play(context, d.pcm)
                            }
                        },
                        onLongClick = { selected = liveSelection + d.id },
                        onPlay = { ClipPlayer.play(context, d.pcm) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(state: Transcriber.State, onClose: () -> Unit, onCopy: (String) -> Unit) {
    val body = when (state) {
        Transcriber.State.Idle -> return
        Transcriber.State.Working -> "Transcribing…"
        Transcriber.State.Waiting -> "Offline. Will retry."
        is Transcriber.State.Done -> state.text
        is Transcriber.State.Error -> state.line
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Palette.card,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transcript", color = Palette.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (state is Transcriber.State.Done) {
                    TextButton(onClick = { onCopy(state.text) }) { Text("Copy") }
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Text(body, fontSize = 17.sp, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

/** Tap plays, or toggles while selecting. Long-press starts selecting. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetectionRow(
    detection: DetectionLog.Detection,
    time: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) Palette.pillOn else Palette.accent,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Palette.onPill)
                } else {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(detection.sound, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(
                String.format(
                    Locale.US, "%s · %.2f %s%s", time, detection.score,
                    if (detection.via == "EACH") "take" else "avg",
                    if (detection.pinned) " · saved" else "",
                ),
                color = Palette.muted,
            )
        }
        IconButton(onClick = onPlay) {
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
                    drawLine(Color.Black, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 6f)
                    drawLine(tint, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            },
    )
}
