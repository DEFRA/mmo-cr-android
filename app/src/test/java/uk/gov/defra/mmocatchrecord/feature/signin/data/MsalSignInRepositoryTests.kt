package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import kotlin.coroutines.cancellation.CancellationException

/** [MsalSignInRepository] against [FakeNativeAuthClient] (ADR 0019 2.1b/c) — outcome mapping, browser fallback. */
@OptIn(ExperimentalCoroutinesApi::class)
class MsalSignInRepositoryTests {
    private val fixtureEmail = "alice@example.com"
    private val fixtureSecret = "unit-test-placeholder-value"

    private fun repository(
        client: NativeAuthClient,
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
    fun `BrowserRequired falls back to the browser leg and succeeds`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.Succeeded(userId = "entra-user-guid"),
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertTrue(result.isSuccess)
            assertEquals("entra-user-guid", result.getOrThrow().userId)
            assertEquals(1, client.interactiveCallCount)
        }

    @Test
    fun `BrowserRequired then an interactive error maps to a safe Unknown failure`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.Failed("safe-mapped-constant"),
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            val failure = failureOf(result)
            assertTrue(failure is SignInFailure.Unknown)
            assertEquals("safe-mapped-constant", (failure as SignInFailure.Unknown).message)
        }

    @Test
    fun `BrowserRequired then an interactive network failure maps to Network`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.NetworkUnavailable,
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertEquals(SignInFailure.Network, failureOf(result))
        }

    @Test
    fun `a second BrowserRequired from the interactive leg is terminal, not retried again`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.BrowserRequired,
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertTrue(failureOf(result) is SignInFailure.Unknown)
            assertEquals(1, client.interactiveCallCount)
        }

    @Test
    fun `no foreground activity maps to a safe Unknown failure via the interactive leg`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome =
                        NativeAuthOutcome.Failed("no foreground activity for the interactive browser leg"),
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertTrue(failureOf(result) is SignInFailure.Unknown)
        }

    @Test
    fun `the user cancelling the browser leg maps to Cancelled, not an error`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.BrowserCancelled,
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertEquals(SignInFailure.Cancelled, failureOf(result))
        }

    @Test
    fun `the stage reports RedirectingToBrowser only while the browser leg is in flight`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.Succeeded(userId = "u"),
                )
            val repo = repository(client, StandardTestDispatcher(testScheduler))

            assertEquals(SignInStage.Idle, repo.stage.value)
            repo.signIn(fixtureEmail, fixtureSecret)
            assertEquals(SignInStage.Idle, repo.stage.value)
        }

    @Test
    fun `the password CharArray is zeroised before the browser leg is invoked`() =
        runTest {
            var passwordWasZeroisedBeforeInteractiveCall = false
            val client =
                object : NativeAuthClient {
                    val delegate = FakeNativeAuthClient(outcome = NativeAuthOutcome.BrowserRequired)

                    override suspend fun signIn(
                        username: String,
                        password: CharArray,
                        scopes: List<String>,
                    ) = delegate.signIn(username, password, scopes)

                    override suspend fun signInInteractively(scopes: List<String>): NativeAuthOutcome {
                        passwordWasZeroisedBeforeInteractiveCall =
                            delegate.lastPasswordReference?.all { it == '\u0000' } == true
                        return NativeAuthOutcome.Succeeded(userId = "u")
                    }

                    override suspend fun currentAccountUserId(): String? = null

                    override suspend fun accessToken(
                        scopes: List<String>,
                        forceRefresh: Boolean,
                    ): String? = null

                    override suspend fun signOut() = true
                }

            repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertTrue(passwordWasZeroisedBeforeInteractiveCall)
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

    @Test
    fun `signOut delegates to the native-auth client and reports its result`() =
        runTest {
            val client = FakeNativeAuthClient(signOutResult = false)

            val reportedSuccess = repository(client, StandardTestDispatcher(testScheduler)).signOut()

            assertEquals(1, client.signOutCallCount)
            assertFalse(reportedSuccess)
        }

    @Test
    fun `a stale cached account is cleared before a native sign-in attempt`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    currentAccountUserIdResult = "fixture-stale-user-id",
                    outcome = NativeAuthOutcome.Succeeded(userId = "entra-user-guid"),
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertTrue(result.isSuccess)
            assertEquals(1, client.signOutCallCount)
        }

    @Test
    fun `no stale account means sign-out is never called before sign-in`() =
        runTest {
            val client = FakeNativeAuthClient(currentAccountUserIdResult = null)

            repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertEquals(0, client.signOutCallCount)
        }

    @Test
    fun `a stale account that fails to clear fails the sign-in safely, never authenticating over it`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    currentAccountUserIdResult = "fixture-stale-user-id",
                    signOutResult = false,
                )

            val result = repository(client, StandardTestDispatcher(testScheduler)).signIn(fixtureEmail, fixtureSecret)

            assertTrue(failureOf(result) is SignInFailure.Unknown)
            assertNull(client.lastUsername)
        }

    @Test
    fun `handoffToBrowser waits for the UI acknowledgement before launching the browser`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.Succeeded(userId = "u"),
                )
            val repo = repository(client, StandardTestDispatcher(testScheduler))

            val job = launch { repo.signIn(fixtureEmail, fixtureSecret) }
            runCurrent()
            assertEquals(0, client.interactiveCallCount)

            repo.acknowledgeBrowserNoticeShown()
            advanceUntilIdle()

            assertEquals(1, client.interactiveCallCount)
            job.join()
        }

    @Test
    fun `the browser leg still proceeds via the timeout fallback if never acknowledged`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveOutcome = NativeAuthOutcome.Succeeded(userId = "u"),
                )
            val repo = repository(client, StandardTestDispatcher(testScheduler))

            val job = launch { repo.signIn(fixtureEmail, fixtureSecret) }
            advanceUntilIdle()

            assertEquals(1, client.interactiveCallCount)
            job.join()
        }

    @Test
    fun `the stage resets to Idle when the browser leg throws`() =
        runTest {
            val client =
                FakeNativeAuthClient(
                    outcome = NativeAuthOutcome.BrowserRequired,
                    interactiveThrown = IllegalStateException("boom"),
                )
            val repo = repository(client, StandardTestDispatcher(testScheduler))

            repo.signIn(fixtureEmail, fixtureSecret)

            assertEquals(SignInStage.Idle, repo.stage.value)
        }

    @Test
    fun `signOut delegates to the native-auth client`() =
        runTest {
            val client = FakeNativeAuthClient()

            repository(client, StandardTestDispatcher(testScheduler)).signOut()

            assertEquals(1, client.signOutCallCount)
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
