// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Configuration for OpenAI-compatible AI provider (BYOK or custom proxy gateway).
 * Task 005 / Decision D-004.
 */
data class VoiceAiConfig(
    val apiEndpoint: String = DEFAULT_ENDPOINT,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val hasUserConsented: Boolean = false
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() || isLocalOrProxyEndpoint

    val isLocalOrProxyEndpoint: Boolean
        get() = apiEndpoint.contains("localhost") ||
                apiEndpoint.contains("127.0.0.1") ||
                apiEndpoint.contains("10.0.2.2") ||
                apiEndpoint.contains("ab-box.de")

    fun save(prefs: SharedPreferences) {
        prefs.edit {
            putString(PREF_AI_ENDPOINT, apiEndpoint)
            putString(PREF_AI_KEY, apiKey)
            putString(PREF_AI_MODEL, model)
            putBoolean(PREF_AI_CONSENT, hasUserConsented)
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        const val PREF_AI_ENDPOINT = "pref_voice_ai_endpoint"
        const val PREF_AI_KEY = "pref_voice_ai_key"
        const val PREF_AI_MODEL = "pref_voice_ai_model"
        const val PREF_AI_CONSENT = "pref_voice_ai_consent"

        fun read(prefs: SharedPreferences): VoiceAiConfig {
            return VoiceAiConfig(
                apiEndpoint = prefs.getString(PREF_AI_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
                apiKey = prefs.getString(PREF_AI_KEY, "") ?: "",
                model = prefs.getString(PREF_AI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
                hasUserConsented = prefs.getBoolean(PREF_AI_CONSENT, false)
            )
        }
    }
}
