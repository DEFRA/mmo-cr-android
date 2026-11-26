package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class ContinueResetInBrowserUseCaseTests {
    private val repository = RecordingPasswordResetRepository()
    private val useCase = ContinueResetInBrowserUseCase(repository)

    @Test
    fun `the repository result is passed through`() =
        runTest {
            repository.browserResult = BrowserContinuationResult.Cancelled

            assertEquals(BrowserContinuationResult.Cancelled, useCase())
            assertEquals(1, repository.browserCallCount)
        }

    @Test
    fun `a thrown exception becomes a safe unknown failure`() =
        runTest {
            val failing =
                object : PasswordResetRepository by repository {
                    override suspend fun continueInBrowser(): BrowserContinuationResult =
                        throw IllegalStateException("raw-canary")
                }

            val result = ContinueResetInBrowserUseCase(failing)() as BrowserContinuationResult.Failed

            assertTrue(result.failure is PasswordResetFailure.Unknown)
            assertTrue(!result.failure.toString().contains("raw-canary"))
        }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown`() =
        runTest {
            val cancelling =
                object : PasswordResetRepository by repository {
                    override suspend fun continueInBrowser(): BrowserContinuationResult =
                        throw CancellationException("cancelled")
                }

            ContinueResetInBrowserUseCase(cancelling)()
        }
}
