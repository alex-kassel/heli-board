// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Interface for AI text transformation clients.
 */
interface VoiceAiClient {
    fun transform(
        rawTranscript: String,
        settings: VoiceSettings,
        config: VoiceAiConfig,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    )
}

/**
 * Standard OpenAI-compatible HTTP client for chat completions.
 * Supports direct BYOK (OpenAI, Groq, OpenRouter) and custom proxies (ab-box.de).
 */
class OpenAiCompatibleAiClient : VoiceAiClient {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun transform(
        rawTranscript: String,
        settings: VoiceSettings,
        config: VoiceAiConfig,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!config.hasUserConsented) {
            dispatchError(onError, "AI processing consent has not been granted.")
            return
        }

        if (config.apiKey.isBlank() && !config.isLocalOrProxyEndpoint) {
            dispatchError(onError, "API key is not configured. Please set it in Voice Settings.")
            return
        }

        val transcript = rawTranscript.trim()
        if (transcript.isEmpty()) {
            dispatchError(onError, "No transcript provided for AI transformation.")
            return
        }

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val baseUrl = config.apiEndpoint.trim().trimEnd('/')
                val fullUrl = if (baseUrl.endsWith("/chat/completions")) {
                    baseUrl
                } else {
                    "$baseUrl/chat/completions"
                }

                val url = URL(fullUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 25_000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (config.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                    }
                }

                val userMessage = VoicePromptBuilder.buildUserMessage(
                    rawTranscript = transcript,
                    targetLanguage = settings.outputLanguage,
                    recipientTone = settings.recipientTone,
                    polishLevel = settings.polishLevel
                )

                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", VoicePromptBuilder.SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                }

                val payload = JSONObject().apply {
                    put("model", config.model.trim())
                    put("temperature", 0.3)
                    put("messages", messages)
                }

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(responseText)
                    val choices = json.optJSONArray("choices")
                    val firstChoice = choices?.optJSONObject(0)
                    val message = firstChoice?.optJSONObject("message")
                    val content = message?.optString("content")?.trim()

                    if (!content.isNullOrBlank()) {
                        dispatchSuccess(onSuccess, content)
                    } else {
                        dispatchError(onError, "AI returned an empty response.")
                    }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    val errorMessage = parseErrorMessage(responseCode, errorBody)
                    Log.w(TAG, "AI API error $responseCode: $errorMessage")
                    dispatchError(onError, errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network request to AI endpoint failed", e)
                val msg = when {
                    e is java.net.SocketTimeoutException -> "AI request timed out. Check network connection."
                    e is java.net.UnknownHostException -> "Could not resolve AI host. Check endpoint URL."
                    else -> e.message ?: "Failed to connect to AI provider."
                }
                dispatchError(onError, msg)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun dispatchSuccess(onSuccess: (String) -> Unit, result: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onSuccess(result)
        } else {
            mainHandler.post { onSuccess(result) }
        }
    }

    private fun dispatchError(onError: (String) -> Unit, error: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onError(error)
        } else {
            mainHandler.post { onError(error) }
        }
    }

    companion object {
        private const val TAG = "VoiceAiClient"

        fun parseErrorMessage(statusCode: Int, errorBody: String): String {
            try {
                if (errorBody.isNotBlank()) {
                    val json = JSONObject(errorBody)
                    val errorObj = json.optJSONObject("error")
                    val msg = errorObj?.optString("message")
                    if (!msg.isNullOrBlank()) {
                        return msg
                    }
                }
            } catch (_: Exception) {}

            return when (statusCode) {
                401 -> "Invalid or missing API key (HTTP 401 Unauthorized)."
                403 -> "Access forbidden by AI provider (HTTP 403 Forbidden)."
                404 -> "Model or endpoint not found (HTTP 404 Not Found)."
                429 -> "Rate limit or quota exceeded (HTTP 429 Too Many Requests)."
                in 500..599 -> "AI server encountered an error (HTTP $statusCode)."
                else -> "AI request failed with HTTP $statusCode."
            }
        }
    }
}
