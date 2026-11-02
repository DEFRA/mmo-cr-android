package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInUseCase

/** Robolectric is needed so the mapped failure messages resolve to the real, localised string resources. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignInViewModelTests {
    private val context: Context = ApplicationProvider.getApplicationContext()

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
    ) = SignInViewModel(SignInUseCase(repository), context, defaultDispatcher = dispatcher)

    private suspend fun signInAndAwaitError(
        repository: FakeSignInRepository,
        dispatcher: CoroutineDispatcher,
        username: String = "alice@example.com",
        password: String = "valid-fixture-input",
    ): UiStatus.Error {
        val viewModel = buildViewModel(repository, dispatcher)
        viewModel.dispatch(SignInEvent.UsernameChanged(username))
        viewModel.dispatch(SignInEvent.PasswordChanged(password))

        var error: UiStatus.Error? = null
        viewModel.state.test {
            assertEquals(password, awaitItem().password)
            viewModel.dispatch(SignInEvent.SubmitRequested)
            assertEquals(UiStatus.Loading, awaitItem().status)
            val status = awaitItem().status
            assertTrue(status is UiStatus.Error)
            error = status as UiStatus.Error
        }
        return requireNotNull(error)
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
                val content = awaitItem().status
                assertTrue(content is UiStatus.Content)
                assertEquals("alice@example.com", (content as UiStatus.Content<SignInSession>).value.userId)
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
            assertEquals(unknownUser, wrongPassword)
            assertEquals(context.getString(R.string.signin_error_invalid_credentials), unknownUser.message)
            assertFalse(unknownUser.isRetryable)
        }

    @Test
    fun `the generic invalid credentials message names neither the email nor the password as wrong`() {
        val message = context.getString(R.string.signin_error_invalid_credentials)

        assertTrue(message.contains("email address or password"))
    }

    @Test
    fun `a network failure maps to the retryable network message`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeSignInRepository(forcedResult = Result.failure(SignInException(SignInFailure.Network)))

            val error = signInAndAwaitError(repository, dispatcher)

            assertEquals(context.getString(R.string.signin_error_network), error.message)
            assertTrue(error.isRetryable)
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

            val error = signInAndAwaitError(repository, dispatcher)

            assertEquals(context.getString(R.string.signin_error_generic), error.message)
            assertFalse(error.message.contains(diagnostic))
            assertFalse(error.isRetryable)
        }

    @Test
    fun `a raw exception from a misbehaving repository never reaches the view state`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val rawMessage = "MSAL AADSTS50126: user bob@example.com not found; token=abc123"
            val repository = FakeSignInRepository(forcedResult = Result.failure(IllegalStateException(rawMessage)))

            val error = signInAndAwaitError(repository, dispatcher)

            assertEquals(context.getString(R.string.signin_error_generic), error.message)
            assertFalse(error.message.contains("MSAL"))
            assertFalse(error.message.contains("AADSTS50126"))
            assertFalse(error.message.contains("bob@example.com"))
            assertFalse(error.message.contains("abc123"))
        }

    @Test
    fun `the default dispatcher parameter constructs a usable view model with its initial state`() {
        val viewModel = SignInViewModel(SignInUseCase(FakeSignInRepository()), context)

        assertEquals("", viewModel.state.value.username)
        assertEquals("", viewModel.state.value.password)
    }
}
