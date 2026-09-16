package com.verisonder.sondersound.sound

import org.junit.Assert.assertEquals
import org.junit.Test

class TakeCheckTest {

    @Test
    fun emptyIsTooQuiet() {
        assertEquals(TakeCheck.Verdict.TOO_QUIET, TakeCheck.judge(ShortArray(0)))
    }

    @Test
    fun roomNoiseIsTooQuiet() {
        val pcm = ShortArray(32000) { ((it % 7) * 100 - 300).toShort() }
        assertEquals(TakeCheck.Verdict.TOO_QUIET, TakeCheck.judge(pcm))
    }

    @Test
    fun aClearVoiceIsAccepted() {
        val pcm = ShortArray(32000) { if (it in 10000..14000) (if (it % 2 == 0) 9000 else -9000).toShort() else 0 }
        assertEquals(TakeCheck.Verdict.OK, TakeCheck.judge(pcm))
    }

    @Test
    fun heavyClippingIsTooLoud() {
        val pcm = ShortArray(32000) { if (it < 1000) 32767 else 0 }
        assertEquals(TakeCheck.Verdict.TOO_LOUD, TakeCheck.judge(pcm))
    }
}
