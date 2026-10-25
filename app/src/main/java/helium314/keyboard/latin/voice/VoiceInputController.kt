// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.app.AlertDialog
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
 * Task 004: Speech recognition adapter, explicit runtime permission, start/stop/cancel,
 * lifecycle cleanup, sensitive field protection, and transcript preview.
 */
class VoiceInputController private constructor() {

    @VisibleForTesting
    var adapter: SpeechRecognitionAdapter = AndroidSpeechRecognitionAdapter()

    var currentState: RecognitionState = RecognitionState.IDLE
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
                override fun onReadyForSpeech() {
                    // Ready to capture voice
                }

                override fun onBeginningOfSpeech() {
                    // Audio stream active
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Volume updates
                }

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
        val text = finalText.trim()

        if (text.isEmpty()) {
            KeyboardSwitcher.getInstance().showToast("No speech recognized", true)
            currentState = RecognitionState.IDLE
            return
        }

        if (latinIme == null) {
            currentState = RecognitionState.IDLE
            return
        }

        val settings = VoiceSettings.read(latinIme.prefs())
        if (settings.isPreviewEnabled) {
            showPreviewDialog(latinIme, settings, text)
        } else {
            insertText(latinIme, text)
            KeyboardSwitcher.getInstance().showToast("✅ Inserted: $text", true)
        }
        currentState = RecognitionState.IDLE
    }

    private fun handleError(errorMessage: String) {
        KeyboardSwitcher.getInstance().showToast("Voice error: $errorMessage", true)
        currentState = RecognitionState.IDLE
    }

    private fun showPreviewDialog(latinIme: LatinIME, settings: VoiceSettings, text: String) {
        val windowToken = getWindowToken(latinIme)
        if (windowToken == null) {
            insertText(latinIme, text)
            return
        }

        val modeLabel = if (settings.mode == VoiceMode.AI) {
            "AI (${settings.recipientTone.title})"
        } else {
            "Plain (${settings.outputLanguage.name})"
        }

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("Voice Preview [$modeLabel]")
            .setMessage(text)
            .setPositiveButton("Insert") { di, _ ->
                di.dismiss()
                insertText(latinIme, text)
            }
            .setNegativeButton("Discard") { di, _ ->
                di.dismiss()
                KeyboardSwitcher.getInstance().showToast("Voice input discarded", true)
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

        fun openRootDialog() {
            val items = arrayOf(
                "🌐 Output Language: ${currentSettings.outputLanguage.title}",
                "⚙️ Mode: ${currentSettings.mode.title}",
                "👤 Recipient: ${currentSettings.recipientTone.title}",
                "✨ Polish: ${currentSettings.polishLevel.title}",
                "👁️ Preview before insert: ${if (currentSettings.isPreviewEnabled) "ENABLED [ON]" else "DISABLED [OFF]"}"
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
                    }
                }
                .setPositiveButton(android.R.string.ok, null)
                .create()

            attachToWindow(dialog, token)
            dialog.show()
        }

        openRootDialog()
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
