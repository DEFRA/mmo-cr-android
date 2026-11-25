package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class RequestPasswordResetUseCaseTests {
    private val repository = RecordingPasswordResetRepository()
    private val useCase = RequestPasswordResetUseCase(repository, EmailAddressValidator())

    @Test
    fun `a blank email is rejected and the repository is never called`() =
        runTest {
            val outcome = useCase("  ")

            assertEquals(RequestResetOutcome.Rejected(EmailAddressError.Blank), outcome)
            assertTrue(repository.startedEmails.isEmpty())
        }

    @Test
    fun `a malformed email is rejected and the repository is never called`() =
        runTest {
            val outcome = useCase("not-an-email")

            assertEquals(RequestResetOutcome.Rejected(EmailAddressError.InvalidFormat), outcome)
            assertTrue(repository.startedEmails.isEmpty())
        }

    @Test
    fun `a valid email is trimmed and passed to the repository once`() =
        runTest {
            val token = TestResetToken()
            repository.startResult = StartResetResult.CodeSent(token)

            val outcome = useCase("  name@example.com ")

            assertEquals(listOf("name@example.com"), repository.startedEmails)
            val attempted = outcome as RequestResetOutcome.Attempted
            assertSame(token, (attempted.result as StartResetResult.CodeSent).token)
        }

    @Test
    fun `repository failures are passed through unchanged`() =
        runTest {
            repository.startResult = StartResetResult.Failed(PasswordResetFailure.Throttled)

            val outcome = useCase("name@example.com")

            assertEquals(
                RequestResetOutcome.Attempted(StartResetResult.Failed(PasswordResetFailure.Throttled)),
                outcome,
            )
        }

    @Test
    fun `a thrown exception is normalised to a safe unknown failure`() =
        runTest {
            repository.thrown = IllegalStateException("raw-canary-name@example.com")

            val outcome = useCase("name@example.com") as RequestResetOutcome.Attempted

            val failed = outcome.result as StartResetResult.Failed
            val unknown = failed.failure as PasswordResetFailure.Unknown
            assertTrue(!unknown.safeDiagnostic.contains("canary"))
            assertTrue(!unknown.safeDiagnostic.contains("example.com"))
        }

    @Test
    fun `cancellation is rethrown, not reported as a failure`() =
        runTest {
            repository.thrown = CancellationException("cancelled")

            val thrown = runCatching { useCase("name@example.com") }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
        }
}
