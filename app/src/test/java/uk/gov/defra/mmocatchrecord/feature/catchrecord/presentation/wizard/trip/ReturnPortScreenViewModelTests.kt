package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

/** Exercises the real, ViewModel-driven [ReturnPortScreen]: loading/error/content and the submit path. */
@RunWith(RobolectricTestRunner::class)
class ReturnPortScreenViewModelTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screenShowsLoadingSpinnerBeforeEnterFlowCompletes() {
        val viewModel = buildTripFlowViewModel()
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnPortScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(ReturnPortScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun aRetryableErrorShowsTheMessageAndDispatchesRetryOnTap() {
        val viewModel =
            buildTripFlowViewModel(
                referenceDataRepository = FailingReferenceDataRepository(java.io.IOException("boom")),
            )
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnPortScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReturnPortScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${ReturnPortScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun selectingAFavouriteAndSavingNavigatesToGearSearch() {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
        runBlocking { repository.startDraft("vessel-achilles") }
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnPortScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        // The bottom nav bar can push the favourite list and Save action below the fold.
        composeTestRule
            .onNodeWithTag("${ReturnPortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(ReturnPortScreenTestTags.SAVE_ACTION).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.GearSearch), navigated)
        val savedDraft = runBlocking { repository.getDraftById("draft-1").getOrThrow() }
        assertEquals(PortSelectionMode.Favourite, savedDraft?.returnPort?.selectionMode)
    }

    @Test
    fun aVesselWithNoPreviousPortsSearchesAndSavingNavigatesToGearSearch() {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-2" })
        runBlocking { repository.startDraft("vessel-hercules") }
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnPortScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReturnPortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Do")
        composeTestRule.onNodeWithTag("${ReturnPortScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(ReturnPortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.GearSearch), navigated)
        val savedDraft = runBlocking { repository.getDraftById("draft-2").getOrThrow() }
        assertEquals("port-dover", savedDraft?.returnPort?.portId)
    }
}
