package com.verisonder.sondersound.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays a frozen clip from memory. Takes transient audio focus, so the user's music pauses
 * for the length of the clip and resumes after.
 */
object ClipPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var audio: AudioManager? = null

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    fun play(context: Context, pcm: ShortArray) {
        main.post { start(context.applicationContext, pcm) }
    }

    fun stop() {
        main.post { release() }
    }

    private fun start(context: Context, pcm: ShortArray) {
        release()
        if (pcm.isEmpty()) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val manager = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) release()
            }
            .build()
        manager.requestAudioFocus(request)
        audio = manager
        focus = request

        val built = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ListenService.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        built.write(pcm, 0, pcm.size)
        built.notificationMarkerPosition = pcm.size
        built.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) = release()
            override fun onPeriodicNotification(t: AudioTrack?) = Unit
        }, main)
        built.play()
        track = built
        _playing.value = true
    }

    private fun release() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
        _playing.value = false
    }
}
