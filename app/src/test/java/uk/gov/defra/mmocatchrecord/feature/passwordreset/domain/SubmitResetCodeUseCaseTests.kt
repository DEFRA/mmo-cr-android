package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class SubmitResetCodeUseCaseTests {
    private val repository = RecordingPasswordResetRepository()
    private val useCase = SubmitResetCodeUseCase(repository, ResetCodeValidator())
    private val token = TestResetToken()

    @Test
    fun `a blank code is rejected and the repository is never called`() =
        runTest {
            val outcome = useCase(token, "   ")

            assertEquals(SubmitCodeOutcome.Rejected(ResetCodeError.Blank), outcome)
            assertTrue(repository.submittedCodes.isEmpty())
        }

    @Test
    fun `a code is normalised before it is submitted`() =
        runTest {
            val accepted = TestResetToken("next")
            repository.submitCodeResult = SubmitCodeResult.Accepted(accepted)

            val outcome = useCase(token, " 1234 5678 ") as SubmitCodeOutcome.Attempted

            assertEquals(listOf("12345678"), repository.submittedCodes)
            assertSame(accepted, (outcome.result as SubmitCodeResult.Accepted).token)
        }

    @Test
    fun `invalid and expired results are passed through`() =
        runTest {
            repository.submitCodeResult = SubmitCodeResult.InvalidCode
            assertEquals(SubmitCodeOutcome.Attempted(SubmitCodeResult.InvalidCode), useCase(token, "1"))

            repository.submitCodeResult = SubmitCodeResult.Expired
            assertEquals(SubmitCodeOutcome.Attempted(SubmitCodeResult.Expired), useCase(token, "1"))
        }

    @Test
    fun `a thrown exception is normalised to a safe unknown failure`() =
        runTest {
            repository.thrown = IllegalStateException("raw-canary-12345678")

            val outcome = useCase(token, "12345678") as SubmitCodeOutcome.Attempted

            val failed = outcome.result as SubmitCodeResult.Failed
            val unknown = failed.failure as PasswordResetFailure.Unknown
            assertTrue(!unknown.safeDiagnostic.contains("canary"))
            assertTrue(!unknown.safeDiagnostic.contains("12345678"))
        }

    @Test
    fun `cancellation is rethrown, not reported as a failure`() =
        runTest {
            repository.thrown = CancellationException("cancelled")

            val thrown = runCatching { useCase(token, "12345678") }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
        }
}
