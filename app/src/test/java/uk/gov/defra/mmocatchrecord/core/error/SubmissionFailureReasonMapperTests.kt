package uk.gov.defra.mmocatchrecord.core.error

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordUnauthorizedException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionFailureReason
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException

class SubmissionFailureReasonMapperTests {
    @Test
    fun `an unauthorized failure needs sign in`() {
        assertEquals(
            SubmissionFailureReason.NeedsSignIn,
            submissionFailureReasonFor(CatchRecordUnauthorizedException("submit", null)),
        )
    }

    @Test
    fun `a submission mapping failure is a data problem`() {
        assertEquals(
            SubmissionFailureReason.DataProblem,
            submissionFailureReasonFor(SubmissionMappingException("species unresolvable")),
        )
    }

    @Test
    fun `http failures map by status code`() {
        val expected =
            mapOf(
                403 to SubmissionFailureReason.NotAuthorisedForVessel,
                413 to SubmissionFailureReason.TooLarge,
                400 to SubmissionFailureReason.Rejected,
                422 to SubmissionFailureReason.Rejected,
                404 to SubmissionFailureReason.DataProblem,
            )

        expected.forEach { (status, reason) ->
            assertEquals(reason, submissionFailureReasonFor(httpError(status)))
        }
    }

    @Test
    fun `an unrecognised failure is a data problem`() {
        assertEquals(SubmissionFailureReason.DataProblem, submissionFailureReasonFor(IllegalStateException()))
    }

    private fun httpError(status: Int) = CatchRecordHttpException("submit", status, null, null, retryable = false)
}
