// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.getPlatformDialogThemeContext
import helium314.keyboard.latin.utils.prefs
import java.lang.ref.WeakReference

/**
 * Controller for AI Voice operations in HeliBoard.
 * Task 004 & Task 005:
 * - Speech recognition adapter with runtime permission and lifecycle management.
 * - AI text processing (OpenAI-compatible BYOK + custom proxy gateway).
 * - Deterministic prompt building preserving facts and commitments.
 * - Explicit user consent gate and sensitive field protection.
 * - Preview and comparison before editor insertion (zero auto-send).
 */
class VoiceInputController private constructor() {

    @VisibleForTesting
    var adapter: SpeechRecognitionAdapter = AndroidSpeechRecognitionAdapter()

    @VisibleForTesting
    var aiClient: VoiceAiClient = OpenAiCompatibleAiClient()

    var currentState: RecognitionState = RecognitionState.IDLE
        private set

    var lastRawTranscript: String = ""
        private set

    private var activeLatinImeRef: WeakReference<LatinIME>? = null

    /**
     * Checks whether RECORD_AUDIO permission has been granted.
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Called when VoicePermissionActivity delivers the permission result.
     */
    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            KeyboardSwitcher.getInstance().showToast("Microphone permission granted. Tap mic to start recording.", true)
        } else {
            KeyboardSwitcher.getInstance().showToast("Microphone permission denied", true)
        }
    }

    /**
     * Toggles speech recognition on short tap of the voice button.
     */
    fun toggleVoiceInput(latinIme: LatinIME) {
        activeLatinImeRef = WeakReference(latinIme)

        // Sensitive / password field check
        if (isSensitiveOrPasswordField(latinIme)) {
            KeyboardSwitcher.getInstance().showToast("Voice input is disabled in password fields for security", true)
            return
        }

        // Runtime permission check
        if (!hasAudioPermission(latinIme)) {
            KeyboardSwitcher.getInstance().showToast("Microphone permission required for voice typing", true)
            VoicePermissionActivity.launch(latinIme)
            return
        }

        // Adapter availability check
        if (!adapter.isAvailable(latinIme)) {
            KeyboardSwitcher.getInstance().showToast("Speech recognition is unavailable on this device", true)
            return
        }

        when (currentState) {
            RecognitionState.LISTENING -> {
                // User tapped mic button again to STOP and process
                KeyboardSwitcher.getInstance().showToast("Stopping recording and processing...", true)
                adapter.stopListening()
            }
            RecognitionState.PROCESSING -> {
                KeyboardSwitcher.getInstance().showToast("Processing voice input, please wait...", true)
            }
            RecognitionState.IDLE, RecognitionState.ERROR -> {
                startListening(latinIme)
            }
        }
    }

    private fun startListening(latinIme: LatinIME) {
        val settings = VoiceSettings.read(latinIme.prefs())
        val languageTag = when (settings.outputLanguage) {
            VoiceOutputLanguage.RU -> "ru-RU"
            VoiceOutputLanguage.DE -> "de-DE"
            VoiceOutputLanguage.EN -> "en-US"
        }

        KeyboardSwitcher.getInstance().showToast("🎤 Listening (${settings.outputLanguage.name})... Tap mic when done.", true)

        adapter.startListening(
            latinIme,
            languageTag = languageTag,
            preferOffline = false,
            listener = object : SpeechRecognitionListener {
                override fun onReadyForSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}

                override fun onPartialResults(partialText: String) {
                    if (partialText.isNotBlank()) {
                        KeyboardSwitcher.getInstance().showToast("🎤 $partialText", false)
                    }
                }

                override fun onResults(finalText: String) {
                    handleFinalResults(finalText)
                }

                override fun onError(errorCode: Int, errorMessage: String) {
                    handleError(errorMessage)
                }

                override fun onStateChanged(state: RecognitionState) {
                    currentState = state
                }
            }
        )
    }

    private fun handleFinalResults(finalText: String) {
        val latinIme = activeLatinImeRef?.get()
        val rawTranscript = finalText.trim()
        lastRawTranscript = rawTranscript

        if (rawTranscript.isEmpty()) {
            KeyboardSwitcher.getInstance().showToast("No speech recognized", true)
            currentState = RecognitionState.IDLE
            return
        }

        if (latinIme == null) {
            currentState = RecognitionState.IDLE
            return
        }

        val settings = VoiceSettings.read(latinIme.prefs())
        val aiConfig = VoiceAiConfig.read(latinIme.prefs())

        if (settings.mode == VoiceMode.PLAIN) {
            // Verbatim plain transcription
            if (settings.isPreviewEnabled) {
                showPreviewDialog(latinIme, settings, rawTranscript, null)
            } else {
                insertText(latinIme, rawTranscript)
                KeyboardSwitcher.getInstance().showToast("✅ Inserted: $rawTranscript", true)
            }
            currentState = RecognitionState.IDLE
        } else {
            // AI-assisted mode
            if (!aiConfig.hasUserConsented) {
                val token = getWindowToken(latinIme)
                if (token != null) {
                    showConsentDialog(latinIme, token) {
                        processAiTransformation(latinIme, settings, VoiceAiConfig.read(latinIme.prefs()), rawTranscript)
                    }
                } else {
                    // Fallback to plain if dialog cannot be presented
                    insertText(latinIme, rawTranscript)
                }
                currentState = RecognitionState.IDLE
            } else if (!aiConfig.isConfigured) {
                showMissingConfigDialog(latinIme, rawTranscript)
                currentState = RecognitionState.IDLE
            } else {
                processAiTransformation(latinIme, settings, aiConfig, rawTranscript)
            }
        }
    }

    private fun processAiTransformation(
        latinIme: LatinIME,
        settings: VoiceSettings,
        aiConfig: VoiceAiConfig,
        rawTranscript: String
    ) {
        currentState = RecognitionState.PROCESSING
        KeyboardSwitcher.getInstance().showToast("✨ AI transforming (${settings.toSummary()})...", true)

        aiClient.transform(
            rawTranscript = rawTranscript,
            settings = settings,
            config = aiConfig,
            onSuccess = { transformedText ->
                currentState = RecognitionState.IDLE
                if (settings.isPreviewEnabled) {
                    showPreviewDialog(latinIme, settings, transformedText, rawTranscript)
                } else {
                    insertText(latinIme, transformedText)
                    KeyboardSwitcher.getInstance().showToast("✅ AI text inserted (${settings.outputLanguage.name})", true)
                }
            },
            onError = { errorMessage ->
                currentState = RecognitionState.IDLE
                showTransformationErrorDialog(latinIme, errorMessage, rawTranscript)
            }
        )
    }

    private fun handleError(errorMessage: String) {
        KeyboardSwitcher.getInstance().showToast("Voice error: $errorMessage", true)
        currentState = RecognitionState.IDLE
    }

    private fun showPreviewDialog(
        latinIme: LatinIME,
        settings: VoiceSettings,
        displayText: String,
        originalTranscript: String?
    ) {
        val windowToken = getWindowToken(latinIme)
        if (windowToken == null) {
            insertText(latinIme, displayText)
            return
        }

        val modeLabel = if (settings.mode == VoiceMode.AI) {
            "AI (${settings.recipientTone.title})"
        } else {
            "Plain (${settings.outputLanguage.name})"
        }

        val message = if (!originalTranscript.isNullOrBlank() && originalTranscript != displayText) {
            "$displayText\n\n────────────────\n🎙️ Original: $originalTranscript"
        } else {
            displayText
        }

        val builder = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Voice Preview [$modeLabel]")
            .setMessage(message)
            .setPositiveButton("Insert") { di, _ ->
                di.dismiss()
                insertText(latinIme, displayText)
            }
            .setNegativeButton("Discard") { di, _ ->
                di.dismiss()
                KeyboardSwitcher.getInstance().showToast("Voice input discarded", true)
            }

        if (!originalTranscript.isNullOrBlank() && originalTranscript != displayText) {
            builder.setNeutralButton("Insert Original") { di, _ ->
                di.dismiss()
                insertText(latinIme, originalTranscript)
                KeyboardSwitcher.getInstance().showToast("Inserted original transcript", true)
            }
        }

        val dialog = builder.create()
        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun showMissingConfigDialog(latinIme: LatinIME, rawTranscript: String) {
        val token = getWindowToken(latinIme) ?: return
        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("AI Provider Not Configured")
            .setMessage("AI processing requires an API Key or custom proxy endpoint.\n\nYou can configure it now, or insert your spoken words directly as plain text.")
            .setPositiveButton("Configure AI") { di, _ ->
                di.dismiss()
                showVoiceSettingsDialog(latinIme, token)
            }
            .setNeutralButton("Insert Plain Text") { di, _ ->
                di.dismiss()
                insertText(latinIme, rawTranscript)
            }
            .setNegativeButton("Cancel", null)
            .create()

        attachToWindow(dialog, token)
        dialog.show()
    }

    private fun showTransformationErrorDialog(
        latinIme: LatinIME,
        errorMessage: String,
        rawTranscript: String
    ) {
        val token = getWindowToken(latinIme)
        if (token == null) {
            KeyboardSwitcher.getInstance().showToast("AI Error: $errorMessage", true)
            return
        }

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("AI Transformation Failed")
            .setMessage("$errorMessage\n\nYour original transcript has been preserved.")
            .setPositiveButton("Insert Original") { di, _ ->
                di.dismiss()
                insertText(latinIme, rawTranscript)
            }
            .setNegativeButton("Discard", null)
            .create()

        attachToWindow(dialog, token)
        dialog.show()
    }

    /**
     * Explicit Privacy Consent Dialog required before the first AI network request is dispatched.
     */
    fun showConsentDialog(latinIme: LatinIME, windowToken: IBinder, onConsented: () -> Unit) {
        val config = VoiceAiConfig.read(latinIme.prefs())
        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("AI Voice Processing Consent")
            .setMessage(
                "To transform your speech into your chosen tone and language, HeliBoard sends only the recognized voice transcript to your configured AI provider (${config.apiEndpoint}).\n\n" +
                "Privacy Safeguards:\n" +
                "• Regular keyboard typing is NEVER transmitted.\n" +
                "• Password fields are strictly blocked.\n" +
                "• Clipboard history is NEVER transmitted.\n" +
                "• Transcripts are not stored remotely.\n\n" +
                "Do you consent to sending speech transcripts for AI processing?"
            )
            .setPositiveButton("Consent & Enable") { di, _ ->
                di.dismiss()
                config.copy(hasUserConsented = true).save(latinIme.prefs())
                KeyboardSwitcher.getInstance().showToast("AI processing enabled", true)
                onConsented()
            }
            .setNegativeButton("Decline") { di, _ ->
                di.dismiss()
                KeyboardSwitcher.getInstance().showToast("AI processing declined. Voice mode set to Plain.", true)
                VoiceSettings.read(latinIme.prefs()).copy(mode = VoiceMode.PLAIN).save(latinIme.prefs())
            }
            .create()

        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun insertText(latinIme: LatinIME, text: String) {
        latinIme.onTextInput(text)
    }

    /**
     * Cancels active recognition and resets state without delivering results.
     */
    fun cancel() {
        if (currentState != RecognitionState.IDLE) {
            adapter.cancel()
            currentState = RecognitionState.IDLE
        }
    }

    /**
     * Cleans up all resources on IME destroy.
     */
    fun destroy() {
        cancel()
        adapter.destroy()
        activeLatinImeRef = null
    }

    /**
     * Checks if current input editor is a password or sensitive field.
     */
    fun isSensitiveOrPasswordField(latinIme: LatinIME): Boolean {
        val editorInfo = latinIme.currentInputEditorInfo ?: return false
        val inputType = editorInfo.inputType
        return InputTypeUtils.isPasswordInputType(inputType) ||
                InputTypeUtils.isVisiblePasswordInputType(inputType)
    }

    fun getWindowToken(latinIme: LatinIME): IBinder? {
        return KeyboardSwitcher.getInstance().mainKeyboardView?.windowToken
            ?: latinIme.window?.window?.decorView?.windowToken
    }

    /**
     * Shows interactive persistent voice configuration panel on long press.
     */
    fun showVoiceSettingsDialog(latinIme: LatinIME, windowToken: IBinder?) {
        val token = windowToken ?: getWindowToken(latinIme) ?: return
        val prefs = latinIme.prefs()
        var currentSettings = VoiceSettings.read(prefs)
        var currentAiConfig = VoiceAiConfig.read(prefs)

        fun openRootDialog() {
            val items = arrayOf(
                "🌐 Output Language: ${currentSettings.outputLanguage.title}",
                "⚙️ Mode: ${currentSettings.mode.title}",
                "👤 Recipient: ${currentSettings.recipientTone.title}",
                "✨ Polish: ${currentSettings.polishLevel.title}",
                "👁️ Preview before insert: ${if (currentSettings.isPreviewEnabled) "ENABLED [ON]" else "DISABLED [OFF]"}",
                "🤖 AI Provider: ${if (currentAiConfig.isConfigured) "Configured (${currentAiConfig.model})" else "NOT CONFIGURED [Key needed]"}"
            )

            val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
                .setTitle("AI Voice Settings")
                .setItems(items) { di, which ->
                    di.dismiss()
                    when (which) {
                        0 -> showLanguagePicker(latinIme, token) {
                            currentSettings = currentSettings.copy(outputLanguage = it)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Output set to ${it.title}", true)
                            openRootDialog()
                        }
                        1 -> showModePicker(latinIme, token) {
                            currentSettings = currentSettings.copy(mode = it)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Mode set to ${it.title}", true)
                            openRootDialog()
                        }
                        2 -> showRecipientPicker(latinIme, token) {
                            currentSettings = currentSettings.copy(recipientTone = it)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Recipient set to ${it.title}", true)
                            openRootDialog()
                        }
                        3 -> showPolishPicker(latinIme, token) {
                            currentSettings = currentSettings.copy(polishLevel = it)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Polish set to ${it.title}", true)
                            openRootDialog()
                        }
                        4 -> {
                            val newPreview = !currentSettings.isPreviewEnabled
                            currentSettings = currentSettings.copy(isPreviewEnabled = newPreview)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast(
                                "Preview before insert: ${if (newPreview) "ENABLED" else "DISABLED"}",
                                true
                            )
                            openRootDialog()
                        }
                        5 -> showAiProviderDialog(latinIme, token) {
                            currentAiConfig = VoiceAiConfig.read(prefs)
                            openRootDialog()
                        }
                    }
                }
                .setPositiveButton(android.R.string.ok, null)
                .create()

            attachToWindow(dialog, token)
            dialog.show()
        }

        openRootDialog()
    }

    private fun showAiProviderDialog(latinIme: LatinIME, windowToken: IBinder, onUpdated: () -> Unit) {
        val prefs = latinIme.prefs()
        var config = VoiceAiConfig.read(prefs)

        fun openAiDialog() {
            val keyDisplay = if (config.apiKey.isBlank()) {
                "[Not configured]"
            } else {
                "•••••••• (${config.apiKey.take(4)}...)"
            }
            val consentDisplay = if (config.hasUserConsented) "Granted [YES]" else "Not Granted [NO]"

            val items = arrayOf(
                "🌐 Endpoint: ${config.apiEndpoint}",
                "🔑 API Key: $keyDisplay",
                "🧠 Model: ${config.model}",
                "📋 Paste API Key from Clipboard",
                "🛡️ AI Cloud Consent: $consentDisplay",
                "🔄 Reset to Defaults"
            )

            val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
                .setTitle("AI Provider & Credentials")
                .setItems(items) { di, which ->
                    di.dismiss()
                    when (which) {
                        0 -> showEndpointPicker(latinIme, windowToken, config) { newEndpoint ->
                            config = config.copy(apiEndpoint = newEndpoint)
                            config.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Endpoint set to $newEndpoint", true)
                            openAiDialog()
                        }
                        1, 3 -> {
                            val clipboard = latinIme.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (!clipText.isNullOrBlank()) {
                                config = config.copy(apiKey = clipText)
                                config.save(prefs)
                                KeyboardSwitcher.getInstance().showToast("API key updated from clipboard!", true)
                            } else {
                                KeyboardSwitcher.getInstance().showToast("Clipboard is empty or contains no text", true)
                            }
                            openAiDialog()
                        }
                        2 -> showModelPicker(latinIme, windowToken, config) { newModel ->
                            config = config.copy(model = newModel)
                            config.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Model set to $newModel", true)
                            openAiDialog()
                        }
                        4 -> {
                            val newConsent = !config.hasUserConsented
                            config = config.copy(hasUserConsented = newConsent)
                            config.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("AI consent: ${if (newConsent) "Granted" else "Revoked"}", true)
                            openAiDialog()
                        }
                        5 -> {
                            config = VoiceAiConfig(
                                apiEndpoint = VoiceAiConfig.DEFAULT_ENDPOINT,
                                apiKey = "",
                                model = VoiceAiConfig.DEFAULT_MODEL,
                                hasUserConsented = false
                            )
                            config.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Reset AI settings to defaults", true)
                            openAiDialog()
                        }
                    }
                }
                .setPositiveButton(android.R.string.ok) { _, _ -> onUpdated() }
                .create()

            attachToWindow(dialog, windowToken)
            dialog.show()
        }

        openAiDialog()
    }

    private fun showEndpointPicker(
        latinIme: LatinIME,
        windowToken: IBinder,
        currentConfig: VoiceAiConfig,
        onSelect: (String) -> Unit
    ) {
        val endpoints = arrayOf(
            "https://api.openai.com/v1 (OpenAI Default)",
            "https://api.groq.com/openai/v1 (Groq Fast Llama)",
            "https://openrouter.ai/api/v1 (OpenRouter Multi-model)",
            "https://ab-box.de/api/v1 (Production VPS Gateway)",
            "📋 Paste Endpoint from Clipboard"
        )

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Select API Endpoint")
            .setItems(endpoints) { di, which ->
                di.dismiss()
                val selected = when (which) {
                    0 -> "https://api.openai.com/v1"
                    1 -> "https://api.groq.com/openai/v1"
                    2 -> "https://openrouter.ai/api/v1"
                    3 -> "https://ab-box.de/api/v1"
                    4 -> {
                        val clipboard = latinIme.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: currentConfig.apiEndpoint
                    }
                    else -> currentConfig.apiEndpoint
                }
                onSelect(selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun showModelPicker(
        latinIme: LatinIME,
        windowToken: IBinder,
        currentConfig: VoiceAiConfig,
        onSelect: (String) -> Unit
    ) {
        val models = arrayOf(
            "gpt-4o-mini (Fast, economical)",
            "gpt-4o (High capability)",
            "llama-3.3-70b-versatile (Groq / OpenRouter)",
            "deepseek-chat (DeepSeek-V3)",
            "📋 Paste Model ID from Clipboard"
        )

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Select Model")
            .setItems(models) { di, which ->
                di.dismiss()
                val selected = when (which) {
                    0 -> "gpt-4o-mini"
                    1 -> "gpt-4o"
                    2 -> "llama-3.3-70b-versatile"
                    3 -> "deepseek-chat"
                    4 -> {
                        val clipboard = latinIme.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: currentConfig.model
                    }
                    else -> currentConfig.model
                }
                onSelect(selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun showLanguagePicker(latinIme: LatinIME, windowToken: IBinder, onSelect: (VoiceOutputLanguage) -> Unit) {
        val current = VoiceSettings.read(latinIme.prefs()).outputLanguage
        val options = VoiceOutputLanguage.entries.map { it.title }.toTypedArray()
        val currentIndex = VoiceOutputLanguage.entries.indexOf(current)

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Select Output Language")
            .setSingleChoiceItems(options, currentIndex) { di, which ->
                di.dismiss()
                onSelect(VoiceOutputLanguage.entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun showModePicker(latinIme: LatinIME, windowToken: IBinder, onSelect: (VoiceMode) -> Unit) {
        val current = VoiceSettings.read(latinIme.prefs()).mode
        val options = VoiceMode.entries.map { it.title }.toTypedArray()
        val currentIndex = VoiceMode.entries.indexOf(current)

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Select Voice Mode")
            .setSingleChoiceItems(options, currentIndex) { di, which ->
                di.dismiss()
                onSelect(VoiceMode.entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun showRecipientPicker(latinIme: LatinIME, windowToken: IBinder, onSelect: (RecipientTone) -> Unit) {
        val current = VoiceSettings.read(latinIme.prefs()).recipientTone
        val options = RecipientTone.entries.map { it.title }.toTypedArray()
        val currentIndex = RecipientTone.entries.indexOf(current)

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Select Recipient Tone")
            .setSingleChoiceItems(options, currentIndex) { di, which ->
                di.dismiss()
                onSelect(RecipientTone.entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun showPolishPicker(latinIme: LatinIME, windowToken: IBinder, onSelect: (PolishLevel) -> Unit) {
        val current = VoiceSettings.read(latinIme.prefs()).polishLevel
        val options = PolishLevel.entries.map { it.title }.toTypedArray()
        val currentIndex = PolishLevel.entries.indexOf(current)

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Select Polish Level")
            .setSingleChoiceItems(options, currentIndex) { di, which ->
                di.dismiss()
                onSelect(PolishLevel.entries[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        attachToWindow(dialog, windowToken)
        dialog.show()
    }

    private fun attachToWindow(dialog: AlertDialog, windowToken: IBinder) {
        val window = dialog.window
        val layoutParams = window?.attributes
        layoutParams?.token = windowToken
        layoutParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window?.attributes = layoutParams
        window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    companion object {
        val instance by lazy { VoiceInputController() }
    }
}
