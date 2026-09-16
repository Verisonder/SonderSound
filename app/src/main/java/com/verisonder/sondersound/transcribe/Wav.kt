package com.verisonder.sondersound.transcribe

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit mono PCM wrapped in a WAV header. Pure, for tests. */
object Wav {
    fun encode(pcm: ShortArray, rate: Int): ByteArray {
        val data = pcm.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt(36 + data)
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1)            // PCM
        b.putShort(1)            // mono
        b.putInt(rate)
        b.putInt(rate * 2)       // byte rate
        b.putShort(2)            // block align
        b.putShort(16)           // bits per sample
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(data)
        for (s in pcm) b.putShort(s)
        return b.array()
    }
}
