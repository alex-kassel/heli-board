// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.voice.OpenAiCompatibleAiClient
import helium314.keyboard.latin.voice.PolishLevel
import helium314.keyboard.latin.voice.RecipientTone
import helium314.keyboard.latin.voice.VoiceAiClient
import helium314.keyboard.latin.voice.VoiceAiConfig
import helium314.keyboard.latin.voice.VoiceMode
import helium314.keyboard.latin.voice.VoiceOutputLanguage
import helium314.keyboard.latin.voice.VoicePromptBuilder
import helium314.keyboard.latin.voice.VoiceSettings
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class VoiceAiProcessingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.prefs().edit().clear().commit()
    }

    @Test
    fun `system prompt strictly enforces zero hallucination and fact preservation`() {
        val prompt = VoicePromptBuilder.SYSTEM_PROMPT
        assertTrue(prompt.contains("PRESERVE ALL FACTS"))
        assertTrue(prompt.contains("ZERO HALLUCINATION"))
        assertTrue(prompt.contains("TARGET LANGUAGE"))
        assertTrue(prompt.contains("RECIPIENT TONE"))
        assertTrue(prompt.contains("POLISH LEVEL"))
        assertTrue(prompt.contains("OUTPUT FORMAT"))
    }

    @Test
    fun `user message builder embeds raw transcript and parameters deterministically`() {
        val transcript = "Привет, мы договорились встретиться в 15:00 возле офиса."
        val message = VoicePromptBuilder.buildUserMessage(
            rawTranscript = transcript,
            targetLanguage = VoiceOutputLanguage.DE,
            recipientTone = RecipientTone.CLIENT,
            polishLevel = PolishLevel.STRONG
        )

        assertTrue(message.contains(transcript))
        assertTrue(message.contains("German (DE)"))
        assertTrue(message.contains("Client (polite, formal)"))
        assertTrue(message.contains("Strong (business rewrite)"))
    }

    @Test
    fun `VoiceAiConfig roundtrips through SharedPreferences`() {
        val config = VoiceAiConfig(
            apiEndpoint = "https://api.groq.com/openai/v1",
            apiKey = "sk-test-secret-key-12345",
            model = "llama-3.3-70b-versatile",
            hasUserConsented = true
        )

        config.save(context.prefs())
        val restored = VoiceAiConfig.read(context.prefs())

        assertEquals("https://api.groq.com/openai/v1", restored.apiEndpoint)
        assertEquals("sk-test-secret-key-12345", restored.apiKey)
        assertEquals("llama-3.3-70b-versatile", restored.model)
        assertTrue(restored.hasUserConsented)
        assertTrue(restored.isConfigured)
    }

    @Test
    fun `VoiceAiConfig isConfigured handles both keys and local proxies`() {
        val emptyConfig = VoiceAiConfig()
        assertFalse(emptyConfig.isConfigured)

        val keyedConfig = VoiceAiConfig(apiKey = "sk-valid-key")
        assertTrue(keyedConfig.isConfigured)

        val proxyConfig = VoiceAiConfig(apiEndpoint = "https://ab-box.de/api/v1", apiKey = "")
        assertTrue(proxyConfig.isConfigured)
        assertTrue(proxyConfig.isLocalOrProxyEndpoint)

        val localConfig = VoiceAiConfig(apiEndpoint = "http://127.0.0.1:8000/v1", apiKey = "")
        assertTrue(localConfig.isConfigured)
        assertTrue(localConfig.isLocalOrProxyEndpoint)
    }

    @Test
    fun `parseErrorMessage maps HTTP codes and JSON payloads`() {
        assertEquals(
            "Invalid or missing API key (HTTP 401 Unauthorized).",
            OpenAiCompatibleAiClient.parseErrorMessage(401, "")
        )
        assertEquals(
            "Rate limit or quota exceeded (HTTP 429 Too Many Requests).",
            OpenAiCompatibleAiClient.parseErrorMessage(429, "")
        )

        val jsonError = """{"error": {"message": "You exceeded your current quota, please check your plan."}}"""
        assertEquals(
            "You exceeded your current quota, please check your plan.",
            OpenAiCompatibleAiClient.parseErrorMessage(429, jsonError)
        )
    }

    @Test
    fun `AI client refuses to send requests without explicit user consent`() {
        val client = OpenAiCompatibleAiClient()
        val config = VoiceAiConfig(
            apiKey = "sk-test",
            hasUserConsented = false
        )
        val settings = VoiceSettings(mode = VoiceMode.AI)

        var errorResult: String? = null
        client.transform("Test message", settings, config,
            onSuccess = {},
            onError = { errorResult = it }
        )

        assertEquals("AI processing consent has not been granted.", errorResult)
    }

    @Test
    fun `AI client refuses to send requests without API key on standard endpoints`() {
        val client = OpenAiCompatibleAiClient()
        val config = VoiceAiConfig(
            apiEndpoint = "https://api.openai.com/v1",
            apiKey = "",
            hasUserConsented = true
        )
        val settings = VoiceSettings(mode = VoiceMode.AI)

        var errorResult: String? = null
        client.transform("Test message", settings, config,
            onSuccess = {},
            onError = { errorResult = it }
        )

        assertEquals("API key is not configured. Please set it in Voice Settings.", errorResult)
    }
}
