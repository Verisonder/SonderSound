package com.verisonder.sondersound

import android.content.Context

/**
 * Plain SharedPreferences. The Gemini key is not here; it lives in [KeyVault].
 *
 * Everything optional defaults to off.
 */
object Settings {
    private const val FILE = "settings"

    private const val SETUP_DONE = "setup_done"
    private const val LISTENING = "listening"
    private const val SNOOZE_UNTIL = "snooze_until"
    private const val BUFFER_SECONDS = "buffer_seconds"
    private const val SENSITIVITY = "sensitivity"
    private const val ON_MATCH = "on_match"
    private const val SAVE_CLIPS = "save_clips"
    private const val KEEP_HOURS = "keep_hours"
    private const val DARIJA_SCRIPT = "darija_script"
    private const val CHIME = "chime"
    private const val MATCH_MODE = "match_mode"
    private const val TRANSCRIBE_OK = "transcribe_disclosed"

    const val DEFAULT_SENSITIVITY = 0.5f
    val BUFFER_CHOICES = listOf(15, 30)

    /** 0 means keep forever. */
    val KEEP_CHOICES = listOf(1, 24, 24 * 7, 0)

    /** What a detection does to music playing on the phone. */
    enum class OnMatch { LOWER, PAUSE, NOTHING }
    enum class Script { ARABIC, LATIN }
    enum class Chime { SOFT, BRIGHT, OFF }

    private fun of(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setupDone(context: Context): Boolean = of(context).getBoolean(SETUP_DONE, false)
    fun setSetupDone(context: Context, value: Boolean) =
        of(context).edit().putBoolean(SETUP_DONE, value).apply()

    fun listening(context: Context): Boolean = of(context).getBoolean(LISTENING, false)
    fun setListening(context: Context, value: Boolean) =
        of(context).edit().putBoolean(LISTENING, value).apply()

    fun snoozeUntil(context: Context): Long = of(context).getLong(SNOOZE_UNTIL, 0L)
    fun setSnoozeUntil(context: Context, value: Long) =
        of(context).edit().putLong(SNOOZE_UNTIL, value).apply()

    fun bufferSeconds(context: Context): Int =
        of(context).getInt(BUFFER_SECONDS, BUFFER_CHOICES.first()).takeIf { it in BUFFER_CHOICES }
            ?: BUFFER_CHOICES.first()
    fun setBufferSeconds(context: Context, value: Int) =
        of(context).edit().putInt(BUFFER_SECONDS, value).apply()

    fun sensitivity(context: Context): Float =
        of(context).getFloat(SENSITIVITY, DEFAULT_SENSITIVITY).coerceIn(0f, 1f)
    fun setSensitivity(context: Context, value: Float) =
        of(context).edit().putFloat(SENSITIVITY, value.coerceIn(0f, 1f)).apply()

    /** Only the starting value for a new sound. Each sound keeps its own actions. */
    fun onMatch(context: Context): OnMatch =
        runCatching { OnMatch.valueOf(of(context).getString(ON_MATCH, null) ?: "") }
            .getOrDefault(OnMatch.LOWER)
    fun setOnMatch(context: Context, value: OnMatch) =
        of(context).edit().putString(ON_MATCH, value.name).apply()

    fun saveClips(context: Context): Boolean = of(context).getBoolean(SAVE_CLIPS, false)
    fun setSaveClips(context: Context, value: Boolean) =
        of(context).edit().putBoolean(SAVE_CLIPS, value).apply()

    fun keepHours(context: Context): Int =
        of(context).getInt(KEEP_HOURS, 24).takeIf { it in KEEP_CHOICES } ?: 24
    fun setKeepHours(context: Context, value: Int) =
        of(context).edit().putInt(KEEP_HOURS, value).apply()

    fun darijaScript(context: Context): Script =
        runCatching { Script.valueOf(of(context).getString(DARIJA_SCRIPT, null) ?: "") }
            .getOrDefault(Script.ARABIC)
    fun setDarijaScript(context: Context, value: Script) =
        of(context).edit().putString(DARIJA_SCRIPT, value.name).apply()

    fun matchMode(context: Context): com.verisonder.sondersound.detect.Matcher.Mode =
        runCatching { com.verisonder.sondersound.detect.Matcher.Mode.valueOf(of(context).getString(MATCH_MODE, null) ?: "") }
            .getOrDefault(com.verisonder.sondersound.detect.Matcher.Mode.AVERAGE)
    fun setMatchMode(context: Context, value: com.verisonder.sondersound.detect.Matcher.Mode) =
        of(context).edit().putString(MATCH_MODE, value.name).apply()

    fun chime(context: Context): Chime =
        runCatching { Chime.valueOf(of(context).getString(CHIME, null) ?: "") }
            .getOrDefault(Chime.SOFT)
    fun setChime(context: Context, value: Chime) =
        of(context).edit().putString(CHIME, value.name).apply()

    /** The one-time notice that transcribed clips go to Google has been accepted. */
    fun transcribeDisclosed(context: Context): Boolean = of(context).getBoolean(TRANSCRIBE_OK, false)
    fun setTranscribeDisclosed(context: Context) =
        of(context).edit().putBoolean(TRANSCRIBE_OK, true).apply()
}
