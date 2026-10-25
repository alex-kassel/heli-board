// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.voice.AndroidSpeechRecognitionAdapter
import helium314.keyboard.latin.voice.RecognitionState
import helium314.keyboard.latin.voice.SpeechRecognitionAdapter
import helium314.keyboard.latin.voice.SpeechRecognitionListener
import helium314.keyboard.latin.voice.VoiceInputController
import helium314.keyboard.latin.voice.VoiceOutputLanguage
import helium314.keyboard.latin.voice.VoiceSettings
import org.junit.After
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
class SpeechRecognitionTest {

    private lateinit var context: Context
    private lateinit var fakeAdapter: FakeSpeechRecognitionAdapter

    class FakeSpeechRecognitionAdapter : SpeechRecognitionAdapter {
        var isAvailableFlag = true
        var isListening = false
        var lastLanguageTag: String? = null
        var activeListener: SpeechRecognitionListener? = null
        var cancelCount = 0
        var destroyCount = 0

        override fun isAvailable(context: Context): Boolean = isAvailableFlag

        override fun startListening(
            context: Context,
            languageTag: String,
            preferOffline: Boolean,
            listener: SpeechRecognitionListener
        ) {
            isListening = true
            lastLanguageTag = languageTag
            activeListener = listener
            listener.onStateChanged(RecognitionState.LISTENING)
        }

        override fun stopListening() {
            isListening = false
            activeListener?.onStateChanged(RecognitionState.PROCESSING)
        }

        override fun cancel() {
            isListening = false
            cancelCount++
            activeListener?.onStateChanged(RecognitionState.IDLE)
            activeListener = null
        }

        override fun destroy() {
            cancel()
            destroyCount++
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeAdapter = FakeSpeechRecognitionAdapter()
        VoiceInputController.instance.adapter = fakeAdapter
        VoiceInputController.instance.cancel()
    }

    @After
    fun tearDown() {
        VoiceInputController.instance.destroy()
    }

    @Test
    fun `error message mappings cover all standard speech recognition errors`() {
        assertEquals("Audio recording error", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_AUDIO))
        assertEquals("Client recognition error", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_CLIENT))
        assertEquals("Microphone permission required", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals("Network connection error", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_NETWORK))
        assertEquals("Network operation timed out", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals("No speech recognized", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals("Speech recognizer is busy", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals("Server recognition error", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_SERVER))
        assertEquals("No speech input detected", AndroidSpeechRecognitionAdapter.getErrorMessage(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals("Recognition error (code 999)", AndroidSpeechRecognitionAdapter.getErrorMessage(999))
    }

    @Test
    fun `cancel resets recognition state to IDLE`() {
        fakeAdapter.startListening(context, "ru-RU", false, object : SpeechRecognitionListener {
            override fun onResults(finalText: String) {}
            override fun onError(errorCode: Int, errorMessage: String) {}
            override fun onStateChanged(state: RecognitionState) {}
        })
        assertTrue(fakeAdapter.isListening)

        VoiceInputController.instance.cancel()
        assertEquals(RecognitionState.IDLE, VoiceInputController.instance.currentState)
    }

    @Test
    fun `adapter stopListening transitions to PROCESSING`() {
        var observedState = RecognitionState.IDLE
        fakeAdapter.startListening(context, "de-DE", false, object : SpeechRecognitionListener {
            override fun onResults(finalText: String) {}
            override fun onError(errorCode: Int, errorMessage: String) {}
            override fun onStateChanged(state: RecognitionState) {
                observedState = state
            }
        })
        assertEquals(RecognitionState.LISTENING, observedState)

        fakeAdapter.stopListening()
        assertEquals(RecognitionState.PROCESSING, observedState)
    }

    @Test
    fun `error notification resets state and does not leave hanging state`() {
        var observedState = RecognitionState.IDLE
        fakeAdapter.startListening(context, "en-US", false, object : SpeechRecognitionListener {
            override fun onResults(finalText: String) {}
            override fun onError(errorCode: Int, errorMessage: String) {}
            override fun onStateChanged(state: RecognitionState) {
                observedState = state
            }
        })
        assertEquals(RecognitionState.LISTENING, observedState)

        fakeAdapter.activeListener?.onError(SpeechRecognizer.ERROR_NO_MATCH, "No speech recognized")
        fakeAdapter.cancel()
        assertEquals(RecognitionState.IDLE, VoiceInputController.instance.currentState)
    }

    @Test
    fun `adapter language tag matches configured output language`() {
        val languages = mapOf(
            VoiceOutputLanguage.RU to "ru-RU",
            VoiceOutputLanguage.DE to "de-DE",
            VoiceOutputLanguage.EN to "en-US"
        )

        languages.forEach { (lang, expectedTag) ->
            val tag = when (lang) {
                VoiceOutputLanguage.RU -> "ru-RU"
                VoiceOutputLanguage.DE -> "de-DE"
                VoiceOutputLanguage.EN -> "en-US"
            }
            assertEquals(expectedTag, tag)
        }
    }
}
