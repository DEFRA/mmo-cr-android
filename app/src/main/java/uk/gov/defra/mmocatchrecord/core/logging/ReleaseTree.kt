package uk.gov.defra.mmocatchrecord.core.logging

import android.util.Log
import timber.log.Timber

/** Release-build tree: WARN+ only, and a [Throwable] is reduced to its class name (ADR 0011).
 * Timber appends the full stack trace to the message before [log] runs, so that suffix is cut here. */
class ReleaseTree : Timber.Tree() {
    override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean = priority >= Log.WARN

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        if (!isLoggable(tag, priority)) return
        val safeMessage = if (t == null) message else withoutStackTrace(message, t)
        val throwableSuffix = t?.let { "(${it::class.java.simpleName})" }
        val line = listOfNotNull(safeMessage.ifBlank { null }, throwableSuffix).joinToString(" ")
        Log.println(priority, tag ?: DEFAULT_TAG, line)
    }

    private fun withoutStackTrace(
        message: String,
        t: Throwable,
    ): String {
        // Every printed stack trace starts with Throwable.toString(), which embeds the unsafe message.
        val traceHead = t.toString()
        return if (message.startsWith(traceHead)) "" else message.substringBefore("\n$traceHead")
    }

    private companion object {
        const val DEFAULT_TAG = "MMOCatchRecord"
    }
}
