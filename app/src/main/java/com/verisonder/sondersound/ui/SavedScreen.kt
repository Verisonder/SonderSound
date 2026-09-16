package com.verisonder.sondersound.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sondersound.KeyVault
import com.verisonder.sondersound.audio.ClipPlayer
import com.verisonder.sondersound.audio.ListenService
import com.verisonder.sondersound.clips.SavedClips
import java.text.DateFormat
import java.util.Date

/** Saved 15/30 s clips. Tap plays; long-press selects, for deleting several or transcribing one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedScreen(onBack: () -> Unit, onTranscribe: (ShortArray) -> Unit) {
    val context = LocalContext.current
    val clips by SavedClips.items.collectAsState()
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val live = selected.filterTo(mutableSetOf()) { id -> clips.any { it.id == id } }
    val hasKey = remember { KeyVault.hasGeminiKey(context) }
    val when_ = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LaunchedEffect(Unit) { SavedClips.load(context) }
    BackHandler(enabled = live.isNotEmpty()) { selected = emptySet() }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        TopBar("Saved clips", onBack)

        if (live.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                }
                Text("${live.size}", fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selected = if (live.size == clips.size) emptySet() else clips.map { it.id }.toSet()
                }) { Text(if (live.size == clips.size) "None" else "All") }
                if (hasKey && live.size == 1) {
                    TextButton(onClick = {
                        clips.firstOrNull { it.id in live }?.let { onTranscribe(it.pcm) }
                        selected = emptySet()
                    }) { Text("Transcribe") }
                }
                TextButton(onClick = {
                    SavedClips.delete(context, live)
                    selected = emptySet()
                }) { Text("Delete") }
            }
        }

        if (clips.isEmpty()) {
            Text(
                "Nothing saved. Tap Save on the last clip.",
                color = Palette.muted,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn {
            items(clips, key = { it.id }) { clip ->
                val isSelected = clip.id in live
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (live.isNotEmpty()) {
                                    selected = if (isSelected) live - clip.id else live + clip.id
                                } else {
                                    ClipPlayer.play(context, clip.pcm)
                                }
                            },
                            onLongClick = { selected = live + clip.id },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) Palette.pillOn else Palette.card,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Palette.onPill)
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(when_.format(Date(clip.atMillis)), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("${clip.pcm.size / ListenService.RATE} s", color = Palette.muted)
                    }
                    IconButton(onClick = { ClipPlayer.play(context, clip.pcm) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                }
            }
        }
    }
}
