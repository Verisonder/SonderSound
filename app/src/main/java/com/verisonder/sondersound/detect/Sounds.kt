package com.verisonder.sondersound.detect

import android.content.Context
import com.verisonder.sondersound.sound.SoundStore

/**
 * The enrolled sounds as the detector sees them. Rebuilt when the store changes, and never
 * on the audio thread's hot path more than once per change.
 */
object Sounds {
    /** One short line for the screen: what went wrong, not just that it did. */
    fun describe(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        val message = root.message?.lineSequence()?.firstOrNull()?.take(90).orEmpty()
        return "Detector failed: ${root.javaClass.simpleName}${if (message.isNotEmpty()) " — $message" else ""}"
    }

    @Volatile private var cachedVersion = -1L
    @Volatile private var cached: List<Matcher.Enrolled> = emptyList()
    @Volatile private var matcher: Matcher? = null

    fun matcher(context: Context): Matcher =
        matcher ?: synchronized(this) {
            matcher ?: Matcher(TfEmbedder.get(context), TfEmbedder.background(context)).also { matcher = it }
        }

    fun enrolled(context: Context): List<Matcher.Enrolled> {
        val version = SoundStore.version(context)
        if (version != cachedVersion) {
            synchronized(this) {
                if (version != cachedVersion) {
                    val m = matcher(context)
                    cached = SoundStore.list(context).mapNotNull { s ->
                        m.enrol(s.id, s.name, SoundStore.takes(context, s.id))
                    }
                    cachedVersion = version
                }
            }
        }
        return cached
    }
}
