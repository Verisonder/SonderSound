package com.verisonder.sondersound.detect

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * A fake embedder that always returns one fixed direction, so each mode's arithmetic can be
 * checked by hand.
 */
class MatchModeTest {

    private val heard = floatArrayOf(1f, 0f, 0f)
    private val embedder = Embedder { _, starts -> starts(117).map { heard } }
    private val matcher = Matcher(embedder, FloatArray(3))

    // Two takes: one exactly what is heard, one at right angles. Average sits between them.
    private val takeA = floatArrayOf(1f, 0f, 0f)
    private val takeB = floatArrayOf(0f, 1f, 0f)
    private val sound = Matcher.Enrolled(
        "a", "Ali",
        Features.normalize(Features.mean(listOf(takeA, takeB))),
        listOf(takeA, takeB),
    )

    private val window = FloatArray(Features.WINDOW) { i ->
        if (i in 8000..11000) (6000 * sin(2 * PI * 300 * i / 16000)).toFloat() else ((i % 5) - 2).toFloat()
    }

    @Test
    fun averageComparesWithTheBlend() {
        val best = matcher.score(window, listOf(sound), Matcher.Mode.AVERAGE)!!
        assertEquals(0.7071f, best.score, 1e-3f)
        assertEquals(Matcher.Mode.AVERAGE, best.via)
    }

    @Test
    fun eachComparesWithTheClosestTake() {
        val best = matcher.score(window, listOf(sound), Matcher.Mode.EACH)!!
        assertEquals(1f, best.score, 1e-4f)
        assertEquals(Matcher.Mode.EACH, best.via)
    }

    @Test
    fun bothTakesTheHigherAndSaysWhich() {
        val best = matcher.score(window, listOf(sound), Matcher.Mode.BOTH)!!
        assertEquals(1f, best.score, 1e-4f)
        assertEquals(Matcher.Mode.EACH, best.via)
    }
}
