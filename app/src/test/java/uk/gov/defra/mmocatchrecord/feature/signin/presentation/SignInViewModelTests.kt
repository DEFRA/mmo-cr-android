package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInUseCase

/** Needs no Robolectric: the view model emits unresolved resource ids, never resolved strings. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTests {
    /** A deliberately mismatched fixture input, distinct from the valid fixture used elsewhere in this file. */
    private val otherTestInput = "mismatched-fixture-input"

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        repository: FakeSignInRepository,
        dispatcher: CoroutineDispatcher,
    ) = SignInViewModel(SignInUseCase(repository), defaultDispatcher = dispatcher)

    private suspend fun signInAndAwaitError(
        repository: FakeSignInRepository,
        dispatcher: CoroutineDispatcher,
        username: String = "alice@example.com",
        password: String = "valid-fixture-input",
    ): SignInViewState {
        val viewModel = buildViewModel(repository, dispatcher)
        viewModel.dispatch(SignInEvent.UsernameChanged(username))
        viewModel.dispatch(SignInEvent.PasswordChanged(password))

        var errored: SignInViewState? = null
        viewModel.state.test {
            assertEquals(password, awaitItem().password)
            viewModel.dispatch(SignInEvent.SubmitRequested)
            assertEquals(UiStatus.Loading, awaitItem().status)
            errored = awaitItem()
        }
        return requireNotNull(errored)
    }

    @Test
    fun `submitting valid credentials moves to Content`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeSignInRepository(accounts = mapOf("alice@example.com" to "valid-fixture-input"))
            val viewModel = buildViewModel(repository, dispatcher)
            viewModel.dispatch(SignInEvent.UsernameChanged("alice@example.com"))
            viewModel.dispatch(SignInEvent.PasswordChanged("valid-fixture-input"))

            viewModel.state.test {
                assertEquals("alice@example.com", awaitItem().username)

                viewModel.dispatch(SignInEvent.SubmitRequested)

                assertEquals(UiStatus.Loading, awaitItem().status)
                val signedIn = awaitItem()
                assertTrue(signedIn.status is UiStatus.Content)
                assertNull(signedIn.error)
                assertEquals(
                    "alice@example.com",
                    (signedIn.status as UiStatus.Content<SignInSession>).value.userId,
                )
            }
        }

    @Test
    fun `a permissive fake with no configured accounts still signs in`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = buildViewModel(FakeSignInRepository(), dispatcher)
            viewModel.dispatch(SignInEvent.PasswordChanged("password"))

            viewModel.state.test {
                assertEquals("password", awaitItem().password)

                viewModel.dispatch(SignInEvent.SubmitRequested)

                assertEquals(UiStatus.Loading, awaitItem().status)
                val content = awaitItem().status
                assertTrue(content is UiStatus.Content)
                assertEquals("", (content as UiStatus.Content<SignInSession>).value.userId)
            }
        }

    @Test
    fun `the error state carries a resource id rather than a resolved string`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeSignInRepository(forcedResult = Result.failure(SignInException(SignInFailure.Network)))

            val errored = signInAndAwaitError(repository, dispatcher)

            // Resolved in composition instead, where AppLanguageProvider's locale override applies.
            assertEquals(R.string.signin_error_network, errored.error?.messageRes)
            assertEquals(UiStatus.Idle, errored.status)
        }

    @Test
    fun `an unknown account and a wrong password produce an identical, generic error state`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val accounts = mapOf("alice@example.com" to "valid-fixture-input")

            val unknownUser =
                signInAndAwaitError(
                    repository = FakeSignInRepository(accounts = accounts),
                    dispatcher = dispatcher,
                    username = "nobody@example.com",
                )
            val wrongPassword =
                signInAndAwaitError(
                    repository = FakeSignInRepository(accounts = accounts),
                    dispatcher = dispatcher,
                    password = otherTestInput,
                )

            // Account-enumeration control: the two paths must be indistinguishable to the UI.
            assertEquals(unknownUser.error, wrongPassword.error)
            assertEquals(R.string.signin_error_invalid_credentials, unknownUser.error?.messageRes)
            assertFalse(unknownUser.error?.isRetryable ?: true)
        }

    @Test
    fun `a network failure maps to the retryable network message`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeSignInRepository(forcedResult = Result.failure(SignInException(SignInFailure.Network)))

            val errored = signInAndAwaitError(repository, dispatcher)

            assertEquals(R.string.signin_error_network, errored.error?.messageRes)
            assertTrue(errored.error?.isRetryable ?: false)
        }

    @Test
    fun `an Unknown failure shows the generic message and never its diagnostic text`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val diagnostic = "aadsts50126-diagnostic-canary"
            val repository =
                FakeSignInRepository(
                    forcedResult = Result.failure(SignInException(SignInFailure.Unknown(diagnostic))),
                )

            val errored = signInAndAwaitError(repository, dispatcher)

            assertEquals(R.string.signin_error_generic, errored.error?.messageRes)
            assertFalse(errored.toString().contains(diagnostic))
        }

    @Test
    fun `a raw exception from a misbehaving repository never reaches the view state`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val rawMessage = "MSAL AADSTS50126: user bob@example.com not found; token=abc123"
            val repository = FakeSignInRepository(forcedResult = Result.failure(IllegalStateException(rawMessage)))

            val errored = signInAndAwaitError(repository, dispatcher)

            assertEquals(R.string.signin_error_generic, errored.error?.messageRes)
            // The whole state is searched, so no future field can reintroduce the leak unnoticed.
            listOf("MSAL", "AADSTS50126", "bob@example.com", "abc123").forEach {
                assertFalse(it, errored.toString().contains(it))
            }
        }

    @Test
    fun `a retried submit clears the previous error before it reports the next outcome`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeSignInRepository(forcedResult = Result.failure(SignInException(SignInFailure.Network)))
            val viewModel = buildViewModel(repository, dispatcher)

            viewModel.state.test {
                assertNull(awaitItem().error)
                viewModel.dispatch(SignInEvent.SubmitRequested)
                assertNull(awaitItem().error)
                assertEquals(R.string.signin_error_network, awaitItem().error?.messageRes)

                viewModel.dispatch(SignInEvent.SubmitRequested)

                val loading = awaitItem()
                assertEquals(UiStatus.Loading, loading.status)
                assertNull(loading.error)
            }
        }

    @Test
    fun `a VerificationNotSupported failure maps to its own non-retryable message`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeSignInRepository(
                    forcedResult = Result.failure(SignInException(SignInFailure.VerificationNotSupported)),
                )

            val errored = signInAndAwaitError(repository, dispatcher)

            assertEquals(R.string.signin_error_verification_not_supported, errored.error?.messageRes)
            assertFalse(errored.error?.isRetryable ?: true)
        }

    @Test
    fun `the default dispatcher parameter constructs a usable view model with its initial state`() {
        val viewModel = SignInViewModel(SignInUseCase(FakeSignInRepository()))

        assertEquals("", viewModel.state.value.username)
        assertEquals("", viewModel.state.value.password)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a Cancelled failure returns to idle with no error and no stuck spinner`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeSignInRepository(forcedResult = Result.failure(SignInException(SignInFailure.Cancelled)))

            val afterCancel = signInAndAwaitError(repository, dispatcher)

            assertEquals(UiStatus.Idle, afterCancel.status)
            assertNull(afterCancel.error)
        }

    @Test
    fun `the stage observer's RedirectingToBrowser is reflected in isRedirectingToBrowser`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val stage = MutableStateFlow(SignInStage.Idle)
            val observer =
                object : SignInStageObserver {
                    override val stage: StateFlow<SignInStage> = stage
                }
            val viewModel =
                SignInViewModel(
                    SignInUseCase(FakeSignInRepository()),
                    defaultDispatcher = dispatcher,
                    signInStageObserver = observer,
                )

            viewModel.state.test {
                assertFalse(awaitItem().isRedirectingToBrowser)
                stage.value = SignInStage.RedirectingToBrowser
                assertTrue(awaitItem().isRedirectingToBrowser)
                stage.value = SignInStage.Idle
                assertFalse(awaitItem().isRedirectingToBrowser)
            }
        }

    @Test
    fun `BrowserNoticeShown forwards to the stage observer's acknowledgement`() {
        val dispatcher = StandardTestDispatcher()
        var acknowledged = false
        val observer =
            object : SignInStageObserver {
                override val stage: StateFlow<SignInStage> = MutableStateFlow(SignInStage.Idle)

                override fun acknowledgeBrowserNoticeShown() {
                    acknowledged = true
                }
            }
        val viewModel =
            SignInViewModel(
                SignInUseCase(FakeSignInRepository()),
                defaultDispatcher = dispatcher,
                signInStageObserver = observer,
            )

        viewModel.dispatch(SignInEvent.BrowserNoticeShown)

        assertTrue(acknowledged)
    }
}
