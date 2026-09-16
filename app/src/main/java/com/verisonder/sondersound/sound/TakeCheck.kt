package com.verisonder.sondersound.sound

import kotlin.math.abs

/** Pure checks on a recorded take, so a bad one is refused on screen rather than kept. */
object TakeCheck {
    /**
     * The loudest part must stand out from the quiet part of the take, the same test
     * listening uses, so a whisper or a take from across the room is accepted as long as
     * there is something in it. An absolute level would refuse exactly those.
     */

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
        val floats = FloatArray(pcm.size) { pcm[it].toFloat() }
        return when {
            !com.verisonder.sondersound.detect.Features.worthEvaluating(floats) -> Verdict.TOO_QUIET
            clipped.toDouble() / pcm.size > MAX_CLIPPED_SHARE -> Verdict.TOO_LOUD
            else -> Verdict.OK
        }
    }
}
