package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import kotlin.coroutines.cancellation.CancellationException

/**
 * [MsalSignInRepository] against a hand-written [FakeNativeAuthClient] (ADR 0019 Phase 2.1b) —
 * covers every outcome mapping, the enumeration guard, cancellation and zeroisation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MsalSignInRepositoryTests {
    private val fixtureEmail = "alice@example.com"
    private val fixtureSecret = "unit-test-placeholder-value"

    private fun repository(
        client: FakeNativeAuthClient,
        dispatcher: CoroutineDispatcher,
    ) = MsalSignInRepository(client, dispatcher)

    @Test
    fun `Succeeded maps to a successful session carrying the native-auth user id`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.Succeeded(userId = "entra-user-guid"))

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertTrue(result.isSuccess)
            assertEquals("entra-user-guid", result.getOrThrow().userId)
        }

    @Test
    fun `InvalidCredentialsOrUnknownAccount maps to InvalidCredentials`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.InvalidCredentialsOrUnknownAccount)

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertEquals(SignInFailure.InvalidCredentials, failureOf(result))
        }

    @Test
    fun `an unknown account and a wrong password produce an identical mapped failure (enumeration guard)`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)

            val unknownAccount =
                repository(
                    FakeNativeAuthClient(outcome = NativeAuthOutcome.InvalidCredentialsOrUnknownAccount),
                    dispatcher,
                ).signIn("nobody@example.com", fixtureSecret)
            val wrongPassword =
                repository(
                    FakeNativeAuthClient(outcome = NativeAuthOutcome.InvalidCredentialsOrUnknownAccount),
                    dispatcher,
                ).signIn(fixtureEmail, "unit-test-placeholder-value-2")

            assertEquals(failureOf(unknownAccount), failureOf(wrongPassword))
            assertEquals(SignInFailure.InvalidCredentials, failureOf(unknownAccount))
        }

    @Test
    fun `CodeRequired maps to VerificationNotSupported`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.CodeRequired)

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertEquals(SignInFailure.VerificationNotSupported, failureOf(result))
        }

    @Test
    fun `BrowserRequired is an interim Unknown failure until the web fallback ships`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.BrowserRequired)

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            val failure = failureOf(result)
            assertTrue(failure is SignInFailure.Unknown)
        }

    @Test
    fun `NetworkUnavailable maps to Network`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.NetworkUnavailable)

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertEquals(SignInFailure.Network, failureOf(result))
        }

    @Test
    fun `Failed carries its safe diagnostic into an Unknown failure`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.Failed("safe-mapped-constant"))

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            val failure = failureOf(result)
            assertTrue(failure is SignInFailure.Unknown)
            assertEquals("safe-mapped-constant", (failure as SignInFailure.Unknown).message)
        }

    @Test
    fun `an unexpected exception from the client maps to a safe Unknown failure`() =
        runTest {
            val client =
                FakeNativeAuthClient(thrown = IllegalStateException("leaky message with $fixtureEmail inside"))

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            val failure = failureOf(result)
            assertTrue(failure is SignInFailure.Unknown)
            assertFalse((failure as SignInFailure.Unknown).message.contains(fixtureEmail))
        }

    @Test(expected = CancellationException::class)
    fun `CancellationException propagates out uncaught`() =
        runTest {
            val client = FakeNativeAuthClient(thrown = CancellationException("structured concurrency unwind"))

            repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)
        }

    @Test
    fun `sign-in runs on the injected dispatcher`() =
        runTest {
            var dispatchCount = 0
            val delegate = StandardTestDispatcher(testScheduler)
            val trackingDispatcher =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: kotlin.coroutines.CoroutineContext,
                        block: Runnable,
                    ) {
                        dispatchCount++
                        delegate.dispatch(context, block)
                    }
                }

            repository(FakeNativeAuthClient(), trackingDispatcher).signIn(fixtureEmail, fixtureSecret)

            assertTrue(dispatchCount > 0)
        }

    @Test
    fun `the password CharArray is zeroised after a successful sign-in`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.Succeeded(userId = "u"))

            repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertZeroised(client.lastPasswordReference)
        }

    @Test
    fun `the password CharArray is zeroised after a mapped failure`() =
        runTest {
            val client = FakeNativeAuthClient(outcome = NativeAuthOutcome.InvalidCredentialsOrUnknownAccount)

            repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertZeroised(client.lastPasswordReference)
        }

    @Test
    fun `the password CharArray is zeroised even when the client throws`() =
        runTest {
            val client = FakeNativeAuthClient(thrown = IllegalStateException("boom"))

            repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertZeroised(client.lastPasswordReference)
        }

    private fun assertZeroised(chars: CharArray?) {
        assertNotNull(chars)
        requireNotNull(chars).forEach { assertEquals('\u0000', it) }
    }

    private fun failureOf(result: Result<*>): SignInFailure {
        val exception = result.exceptionOrNull()
        assertTrue(exception is SignInException)
        return (exception as SignInException).failure
    }
}
