package com.verisonder.sondersound.sound

import android.content.Context
import com.verisonder.sondersound.Settings
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

    /** What happens when this sound is heard. */
    data class Actions(
        val chime: Settings.Chime,
        val vibrate: Boolean,
        val music: Settings.OnMatch,
    )

    fun actions(context: Context, id: String): Actions {
        val defaults = Actions(Settings.chime(context), false, Settings.onMatch(context))
        val file = File(dir(context, id), "actions.txt")
        if (!file.exists()) return defaults
        val map = file.readLines().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
        return Actions(
            chime = runCatching { Settings.Chime.valueOf(map["chime"] ?: "") }.getOrDefault(defaults.chime),
            vibrate = map["vibrate"]?.toBooleanStrictOrNull() ?: defaults.vibrate,
            music = runCatching { Settings.OnMatch.valueOf(map["music"] ?: "") }.getOrDefault(defaults.music),
        )
    }

    fun setActions(context: Context, id: String, actions: Actions) {
        File(dir(context, id), "actions.txt").writeText(
            "chime=${actions.chime.name}\nvibrate=${actions.vibrate}\nmusic=${actions.music.name}\n"
        )
    }

    /** One recorded take. [number] is stable: deleting another take does not renumber it. */
    data class Take(val number: Int, val pcm: ShortArray)

    const val MIN_TAKES = 5
    const val MAX_TAKES = 50

    /**
     * Changes whenever a sound or take is added or removed, so the detector knows to
     * rebuild. Stored so a restarted service sees changes made while it was down.
     */
    fun version(context: Context): Long =
        context.getSharedPreferences("sounds", Context.MODE_PRIVATE).getLong("version", 0L)

    private fun bump(context: Context) {
        val prefs = context.getSharedPreferences("sounds", Context.MODE_PRIVATE)
        prefs.edit().putLong("version", prefs.getLong("version", 0L) + 1).apply()
    }

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
        // Highest number plus one, not count plus one: after deleting take 2 of 4, count+1
        // would overwrite take 4.
        val next = (takeFiles(folder).maxOfOrNull { number(it) } ?: 0) + 1
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(pcm)
        File(folder, "take_$next.pcm").writeBytes(bytes.array())
        bump(context)
    }

    fun takes(context: Context, id: String): List<ShortArray> = numberedTakes(context, id).map { it.pcm }

    fun numberedTakes(context: Context, id: String): List<Take> =
        takeFiles(dir(context, id)).map { file ->
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            Take(number(file), ShortArray(buffer.remaining()).also { buffer.get(it) })
        }

    fun name(context: Context, id: String): String? =
        File(dir(context, id), "name.txt").takeIf { it.exists() }?.readText()

    fun rename(context: Context, id: String, name: String) {
        File(dir(context, id), "name.txt").writeText(name.trim())
        bump(context)
    }

    fun deleteTake(context: Context, id: String, number: Int) {
        File(dir(context, id), "take_$number.pcm").delete()
        bump(context)
    }

    fun removeLastTake(context: Context, id: String) {
        takeFiles(dir(context, id)).lastOrNull()?.delete()
        bump(context)
    }

    fun delete(context: Context, id: String) {
        dir(context, id).deleteRecursively()
        bump(context)
    }

    private fun number(file: File): Int = file.name.removePrefix("take_").removeSuffix(".pcm").toIntOrNull() ?: 0

    private fun takeFiles(folder: File): List<File> =
        folder.listFiles { f -> f.name.startsWith("take_") && f.name.endsWith(".pcm") }
            .orEmpty()
            .sortedBy { number(it) }
}
