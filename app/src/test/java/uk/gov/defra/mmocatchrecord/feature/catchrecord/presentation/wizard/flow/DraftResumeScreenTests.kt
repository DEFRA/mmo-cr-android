package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBarTestTags
import uk.gov.defra.mmocatchrecord.common.navigation.CatchRecordGraphRoute
import uk.gov.defra.mmocatchrecord.common.navigation.HomeRoute
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository

/** Robolectric tests for the Hilt-backed [DraftResumeScreen] wrapper (FR2/B5: resume a draft from Home). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DraftResumeScreenTests {
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

    private fun buildViewModel(
        dispatcher: CoroutineDispatcher,
        repository: CatchRecordDraftRepository = FakeCatchRecordDraftRepository(),
    ) = CatchRecordFlowViewModel(
        repository,
        FakeReferenceDataRepository(),
        FakeCatchRecordSubmissionRepository(),
        FakeNetworkConnectivityChecker(),
        FakeCatchRecordSyncScheduler(),
        { 0L },
        { "generated-id" },
        dispatcher,
    )

    /** Composes the screen then deterministically advances the shared test scheduler, replacing a real-time
     * `waitUntil` poll, until the draft load settles into [UiStatus.Content]. */
    private fun TestScope.launchScreen(
        viewModel: CatchRecordFlowViewModel,
        draftId: String? = "draft-1",
        onNavigate: (WizardStep) -> Unit = {},
        onDraftDeleted: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            WizardTestTheme {
                DraftResumeScreen(
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    onDraftDeleted = onDraftDeleted,
                    onBack = onBack,
                    draftId = draftId,
                )
            }
        }
        testScheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `supplying a draftId loads that specific draft via EnterFlowForDraft`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)

            launchScreen(viewModel, draftId = "draft-1")

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SCREEN).assertIsDisplayed()
            assertTrue(viewModel.state.value.isDraftPersisted)
        }

    @Test
    fun `no draftId leaves the view model idle and renders the loading state`() =
        runTest {
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler))

            composeTestRule.setContent {
                WizardTestTheme {
                    DraftResumeScreen(viewModel = viewModel, onNavigate = {}, onBack = {}, draftId = null)
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SCREEN).assertIsDisplayed()
            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun `selecting complete and continuing dispatches ResumeDraft and navigates to the resolved step`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            var navigatedStep: WizardStep? = null

            launchScreen(viewModel, onNavigate = { navigatedStep = it })

            composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_0").performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()

            assertEquals(WizardStep.TripToday, navigatedStep)
            assertEquals(WizardStep.TripToday, viewModel.state.value.currentStep)
        }

    @Test
    fun `tapping save with no option selected shows a validation error`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)

            launchScreen(viewModel)

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        }

    @Test
    fun `confirming delete dispatches DeleteDraft and triggers onDraftDeleted`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            var draftDeletedInvoked = false

            launchScreen(viewModel, onDraftDeleted = { draftDeletedInvoked = true })

            composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DIALOG).assertIsDisplayed()

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DELETE).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertTrue(draftDeletedInvoked)
            assertTrue(repository.deletedDraftIds.contains("draft-1"))
        }

    @Test
    fun `delete failure surfaces error and does not invoke onDraftDeleted`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            var draftDeletedInvoked = false

            launchScreen(viewModel, onDraftDeleted = { draftDeletedInvoked = true })

            repository.failNextOperation = true
            composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DELETE).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertTrue(!draftDeletedInvoked)
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        }

    @Test
    fun `confirming delete navigates to HomeRoute and pops catch record graph`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            lateinit var navController: NavHostController

            composeTestRule.setContent {
                navController = rememberNavController()
                WizardTestTheme {
                    NavHost(navController = navController, startDestination = HomeRoute()) {
                        composable<HomeRoute> {
                            Box(modifier = Modifier.size(50.dp).testTag("home_screen_destination"))
                        }
                        navigation<CatchRecordGraphRoute>(startDestination = DraftResumeRoute("draft-1")) {
                            composable<DraftResumeRoute> {
                                DraftResumeScreen(
                                    viewModel = viewModel,
                                    onNavigate = { navController.navigate(routeFor(it)) },
                                    onDraftDeleted = {
                                        navController.navigate(HomeRoute()) {
                                            popUpTo<CatchRecordGraphRoute> { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onBack = { navController.popBackStack() },
                                    draftId = "draft-1",
                                )
                            }
                        }
                    }
                }
            }

            navController.navigate(DraftResumeRoute("draft-1"))
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                testScheduler.advanceUntilIdle()
                viewModel.state.value.status is UiStatus.Content<*>
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DELETE).performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                testScheduler.advanceUntilIdle()
                viewModel.state.value.isDraftDeleted
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("home_screen_destination").assertIsDisplayed()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<HomeRoute>() == true)
            val hasDraftResumeOnStack = runCatching { navController.getBackStackEntry<DraftResumeRoute>() }.isSuccess
            assertTrue(!hasDraftResumeOnStack)
            val hasGraphOnStack = runCatching { navController.getBackStackEntry<CatchRecordGraphRoute>() }.isSuccess
            assertTrue(!hasGraphOnStack)
        }

    @Test
    fun `cancelling the delete confirmation dialog leaves the draft untouched`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)

            launchScreen(viewModel)

            composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CANCEL_DELETE).performClick()

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DIALOG).assertDoesNotExist()
            assertTrue(repository.deletedDraftIds.isEmpty())
        }

    @Test
    fun `a retryable load error is shown and Retry redispatches EnterFlowForDraft to recover`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.failNextOperation = true
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)

            composeTestRule.setContent {
                WizardTestTheme {
                    DraftResumeScreen(viewModel = viewModel, onNavigate = {}, onBack = {}, draftId = "draft-1")
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("${DraftResumeScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SCREEN).assertIsDisplayed()
        }

    @Test
    fun `the back action invokes the supplied onBack callback`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            var backInvoked = false

            launchScreen(viewModel, onBack = { backInvoked = true })

            composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.BACK_BUTTON).performClick()
            assertTrue(backInvoked)
        }
}
