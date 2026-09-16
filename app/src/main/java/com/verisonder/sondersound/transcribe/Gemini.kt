package com.verisonder.sondersound.transcribe

import android.util.Base64
import com.verisonder.sondersound.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the Gemini API with the user's own key. No backend.
 *
 * Uses the Interactions endpoint, and falls back to generateContent if the account or model
 * does not offer it, since Google has been moving between the two.
 */
object Gemini {
    const val MODEL = "gemini-3.8-flash"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    sealed interface Result {
        data class Text(val text: String) : Result
        data object Rejected : Result
        data object Limit : Result
        data object Offline : Result
        data class Failed(val code: Int) : Result
    }

    enum class KeyCheck { WORKS, REJECTED, UNKNOWN }

    suspend fun checkKey(key: String): KeyCheck = withContext(Dispatchers.IO) {
        try {
            val c = open("$BASE/models?pageSize=1", key, "GET")
            when (c.responseCode) {
                in 200..299 -> KeyCheck.WORKS
                400, 401, 403 -> KeyCheck.REJECTED
                else -> KeyCheck.UNKNOWN
            }.also { c.disconnect() }
        } catch (e: IOException) {
            KeyCheck.UNKNOWN
        }
    }

    fun prompt(script: Settings.Script): String = buildString {
        append("Transcribe this audio word for word. Do not translate, summarise or add anything. ")
        append("Keep every language as it was spoken: Moroccan Darija, French, English, Arabic or any other. ")
        if (script == Settings.Script.LATIN) {
            append("Write Darija in Latin letters, the way Moroccans text (for example 3, 7 and 9 for Arabic sounds). ")
        } else {
            append("Write Darija in Arabic script. ")
        }
        append("Write [unclear] where speech cannot be made out. ")
        append("If there is no speech, reply with [no speech]. Reply with the transcript only.")
    }

    suspend fun transcribe(key: String, wav: ByteArray, script: Settings.Script): Result =
        withContext(Dispatchers.IO) {
            val audio = Base64.encodeToString(wav, Base64.NO_WRAP)
            val text = prompt(script)
            try {
                val first = post(
                    "$BASE/interactions", key,
                    JSONObject()
                        .put("model", MODEL)
                        .put(
                            "input", JSONArray()
                                .put(JSONObject().put("type", "text").put("text", text))
                                .put(JSONObject().put("type", "audio").put("data", audio).put("mime_type", "audio/wav"))
                        ),
                )
                if (first.first == 404) {
                    val fallback = post(
                        "$BASE/models/$MODEL:generateContent", key,
                        JSONObject().put(
                            "contents", JSONArray().put(
                                JSONObject().put(
                                    "parts", JSONArray()
                                        .put(JSONObject().put("text", text))
                                        .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", audio)))
                                )
                            )
                        ),
                    )
                    interpret(fallback.first, fallback.second)
                } else {
                    interpret(first.first, first.second)
                }
            } catch (e: IOException) {
                Result.Offline
            }
        }

    private fun interpret(code: Int, body: String): Result = when {
        code in 200..299 -> extractText(body)?.let { Result.Text(it) } ?: Result.Failed(code)
        code == 400 && body.contains("API_KEY", ignoreCase = true) -> Result.Rejected
        code == 401 || code == 403 -> Result.Rejected
        code == 429 -> Result.Limit
        else -> Result.Failed(code)
    }

    /** Pulls the model's text out of either response shape. */
    fun extractText(body: String): String? = runCatching {
        val json = JSONObject(body)
        json.optString("output_text").takeIf { it.isNotBlank() }?.let { return it.trim() }

        val parts = mutableListOf<String>()
        json.optJSONArray("steps")?.let { steps ->
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                if (step.optString("type") != "model_output") continue
                val content = step.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val item = content.getJSONObject(j)
                    if (item.optString("type") == "text") parts += item.optString("text")
                }
            }
        }
        json.optJSONArray("outputs")?.let { outputs ->
            for (i in 0 until outputs.length()) {
                val item = outputs.optJSONObject(i) ?: continue
                if (item.optString("type") == "text") parts += item.optString("text")
            }
        }
        json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.let { p ->
            for (i in 0 until p.length()) p.optJSONObject(i)?.optString("text")?.let { parts += it }
        }
        parts.joinToString("").trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun open(url: String, key: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("x-goog-api-key", key)
        }

    private fun post(url: String, key: String, body: JSONObject): Pair<Int, String> {
        val c = open(url, key, "POST")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }
}
