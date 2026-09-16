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

    /**
     * AVERAGE compares with the mean of the takes. EACH compares with every take and keeps
     * the closest. BOTH takes whichever of the two is higher. Replay numbers in DETECTION.md.
     */
    enum class Mode { AVERAGE, EACH, BOTH }

    /** [takes] are the per-take directions, already centred and normalised. */
    data class Enrolled(val id: String, val name: String, val centroid: FloatArray, val takes: List<FloatArray> = emptyList())

    /** [via] is AVERAGE or EACH: which comparison produced [score]. */
    data class Best(val id: String, val name: String, val score: Float, val via: Mode = Mode.AVERAGE)

    private fun speechVectors(window: FloatArray): List<FloatArray> {
        val starts = mutableListOf<Int>()
        val vectors = embedder.embed(window) { melFrames ->
            Features.embeddingStarts(melFrames).also { starts.addAll(it) }
        }
        if (vectors.isEmpty()) return emptyList()
        val mask = Features.speechMask(window, starts)
        return vectors.filterIndexed { i, _ -> i < mask.size && mask[i] }
    }

    /** One take as a centred, normalised direction, or null if it holds no sound. */
    fun takeVector(take: ShortArray): FloatArray? {
        val x = Features.pad(Features.trim(FloatArray(take.size) { take[it].toFloat() }))
        val window = if (x.size > Features.WINDOW) x.copyOfRange(0, Features.WINDOW) else x
        val speech = speechVectors(window)
        val all = if (speech.isNotEmpty()) speech else embedder.embed(window) { Features.embeddingStarts(it) }
        return if (all.isEmpty()) null else Features.centre(Features.mean(all), background)
    }

    /** Null if no take had any sound in it. */
    fun enrol(id: String, name: String, takes: List<ShortArray>): Enrolled? {
        val perTake = takes.mapNotNull { takeVector(it) }
        if (perTake.isEmpty()) return null
        return Enrolled(id, name, Features.normalize(Features.mean(perTake)), perTake.map { Features.normalize(it) })
    }

    /** Null for silence or when nothing is enrolled. */
    fun score(window: FloatArray, sounds: List<Enrolled>, mode: Mode = Mode.AVERAGE): Best? {
        if (sounds.isEmpty() || !Features.worthEvaluating(window)) return null
        val speech = speechVectors(window)
        if (speech.isEmpty()) return null
        var best: Best? = null
        for (v in speech) {
            val c = Features.centre(v, background)
            for (s in sounds) {
                val candidate = pick(c, s, mode)
                if (best == null || candidate.score > best.score) best = candidate
            }
        }
        return best
    }

    private fun pick(c: FloatArray, s: Enrolled, mode: Mode): Best {
        val average = Features.dot(c, s.centroid)
        if (mode == Mode.AVERAGE || s.takes.isEmpty()) return Best(s.id, s.name, average, Mode.AVERAGE)
        var each = Float.NEGATIVE_INFINITY
        for (t in s.takes) each = maxOf(each, Features.dot(c, t))
        return if (mode == Mode.EACH || each > average) Best(s.id, s.name, each, Mode.EACH)
        else Best(s.id, s.name, average, Mode.AVERAGE)
    }

    /** Scores a whole recording by stepping the window across it, as listening does. */
    fun scoreRecording(pcm: ShortArray, sounds: List<Enrolled>, mode: Mode = Mode.AVERAGE): Best? {
        val x = FloatArray(pcm.size + 2 * Features.WINDOW)
        val noise = Features.pad(FloatArray(0), x.size, seed = 11)
        noise.copyInto(x)
        for (i in pcm.indices) x[Features.WINDOW + i] = pcm[i].toFloat()
        var best: Best? = null
        var a = 0
        while (a + Features.WINDOW <= x.size) {
            score(x.copyOfRange(a, a + Features.WINDOW), sounds, mode)?.let {
                if (best == null || it.score > best!!.score) best = it
            }
            a += Features.HOP
        }
        return best
    }

    companion object {
        /**
         * How well each take agrees with the others: the cosine between it and the mean of
         * all the other takes. A take recorded badly scores well below the rest. Null for a
         * sound with fewer than two takes.
         */
        fun agreement(vectors: List<FloatArray>): List<Float>? {
            if (vectors.size < 2) return null
            return vectors.indices.map { i ->
                val others = Features.normalize(Features.mean(vectors.filterIndexed { j, _ -> j != i }))
                Features.dot(Features.normalize(vectors[i]), others)
            }
        }
    }
}
