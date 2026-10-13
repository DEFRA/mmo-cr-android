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
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
class ReleaseTreeTests {
    private val secret = "do not log this message"

    @Before
    fun plantReleaseTree() {
        Timber.uprootAll()
        Timber.plant(ReleaseTree())
        ShadowLog.clear()
    }

    @After
    fun removeAllTrees() {
        Timber.uprootAll()
        ShadowLog.clear()
    }

    @Test
    fun `verbose and debug priorities are dropped entirely`() {
        Timber.v("verbose message")
        Timber.d("debug message")

        assertTrue(defaultTagEntries().isEmpty())
    }

    @Test
    fun `warn priority without a throwable logs the message without a suffix`() {
        Timber.w("something went wrong")

        val entry = defaultTagEntries().single()
        assertEquals(Log.WARN, entry.type)
        assertEquals("something went wrong", entry.msg)
    }

    @Test
    fun `error with a message and throwable logs only the message and throwable class name`() {
        Timber.e(IllegalStateException(secret), "failed")

        val entry = defaultTagEntries().single()
        assertEquals(Log.ERROR, entry.type)
        assertEquals("failed (IllegalStateException)", entry.msg)
    }

    @Test
    fun `error with only a throwable logs just its class name`() {
        Timber.e(IllegalStateException(secret))

        assertEquals("(IllegalStateException)", defaultTagEntries().single().msg)
    }

    @Test
    fun `a nested cause and formatted args never leak throwable messages or stack frames`() {
        val error = RuntimeException("outer $secret", IllegalArgumentException("inner $secret"))

        Timber.w(error, "sync failed for %s", "draft-1")

        val message = defaultTagEntries().single().msg
        assertEquals("sync failed for draft-1 (RuntimeException)", message)
        assertFalse(message.contains(secret))
        assertFalse(message.contains("\tat "))
    }

    @Test
    fun `a multi-line message keeps its own lines but drops the stack trace`() {
        Timber.e(IllegalStateException(secret), "first line\nsecond line")

        assertEquals("first line\nsecond line (IllegalStateException)", defaultTagEntries().single().msg)
    }

    @Test
    fun `log with no explicit tag falls back to the default tag`() {
        Timber.e("failed")

        assertEquals(listOf("failed"), defaultTagEntries().map { it.msg })
    }

    // Other threads (e.g. Robolectric loopers) can log concurrently, so never rely on the last entry.
    private fun defaultTagEntries() = ShadowLog.getLogsForTag("MMOCatchRecord")
}
