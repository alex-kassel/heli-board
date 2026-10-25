// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Output language for AI Voice transcription and translation.
 * Explicitly chosen by user (RU, DE, EN), regardless of spoken input language.
 */
enum class VoiceOutputLanguage(val code: String, val title: String) {
    RU("ru", "Russian (RU)"),
    DE("de", "German (DE)"),
    EN("en", "English (EN)");

    companion object {
        fun fromCode(code: String?): VoiceOutputLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RU
    }
}

/**
 * Voice processing mode:
 * - PLAIN: Verbatim transcription (no rewriting).
 * - AI: AI-assisted text transformation.
 */
enum class VoiceMode(val code: String, val title: String) {
    AI("ai", "AI-assisted rewrite"),
    PLAIN("plain", "Plain transcription (verbatim)");

    companion object {
        fun fromCode(code: String?): VoiceMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AI
    }
}

/**
 * Target recipient/tone for AI rewriting.
 */
enum class RecipientTone(val code: String, val title: String) {
    UNCHANGED("unchanged", "Unchanged"),
    FRIEND("friend", "Friend (casual, warm)"),
    COLLEAGUE("colleague", "Colleague (direct, professional)"),
    MANAGER("manager", "Manager (clear, respectful)"),
    CLIENT("client", "Client (polite, formal)");

    companion object {
        fun fromCode(code: String?): RecipientTone =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: FRIEND
    }
}

/**
 * Polish level for rewriting intensity.
 */
enum class PolishLevel(val code: String, val title: String) {
    MINIMAL("minimal", "Minimal (spelling/punctuation only)"),
    BALANCED("balanced", "Balanced (natural phrasing)"),
    STRONG("strong", "Strong (business rewrite)");

    companion object {
        fun fromCode(code: String?): PolishLevel =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: BALANCED
    }
}

/**
 * Model representing persistent voice settings across sessions and apps.
 */
data class VoiceSettings(
    val outputLanguage: VoiceOutputLanguage = VoiceOutputLanguage.RU,
    val mode: VoiceMode = VoiceMode.AI,
    val recipientTone: RecipientTone = RecipientTone.FRIEND,
    val polishLevel: PolishLevel = PolishLevel.BALANCED,
    val isPreviewEnabled: Boolean = true
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit {
            putString(PREF_VOICE_OUTPUT_LANGUAGE, outputLanguage.code)
            putString(PREF_VOICE_MODE, mode.code)
            putString(PREF_VOICE_RECIPIENT_TONE, recipientTone.code)
            putString(PREF_VOICE_POLISH_LEVEL, polishLevel.code)
            putBoolean(PREF_VOICE_PREVIEW_ENABLED, isPreviewEnabled)
        }
    }

    fun toSummary(): String {
        return if (mode == VoiceMode.PLAIN) {
            "${outputLanguage.name} • Plain (verbatim)"
        } else {
            "${outputLanguage.name} • ${recipientTone.name} (${polishLevel.name.lowercase()})"
        }
    }

    companion object {
        const val PREF_VOICE_OUTPUT_LANGUAGE = "pref_voice_output_language"
        const val PREF_VOICE_MODE = "pref_voice_mode"
        const val PREF_VOICE_RECIPIENT_TONE = "pref_voice_recipient_tone"
        const val PREF_VOICE_POLISH_LEVEL = "pref_voice_polish_level"
        const val PREF_VOICE_PREVIEW_ENABLED = "pref_voice_preview_enabled"

        fun read(prefs: SharedPreferences): VoiceSettings {
            return VoiceSettings(
                outputLanguage = VoiceOutputLanguage.fromCode(prefs.getString(PREF_VOICE_OUTPUT_LANGUAGE, VoiceOutputLanguage.RU.code)),
                mode = VoiceMode.fromCode(prefs.getString(PREF_VOICE_MODE, VoiceMode.AI.code)),
                recipientTone = RecipientTone.fromCode(prefs.getString(PREF_VOICE_RECIPIENT_TONE, RecipientTone.FRIEND.code)),
                polishLevel = PolishLevel.fromCode(prefs.getString(PREF_VOICE_POLISH_LEVEL, PolishLevel.BALANCED.code)),
                isPreviewEnabled = prefs.getBoolean(PREF_VOICE_PREVIEW_ENABLED, true)
            )
        }
    }
}
