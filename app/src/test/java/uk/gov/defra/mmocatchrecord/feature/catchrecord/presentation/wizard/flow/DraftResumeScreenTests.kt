package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            WizardTestTheme {
                DraftResumeScreen(viewModel = viewModel, onNavigate = onNavigate, onBack = onBack, draftId = draftId)
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
    fun `confirming delete dispatches DeleteDraft and navigates to vessel selection`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(StandardTestDispatcher(testScheduler), repository)
            var navigatedStep: WizardStep? = null

            launchScreen(viewModel, onNavigate = { navigatedStep = it })

            composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()
            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DIALOG).assertIsDisplayed()

            composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DELETE).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.VesselSelection, navigatedStep)
            assertTrue(repository.deletedDraftIds.contains("draft-1"))
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
