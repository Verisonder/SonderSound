package com.verisonder.sondersound.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FeaturesTest {

    private fun tone(samples: Int, amplitude: Float) =
        FloatArray(samples) { (amplitude * sin(2 * PI * 440 * it / Features.RATE)).toFloat() }

    private fun silence(samples: Int) = FloatArray(samples) { ((it % 5) - 2).toFloat() }

    @Test
    fun thresholdRunsFromCautiousToEager() {
        assertEquals(0.75f, Features.threshold(0f), 1e-6f)
        assertEquals(0.625f, Features.threshold(0.5f), 1e-6f)
        assertEquals(0.50f, Features.threshold(1f), 1e-6f)
        assertEquals(0.50f, Features.threshold(3f), 1e-6f)
    }

    @Test
    fun aQuietWordInAQuietRoomIsWorthEvaluating() {
        val x = silence(8000) + tone(3200, 300f) + silence(8000)
        assertTrue(Features.worthEvaluating(x))
    }

    @Test
    fun steadyNoiseAndSilenceAreNot() {
        assertFalse(Features.worthEvaluating(silence(19200)))
        assertFalse(Features.worthEvaluating(tone(19200, 3000f)))
    }

    @Test
    fun percentileMatchesNumpy() {
        // numpy.percentile([1,2,3,4,5,6,7,8,9,10], 10) == 1.9
        val sorted = DoubleArray(10) { (it + 1).toDouble() }
        assertEquals(1.9, Features.percentile(sorted, 0.10), 1e-9)
    }

    @Test
    fun trimCutsSilenceAroundTheSound() {
        val x = silence(8000) + tone(4800, 8000f) + silence(8000)
        val trimmed = Features.trim(x)
        // Same length the Python replay gives for this signal.
        assertEquals(6960, trimmed.size)
    }

    @Test
    fun padCentresAndKeepsLongInput() {
        val x = tone(1000, 5000f)
        val padded = Features.pad(x)
        assertEquals(Features.WINDOW, padded.size)
        val left = (Features.WINDOW - 1000) / 2
        assertEquals(x[10], padded[left + 10], 0f)
        val long = tone(Features.WINDOW + 5, 1f)
        assertEquals(long.size, Features.pad(long).size)
    }

    @Test
    fun embeddingStartsStepThroughTheWindow() {
        assertEquals(listOf(0, 8, 16, 24, 32, 40), Features.embeddingStarts(117))
        assertEquals(emptyList<Int>(), Features.embeddingStarts(70))
    }

    @Test
    fun speechMaskKeepsOnlyEmbeddingsOverSound() {
        // Sound in the first half only.
        val x = tone(9600, 8000f) + silence(9600)
        val mask = Features.speechMask(x, Features.embeddingStarts(117))
        assertTrue(mask[0])
        assertFalse(mask[5])
    }

    @Test
    fun centreRemovesTheBackground() {
        val bg = floatArrayOf(1f, 1f, 0f)
        val c = Features.centre(floatArrayOf(1f, 1f, 2f), bg)
        assertEquals(0f, c[0], 1e-6f)
        assertEquals(1f, c[2], 1e-6f)
    }
}
