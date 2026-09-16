package com.verisonder.sondersound.clips

import android.content.Context
import com.verisonder.sondersound.KeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * The last 15 or 30 seconds, kept on purpose. Encrypted in app-private storage and never
 * deleted automatically: the user saved it, the user deletes it.
 */
object SavedClips {

    data class Clip(val id: String, val atMillis: Long, val pcm: ShortArray)

    private val _items = MutableStateFlow<List<Clip>>(emptyList())
    val items: StateFlow<List<Clip>> = _items

    @Volatile private var loaded = false

    private fun dir(context: Context) = File(context.filesDir, "saved").apply { mkdirs() }

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _items.value = dir(context).listFiles().orEmpty()
                .mapNotNull { read(it) }
                .sortedByDescending { it.atMillis }
            loaded = true
        }
    }

    /** Writes the clip before listing it, so a clip on screen is a clip on disk. */
    fun add(context: Context, pcm: ShortArray): Boolean {
        load(context)
        if (pcm.isEmpty()) return false
        val clip = Clip(UUID.randomUUID().toString(), System.currentTimeMillis(), pcm)
        val buffer = ByteBuffer.allocate(8 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(clip.atMillis)
        buffer.asShortBuffer().put(pcm)
        val ok = runCatching {
            File(dir(context), "${clip.id}.bin").writeBytes(KeyVault.seal(buffer.array()))
        }.isSuccess
        if (ok) _items.value = listOf(clip) + _items.value
        return ok
    }

    fun delete(context: Context, ids: Set<String>) {
        ids.forEach { File(dir(context), "$it.bin").delete() }
        _items.value = _items.value.filterNot { it.id in ids }
    }

    private fun read(file: File): Clip? = runCatching {
        val plain = KeyVault.open(file.readBytes()) ?: return null
        val buffer = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        val at = buffer.long
        val shorts = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        Clip(file.nameWithoutExtension, at, ShortArray(shorts.remaining()).also { shorts.get(it) })
    }.getOrNull()
}
