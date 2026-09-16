package com.verisonder.sondersound.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import com.verisonder.sondersound.Settings
import com.verisonder.sondersound.sound.SoundStore
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * What happens in the earphones when a sound is heard: music lowered or paused, then a
 * short chime. The chime is generated, so there is no audio file to ship or lose.
 */
object Alert {
    private val main = Handler(Looper.getMainLooper())
    private var focus: AudioFocusRequest? = null

    private const val DUCK_HOLD_MS = 3_000L

    fun fire(context: Context, actions: SoundStore.Actions) {
        val app = context.applicationContext
        main.post { start(app, actions) }
    }

    private fun start(context: Context, actions: SoundStore.Actions) {
        val audio = context.getSystemService(AudioManager::class.java)
        release(audio)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (actions.vibrate) vibrate(context)

        if (actions.music != Settings.OnMatch.NOTHING) {
            val pause = actions.music == Settings.OnMatch.PAUSE
            // Pausing takes focus for good, so music does not resume on its own; lowering
            // takes it briefly and gives it back.
            val request = AudioFocusRequest.Builder(
                if (pause) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .build()
            audio.requestAudioFocus(request)
            focus = request
            main.postDelayed({ release(audio) }, if (pause) 800L else DUCK_HOLD_MS)
        }

        chime(actions.chime, attributes)
    }

    /**
     * Two short buzzes, declared as an alarm vibration. Android may not deliver a background
     * app's vibration without a usage, and alarm also reaches a phone on Do Not Disturb.
     */
    private fun vibrate(context: Context) {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                // VibratorManager is 31, VibrationAttributes is 33.
                context.getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            }
        }
    }

    private fun release(audio: AudioManager) {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun chime(kind: Settings.Chime, attributes: AudioAttributes) {
        val notes = when (kind) {
            Settings.Chime.OFF -> return
            Settings.Chime.SOFT -> listOf(660.0 to 180, 880.0 to 260)
            Settings.Chime.BRIGHT -> listOf(988.0 to 120, 1319.0 to 120, 1760.0 to 220)
        }
        val rate = 44_100
        val pcm = ArrayList<Short>()
        for ((freq, ms) in notes) {
            val n = rate * ms / 1000
            for (i in 0 until n) {
                // A quick attack and a longer release, so it does not click.
                val env = min(1.0, i / (rate * 0.01)) * min(1.0, (n - i) / (rate * 0.08))
                pcm += (sin(2 * PI * freq * i / rate) * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        val data = pcm.toShortArray()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        track.play()
        main.postDelayed({ runCatching { track.stop() }; track.release() }, notes.sumOf { it.second }.toLong() + 200)
    }
}
