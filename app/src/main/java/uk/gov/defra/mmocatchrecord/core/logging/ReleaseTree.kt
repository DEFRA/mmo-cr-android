package uk.gov.defra.mmocatchrecord.core.logging

import android.util.Log
import timber.log.Timber

/**
 * Release-build [Timber.Tree]: drops [Log.VERBOSE]/[Log.DEBUG] entirely, and for [Log.WARN]/[Log.ERROR]
 * never logs a [Throwable]'s message or stack trace content — only its class name — since a lower layer's
 * exception message may embed data that is not safe to write to a device log (see ADR 0011 and the security
 * instructions "no PII/secrets in logs"). Debug builds use Timber's own [Timber.DebugTree] instead (see
 * `MmoApplication`), which is unrestricted for local development.
 */
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
        val throwableSuffix = t?.let { " (${it::class.java.simpleName})" }.orEmpty()
        Log.println(priority, tag ?: "MMOCatchRecord", "$message$throwableSuffix")
    }
}
