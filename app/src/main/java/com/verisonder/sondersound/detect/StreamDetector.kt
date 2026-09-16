package com.verisonder.sondersound.detect

/**
 * Feeds live audio to a [Matcher]: keeps the last 1.2 s, evaluates every 240 ms, and fires
 * at most once per [cooldownMs] of audio. Time is counted in samples, so tests replay it
 * without a clock.
 */
class StreamDetector(
    private val matcher: Matcher,
    private val sounds: () -> List<Matcher.Enrolled>,
    private val threshold: () -> Float,
    private val cooldownMs: Long = 3_000,
) {
    data class Reading(val best: Matcher.Best?, val threshold: Float, val fired: Boolean)

    private val ring = FloatArray(Features.WINDOW)
    private var head = 0
    private var filled = 0
    private var sinceEval = 0
    private var samplesSeen = 0L
    private var lastFireAt = Long.MIN_VALUE / 2

    /** Returns a reading for each evaluation this chunk completed. */
    fun feed(chunk: ShortArray, count: Int = chunk.size): List<Reading> {
        val readings = mutableListOf<Reading>()
        for (i in 0 until minOf(count, chunk.size)) {
            ring[head] = chunk[i].toFloat()
            head = (head + 1) % ring.size
            if (filled < ring.size) filled++
            samplesSeen++
            sinceEval++
            if (filled == ring.size && sinceEval >= Features.HOP) {
                sinceEval = 0
                readings += evaluate()
            }
        }
        return readings
    }

    /** The ring, oldest sample first. */
    private fun linear(): FloatArray {
        val out = FloatArray(ring.size)
        val tail = ring.size - head
        System.arraycopy(ring, head, out, 0, tail)
        System.arraycopy(ring, 0, out, tail, head)
        return out
    }

    private fun evaluate(): Reading {
        val limit = threshold()
        val best = matcher.score(linear(), sounds())
        val nowMs = samplesSeen * 1000 / Features.RATE
        val fire = best != null && best.score >= limit && nowMs - lastFireAt >= cooldownMs
        if (fire) lastFireAt = nowMs
        return Reading(best, limit, fire)
    }
}
