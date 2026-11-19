package uk.gov.defra.mmocatchrecord.core.error

import android.database.SQLException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.DraftMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataApiException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordApiException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordUnauthorizedException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MissingVesselRegistrationException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.UnresolvedGearMeasurementException
import java.io.IOException

/** How a [Throwable] was classified by [SafeErrorMapper] — drives both the safe message and retryability. */
enum class CatchRecordErrorClassification {
    /** A transient/offline-style failure (I/O, connectivity) — safe and useful to retry. */
    Transient,

    /** A terminal failure (data corruption, programming error, unexpected state) — retrying will not help. */
    Terminal,

    /** A 401: one silent token refresh is attempted before this is surfaced (ADR 0026, Phase 5). */
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
    private const val SUBMIT_LOCAL_SAVE_TRANSIENT_MESSAGE =
        "We could not save your catch record on this device. Try again."

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

    /** As [map], but for the accept-and-submit local-save path only, whose retryable message is
     *  submit-specific ("...on this device...") — the terminal message is shared and unchanged. */
    fun mapSubmitLocalSave(throwable: Throwable): SafeUserFacingError {
        if (throwable is MissingVesselRegistrationException) {
            return SafeUserFacingError(MISSING_REGISTRATION_MESSAGE, isRetryable = false)
        }
        return when (classify(throwable)) {
            CatchRecordErrorClassification.Transient ->
                SafeUserFacingError(SUBMIT_LOCAL_SAVE_TRANSIENT_MESSAGE, isRetryable = true)
            CatchRecordErrorClassification.Terminal -> SafeUserFacingError(TERMINAL_MESSAGE, isRetryable = false)
            // Save-path saveDraft never surfaces this directly; kept for exhaustiveness.
            CatchRecordErrorClassification.NeedsAuthentication ->
                SafeUserFacingError(TERMINAL_MESSAGE, isRetryable = false)
        }
    }

    fun classify(throwable: Throwable): CatchRecordErrorClassification =
        when (throwable) {
            // Data corruption / an unexpected persisted value — retrying the same operation will not help.
            is DraftMappingException -> CatchRecordErrorClassification.Terminal
            // Stale cached gear data (e.g. a just-migrated apiMeasurementId) — a refresh can resolve it.
            is UnresolvedGearMeasurementException -> CatchRecordErrorClassification.Transient
            // A regulatory field could not be resolved against cached reference data — never retryable.
            is SubmissionMappingException -> CatchRecordErrorClassification.Terminal
            is CatchRecordUnauthorizedException -> CatchRecordErrorClassification.NeedsAuthentication
            // The catch-recording/reference-data APIs already tell us whether a failure is retryable.
            is CatchRecordApiException -> classifyByRetryable(throwable.retryable)
            is ReferenceDataApiException -> classifyByRetryable(throwable.retryable)
            // Network/IO-shaped failures default to transient/retryable (see ADR 0011 for the full rationale).
            is IOException -> CatchRecordErrorClassification.Transient
            // A local Room/disk failure on the submission path (S2) — retrying the save can succeed.
            is SQLException -> CatchRecordErrorClassification.Transient
            else -> CatchRecordErrorClassification.Terminal
        }

    private fun classifyByRetryable(retryable: Boolean): CatchRecordErrorClassification =
        if (retryable) CatchRecordErrorClassification.Transient else CatchRecordErrorClassification.Terminal
}
