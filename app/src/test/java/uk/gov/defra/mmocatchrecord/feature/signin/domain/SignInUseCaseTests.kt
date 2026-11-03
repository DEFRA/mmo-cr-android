package uk.gov.defra.mmocatchrecord.feature.signin.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import kotlin.coroutines.cancellation.CancellationException

class SignInUseCaseTests {
    private val accounts = mapOf("alice@example.com" to "valid-fixture-input")

    private suspend fun failureOf(repository: SignInRepository): SignInFailure {
        val result = SignInUseCase(repository)("alice@example.com", "valid-fixture-input")
        val thrown = result.exceptionOrNull()
        assertTrue("Expected a SignInException, got $thrown", thrown is SignInException)
        return (thrown as SignInException).failure
    }

    @Test
    fun `a successful sign in is passed straight through`() =
        runTest {
            val repository = FakeSignInRepository(accounts = accounts)

            val result = SignInUseCase(repository)("alice@example.com", "valid-fixture-input")

            assertEquals("alice@example.com", result.getOrNull()?.userId)
        }

    @Test
    fun `an already-mapped failure is passed through unchanged`() =
        runTest {
            val failure = SignInFailure.Unknown("mapped-safe-text")
            val repository = FakeSignInRepository(forcedResult = Result.failure(SignInException(failure)))

            assertSame(failure, failureOf(repository))
        }

    @Test
    fun `a raw failure result is normalised to an Unknown failure`() =
        runTest {
            val repository = FakeSignInRepository(forcedResult = Result.failure(IllegalStateException("raw-canary")))

            val failure = failureOf(repository)

            assertTrue(failure is SignInFailure.Unknown)
            assertEquals("unclassified sign-in failure", (failure as SignInFailure.Unknown).message)
        }

    @Test
    fun `a repository that throws is normalised to an Unknown failure`() =
        runTest {
            val repository = SignInRepository { _, _ -> throw UnsupportedOperationException("thrown-canary") }

            val failure = failureOf(repository)

            assertTrue(failure is SignInFailure.Unknown)
            assertEquals("unclassified sign-in failure", (failure as SignInFailure.Unknown).message)
        }

    @Test
    fun `an unknown account and a wrong password are indistinguishable`() =
        runTest {
            val useCase = SignInUseCase(FakeSignInRepository(accounts = accounts))

            val unknownUser = useCase("nobody@example.com", "valid-fixture-input").exceptionOrNull()
            val wrongPassword = useCase("alice@example.com", "mismatched-fixture-input").exceptionOrNull()

            assertEquals(SignInFailure.InvalidCredentials, (unknownUser as SignInException).failure)
            assertEquals(SignInFailure.InvalidCredentials, (wrongPassword as SignInException).failure)
            // Nothing in the thrown value may hint at which half of the credential pair was wrong.
            assertNull(unknownUser.message)
            assertNull(wrongPassword.message)
        }

    @Test
    fun `a cancellation thrown by the repository propagates instead of being mapped to a failure`() =
        runTest {
            val repository = SignInRepository { _, _ -> throw CancellationException("cancelled") }

            val propagated = cancellationFrom { SignInUseCase(repository)("alice@example.com", "valid-fixture-input") }

            assertNotNull("Cancellation must unwind the coroutine, not become a sign-in failure", propagated)
        }

    @Test
    fun `a cancellation carried in a failure result also propagates`() =
        runTest {
            val repository =
                FakeSignInRepository(forcedResult = Result.failure(CancellationException("cancelled")))

            val propagated = cancellationFrom { SignInUseCase(repository)("alice@example.com", "valid-fixture-input") }

            assertNotNull("Cancellation must unwind the coroutine, not become a sign-in failure", propagated)
        }

    private suspend fun cancellationFrom(block: suspend () -> Result<SignInSession>): CancellationException? =
        try {
            block()
            null
        } catch (expected: CancellationException) {
            expected
        }

    @Test
    fun `a forced result takes precedence over configured accounts`() =
        runTest {
            val repository =
                FakeSignInRepository(
                    accounts = accounts,
                    forcedResult = Result.failure(SignInException(SignInFailure.Network)),
                )

            assertEquals(SignInFailure.Network, failureOf(repository))
        }
}
