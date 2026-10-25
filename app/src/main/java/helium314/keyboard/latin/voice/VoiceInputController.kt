// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.app.AlertDialog
import android.os.IBinder
import android.view.WindowManager
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.getPlatformDialogThemeContext
import helium314.keyboard.latin.utils.prefs

/**
 * Controller for AI Voice operations in HeliBoard.
 * Task 003: Full persistent settings integration with SharedPreferences.
 */
class VoiceInputController private constructor() {

    private var isRecordingPlaceholder: Boolean = false

    /**
     * Toggles recording placeholder state on short tap, displaying active persistent mode.
     */
    fun toggleVoiceInput(latinIme: LatinIME) {
        val settings = VoiceSettings.read(latinIme.prefs())
        isRecordingPlaceholder = !isRecordingPlaceholder
        if (isRecordingPlaceholder) {
            val modeSummary = settings.toSummary()
            KeyboardSwitcher.getInstance().showToast("🎤 Voice ($modeSummary): Recording started (tap to stop)", true)
        } else {
            val previewTag = if (settings.isPreviewEnabled) "Preview ON" else "Direct Insert"
            KeyboardSwitcher.getInstance().showToast("✅ Voice: Stopped -> ${settings.outputLanguage.name} [$previewTag]", true)
        }
    }

    /**
     * Shows interactive persistent voice configuration panel on long press.
     */
    fun showVoiceSettingsDialog(latinIme: LatinIME, windowToken: IBinder?) {
        if (windowToken == null) return
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
                        0 -> showLanguagePicker(latinIme, windowToken) {
                            currentSettings = currentSettings.copy(outputLanguage = it)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Output set to ${it.title}", true)
                            openRootDialog()
                        }
                        1 -> showModePicker(latinIme, windowToken) {
                            currentSettings = currentSettings.copy(mode = it)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Mode set to ${it.title}", true)
                            openRootDialog()
                        }
                        2 -> showRecipientPicker(latinIme, windowToken) {
                            currentSettings = currentSettings.copy(recipientTone = it)
                            currentSettings.save(prefs)
                            KeyboardSwitcher.getInstance().showToast("Recipient set to ${it.title}", true)
                            openRootDialog()
                        }
                        3 -> showPolishPicker(latinIme, windowToken) {
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

            attachToWindow(dialog, windowToken)
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
