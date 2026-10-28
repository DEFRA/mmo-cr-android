package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import uk.gov.defra.mmocatchrecord.common.navigation.CatchRecordGraphRoute
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

/** Uses a dispatcher and fake draft repository to drive every status branch deterministically. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatchRecordFlowScreenTests {
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
        repository: FakeCatchRecordDraftRepository = FakeCatchRecordDraftRepository(),
        referenceDataRepository: FakeReferenceDataRepository = FakeReferenceDataRepository(),
    ) = CatchRecordFlowViewModel(
        repository,
        referenceDataRepository,
        FakeCatchRecordSubmissionRepository(),
        FakeNetworkConnectivityChecker(),
        FakeCatchRecordSyncScheduler(),
        { 0L },
        { "generated-id" },
        dispatcher,
    )

    @Test
    fun `entry screen navigates to vessel selection once enter flow resolves with no existing draft`() =
        runTest {
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler))
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    CatchRecordFlowEntryScreen(viewModel = viewModel, onNavigate = { navigatedStep = it })
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(CatchRecordFlowEntryScreenTestTags.SCREEN).assertIsDisplayed()
            assertEquals(WizardStep.VesselSelection, navigatedStep)
        }

    @Test
    fun `entry screen shows a retryable error when loading fails and recovers on retry`() =
        runTest {
            val referenceData = FakeReferenceDataRepository().apply { failNextOperation = true }
            val viewModel =
                buildViewModel(StandardTestDispatcher(testScheduler), referenceDataRepository = referenceData)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    CatchRecordFlowEntryScreen(viewModel = viewModel, onNavigate = { navigatedStep = it })
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(CatchRecordFlowEntryScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()

            composeTestRule
                .onNodeWithTag("${CatchRecordFlowEntryScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.VesselSelection, navigatedStep)
        }

    @Test
    fun `entry screen always starts a new record at vessel selection even with an existing draft`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository()
            repository.startDraft("vessel-1")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    CatchRecordFlowEntryScreen(viewModel = viewModel, onNavigate = { navigatedStep = it })
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(CatchRecordFlowEntryScreenTestTags.SCREEN).assertIsDisplayed()
            assertEquals(UiStatus.Idle, viewModel.state.value.status)
            assertEquals(WizardStep.VesselSelection, navigatedStep)
        }

    /** Guards the whole FR2 entry route end-to-end, not just the ViewModel: with an active draft present,
     *  Create lands on vessel selection and the entry destination is popped off the back stack. */
    @Test
    fun `create a new catch record pops the entry route straight to vessel selection despite an active draft`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            lateinit var navController: NavHostController

            composeTestRule.setContent {
                navController = rememberNavController()
                WizardTestTheme {
                    NavHost(navController = navController, startDestination = CatchRecordGraphRoute) {
                        navigation<CatchRecordGraphRoute>(startDestination = CatchRecordEntryRoute) {
                            composable<CatchRecordEntryRoute> {
                                CatchRecordFlowEntryScreen(
                                    viewModel = viewModel,
                                    onNavigate = { step ->
                                        navController.navigate(routeFor(step)) {
                                            popUpTo<CatchRecordEntryRoute> { inclusive = true }
                                        }
                                    },
                                )
                            }
                            composable<VesselSelectionRoute> {
                                Box(modifier = Modifier.size(50.dp).testTag("vessel_selection_destination"))
                            }
                        }
                    }
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("vessel_selection_destination").assertIsDisplayed()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<VesselSelectionRoute>() == true)
            val hasEntryOnStack = runCatching { navController.getBackStackEntry<CatchRecordEntryRoute>() }.isSuccess
            assertTrue(!hasEntryOnStack)
            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun `phase three complete screen shows its body text when idle`() {
        val viewModel = buildViewModel(StandardTestDispatcher())

        composeTestRule.setContent {
            WizardTestTheme {
                PhaseThreeCompleteScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(PhaseThreeCompleteScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun `phase three complete screen shows a loading state while enter flow is in flight`() {
        val viewModel = buildViewModel(StandardTestDispatcher())
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)

        composeTestRule.setContent {
            WizardTestTheme {
                PhaseThreeCompleteScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(PhaseThreeCompleteScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun `phase three complete screen shows a retryable error and recovers on retry`() =
        runTest {
            val referenceData = FakeReferenceDataRepository().apply { failNextOperation = true }
            val viewModel =
                buildViewModel(StandardTestDispatcher(testScheduler), referenceDataRepository = referenceData)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()

            composeTestRule.setContent {
                WizardTestTheme {
                    PhaseThreeCompleteScreen(viewModel = viewModel, onBack = {})
                }
            }

            composeTestRule.onNodeWithTag(CatchRecordFlowEntryScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
            composeTestRule.onNodeWithText("Try again").assertIsDisplayed()

            composeTestRule
                .onNodeWithTag("${CatchRecordFlowEntryScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(PhaseThreeCompleteScreenTestTags.SCREEN).assertIsDisplayed()
        }

    @Test
    fun `phase three complete screen shows its body text when status is content`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository()
            repository.startDraft("vessel-1")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-id"))
            testScheduler.advanceUntilIdle()

            composeTestRule.setContent {
                WizardTestTheme {
                    PhaseThreeCompleteScreen(viewModel = viewModel, onBack = {})
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(PhaseThreeCompleteScreenTestTags.SCREEN).assertIsDisplayed()
        }
}
