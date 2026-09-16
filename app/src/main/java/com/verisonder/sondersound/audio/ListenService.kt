package com.verisonder.sondersound.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.verisonder.sondersound.R
import com.verisonder.sondersound.Settings
import com.verisonder.sondersound.clips.DetectionLog
import com.verisonder.sondersound.detect.Features
import com.verisonder.sondersound.detect.Sounds
import com.verisonder.sondersound.detect.StreamDetector
import com.verisonder.sondersound.sound.SoundStore
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import com.verisonder.sondersound.tile.ListenTile
import com.verisonder.sondersound.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the microphone and the rolling buffer. Audio is kept in memory only.
 *
 * Since Android 14 a microphone foreground service may only start while the app is on
 * screen, so this is never restarted by the system (START_NOT_STICKY). If it dies, the
 * app restarts it the next time it is opened, and the main screen shows it as off until
 * then, because the screen reads [state] rather than the saved switch.
 */
class ListenService : Service() {

    data class State(
        val running: Boolean = false,
        val heldSeconds: Int = 0,
        val note: String? = null,
        /** Best match in the last evaluation with sound in it, or null in silence. */
        val score: Float? = null,
        val needed: Float = 0f,
        val sounds: Int = 0,
    )

    companion object {
        const val RATE = 16_000
        private const val CHANNEL = "listening"
        private const val HEARD_CHANNEL = "heard"
        private const val HEARD_ID = 2
        /** How much audio before the moment of a match is kept with the detection. */
        private const val CLIP_SECONDS = 4
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY = "com.verisonder.sondersound.PLAY"
        private const val ACTION_STOP = "com.verisonder.sondersound.STOP"

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state

        @Volatile
        private var ring: RingBuffer? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ListenService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenService::class.java))
        }

        /** The buffer as it is now, oldest first, or null if nothing is held. */
        fun freeze(): ShortArray? = ring?.snapshot()?.takeIf { it.isNotEmpty() }

        /** Applies a changed buffer length to a running service at once. */
        fun setSeconds(seconds: Int) {
            ring?.let {
                it.resize(seconds * RATE)
                _state.value = _state.value.copy(heldSeconds = it.held() / RATE)
            }
        }

        private fun note(text: String?) {
            _state.value = _state.value.copy(note = text)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private var worker: Thread? = null
    @Volatile private var recording = false
    /** Chunks from the mic thread to the detector thread. Dropped, not queued, if it falls behind. */
    private val chunks = ArrayBlockingQueue<ShortArray>(50)

    private val silencing = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val session = record?.audioSessionId ?: return
            // Android silences a background recorder while another app, a call for
            // instance, takes the microphone. Nothing fails; the samples are just zero.
            val silenced = configs.any { it.clientAudioSessionId == session && it.isClientSilenced }
            note(if (silenced) "Paused while another app uses the mic." else null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                freeze()?.let { ClipPlayer.play(this, it) }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                Settings.setListening(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (recording) return START_NOT_STICKY

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            note("Microphone permission is off.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            // Android may refuse a microphone service started from the background, for
            // instance from the tile. The switch stays on, so opening the app starts it.
            note("Android refused to start. Open the app and switch on.")
            postStartRefused()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!startRecording()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // Checked in onStartCommand before this runs.
    private fun startRecording(): Boolean {
        val seconds = Settings.bufferSeconds(this)
        val buffer = RingBuffer(seconds * RATE)

        val minBuffer = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, RATE / 5 * 2),
            )
        }.getOrNull()

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            note("Microphone unavailable.")
            return false
        }

        recorder.startRecording()
        record = recorder
        ring = buffer
        recording = true
        getSystemService(AudioManager::class.java).registerAudioRecordingCallback(silencing, main)
        _state.value = State(running = true, needed = Features.threshold(Settings.sensitivity(this)))
        ListenTile.update(this)

        reader = Thread({
            val chunk = ShortArray(RATE / 10)
            var lastPublish = 0L
            while (recording) {
                val n = recorder.read(chunk, 0, chunk.size)
                if (n < 0) {
                    note("Microphone stopped (error $n).")
                    break
                }
                if (n > 0) {
                    buffer.write(chunk, n)
                    chunks.offer(chunk.copyOf(n))
                }
                val now = System.currentTimeMillis()
                if (now - lastPublish >= 1000) {
                    lastPublish = now
                    _state.value = _state.value.copy(heldSeconds = buffer.held() / RATE)
                }
            }
        }, "sondersound-mic").apply { start() }

        worker = Thread({ detectLoop() }, "sondersound-detect").apply { start() }
        return true
    }

    private fun detectLoop() {
        val detector = runCatching {
            val matcher = Sounds.matcher(this)
            StreamDetector(
                matcher = matcher,
                sounds = { Sounds.enrolled(this) },
                threshold = { Features.threshold(Settings.sensitivity(this)) },
            )
        }.getOrElse {
            note(Sounds.describe(it))
            return
        }
        while (recording) {
            val chunk = chunks.poll(200, TimeUnit.MILLISECONDS) ?: continue
            val readings = runCatching { detector.feed(chunk) }.getOrElse {
                note(Sounds.describe(it))
                emptyList()
            }
            for (r in readings) {
                _state.value = _state.value.copy(
                    score = r.best?.score,
                    needed = r.threshold,
                    sounds = Sounds.enrolled(this).size,
                )
                if (r.fired && r.best != null) onHeard(r.best.id, r.best.name, r.best.score, r.threshold)
            }
        }
    }

    private fun onHeard(id: String, sound: String, score: Float, needed: Float) {
        // Snooze silences the alert and keeps nothing.
        if (Settings.snoozeUntil(this) > System.currentTimeMillis()) return
        Alert.fire(this, SoundStore.actions(this, id))
        val all = ring?.snapshot() ?: ShortArray(0)
        val keep = minOf(all.size, CLIP_SECONDS * RATE)
        DetectionLog.add(this, sound, score, needed, all.copyOfRange(all.size - keep, all.size))
        postHeard(sound)
    }

    private fun postStartRefused() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(HEARD_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(HEARD_CHANNEL, getString(R.string.heard_channel), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { setSound(null, null) }
            )
        }
        val open = PendingIntent.getActivity(
            this, 4, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, HEARD_CHANNEL)
            .setSmallIcon(R.drawable.ic_bars)
            .setContentTitle(getString(R.string.start_refused))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(HEARD_ID + 1, notification) }
    }

    private fun postHeard(sound: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(HEARD_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(HEARD_CHANNEL, getString(R.string.heard_channel), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { setSound(null, null) } // The chime is the sound.
            )
        }
        val open = PendingIntent.getActivity(
            this, 3, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, HEARD_CHANNEL)
            .setSmallIcon(R.drawable.ic_bars)
            .setContentTitle(getString(R.string.heard_notification, sound))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(HEARD_ID, notification) }
    }

    override fun onDestroy() {
        recording = false
        reader?.join(500)
        reader = null
        worker?.join(500)
        worker = null
        chunks.clear()
        runCatching { getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(silencing) }
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        ring = null
        val keptNote = _state.value.note
        _state.value = State(note = keptNote)
        ListenTile.update(this)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.listening_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val play = PendingIntent.getService(
            this, 1, Intent(this, ListenService::class.java).setAction(ACTION_PLAY), flags,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, ListenService::class.java).setAction(ACTION_STOP), flags,
        )
        val seconds = Settings.bufferSeconds(this)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bars)
            .setContentTitle(getString(R.string.listening_notification))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Play last $seconds s", play)
            .addAction(0, "Stop", stop)
            .build()
    }
}
