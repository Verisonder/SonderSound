package com.verisonder.sondersound.transcribe

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.verisonder.sondersound.KeyVault
import com.verisonder.sondersound.Settings
import com.verisonder.sondersound.audio.ListenService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One transcription at a time, in the background. The clip stays in memory only; if the
 * phone is offline it waits for a connection and gives up after [GIVE_UP_MS].
 */
object Transcriber {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data object Waiting : State
        data class Done(val text: String) : State
        data class Error(val line: String) : State
    }

    private const val GIVE_UP_MS = 10 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var pending: ShortArray? = null
    private var pendingSince = 0L
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context, pcm: ShortArray) {
        val app = context.applicationContext
        pending = pcm
        pendingSince = System.currentTimeMillis()
        run(app)
    }

    fun dismiss(context: Context) {
        pending = null
        unwatch(context.applicationContext)
        _state.value = State.Idle
    }

    private fun run(context: Context) {
        val pcm = pending ?: return
        val key = KeyVault.geminiKey(context)
        if (key == null) {
            _state.value = State.Error("Add a Gemini key in settings.")
            pending = null
            return
        }
        _state.value = State.Working
        scope.launch {
            val result = Gemini.transcribe(key, Wav.encode(pcm, ListenService.RATE), Settings.darijaScript(context))
            when (result) {
                is Gemini.Result.Text -> finish(context, State.Done(result.text))
                Gemini.Result.Rejected -> finish(context, State.Error("Key rejected. Check it in settings."))
                Gemini.Result.Limit -> finish(context, State.Error("Gemini limit reached. Try later."))
                is Gemini.Result.Failed -> finish(context, State.Error("Gemini failed (${result.code})."))
                Gemini.Result.Offline -> {
                    if (System.currentTimeMillis() - pendingSince > GIVE_UP_MS) {
                        finish(context, State.Error("Offline too long. Clip dropped."))
                    } else {
                        _state.value = State.Waiting
                        watch(context)
                    }
                }
            }
        }
    }

    private fun finish(context: Context, state: State) {
        pending = null
        unwatch(context)
        _state.value = state
    }

    private fun watch(context: Context) {
        if (callback != null) return
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    if (pending != null && _state.value == State.Waiting) run(context)
                }
            }
        }
        callback = cb
        manager.registerDefaultNetworkCallback(cb)
    }

    private fun unwatch(context: Context) {
        val cb = callback ?: return
        runCatching { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        callback = null
    }
}
