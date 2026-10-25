// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import helium314.keyboard.latin.utils.Log

/**
 * Standard Android implementation of SpeechRecognitionAdapter using android.speech.SpeechRecognizer.
 */
class AndroidSpeechRecognitionAdapter : SpeechRecognitionAdapter {

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeListener: SpeechRecognitionListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun isAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening(
        context: Context,
        languageTag: String,
        preferOffline: Boolean,
        listener: SpeechRecognitionListener
    ) {
        runOnMainThread {
            try {
                cancelInternal()
                activeListener = listener
                listener.onStateChanged(RecognitionState.LISTENING)

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    speechRecognizer = it
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    if (languageTag.isNotBlank()) {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    }
                    if (preferOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        activeListener?.onReadyForSpeech()
                    }

                    override fun onBeginningOfSpeech() {
                        activeListener?.onBeginningOfSpeech()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        activeListener?.onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        activeListener?.onStateChanged(RecognitionState.PROCESSING)
                    }

                    override fun onError(error: Int) {
                        val message = getErrorMessage(error)
                        Log.w(TAG, "SpeechRecognizer error: $error ($message)")
                        activeListener?.onError(error, message)
                        activeListener?.onStateChanged(RecognitionState.ERROR)
                        cancelInternal()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        activeListener?.onResults(text)
                        activeListener?.onStateChanged(RecognitionState.IDLE)
                        cancelInternal()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            activeListener?.onPartialResults(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                listener.onError(-1, e.message ?: "Failed to start speech recognition")
                listener.onStateChanged(RecognitionState.ERROR)
                cancelInternal()
            }
        }
    }

    override fun stopListening() {
        runOnMainThread {
            try {
                speechRecognizer?.stopListening()
                activeListener?.onStateChanged(RecognitionState.PROCESSING)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop listening", e)
            }
        }
    }

    override fun cancel() {
        runOnMainThread {
            cancelInternal()
        }
    }

    override fun destroy() {
        runOnMainThread {
            cancelInternal()
        }
    }

    private fun cancelInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up SpeechRecognizer", e)
        } finally {
            speechRecognizer = null
            activeListener?.onStateChanged(RecognitionState.IDLE)
            activeListener = null
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val TAG = "SpeechAdapter"

        fun getErrorMessage(errorCode: Int): String {
            return when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network operation timed out"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                SpeechRecognizer.ERROR_SERVER -> "Server recognition error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                else -> "Recognition error (code $errorCode)"
            }
        }
    }
}
