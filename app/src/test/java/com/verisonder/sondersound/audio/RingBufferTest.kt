package com.verisonder.sondersound.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RingBufferTest {

    private fun shorts(vararg v: Int) = ShortArray(v.size) { v[it].toShort() }

    @Test
    fun holdsWhatWasWrittenBeforeItFills() {
        val ring = RingBuffer(5)
        ring.write(shorts(1, 2, 3))
        assertEquals(3, ring.held())
        assertArrayEquals(shorts(1, 2, 3), ring.snapshot())
    }

    @Test
    fun keepsOnlyTheNewestOnceFull() {
        val ring = RingBuffer(4)
        ring.write(shorts(1, 2, 3))
        ring.write(shorts(4, 5, 6))
        assertEquals(4, ring.held())
        assertArrayEquals(shorts(3, 4, 5, 6), ring.snapshot())
    }

    @Test
    fun writesOnlyTheCountGiven() {
        val ring = RingBuffer(4)
        ring.write(shorts(1, 2, 3, 4), count = 2)
        assertArrayEquals(shorts(1, 2), ring.snapshot())
    }

    @Test
    fun snapshotIsACopy() {
        val ring = RingBuffer(3)
        ring.write(shorts(1, 2, 3))
        val frozen = ring.snapshot()
        ring.write(shorts(9, 9, 9))
        assertArrayEquals(shorts(1, 2, 3), frozen)
    }

    @Test
    fun shrinkingKeepsTheNewest() {
        val ring = RingBuffer(6)
        ring.write(shorts(1, 2, 3, 4, 5, 6, 7))
        ring.resize(3)
        assertArrayEquals(shorts(5, 6, 7), ring.snapshot())
        ring.write(shorts(8))
        assertArrayEquals(shorts(6, 7, 8), ring.snapshot())
    }

    @Test
    fun growingKeepsEverythingAndFillsFromNow() {
        val ring = RingBuffer(3)
        ring.write(shorts(1, 2, 3, 4))
        ring.resize(6)
        assertEquals(3, ring.held())
        ring.write(shorts(5, 6, 7, 8))
        assertArrayEquals(shorts(3, 4, 5, 6, 7, 8), ring.snapshot())
    }
}
