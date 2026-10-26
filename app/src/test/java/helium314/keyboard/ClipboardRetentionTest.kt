// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.database.ClipboardDao
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class ClipboardRetentionTest {

    private lateinit var context: Context
    private lateinit var clipboardDao: ClipboardDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clipboardDao = ClipboardDao.getInstance(context)!!
        clipboardDao.clear()
    }

    @Test
    fun `MAX_RECENT_UNPINNED_CLIPS constant is 20`() {
        assertEquals(20, ClipboardDao.MAX_RECENT_UNPINNED_CLIPS)
    }

    @Test
    fun `unpinned clipboard items are capped at 20 items`() {
        val baseTime = 1_000_000L
        for (i in 1..25) {
            clipboardDao.addClip(timestamp = baseTime + i * 1000L, pinned = false, text = "Clip item #$i")
        }

        clipboardDao.clearOldClips(now = true)

        assertEquals(20, clipboardDao.count())
        // Items 6 through 25 should remain (the 20 most recent), items 1 to 5 should be pruned
        for (i in 6..25) {
            val exists = (0 until clipboardDao.count()).any { idx ->
                clipboardDao.getHistoryEntry(idx)?.text == "Clip item #$i"
            }
            assertTrue(exists, "Expected Clip item #$i to be retained")
        }
    }

    @Test
    fun `pinned clipboard items are never pruned by the 20-item cap`() {
        val baseTime = 1_000_000L
        // Add 5 pinned clips
        for (i in 1..5) {
            clipboardDao.addClip(timestamp = baseTime + i * 1000L, pinned = true, text = "Pinned #$i")
        }
        // Add 25 unpinned clips
        for (i in 1..25) {
            clipboardDao.addClip(timestamp = baseTime + (i + 10) * 1000L, pinned = false, text = "Unpinned #$i")
        }

        clipboardDao.clearOldClips(now = true)

        // 5 pinned + 20 unpinned = 25 clips
        assertEquals(25, clipboardDao.count())

        for (i in 1..5) {
            val exists = (0 until clipboardDao.count()).any { idx ->
                val entry = clipboardDao.getHistoryEntry(idx)
                entry?.text == "Pinned #$i" && entry.isPinned
            }
            assertTrue(exists, "Expected Pinned #$i to be preserved")
        }
    }
}
