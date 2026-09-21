package uk.gov.defra.mmocatchrecord.core.error

import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.DraftMappingException
import java.io.IOException

/** How a [Throwable] was classified by [SafeErrorMapper] — drives both the safe message and retryability. */
enum class CatchRecordErrorClassification {
    /** A transient/offline-style failure (I/O, connectivity) — safe and useful to retry. */
    Transient,

    /** A terminal failure (data corruption, programming error, unexpected state) — retrying will not help. */
    Terminal,
}

/** The result of mapping a [Throwable] to something safe to show the user — see [SafeErrorMapper]. */
data class SafeUserFacingError(
    val message: String,
    val isRetryable: Boolean,
)

/**
 * Maps any [Throwable] to a safe, generic, plain-English user-facing message plus the correct
 * [SafeUserFacingError.isRetryable] flag — **never** reads or forwards [Throwable.message] (see ADR 0011 and
 * the error-handling standards: "never surface a raw exception message"). Callers additionally log the
 * throwable itself via Timber for diagnostics; [SafeErrorMapper] only decides what the *user* sees.
 */
object SafeErrorMapper {
    private const val TRANSIENT_MESSAGE = "We could not save your changes. Check your connection and try again."
    private const val TERMINAL_MESSAGE = "Something went wrong and we could not complete that action."

    fun map(throwable: Throwable): SafeUserFacingError =
        when (classify(throwable)) {
            CatchRecordErrorClassification.Transient -> SafeUserFacingError(TRANSIENT_MESSAGE, isRetryable = true)
            CatchRecordErrorClassification.Terminal -> SafeUserFacingError(TERMINAL_MESSAGE, isRetryable = false)
        }

    fun classify(throwable: Throwable): CatchRecordErrorClassification =
        when (throwable) {
            // Data corruption / an unexpected persisted value — retrying the same operation will not help.
            is DraftMappingException -> CatchRecordErrorClassification.Terminal
            // Network/IO-shaped failures (including android.database.sqlite transient lock/busy exceptions,
            // which surface as generic RuntimeException subclasses on some API levels) are treated as
            // transient/retryable; everything else defaults to terminal rather than risking an infinite
            // retry loop against a non-recoverable failure.
            is IOException -> CatchRecordErrorClassification.Transient
            else -> CatchRecordErrorClassification.Terminal
        }
}
