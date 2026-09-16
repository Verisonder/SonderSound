package com.verisonder.sondersound.detect

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure signal helpers for the detector. No Android here, so all of it runs in unit tests.
 *
 * The numbers match the Python replay the detector was tuned on (docs/DETECTION.md):
 * 16 kHz audio, 10 ms frames, a 1.2 s analysis window stepped every 240 ms.
 */
object Features {
    const val RATE = 16_000
    const val WINDOW = 19_200          // 1.2 s, the embedder's input
    const val HOP = 3_840              // 240 ms between evaluations
    const val FRAME = 160              // 10 ms
    const val MEL_WINDOW = 76          // mel frames per embedding
    const val MEL_STEP = 8             // mel frames between embeddings
    const val GATE_RATIO = 4.0         // loudest 10 ms frame vs the quiet ones, see worthEvaluating
    const val GATE_FLOOR = 25.0        // below this even the loudest frame is the mic's own hiss
    const val PAD_NOISE = 30.0         // std of the noise a short take is padded with

    /** Sensitivity 0 is the most cautious. */
    fun threshold(sensitivity: Float): Float = 0.75f - 0.25f * sensitivity.coerceIn(0f, 1f)

    fun rms(x: FloatArray): Double {
        if (x.isEmpty()) return 0.0
        var sum = 0.0
        for (v in x) sum += v.toDouble() * v
        return sqrt(sum / x.size)
    }

    /**
     * Whether a window has something in it worth scoring: its loudest 10 ms stands at least
     * 4x (12 dB) above its quiet frames. Relative, not absolute, so a whisper or a voice
     * across the room passes in a quiet place, and steady noise does not keep the models busy.
     *
     * Replaces a fixed RMS of 200, which in replay rejected every far or whispered call
     * before it was ever compared.
     */
    fun worthEvaluating(x: FloatArray): Boolean {
        val e = frameEnergies(x)
        if (e.isEmpty()) return false
        val sorted = e.sortedArray()
        return sorted.last() >= max(GATE_RATIO * percentile(sorted, 0.20), GATE_FLOOR)
    }

    /** Root-mean-square per non-overlapping 10 ms frame, on raw sample values. */
    fun frameEnergies(x: FloatArray): DoubleArray {
        val n = x.size / FRAME
        return DoubleArray(n) { i ->
            var sum = 0.0
            for (k in i * FRAME until (i + 1) * FRAME) sum += x[k].toDouble() * x[k]
            sqrt(sum / FRAME) + 1e-9
        }
    }

    /**
     * Cuts a take down to where the sound is, plus 50 ms either side. 25 ms frames every
     * 10 ms; a frame counts if it is within 30 dB of the loudest and clearly above the quiet.
     */
    fun trim(x: FloatArray): FloatArray {
        val win = 400
        val n = if (x.size < win) 0 else 1 + (x.size - win) / FRAME
        if (n == 0) return x
        val e = DoubleArray(n) { i ->
            var sum = 0.0
            for (k in i * FRAME until i * FRAME + win) {
                val v = x[k] / 32768.0
                sum += v * v
            }
            sqrt(sum / win) + 1e-9
        }
        val sorted = e.sortedArray()
        val p10 = percentile(sorted, 0.10)
        val threshold = max(sorted.last() * 10.0.pow(-1.5), p10 * 3)
        var first = -1
        var last = -1
        for (i in e.indices) if (e[i] > threshold) {
            if (first < 0) first = i
            last = i
        }
        if (first < 0) return x
        val a = max(first - 5, 0)
        val b = min(last + 5, n - 1)
        return x.copyOfRange(a * FRAME, min(b * FRAME + win, x.size))
    }

    /** Linear-interpolated percentile of an ascending array, as numpy computes it. */
    fun percentile(sorted: DoubleArray, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = q * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = min(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    /** Centres a short take in [length] samples of faint noise, from a fixed seed. */
    fun pad(x: FloatArray, length: Int = WINDOW, seed: Long = 7): FloatArray {
        if (x.size >= length) return x
        val random = java.util.Random(seed)
        val left = (length - x.size) / 2
        val out = FloatArray(length) { (random.nextGaussian() * PAD_NOISE).toFloat() }
        x.copyInto(out, left)
        return out
    }

    /** Where embeddings start, in mel frames, for a window of [melFrames]. */
    fun embeddingStarts(melFrames: Int): List<Int> =
        if (melFrames < MEL_WINDOW) emptyList() else (0..melFrames - MEL_WINDOW step MEL_STEP).toList()

    /**
     * Which embeddings mostly cover sound rather than silence. Looks at the middle 460 ms
     * of each 760 ms embedding and keeps it if more than 35% of those frames are within
     * 20 dB of the loudest.
     */
    fun speechMask(x: FloatArray, starts: List<Int>): BooleanArray {
        val e = frameEnergies(x)
        if (e.isEmpty()) return BooleanArray(starts.size)
        val threshold = max(e.max() * 0.1, 1.0)
        return BooleanArray(starts.size) { k ->
            val i = starts[k]
            val from = if (i + 61 <= e.size) i + 15 else i
            val to = if (i + 61 <= e.size) i + 61 else e.size
            if (from >= to) false else {
                var loud = 0
                for (j in from until to) if (e[j] > threshold) loud++
                loud.toDouble() / (to - from) > 0.35
            }
        }
    }

    fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x
        val n = sqrt(sum).toFloat()
        return if (n == 0f) v.copyOf() else FloatArray(v.size) { v[it] / n }
    }

    /** Subtracts the background mean, then normalises. */
    fun centre(v: FloatArray, background: FloatArray): FloatArray =
        normalize(FloatArray(v.size) { v[it] - background[it] })

    fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun mean(vectors: List<FloatArray>): FloatArray {
        val out = FloatArray(vectors.first().size)
        for (v in vectors) for (i in v.indices) out[i] += v[i]
        for (i in out.indices) out[i] /= vectors.size
        return out
    }
}
