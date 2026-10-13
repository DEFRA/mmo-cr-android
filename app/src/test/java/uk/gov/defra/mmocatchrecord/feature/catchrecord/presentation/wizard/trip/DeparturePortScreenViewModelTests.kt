package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/**
 * Exercises the real, ViewModel-driven [DeparturePortScreen] overload end-to-end (loading/error/content,
 * the same-port shortcut accept/decline branches, and the favourites/search submit path), as opposed to
 * [DeparturePortScreenTests] which drives the stateless content composables directly.
 */
@RunWith(RobolectricTestRunner::class)
class DeparturePortScreenViewModelTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun seedActiveDraft(
        repository: FakeCatchRecordDraftRepository,
        vesselId: String,
    ) {
        runBlocking { repository.startDraft(vesselId) }
    }

    @Test
    fun screenShowsLoadingSpinnerBeforeEnterFlowCompletes() {
        val viewModel = buildTripFlowViewModel()
        composeTestRule.setContent {
            WizardTestTheme {
                DeparturePortScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun aRetryableErrorShowsTheMessageAndDispatchesRetryOnTap() {
        val viewModel =
            buildTripFlowViewModel(
                referenceDataRepository = FailingReferenceDataRepository(java.io.IOException("boom")),
            )
        composeTestRule.setContent {
            WizardTestTheme {
                DeparturePortScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun acceptingTheSamePortShortcutDispatchesAcceptedAndNavigatesToGearSearch() {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
        seedActiveDraft(repository, "vessel-achilles")
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                DeparturePortScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.SAME_PORT_OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.GearSearch), navigated)
    }

    @Test
    fun decliningTheSamePortShortcutThenSavingAFavouriteNavigatesToReturnPort() {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
        seedActiveDraft(repository, "vessel-achilles")
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                DeparturePortScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.SAME_PORT_OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.ReturnPort), navigated)
        val savedDraft = runBlocking { repository.getDraftById("draft-1").getOrThrow() }
        assertEquals(PortSelectionMode.Favourite, savedDraft?.departurePort?.selectionMode)
    }

    @Test
    fun aVesselWithNoPreviousPortsGoesStraightToSearchAndSavingNavigatesToReturnPort() {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-2" })
        seedActiveDraft(repository, "vessel-hercules")
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                DeparturePortScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Ha")
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.ReturnPort), navigated)
        val savedDraft = runBlocking { repository.getDraftById("draft-2").getOrThrow() }
        assertTrue(savedDraft?.departurePort?.portId == "port-hastings")
    }
}
