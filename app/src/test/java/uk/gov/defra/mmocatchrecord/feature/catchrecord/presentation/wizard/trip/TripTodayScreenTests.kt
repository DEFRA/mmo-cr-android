package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

@RunWith(RobolectricTestRunner::class)
class TripTodayScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun previewRendersWithoutError() {
        // TripTodayScreenContentPreview otherwise stays uncounted new code — see
        // GdsAutocompleteFieldTests's equivalent doc comment.
        composeTestRule.setContent { TripTodayScreenContentPreview() }

        composeTestRule.onNodeWithTag("${TripTodayScreenTestTags.OPTION_PREFIX}_0").assertIsDisplayed()
    }

    @Test
    fun selectingNoImmediatelySubmitsFalse() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            MmoTheme {
                TripTodayScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${TripTodayScreenTestTags.OPTION_PREFIX}_1").performClick()

        assertEquals(false, submitted)
    }

    @Test
    fun screenShowsLoadingSpinnerBeforeEnterFlowCompletes() {
        val viewModel = buildTripFlowViewModel()
        composeTestRule.setContent {
            WizardTestTheme {
                TripTodayScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(TripTodayScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun aRetryableErrorShowsTheMessageAndDispatchesRetryOnTap() {
        val viewModel =
            buildTripFlowViewModel(
                referenceDataRepository = FailingReferenceDataRepository(java.io.IOException("boom")),
            )
        composeTestRule.setContent {
            WizardTestTheme {
                TripTodayScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TripTodayScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${TripTodayScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun answeringYesNavigatesToDeparturePort() {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
        runBlocking { repository.startDraft("vessel-achilles") }
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                TripTodayScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${TripTodayScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.DeparturePort), navigated)
    }

    @Test
    fun answeringNoNavigatesToDepartureDate() {
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-2" })
        runBlocking { repository.startDraft("vessel-hercules") }
        val viewModel = buildTripFlowViewModel(draftRepository = repository)
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                TripTodayScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${TripTodayScreenTestTags.OPTION_PREFIX}_1").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.DepartureDate), navigated)
    }
}
