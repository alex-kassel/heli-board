// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context

/**
 * State machine for voice recording lifecycle.
 */
enum class RecognitionState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

/**
 * Callbacks from the speech recognition engine.
 */
interface SpeechRecognitionListener {
    fun onReadyForSpeech() {}
    fun onBeginningOfSpeech() {}
    fun onRmsChanged(rmsdB: Float) {}
    fun onPartialResults(partialText: String) {}
    fun onResults(finalText: String)
    fun onError(errorCode: Int, errorMessage: String)
    fun onStateChanged(state: RecognitionState) {}
}

/**
 * Abstraction layer for pluggable speech recognition backends (Android system STT, offline models, cloud).
 */
interface SpeechRecognitionAdapter {
    fun isAvailable(context: Context): Boolean

    fun startListening(
        context: Context,
        languageTag: String,
        preferOffline: Boolean = false,
        listener: SpeechRecognitionListener
    )

    fun stopListening()

    fun cancel()

    fun destroy()
}
