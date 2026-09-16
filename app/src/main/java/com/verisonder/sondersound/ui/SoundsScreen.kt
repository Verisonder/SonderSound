package com.verisonder.sondersound.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sondersound.sound.SoundStore

@Composable
fun SoundsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var sounds by remember { mutableStateOf(SoundStore.list(context)) }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar(if (adding) "Add sound" else "My sounds", onBack = {
            if (adding) adding = false else onBack()
        })

        if (adding) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                RecordSound(
                    onDone = {
                        sounds = SoundStore.list(context)
                        adding = false
                    },
                    secondary = "Cancel",
                    onSecondary = { adding = false },
                )
            }
            return@Column
        }

        if (sounds.isEmpty()) {
            Text("No sounds yet.", color = Palette.muted, modifier = Modifier.padding(16.dp))
        }
        sounds.forEach { sound ->
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Palette.card,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sound.name, fontSize = 20.sp)
                        Text("${sound.takes} takes", color = Palette.muted, fontSize = 14.sp)
                    }
                    IconButton(onClick = {
                        SoundStore.delete(context, sound.id)
                        sounds = SoundStore.list(context)
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${sound.name}")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { adding = true },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.pillOn, contentColor = Palette.onPill),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Add sound", fontSize = 18.sp) }
    }
}
