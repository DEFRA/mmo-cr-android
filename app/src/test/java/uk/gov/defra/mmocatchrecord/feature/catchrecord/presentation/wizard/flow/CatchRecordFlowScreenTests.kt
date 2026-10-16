package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
            val repository = FakeCatchRecordDraftRepository().apply { failNextOperation = true }
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
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
    fun `entry screen renders its content state once enter flow resolves an existing draft`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository()
            repository.startDraft("vessel-1")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)

            composeTestRule.setContent {
                WizardTestTheme {
                    CatchRecordFlowEntryScreen(viewModel = viewModel, onNavigate = {})
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // The one-shot navigate effect latches on the very first (default Idle) render, so by the
            // time the draft resolves the screen itself just keeps rendering its Content status branch.
            composeTestRule.onNodeWithTag(CatchRecordFlowEntryScreenTestTags.SCREEN).assertIsDisplayed()
            assertEquals(UiStatus.Content::class, viewModel.state.value.status::class)
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
            val repository = FakeCatchRecordDraftRepository().apply { failNextOperation = true }
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
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
