package com.verisonder.sondersound.transcribe

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {
    @Test
    fun headerDescribesTheData() {
        val wav = Wav.encode(shortArrayOf(1, -2, 3), 16000)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(50, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(42, b.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals(16000, b.getInt(24))
        assertEquals(16.toShort(), b.getShort(34))
        assertEquals(6, b.getInt(40))
        assertEquals((-2).toShort(), b.getShort(46))
    }
}
