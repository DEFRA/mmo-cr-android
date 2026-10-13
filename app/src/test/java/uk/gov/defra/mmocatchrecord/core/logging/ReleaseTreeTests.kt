package uk.gov.defra.mmocatchrecord.core.logging

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import timber.log.Timber

/** Covers [ReleaseTree]'s log-level filtering and throwable-message redaction (ADR 0011). */
@RunWith(RobolectricTestRunner::class)
class ReleaseTreeTests {
    @Before
    fun plantReleaseTree() {
        Timber.uprootAll()
        Timber.plant(ReleaseTree())
        ShadowLog.clear()
    }

    @After
    fun removeAllTrees() {
        Timber.uprootAll()
    }

    @Test
    fun `verbose and debug priorities are dropped entirely`() {
        Timber.v("verbose message")
        Timber.d("debug message")

        val logs = ShadowLog.getLogs()
        assertTrue(logs.none { it.msg?.contains("verbose message") == true })
        assertTrue(logs.none { it.msg?.contains("debug message") == true })
    }

    @Test
    fun `warn priority without a throwable logs the message without a suffix`() {
        Timber.w("something went wrong")

        val entry = ShadowLog.getLogs().last()
        assertEquals(Log.WARN, entry.type)
        assertEquals("something went wrong", entry.msg)
    }

    @Test
    fun `error priority with a throwable appends the throwable class name suffix`() {
        Timber.e(IllegalStateException("do not log this message"), "failed")

        val entry = ShadowLog.getLogs().last()
        assertTrue(entry.msg.startsWith("failed"))
        assertTrue(entry.msg.endsWith("(IllegalStateException)"))
    }

    @Test
    fun `log with no explicit tag falls back to the default tag`() {
        Timber.e("failed")

        val entry = ShadowLog.getLogs().last()
        assertEquals("MMOCatchRecord", entry.tag)
    }
}
