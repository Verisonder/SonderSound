package com.verisonder.sondersound.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * A fake embedder: every embedding points along axis 0 when the window is loud and along
 * axis 1 when it is quiet, so a loud tone "matches" a sound enrolled on axis 0.
 */
class StreamDetectorTest {

    private val background = FloatArray(4)

    private val embedder = Embedder { window, starts ->
        val loud = Features.rms(window) > 1000
        starts(117).map { if (loud) floatArrayOf(1f, 0f, 0f, 0f) else floatArrayOf(0f, 1f, 0f, 0f) }
    }

    private val sound = Matcher.Enrolled("a", "Ali", floatArrayOf(1f, 0f, 0f, 0f))

    private fun tone(samples: Int, amplitude: Double) =
        ShortArray(samples) { (amplitude * sin(2 * PI * 300 * it / 16000)).toInt().toShort() }

    private fun run(audio: ShortArray, threshold: Float = 0.6f): List<StreamDetector.Reading> {
        val detector = StreamDetector(Matcher(embedder, background), { listOf(sound) }, { threshold })
        val out = mutableListOf<StreamDetector.Reading>()
        var i = 0
        while (i < audio.size) {
            val n = minOf(1600, audio.size - i)
            out += detector.feed(audio.copyOfRange(i, i + n))
            i += n
        }
        return out
    }

    @Test
    fun evaluatesEvery240msOnceTheWindowIsFull() {
        val readings = run(ShortArray(16000 * 3))
        // 3 s = 48000 samples; the first evaluation needs 19200, then one per 3840.
        assertEquals(1 + (48000 - 19200) / 3840, readings.size)
    }

    @Test
    fun silenceIsNeverScored() {
        assertTrue(run(ShortArray(16000 * 3)).all { it.best == null && !it.fired })
    }

    @Test
    fun aMatchFiresOnceThenCoolsDown() {
        val audio = tone(16000 * 2, 8000.0) + ShortArray(16000 * 2)
        val fired = run(audio).count { it.fired }
        assertEquals(1, fired)
    }

    @Test
    fun twoMatchesFarApartBothFire() {
        val audio = tone(16000 * 2, 8000.0) + ShortArray(16000 * 5) + tone(16000 * 2, 8000.0)
        assertEquals(2, run(audio).count { it.fired })
    }

    @Test
    fun belowThresholdDoesNotFire() {
        val audio = tone(16000 * 2, 8000.0)
        assertTrue(run(audio, threshold = 1.5f).none { it.fired })
    }
}
