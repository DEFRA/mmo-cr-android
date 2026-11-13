package uk.gov.defra.mmocatchrecord.core.error

import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordUnauthorizedException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionFailureReason
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException

private const val HTTP_FORBIDDEN = 403
private const val HTTP_PAYLOAD_TOO_LARGE = 413
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNPROCESSABLE = 422

/** Terminal-only: maps a throwable to the §6 [SubmissionFailureReason] it persists. See ADR 0022. */
fun submissionFailureReasonFor(throwable: Throwable): SubmissionFailureReason =
    when (throwable) {
        is CatchRecordUnauthorizedException -> SubmissionFailureReason.NeedsSignIn
        is SubmissionMappingException -> SubmissionFailureReason.DataProblem
        is CatchRecordHttpException ->
            when (throwable.httpStatusCode) {
                HTTP_FORBIDDEN -> SubmissionFailureReason.NotAuthorisedForVessel
                HTTP_PAYLOAD_TOO_LARGE -> SubmissionFailureReason.TooLarge
                HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> SubmissionFailureReason.Rejected
                else -> SubmissionFailureReason.DataProblem
            }
        else -> SubmissionFailureReason.DataProblem
    }
