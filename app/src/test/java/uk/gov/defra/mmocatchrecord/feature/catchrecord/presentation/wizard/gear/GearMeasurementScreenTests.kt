package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/** Robolectric tests for the Hilt-backed, stateful [GearMeasurementScreen] wrapper. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GearMeasurementScreenTests {
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
        repository: FakeCatchRecordDraftRepository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" }),
    ) = CatchRecordFlowViewModel(
        repository,
        FakeReferenceDataRepository(),
        FakeCatchRecordSubmissionRepository(),
        FakeNetworkConnectivityChecker(),
        FakeCatchRecordSyncScheduler(),
        { 0L },
        { "gear-use-1" },
        dispatcher,
    )

    /** Drives the view model to the "pending gear type" state, deterministically advancing the shared test
     * scheduler after each dispatch rather than polling in real time. */
    private fun TestScope.enterFlowWithPendingGearType(viewModel: CatchRecordFlowViewModel) {
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        testScheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
        viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
        testScheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
        viewModel.dispatch(CatchRecordFlowEvent.GearTypeSelected("gear-seine-nets"))
        testScheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `submitting a new gear measurement dispatches GearMeasurementsSubmitted and navigates to gear summary`() =
        runTest {
            val viewModel = buildViewModel(dispatcher = StandardTestDispatcher(testScheduler))
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearMeasurementScreen(viewModel = viewModel, onNavigate = { navigatedStep = it }, onBack = {})
                }
            }
            enterFlowWithPendingGearType(viewModel)

            composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("80")
            composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.GearSummary, navigatedStep)
            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertNotNull(draft.gearUses.firstOrNull { it.id == "gear-use-1" })
        }

    @Test
    fun `editing an existing gear use dispatches EditGearMeasurements and returns to check your answers`() =
        runTest {
            val viewModel = buildViewModel(dispatcher = StandardTestDispatcher(testScheduler))
            var navigatedStep: WizardStep? = null
            // A single setContent (a test rule only permits one) that switches to edit mode once the gear use
            // from the add-new-gear path exists, mirroring GearMeasurementScreenContentDirtyTrackingTests.
            composeTestRule.setContent {
                WizardTestTheme {
                    val uiState by viewModel.state.collectAsStateWithLifecycle()
                    val draft = (uiState.status as? UiStatus.Content<*>)?.value as? CatchRecordDraft
                    if (draft?.gearUses?.any { it.id == "gear-use-1" } == true) {
                        GearMeasurementScreen(
                            viewModel = viewModel,
                            onNavigate = { navigatedStep = it },
                            onBack = {},
                            editGearUseId = "gear-use-1",
                        )
                    } else {
                        GearMeasurementScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                    }
                }
            }
            enterFlowWithPendingGearType(viewModel)
            composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("80")
            composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // The field is pre-filled from the existing gear use's measurements — saving unchanged still
            // exercises the edit-mode dispatch/navigation branch without needing a further edit.
            composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.CheckYourAnswers, navigatedStep)
        }
}
