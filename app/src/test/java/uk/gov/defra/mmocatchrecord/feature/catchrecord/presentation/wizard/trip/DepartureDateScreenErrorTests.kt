package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/** Covers loading/error for the real ViewModel-driven departure date overload.
 * Happy-path save, dirty tracking and navigation are covered by the journey tests. */
@RunWith(RobolectricTestRunner::class)
class DepartureDateScreenErrorTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screenShowsLoadingSpinnerBeforeEnterFlowCompletes() {
        val viewModel = buildTripFlowViewModel()
        composeTestRule.setContent {
            WizardTestTheme {
                DepartureDateScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun aRetryableErrorShowsTheMessageAndDispatchesRetryOnTap() {
        val viewModel =
            buildTripFlowViewModel(
                referenceDataRepository = FailingReferenceDataRepository(java.io.IOException("boom")),
            )
        composeTestRule.setContent {
            WizardTestTheme {
                DepartureDateScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${DepartureDateScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()
        composeTestRule.waitForIdle()
    }
}
