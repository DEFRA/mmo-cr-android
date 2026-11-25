package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordCodeRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordEmailRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordGraphRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordNewPasswordRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordSuccessRoute
import uk.gov.defra.mmocatchrecord.common.navigation.SignInRoute
import uk.gov.defra.mmocatchrecord.core.connectivity.FakeConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.BrowserContinuationResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ContinueResetInBrowserUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.EmailAddressValidator
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicy
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicyValidator
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.RecordingPasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.RequestPasswordResetUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ResetCodeValidator
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.StartResetResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitNewPasswordUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitResetCodeUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.TEST_SESSION
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver

private const val STUB_SIGN_IN = "stub_sign_in"
private const val STUB_FORGOTTEN = "stub_forgotten_password"

/**
 * Back-stack behaviour of the reset graph (Q8, AC20 to AC22), via a stub Sign in destination.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions")
class ResetPasswordNavigationTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = RecordingPasswordResetRepository()
    private val viewModels = mutableListOf<ResetPasswordViewModel>()
    private var signedIn = 0
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        ResetPasswordViewModel(
            useCases =
                ResetPasswordUseCases(
                    RequestPasswordResetUseCase(repository, EmailAddressValidator()),
                    SubmitResetCodeUseCase(repository, ResetCodeValidator()),
                    SubmitNewPasswordUseCase(repository, PasswordPolicyValidator(PasswordPolicy())),
                    ContinueResetInBrowserUseCase(repository),
                    PasswordPolicy(),
                ),
            connectivityObserver = FakeConnectivityObserver(initiallyOnline = true),
            connectivityChecker = NetworkConnectivityChecker { true },
            stageObserver =
                object : SignInStageObserver {
                    override val stage = MutableStateFlow(SignInStage.Idle)
                },
            clock = { 0L },
            codeLifetimeMinutes = 5,
            defaultDispatcher = dispatcher,
        ).also { viewModels += it }

    private fun newController(): NavHostController =
        NavHostController(ApplicationProvider.getApplicationContext<Context>()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            navigatorProvider.addNavigator(DialogNavigator())
        }

    @Composable
    private fun graphViewModel(
        controller: NavHostController,
        entry: NavBackStackEntry,
    ): ResetPasswordViewModel {
        val graphEntry = remember(entry) { controller.getBackStackEntry<ResetPasswordGraphRoute>() }
        return viewModel(
            viewModelStoreOwner = graphEntry,
            factory = viewModelFactory { initializer { newViewModel() } },
        )
    }

    @Composable
    private fun TestHost(controller: NavHostController) {
        CompositionLocalProvider(LocalResetChrome provides ResetChrome("en") {}) {
            MmoTheme {
                NavHost(controller, startDestination = SignInRoute) {
                    composable<SignInRoute> {
                        Column {
                            Text("Sign in", modifier = Modifier.testTag(STUB_SIGN_IN))
                            Text(
                                "Forgotten your password?",
                                modifier =
                                    Modifier.testTag(STUB_FORGOTTEN).clickable {
                                        controller.navigate(ResetPasswordGraphRoute) { launchSingleTop = true }
                                    },
                            )
                        }
                    }
                    resetPasswordGraph(
                        navController = controller,
                        onSignedIn = { signedIn++ },
                        viewModelFor = { entry -> graphViewModel(controller, entry) },
                    )
                }
            }
        }
    }

    private fun start() {
        navController = newController()
        composeTestRule.setContent { TestHost(navController) }
        composeTestRule.onNodeWithTag(STUB_SIGN_IN).assertIsDisplayed()
    }

    private fun openReset() {
        composeTestRule.onNodeWithTag(STUB_FORGOTTEN).performClick()
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    private fun typeAndSubmit(
        tag: String,
        write: (ResetPasswordViewModel) -> Unit,
    ) {
        composeTestRule.runOnUiThread { write(viewModels.last()) }
        composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    private fun toCodeStep() {
        openReset()
        typeAndSubmit(ResetPasswordTestTags.SUBMIT_ACTION) { it.emailState.setTextAndPlaceCursorAtEnd("a@b.co") }
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CODE_SCREEN).assertIsDisplayed()
    }

    private fun toNewPasswordStep() {
        toCodeStep()
        typeAndSubmit(ResetPasswordTestTags.SUBMIT_ACTION) { it.codeState.setTextAndPlaceCursorAtEnd("123456") }
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.NEW_PASSWORD_SCREEN).assertIsDisplayed()
    }

    private fun toSuccessStep() {
        toNewPasswordStep()
        typeAndSubmit(ResetPasswordTestTags.SUBMIT_ACTION) {
            it.newPasswordState.setTextAndPlaceCursorAtEnd("Abcdef1!")
            it.confirmPasswordState.setTextAndPlaceCursorAtEnd("Abcdef1!")
        }
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUCCESS_SCREEN).assertIsDisplayed()
    }

    private inline fun <reified T : Any> stackHas() =
        navController.currentBackStack.value.any {
            it.destination.hasRoute<T>()
        }

    private fun assertBackAtSignIn() {
        composeTestRule.onNodeWithTag(STUB_SIGN_IN).assertIsDisplayed()
        assertFalse(stackHas<ResetPasswordGraphRoute>())
        assertFalse(stackHas<ResetPasswordEmailRoute>())
    }

    @Test
    fun `Forgotten password opens the email step`() {
        start()

        openReset()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_SCREEN).assertIsDisplayed()
        assertTrue(stackHas<ResetPasswordGraphRoute>())
    }

    @Test
    fun `Back on the email step returns to sign in and clears the graph`() {
        start()
        openReset()

        pressBack()

        assertBackAtSignIn()
    }

    @Test
    fun `a sent code opens the code step`() {
        start()

        toCodeStep()

        assertTrue(stackHas<ResetPasswordCodeRoute>())
        assertEquals(1, repository.startedEmails.size)
    }

    @Test
    fun `Back on the code step returns to a blank email step without resending`() {
        start()
        toCodeStep()

        pressBack()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_SCREEN).assertIsDisplayed()
        assertFalse(stackHas<ResetPasswordCodeRoute>())
        assertEquals(
            "",
            viewModels
                .last()
                .emailState.text
                .toString(),
        )
        assertEquals(
            ResetStep.EmailEntry,
            viewModels
                .last()
                .state.value.step,
        )
        assertEquals(1, repository.startedEmails.size)
    }

    @Test
    fun `Send a new code on the code step also returns to a blank email step`() {
        start()
        toCodeStep()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SEND_NEW_CODE_ACTION).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_SCREEN).assertIsDisplayed()
        assertFalse(stackHas<ResetPasswordCodeRoute>())
    }

    @Test
    fun `an accepted code removes the code step from the back stack`() {
        start()

        toNewPasswordStep()

        assertFalse(stackHas<ResetPasswordCodeRoute>())
        assertTrue(stackHas<ResetPasswordNewPasswordRoute>())
    }

    @Test
    fun `Back on the new password step asks first and Continue resetting stays`() {
        start()
        toNewPasswordStep()

        pressBack()
        composeTestRule.onNodeWithText("Cancel resetting your password?").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CANCEL_DIALOG_DISMISS).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.NEW_PASSWORD_SCREEN).assertIsDisplayed()
        assertTrue(stackHas<ResetPasswordNewPasswordRoute>())
    }

    @Test
    fun `confirming the cancel dialog returns to sign in and wipes the reset`() {
        start()
        toNewPasswordStep()
        val resetViewModel = viewModels.last()
        composeTestRule.runOnUiThread { resetViewModel.newPasswordState.setTextAndPlaceCursorAtEnd("Abcdef1!") }

        pressBack()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CANCEL_DIALOG_CONFIRM).performClick()
        composeTestRule.waitForIdle()

        assertBackAtSignIn()
        assertEquals("", resetViewModel.newPasswordState.text.toString())
    }

    @Test
    fun `success offers Sign in, and Back and the button both return to sign in`() {
        start()
        toSuccessStep()
        assertFalse(stackHas<ResetPasswordNewPasswordRoute>())
        assertTrue(stackHas<ResetPasswordSuccessRoute>())

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SIGN_IN_ACTION).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertBackAtSignIn()

        openReset()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_SCREEN).assertIsDisplayed()
        assertEquals(
            "a fresh reset starts blank",
            ResetStep.EmailEntry,
            viewModels
                .last()
                .state.value.step,
        )
    }

    @Test
    fun `Back on the success step returns to sign in`() {
        start()
        toSuccessStep()

        pressBack()

        assertBackAtSignIn()
    }

    @Test
    fun `leaving the graph clears its view model and re-entering creates a fresh one`() {
        start()
        toCodeStep()
        val first = viewModels.last()

        pressBack()
        pressBack()
        assertBackAtSignIn()
        openReset()

        assertEquals(2, viewModels.size)
        assertFalse(first === viewModels.last())
        assertEquals(
            ResetStep.EmailEntry,
            viewModels
                .last()
                .state.value.step,
        )
    }

    @Test
    fun `a browser fallback that signs in hands over to the session once`() {
        repository.startResult = StartResetResult.BrowserRequired
        repository.browserResult = BrowserContinuationResult.SignedIn(TEST_SESSION)
        start()
        openReset()

        typeAndSubmit(ResetPasswordTestTags.SUBMIT_ACTION) { it.emailState.setTextAndPlaceCursorAtEnd("a@b.co") }

        assertEquals(1, signedIn)
        assertEquals(1, repository.browserCallCount)
    }

    @Test
    fun `restored navigation with a fresh view model returns to a blank email step`() {
        val restoredState = Bundle()
        var generation by mutableIntStateOf(0)
        val owners = mutableMapOf<Int, ViewModelStoreOwner>()
        val controllers = mutableMapOf<Int, NavHostController>()
        controllers[0] = newController()
        composeTestRule.setContent {
            val owner =
                remember(generation) {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }.also { owners[generation] = it }
                }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                key(generation) { TestHost(controllers.getValue(generation)) }
            }
        }
        navController = controllers.getValue(0)
        openReset()
        typeAndSubmit(ResetPasswordTestTags.SUBMIT_ACTION) { it.emailState.setTextAndPlaceCursorAtEnd("a@b.co") }
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CODE_SCREEN).assertIsDisplayed()
        composeTestRule.runOnUiThread { restoredState.putAll(navController.saveState() ?: Bundle()) }

        // Process death: the saved back stack comes back, but the view model (and so the reset) does not.
        controllers[1] = newController().also { it.restoreState(restoredState) }
        composeTestRule.runOnUiThread { generation = 1 }
        composeTestRule.waitForIdle()
        navController = controllers.getValue(1)

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_SCREEN).assertIsDisplayed()
        assertFalse(stackHas<ResetPasswordCodeRoute>())
        assertEquals(
            ResetStep.EmailEntry,
            viewModels
                .last()
                .state.value.step,
        )
        assertEquals(
            "",
            viewModels
                .last()
                .emailState.text
                .toString(),
        )
        assertEquals(2, viewModels.size)
    }
}
