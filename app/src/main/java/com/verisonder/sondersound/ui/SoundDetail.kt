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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.verisonder.sondersound.detect.Matcher
import com.verisonder.sondersound.detect.Sounds
import com.verisonder.sondersound.sound.SoundStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** How far below the other takes a take has to be before it is marked as the one to redo. */
private const val WEAK_GAP = 0.15f

/**
 * One sound: rename it, hear each take, drop the weak ones, add more, test it.
 * Each take shows how well it agrees with the rest, so a bad one is visible.
 */
@Composable
fun SoundDetail(soundId: String, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var savedName by remember { mutableStateOf(SoundStore.name(context, soundId).orEmpty()) }
    var name by remember { mutableStateOf(savedName) }
    var takes by remember { mutableStateOf(SoundStore.numberedTakes(context, soundId)) }
    var agreement by remember { mutableStateOf<List<Float>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(revision) {
        agreement = null
        val current = takes
        agreement = withContext(Dispatchers.Default) {
            runCatching {
                val matcher = Sounds.matcher(context)
                val vectors = current.map { matcher.takeVector(it.pcm) }
                if (vectors.any { it == null }) null else Matcher.agreement(vectors.filterNotNull())
            }.onFailure { line = Sounds.describe(it) }.getOrNull()
        }
    }

    fun reload() {
        takes = SoundStore.numberedTakes(context, soundId)
        revision++
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = name.isNotBlank() && name.trim() != savedName,
            onClick = {
                SoundStore.rename(context, soundId, name)
                savedName = name.trim()
                line = "Renamed."
            },
        ) { Text("Save name") }

        Spacer(Modifier.height(16.dp))
        Text("Takes", fontSize = 18.sp)
        Text(
            "Each shows how well it matches the others. Redo the weakest.",
            color = Palette.muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))

        val scores = agreement
        val best = scores?.maxOrNull()
        val weakest = scores?.let { s -> s.indices.minByOrNull { s[it] } }
        takes.forEachIndexed { index, take ->
            val score = scores?.getOrNull(index)
            val weak = score != null && best != null && index == weakest && best - score >= WEAK_GAP
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Palette.card,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Take ${index + 1}", fontSize = 17.sp)
                        Text(
                            when {
                                score == null -> "…"
                                weak -> String.format(Locale.US, "Fits %.2f · weakest", score)
                                else -> String.format(Locale.US, "Fits %.2f", score)
                            },
                            color = if (weak) Palette.pillOn else Palette.muted,
                            fontSize = 14.sp,
                        )
                    }
                    IconButton(onClick = { ClipPlayer.play(context, take.pcm) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play take ${index + 1}")
                    }
                    IconButton(
                        enabled = !busy && takes.size > SoundStore.MIN_TAKES,
                        onClick = {
                            SoundStore.deleteTake(context, soundId, take.number)
                            line = "Take removed."
                            reload()
                        },
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete take ${index + 1}")
                    }
                }
            }
        }
        if (takes.size <= SoundStore.MIN_TAKES) {
            Text(
                "Add a take before removing one. ${SoundStore.MIN_TAKES} is the minimum.",
                color = Palette.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && takes.size < SoundStore.MAX_TAKES,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.pillOn, contentColor = Palette.onPill),
                onClick = {
                    busy = true
                    line = "Say it now."
                    scope.launch {
                        val (pcm, result) = recordCheckedTake()
                        if (pcm != null) {
                            SoundStore.addTake(context, soundId, pcm)
                            reload()
                        }
                        line = result
                        busy = false
                    }
                },
            ) { Text(if (takes.size >= SoundStore.MAX_TAKES) "${SoundStore.MAX_TAKES} takes max" else "Add take") }

            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    line = "Say it now."
                    scope.launch {
                        line = testSound(context, soundId)
                        busy = false
                    }
                },
            ) { Text("Test") }
        }

        line?.let { Text(it, color = Palette.muted, modifier = Modifier.padding(top = 12.dp)) }

        Spacer(Modifier.height(32.dp))
        TextButton(onClick = {
            SoundStore.delete(context, soundId)
            onDeleted()
        }) { Text("Delete this sound") }
    }
}
