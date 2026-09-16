package com.verisonder.sondersound.audio

/**
 * The last N samples of 16-bit PCM, overwritten continuously. Pure Kotlin so it can be
 * tested without a device.
 *
 * Written by the recording thread, read by whoever freezes a clip, so every method is
 * synchronised. The lock is held for at most one read's worth of samples.
 */
class RingBuffer(capacity: Int) {
    private var data = ShortArray(capacity.coerceAtLeast(1))
    private var head = 0
    private var size = 0

    val capacity: Int
        @Synchronized get() = data.size

    @Synchronized
    fun held(): Int = size

    @Synchronized
    fun write(src: ShortArray, count: Int = src.size) {
        val cap = data.size
        for (i in 0 until count.coerceAtMost(src.size)) {
            data[head] = src[i]
            head = (head + 1) % cap
            if (size < cap) size++
        }
    }

    /** Oldest first. A copy: the caller may keep it while recording carries on. */
    @Synchronized
    fun snapshot(): ShortArray {
        val cap = data.size
        val out = ShortArray(size)
        val start = (head - size + cap) % cap
        for (i in 0 until size) out[i] = data[(start + i) % cap]
        return out
    }

    /**
     * Shrinking keeps the newest samples and drops the rest at once. Growing keeps what is
     * held; the extra room fills from now on.
     */
    @Synchronized
    fun resize(newCapacity: Int) {
        val cap = newCapacity.coerceAtLeast(1)
        if (cap == data.size) return
        val all = snapshot()
        val keep = if (all.size > cap) all.copyOfRange(all.size - cap, all.size) else all
        data = ShortArray(cap)
        keep.copyInto(data)
        size = keep.size
        head = size % cap
    }

    @Synchronized
    fun clear() {
        head = 0
        size = 0
    }
}
