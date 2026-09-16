package com.verisonder.sondersound.sound

import kotlin.math.abs

/** Pure checks on a recorded take, so a bad one is refused on screen rather than kept. */
object TakeCheck {
    /** About -27 dBFS. Below this the take is mostly room noise. */
    const val MIN_PEAK = 1500

    /** Clipped samples beyond this share mean the voice was too close or too loud. */
    const val MAX_CLIPPED_SHARE = 0.01

    enum class Verdict { OK, TOO_QUIET, TOO_LOUD }

    fun judge(pcm: ShortArray): Verdict {
        if (pcm.isEmpty()) return Verdict.TOO_QUIET
        var peak = 0
        var clipped = 0
        for (s in pcm) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
            if (a >= 32000) clipped++
        }
        return when {
            peak < MIN_PEAK -> Verdict.TOO_QUIET
            clipped.toDouble() / pcm.size > MAX_CLIPPED_SHARE -> Verdict.TOO_LOUD
            else -> Verdict.OK
        }
    }
}
