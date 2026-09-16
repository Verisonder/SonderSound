package com.verisonder.sondersound.sound

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Enrolled sounds, in app-private storage: one folder per sound holding its name and the
 * takes recorded during setup, as raw 16 kHz mono PCM. These are recordings the user made
 * on purpose to teach the app a sound; they are not clips of what it heard.
 */
object SoundStore {

    data class Sound(val id: String, val name: String, val takes: Int)

    const val MIN_TAKES = 5
    const val MAX_TAKES = 8

    private fun root(context: Context) = File(context.filesDir, "sounds").apply { mkdirs() }
    private fun dir(context: Context, id: String) = File(root(context), id)

    fun list(context: Context): List<Sound> =
        root(context).listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { folder ->
                val name = File(folder, "name.txt").takeIf { it.exists() }?.readText() ?: return@mapNotNull null
                Sound(folder.name, name, takeFiles(folder).size)
            }
            .filter { it.takes > 0 }
            .sortedBy { it.name.lowercase() }

    fun create(context: Context, name: String): String {
        val id = UUID.randomUUID().toString()
        val folder = dir(context, id).apply { mkdirs() }
        File(folder, "name.txt").writeText(name.trim())
        return id
    }

    fun addTake(context: Context, id: String, pcm: ShortArray) {
        val folder = dir(context, id)
        val next = takeFiles(folder).size + 1
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(pcm)
        File(folder, "take_$next.pcm").writeBytes(bytes.array())
    }

    fun takes(context: Context, id: String): List<ShortArray> =
        takeFiles(dir(context, id)).map { file ->
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            ShortArray(buffer.remaining()).also { buffer.get(it) }
        }

    fun removeLastTake(context: Context, id: String) {
        takeFiles(dir(context, id)).lastOrNull()?.delete()
    }

    fun delete(context: Context, id: String) {
        dir(context, id).deleteRecursively()
    }

    private fun takeFiles(folder: File): List<File> =
        folder.listFiles { f -> f.name.startsWith("take_") && f.name.endsWith(".pcm") }
            .orEmpty()
            .sortedBy { it.name.removePrefix("take_").removeSuffix(".pcm").toIntOrNull() ?: 0 }
}
