// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.voice.PolishLevel
import helium314.keyboard.latin.voice.RecipientTone
import helium314.keyboard.latin.voice.VoiceMode
import helium314.keyboard.latin.voice.VoiceOutputLanguage
import helium314.keyboard.latin.voice.VoiceSettings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class VoiceSettingsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("test_voice_prefs", Context.MODE_PRIVATE)

    @Test
    fun `default voice settings conform to product specification`() {
        prefs.edit().clear().commit()
        val settings = VoiceSettings.read(prefs)

        assertEquals(VoiceOutputLanguage.RU, settings.outputLanguage)
        assertEquals(VoiceMode.AI, settings.mode)
        assertEquals(RecipientTone.FRIEND, settings.recipientTone)
        assertEquals(PolishLevel.BALANCED, settings.polishLevel)
        assertTrue(settings.isPreviewEnabled, "Preview must be default ON")
    }

    @Test
    fun `settings serialize and persist correctly across sessions`() {
        prefs.edit().clear().commit()
        val customSettings = VoiceSettings(
            outputLanguage = VoiceOutputLanguage.EN,
            mode = VoiceMode.PLAIN,
            recipientTone = RecipientTone.CLIENT,
            polishLevel = PolishLevel.STRONG,
            isPreviewEnabled = false
        )
        customSettings.save(prefs)

        val restored = VoiceSettings.read(prefs)
        assertEquals(VoiceOutputLanguage.EN, restored.outputLanguage)
        assertEquals(VoiceMode.PLAIN, restored.mode)
        assertEquals(RecipientTone.CLIENT, restored.recipientTone)
        assertEquals(PolishLevel.STRONG, restored.polishLevel)
        assertFalse(restored.isPreviewEnabled)
    }

    @Test
    fun `fallback to safe defaults on invalid or missing values`() {
        assertEquals(VoiceOutputLanguage.RU, VoiceOutputLanguage.fromCode("invalid"))
        assertEquals(VoiceOutputLanguage.RU, VoiceOutputLanguage.fromCode(null))

        assertEquals(VoiceMode.AI, VoiceMode.fromCode("unknown"))
        assertEquals(RecipientTone.FRIEND, RecipientTone.fromCode(null))
        assertEquals(PolishLevel.BALANCED, PolishLevel.fromCode("xyz"))
    }

    @Test
    fun `summary formatting reflects current mode`() {
        val plain = VoiceSettings(outputLanguage = VoiceOutputLanguage.DE, mode = VoiceMode.PLAIN)
        assertTrue(plain.toSummary().contains("DE"))
        assertTrue(plain.toSummary().contains("Plain"))

        val ai = VoiceSettings(
            outputLanguage = VoiceOutputLanguage.RU,
            mode = VoiceMode.AI,
            recipientTone = RecipientTone.MANAGER,
            polishLevel = PolishLevel.STRONG
        )
        assertTrue(ai.toSummary().contains("RU"))
        assertTrue(ai.toSummary().contains("MANAGER"))
        assertTrue(ai.toSummary().contains("strong"))
    }
}
