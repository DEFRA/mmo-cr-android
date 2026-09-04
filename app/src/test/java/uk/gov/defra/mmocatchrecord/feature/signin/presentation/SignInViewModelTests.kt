package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitting valid credentials moves to Content`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeSignInRepository()
            val viewModel = SignInViewModel(SignInUseCase(repository), defaultDispatcher = dispatcher)
            viewModel.dispatch(SignInEvent.UsernameChanged("alice"))
            viewModel.dispatch(SignInEvent.PasswordChanged("password"))

            viewModel.state.test {
                assertEquals("alice", awaitItem().username)

                viewModel.dispatch(SignInEvent.SubmitRequested)

                assertEquals(UiStatus.Loading, awaitItem().status)
                val content = awaitItem().status
                assertTrue(content is UiStatus.Content)
                assertEquals("alice", (content as UiStatus.Content<SignInSession>).value.userId)
            }
        }

    @Test
    fun `submitting blank username surfaces a validation error and never calls the repository`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            var repositoryCalled = false
            val repository =
                FakeSignInRepository(result = { username ->
                    repositoryCalled = true
                    Result.success(SignInSession(userId = username, signedInAtEpochMillis = 0L))
                })
            val viewModel = SignInViewModel(SignInUseCase(repository), defaultDispatcher = dispatcher)
            viewModel.dispatch(SignInEvent.PasswordChanged("password"))

            viewModel.state.test {
                assertEquals("password", awaitItem().password)

                viewModel.dispatch(SignInEvent.SubmitRequested)

                assertEquals(UiStatus.Loading, awaitItem().status)
                val error = awaitItem().status
                assertTrue(error is UiStatus.Error)
                assertTrue((error as UiStatus.Error).isRetryable)
            }
            assertTrue(!repositoryCalled)
        }
}
