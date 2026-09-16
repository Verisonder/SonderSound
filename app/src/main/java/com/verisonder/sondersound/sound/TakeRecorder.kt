package com.verisonder.sondersound.sound

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.verisonder.sondersound.audio.ListenService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TakeRecorder {
    const val TAKE_MS = 2000

    /**
     * Records one fixed-length take. Throws if the microphone cannot be opened, which the
     * screen turns into a line of text.
     */
    @SuppressLint("MissingPermission") // Setup does not reach this step without it.
    suspend fun record(durationMs: Int = TAKE_MS): ShortArray = withContext(Dispatchers.IO) {
        val rate = ListenService.RATE
        val total = rate * durationMs / 1000
        val minBuffer = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, total),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone unavailable")
        }
        val out = ShortArray(total)
        var got = 0
        recorder.startRecording()
        try {
            while (got < total) {
                val n = recorder.read(out, got, total - got)
                if (n < 0) break
                got += n
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        out.copyOf(got)
    }
}
