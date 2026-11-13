package uk.gov.defra.mmocatchrecord.core.error

import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.DraftMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataApiException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordApiException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordUnauthorizedException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MissingVesselRegistrationException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException
import java.io.IOException

/** How a [Throwable] was classified by [SafeErrorMapper] — drives both the safe message and retryability. */
enum class CatchRecordErrorClassification {
    /** A transient/offline-style failure (I/O, connectivity) — safe and useful to retry. */
    Transient,

    /** A terminal failure (data corruption, programming error, unexpected state) — retrying will not help. */
    Terminal,

    /** A 401: one silent token refresh is attempted before this is surfaced (ADR 0022, Phase 5). */
    NeedsAuthentication,
}

/** The result of mapping a [Throwable] to something safe to show the user — see [SafeErrorMapper]. */
data class SafeUserFacingError(
    val message: String,
    val isRetryable: Boolean,
)

/**
 * Maps any [Throwable] to a safe, plain-English user-facing message plus [SafeUserFacingError.isRetryable] —
 * never reads/forwards [Throwable.message] (ADR 0011). Callers log the throwable itself via Timber.
 */
object SafeErrorMapper {
    private const val TRANSIENT_MESSAGE = "We could not save your changes. Check your connection and try again."
    private const val TERMINAL_MESSAGE = "Something went wrong and we could not complete that action."
    private const val MISSING_REGISTRATION_MESSAGE =
        "You cannot start a catch record for this vessel because its registration details are missing. " +
            "Contact the MMO."

    fun map(throwable: Throwable): SafeUserFacingError {
        if (throwable is MissingVesselRegistrationException) {
            return SafeUserFacingError(MISSING_REGISTRATION_MESSAGE, isRetryable = false)
        }
        return when (classify(throwable)) {
            CatchRecordErrorClassification.Transient -> SafeUserFacingError(TRANSIENT_MESSAGE, isRetryable = true)
            CatchRecordErrorClassification.Terminal -> SafeUserFacingError(TERMINAL_MESSAGE, isRetryable = false)
            CatchRecordErrorClassification.NeedsAuthentication ->
                SafeUserFacingError(TERMINAL_MESSAGE, isRetryable = false)
        }
    }

    fun classify(throwable: Throwable): CatchRecordErrorClassification =
        when (throwable) {
            // Data corruption / an unexpected persisted value — retrying the same operation will not help.
            is DraftMappingException -> CatchRecordErrorClassification.Terminal
            // A regulatory field could not be resolved against cached reference data — never retryable.
            is SubmissionMappingException -> CatchRecordErrorClassification.Terminal
            is CatchRecordUnauthorizedException -> CatchRecordErrorClassification.NeedsAuthentication
            // The catch-recording/reference-data APIs already tell us whether a failure is retryable.
            is CatchRecordApiException ->
                if (throwable.retryable) {
                    CatchRecordErrorClassification.Transient
                } else {
                    CatchRecordErrorClassification.Terminal
                }
            is ReferenceDataApiException ->
                if (throwable.retryable) {
                    CatchRecordErrorClassification.Transient
                } else {
                    CatchRecordErrorClassification.Terminal
                }
            // Network/IO-shaped failures default to transient/retryable (see ADR 0011 for the full rationale).
            is IOException -> CatchRecordErrorClassification.Transient
            else -> CatchRecordErrorClassification.Terminal
        }
}
