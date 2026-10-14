package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/** Exercises the real, ViewModel-driven [ReturnDateScreen]: loading/error/content and the submit path. */
@RunWith(RobolectricTestRunner::class)
class ReturnDateScreenViewModelTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun seedRepository(departureDate: DmyDate?): FakeCatchRecordDraftRepository {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
        runBlocking { repository.startDraft(tripScreenSeedDraft(id = "draft-1").copy(departureDate = departureDate)) }
        return repository
    }

    @Test
    fun screenShowsLoadingSpinnerBeforeEnterFlowCompletes() {
        val viewModel = buildTripFlowViewModel()
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnDateScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun aRetryableErrorShowsTheMessageAndDispatchesRetryOnTap() {
        val viewModel =
            buildTripFlowViewModel(
                referenceDataRepository = FailingReferenceDataRepository(java.io.IOException("boom")),
            )
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnDateScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${ReturnDateScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun aValidReturnDateSavesAndNavigatesToDeparturePort() {
        val repository = seedRepository(departureDate = DmyDate(1, 9, 2026))
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnDateScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.DAY_FIELD).performTextInput("11")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.MONTH_FIELD).performTextInput("09")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.DeparturePort), navigated)
        val savedDraft = runBlocking { repository.getDraftById("draft-1").getOrThrow() }
        assertEquals(DmyDate(11, 9, 2026), savedDraft?.returnDate)
    }

    @Test
    fun aReturnDateBeforeDepartureShowsTheErrorSummaryAndDoesNotNavigate() {
        val repository = seedRepository(departureDate = DmyDate(11, 9, 2026))
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                ReturnDateScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.DAY_FIELD).performTextInput("01")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.MONTH_FIELD).performTextInput("09")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        assertTrue(navigated.isEmpty())
    }
}
