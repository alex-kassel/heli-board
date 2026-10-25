// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class VoiceButtonShellTest {

    @Test
    fun `voice key is mapped to VOICE_INPUT on click`() {
        assertEquals(KeyCode.VOICE_INPUT, getCodeForToolbarKey(ToolbarKey.VOICE))
    }

    @Test
    fun `voice key is mapped to VOICE_SETTINGS on long click`() {
        assertEquals(KeyCode.VOICE_SETTINGS, getCodeForToolbarKeyLongClick(ToolbarKey.VOICE))
    }

    @Test
    fun `voice key is pinned by default on far right`() {
        val pinnedPref = defaultPinnedToolbarPref
        assertTrue(pinnedPref.contains("${ToolbarKey.VOICE.name}${Separators.KV}true"))
    }
}
