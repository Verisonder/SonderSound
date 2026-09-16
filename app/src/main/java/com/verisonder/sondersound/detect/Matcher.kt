package com.verisonder.sondersound.detect

/** Turns 1.2 s of audio into speech embeddings. The Android implementation runs TFLite. */
fun interface Embedder {
    /** [window] is exactly [Features.WINDOW] samples. Returns one vector per start in [starts]. */
    fun embed(window: FloatArray, starts: (Int) -> List<Int>): List<FloatArray>
}

/**
 * Enrolment and scoring, independent of Android. A sound is one direction in embedding
 * space: the normalised mean of its takes, each centred on a background of ordinary speech.
 * A window scores the best cosine between any of its speech embeddings and that direction.
 */
class Matcher(private val embedder: Embedder, private val background: FloatArray) {

    data class Enrolled(val id: String, val name: String, val centroid: FloatArray)
    data class Best(val id: String, val name: String, val score: Float)

    private fun speechVectors(window: FloatArray): List<FloatArray> {
        val starts = mutableListOf<Int>()
        val vectors = embedder.embed(window) { melFrames ->
            Features.embeddingStarts(melFrames).also { starts.addAll(it) }
        }
        if (vectors.isEmpty()) return emptyList()
        val mask = Features.speechMask(window, starts)
        return vectors.filterIndexed { i, _ -> i < mask.size && mask[i] }
    }

    /** Null if no take had any sound in it. */
    fun enrol(id: String, name: String, takes: List<ShortArray>): Enrolled? {
        val perTake = takes.mapNotNull { take ->
            val x = Features.pad(Features.trim(FloatArray(take.size) { take[it].toFloat() }))
            val window = if (x.size > Features.WINDOW) x.copyOfRange(0, Features.WINDOW) else x
            val speech = speechVectors(window)
            val all = if (speech.isNotEmpty()) speech else embedder.embed(window) { Features.embeddingStarts(it) }
            if (all.isEmpty()) null else Features.centre(Features.mean(all), background)
        }
        if (perTake.isEmpty()) return null
        return Enrolled(id, name, Features.normalize(Features.mean(perTake)))
    }

    /** Null for silence or when nothing is enrolled. */
    fun score(window: FloatArray, sounds: List<Enrolled>): Best? {
        if (sounds.isEmpty() || Features.rms(window) < Features.MIN_WINDOW_RMS) return null
        val speech = speechVectors(window)
        if (speech.isEmpty()) return null
        var best: Best? = null
        for (v in speech) {
            val c = Features.centre(v, background)
            for (s in sounds) {
                val score = Features.dot(c, s.centroid)
                if (best == null || score > best.score) best = Best(s.id, s.name, score)
            }
        }
        return best
    }

    /** Scores a whole recording by stepping the window across it, as listening does. */
    fun scoreRecording(pcm: ShortArray, sounds: List<Enrolled>): Best? {
        val x = FloatArray(pcm.size + 2 * Features.WINDOW)
        val noise = Features.pad(FloatArray(0), x.size, seed = 11)
        noise.copyInto(x)
        for (i in pcm.indices) x[Features.WINDOW + i] = pcm[i].toFloat()
        var best: Best? = null
        var a = 0
        while (a + Features.WINDOW <= x.size) {
            score(x.copyOfRange(a, a + Features.WINDOW), sounds)?.let {
                if (best == null || it.score > best!!.score) best = it
            }
            a += Features.HOP
        }
        return best
    }
}
