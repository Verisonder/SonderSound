package com.verisonder.sondersound.clips

import android.content.Context
import com.verisonder.sondersound.KeyVault
import com.verisonder.sondersound.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * What the app heard. Kept in memory; written to disk, encrypted, only when "Save detection
 * clips" is on or the user saves one clip by hand.
 */
object DetectionLog {

    data class Detection(
        val id: String,
        val sound: String,
        val atMillis: Long,
        val score: Float,
        val needed: Float,
        val pcm: ShortArray,
        val saved: Boolean,
        /** Saved by hand. Exempt from auto-delete. */
        val pinned: Boolean,
        /** Which comparison matched: AVERAGE or EACH. */
        val via: String = "AVERAGE",
    )

    /** The latest this many are kept. Clips saved by hand are kept on top of these. */
    const val KEEP_LATEST = 10

    private val _items = MutableStateFlow<List<Detection>>(emptyList())
    val items: StateFlow<List<Detection>> = _items

    @Volatile private var loaded = false

    private fun dir(context: Context) = File(context.filesDir, "clips").apply { mkdirs() }

    /** Reads saved clips once per process and removes the expired ones. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val keepHours = Settings.keepHours(context)
            val now = System.currentTimeMillis()
            val fromDisk = dir(context).listFiles().orEmpty().mapNotNull { file ->
                val item = read(file)
                if (item == null) {
                    file.delete()
                    null
                } else if (!item.pinned && keepHours > 0 && now - item.atMillis > keepHours * 3_600_000L) {
                    file.delete()
                    null
                } else item
            }
            val merged = (_items.value + fromDisk).distinctBy { it.id }.sortedByDescending { it.atMillis }
            _items.value = trim(context, merged)
            loaded = true
        }
    }

    fun add(context: Context, sound: String, score: Float, needed: Float, pcm: ShortArray, via: String) {
        load(context)
        val save = Settings.saveClips(context)
        val item = Detection(UUID.randomUUID().toString(), sound, System.currentTimeMillis(), score, needed, pcm, save, false, via)
        if (save) write(context, item)
        _items.value = trim(context, listOf(item) + _items.value)
    }

    fun pin(context: Context, ids: Set<String>) {
        _items.value = _items.value.map {
            if (it.id in ids && !it.pinned) it.copy(saved = true, pinned = true).also { pinned -> write(context, pinned) } else it
        }
    }

    fun delete(context: Context, ids: Set<String>) {
        ids.forEach { File(dir(context), "$it.bin").delete() }
        _items.value = _items.value.filterNot { it.id in ids }
    }

    /**
     * Newest first. Keeps the latest [KEEP_LATEST] plus every clip saved by hand, and removes
     * the files of anything dropped, so an auto-saved clip does not outlive its place in the list.
     */
    private fun trim(context: Context, items: List<Detection>): List<Detection> {
        val sorted = items.sortedByDescending { it.atMillis }
        val keep = sorted.filterIndexed { index, d -> d.pinned || index < KEEP_LATEST }
        val dropped = sorted.filterNot { d -> keep.any { it.id == d.id } }
        dropped.forEach { File(dir(context), "${it.id}.bin").delete() }
        return keep
    }

    fun clear(context: Context) {
        dir(context).listFiles().orEmpty().forEach { it.delete() }
        _items.value = emptyList()
    }

    private fun write(context: Context, item: Detection) {
        val meta = JSONObject()
            .put("sound", item.sound)
            .put("at", item.atMillis)
            .put("score", item.score.toDouble())
            .put("needed", item.needed.toDouble())
            .put("pinned", item.pinned)
            .put("via", item.via)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + meta.size + item.pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(meta.size)
        buffer.put(meta)
        buffer.asShortBuffer().put(item.pcm)
        File(dir(context), "${item.id}.bin").writeBytes(KeyVault.seal(buffer.array()))
    }

    private fun read(file: File): Detection? = runCatching {
        val plain = KeyVault.open(file.readBytes()) ?: return null
        val buffer = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        val metaSize = buffer.int
        val metaBytes = ByteArray(metaSize).also { buffer.get(it) }
        val meta = JSONObject(String(metaBytes, Charsets.UTF_8))
        val shorts = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val pcm = ShortArray(shorts.remaining()).also { shorts.get(it) }
        Detection(
            id = file.nameWithoutExtension,
            sound = meta.getString("sound"),
            atMillis = meta.getLong("at"),
            score = meta.getDouble("score").toFloat(),
            needed = meta.getDouble("needed").toFloat(),
            pcm = pcm,
            saved = true,
            pinned = meta.optBoolean("pinned", false),
            via = meta.optString("via", "AVERAGE"),
        )
    }.getOrNull()
}
