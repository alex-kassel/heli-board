// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.app.AlertDialog
import android.os.IBinder
import android.view.WindowManager
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.getPlatformDialogThemeContext

/**
 * Controller for AI Voice operations in HeliBoard.
 * Task 002: Shell implementation providing short-tap toggle and long-press settings placeholders.
 */
class VoiceInputController private constructor() {

    private var isRecordingPlaceholder: Boolean = false

    /**
     * Toggles recording placeholder state on short tap.
     */
    fun toggleVoiceInput(latinIme: LatinIME) {
        isRecordingPlaceholder = !isRecordingPlaceholder
        if (isRecordingPlaceholder) {
            KeyboardSwitcher.getInstance().showToast("AI Voice: Recording started (tap to stop)", true)
        } else {
            KeyboardSwitcher.getInstance().showToast("AI Voice: Recording stopped (processing placeholder)", true)
        }
    }

    /**
     * Shows modal voice mode configuration panel on long press.
     */
    fun showVoiceSettingsDialog(latinIme: LatinIME, windowToken: IBinder?) {
        if (windowToken == null) return

        val options = arrayOf(
            "Output: Russian (RU)",
            "Output: German (DE)",
            "Output: English (EN)",
            "---",
            "Mode: Verbatim transcription",
            "Tone: Friend",
            "Tone: Colleague",
            "Tone: Manager",
            "Tone: Client",
            "---",
            "Polish: Minimal (spelling/punctuation)",
            "Polish: Balanced (natural flow)",
            "Polish: Strong (business rewrite)"
        )

        val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
            .setTitle("AI Voice Settings (Shell)")
            .setItems(options) { di, which ->
                di.dismiss()
                val selected = options[which]
                if (selected != "---") {
                    KeyboardSwitcher.getInstance().showToast("Selected: $selected", true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val window = dialog.window
        val layoutParams = window?.attributes
        layoutParams?.token = windowToken
        layoutParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window?.attributes = layoutParams
        window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog.show()
    }

    companion object {
        val instance by lazy { VoiceInputController() }
    }
}
