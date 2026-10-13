package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageViewModel
import uk.gov.defra.mmocatchrecord.core.language.FakeAppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInUseCase

/** Robolectric coverage is needed because instrumented coverage is not merged into Kover/SonarCloud.
 * Below-the-fold assertions scroll through the tall sign-in content. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignInScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sign in content renders fields, header, trouble section and the expandable details`() {
        composeTestRule.setContent {
            MmoTheme {
                SignInContent(
                    state = SignInViewState(username = "james.wilson@company.co.uk", password = ""),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SignInScreenTestTags.USERNAME_FIELD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(SignInScreenTestTags.SUBMIT_ACTION).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("CYM").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Having trouble signing in?").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Create an account").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Vessel owner:").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Skipper or agent:").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Forgotten your password?").performScrollTo().performClick()
    }

    @Test
    fun `typing into the username and password fields updates the controlled state`() {
        var state by mutableStateOf(SignInViewState())
        composeTestRule.setContent {
            MmoTheme {
                SignInContent(
                    state = state,
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    onUsernameChanged = { state = state.copy(username = it) },
                    onPasswordChanged = { state = state.copy(password = it) },
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SignInScreenTestTags.USERNAME_FIELD).performScrollTo().performTextInput("alice")
        composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("secret")

        assertEquals("alice", state.username)
        assertEquals("secret", state.password)
    }

    @Test
    fun `an error state shows the error message and a welsh language shows the english toggle label`() {
        var toggled = false
        composeTestRule.setContent {
            MmoTheme {
                SignInContent(
                    state =
                        SignInViewState(
                            status = UiStatus.Error("Sign in failed. Please try again.", isRetryable = true),
                        ),
                    currentLanguage = "cy",
                    onLanguageToggle = { toggled = true },
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("ENG").performScrollTo().performClick()
        assertTrue(toggled)
    }

    @Test
    fun `submitting shows the loading indicator then the retryable error message on failure`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeSignInRepository(result = { Result.failure(IllegalStateException("Invalid credentials")) })
            val viewModel = SignInViewModel(SignInUseCase(repository), defaultDispatcher = dispatcher)

            composeTestRule.setContent {
                MmoTheme {
                    val state by viewModel.state.collectAsState()
                    SignInContent(
                        state = state,
                        currentLanguage = "en",
                        onLanguageToggle = {},
                        onUsernameChanged = { viewModel.dispatch(SignInEvent.UsernameChanged(it)) },
                        onPasswordChanged = { viewModel.dispatch(SignInEvent.PasswordChanged(it)) },
                        onSubmit = { viewModel.dispatch(SignInEvent.SubmitRequested) },
                    )
                }
            }

            composeTestRule
                .onNodeWithTag(
                    SignInScreenTestTags.USERNAME_FIELD,
                ).performScrollTo()
                .performTextInput("alice")
            composeTestRule.onNodeWithTag(SignInScreenTestTags.SUBMIT_ACTION).performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertTrue(viewModel.state.value.status is UiStatus.Loading)

            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE).performScrollTo().assertIsDisplayed()
            assertFalse(viewModel.state.value.status is UiStatus.Loading)
        }

    @Test
    fun `the stateful screen wrapper wires the real view models and navigates on success`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeSignInRepository()
            val viewModel = SignInViewModel(SignInUseCase(repository), defaultDispatcher = dispatcher)
            val languageViewModel = AppLanguageViewModel(FakeAppLanguageRepository())
            var signedIn = false

            composeTestRule.setContent {
                MmoTheme {
                    SignInScreen(
                        onSignedIn = { signedIn = true },
                        viewModel = viewModel,
                        languageViewModel = languageViewModel,
                    )
                }
            }

            composeTestRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag(
                    SignInScreenTestTags.USERNAME_FIELD,
                ).performScrollTo()
                .performTextInput("alice")
            composeTestRule.onNodeWithTag(SignInScreenTestTags.SUBMIT_ACTION).performScrollTo().performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertTrue(signedIn)
            assertEquals(AppLanguage.ENGLISH, languageViewModel.language.value)
        }

    @Test
    fun `the preview composable renders without crashing`() {
        composeTestRule.setContent {
            SignInScreenPreview()
        }
        composeTestRule.onNodeWithTag(SignInScreenTestTags.USERNAME_FIELD).performScrollTo().assertIsDisplayed()
    }
}
