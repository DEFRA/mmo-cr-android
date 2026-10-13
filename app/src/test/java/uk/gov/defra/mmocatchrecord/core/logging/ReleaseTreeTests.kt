package uk.gov.defra.mmocatchrecord.core.logging

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class ReleaseTreeTests {
    private val secret = "do not log this message"

    // Called directly, not planted: a planted tree would also capture other tests' background logging.
    private val tree = ReleaseTree()

    @Before
    fun clearLogs() {
        ShadowLog.clear()
    }

    @After
    fun clearLogsAfter() {
        ShadowLog.clear()
    }

    @Test
    fun `verbose and debug priorities are dropped entirely`() {
        tree.v("verbose message")
        tree.d("debug message")

        assertTrue(defaultTagEntries().isEmpty())
    }

    @Test
    fun `warn priority without a throwable logs the message without a suffix`() {
        tree.w("something went wrong")

        val entry = defaultTagEntries().single()
        assertEquals(Log.WARN, entry.type)
        assertEquals("something went wrong", entry.msg)
    }

    @Test
    fun `error with a message and throwable logs only the message and throwable class name`() {
        tree.e(IllegalStateException(secret), "failed")

        val entry = defaultTagEntries().single()
        assertEquals(Log.ERROR, entry.type)
        assertEquals("failed (IllegalStateException)", entry.msg)
    }

    @Test
    fun `error with only a throwable logs just its class name`() {
        tree.e(IllegalStateException(secret))

        assertEquals("(IllegalStateException)", defaultTagEntries().single().msg)
    }

    @Test
    fun `a nested cause and formatted args never leak throwable messages or stack frames`() {
        val error = RuntimeException("outer $secret", IllegalArgumentException("inner $secret"))

        tree.w(error, "sync failed for %s", "draft-1")

        val message = defaultTagEntries().single().msg
        assertEquals("sync failed for draft-1 (RuntimeException)", message)
        assertFalse(message.contains(secret))
        assertFalse(message.contains("\tat "))
    }

    @Test
    fun `a multi-line message keeps its own lines but drops the stack trace`() {
        tree.e(IllegalStateException(secret), "first line\nsecond line")

        assertEquals("first line\nsecond line (IllegalStateException)", defaultTagEntries().single().msg)
    }

    @Test
    fun `log with no explicit tag falls back to the default tag`() {
        tree.e("failed")

        assertEquals(listOf("failed"), defaultTagEntries().map { it.msg })
    }

    // Other threads (e.g. Robolectric loopers) can log concurrently, so never rely on the last entry.
    private fun defaultTagEntries() = ShadowLog.getLogsForTag("MMOCatchRecord")
}
