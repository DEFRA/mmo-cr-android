package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBarTestTags
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference

@RunWith(RobolectricTestRunner::class)
class VesselSelectionScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun previewRendersWithoutError() {
        // VesselSelectionScreenContentPreview otherwise stays uncounted new code — see
        // GdsAutocompleteFieldTests's equivalent doc comment.
        composeTestRule.setContent { VesselSelectionScreenContentPreview() }

        composeTestRule.onNodeWithTag("${VesselSelectionScreenTestTags.OPTION_PREFIX}_0").assertIsDisplayed()
    }

    @Test
    fun selectingAVesselImmediatelySubmitsItsId() {
        var submitted: String? = null
        composeTestRule.setContent {
            MmoTheme {
                VesselSelectionScreenContent(
                    vessels = listOf(Vessel("vessel-achilles", "ACHILLES"), Vessel("vessel-hercules", "HERCULES")),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${VesselSelectionScreenTestTags.OPTION_PREFIX}_1").performClick()

        assertEquals("vessel-hercules", submitted)
    }

    @Test
    fun screenRendersImmediatelyBeforeEnterFlowCompletes() {
        val viewModel = buildTripFlowViewModel()
        composeTestRule.setContent {
            WizardTestTheme {
                VesselSelectionScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(VesselSelectionScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun selectingAVesselDispatchesSelectionAndNavigatesToTripToday() {
        val viewModel = buildTripFlowViewModel()
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                VesselSelectionScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.vessels
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${VesselSelectionScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.TripToday), navigated)
    }

    @Test
    fun theTripReferenceIsNotShownEvenOnceADraftExists() {
        val viewModel = buildTripFlowViewModel()
        composeTestRule.setContent {
            WizardTestTheme {
                VesselSelectionScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.vessels
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("${VesselSelectionScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.catchRecordReference != null }

        composeTestRule.onNodeWithTag("${VesselSelectionScreenTestTags.SCREEN}_reference_number").assertDoesNotExist()
        composeTestRule.onNodeWithText(checkNotNull(viewModel.state.value.catchRecordReference)).assertDoesNotExist()
    }

    /** ADR 0018 made selection a write, so a failed create must surface here, not on the next screen. */
    @Test
    fun aFailedDraftCreationShowsTheErrorOnThisScreenAndDoesNotNavigate() {
        val viewModel =
            buildTripFlowViewModel(
                referenceDataRepository =
                    FakeReferenceDataRepository(vessels = listOf(Vessel("vessel-achilles", "ACHILLES", pln = " - "))),
            )
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                VesselSelectionScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.vessels
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${VesselSelectionScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(VesselSelectionScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertEquals(emptyList<WizardStep>(), navigated)
    }

    /** Back-navigation must not bounce the user straight forward again on the still-advanced state. */
    @Test
    fun reEnteringTheScreenAfterADraftWasCreatedDoesNotAutoNavigate() {
        val viewModel = buildTripFlowViewModel()
        viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.currentStep == WizardStep.TripToday &&
                viewModel.state.value.status is UiStatus.Content
        }
        val navigated = mutableListOf<WizardStep>()
        composeTestRule.setContent {
            WizardTestTheme {
                VesselSelectionScreen(viewModel = viewModel, onNavigate = { navigated += it }, onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(VesselSelectionScreenTestTags.SCREEN).assertIsDisplayed()
        assertEquals(emptyList<WizardStep>(), navigated)
    }

    @Test
    fun tappingTheLanguageToggleTranslatesTheTitleToWelshAndBack() {
        var language by mutableStateOf(AppLanguage.ENGLISH)
        composeTestRule.setContent {
            WizardTestTheme(
                language = language,
                onLanguageToggle = {
                    language = if (language == AppLanguage.ENGLISH) AppLanguage.WELSH else AppLanguage.ENGLISH
                },
            ) {
                VesselSelectionScreen(viewModel = buildTripFlowViewModel(), onNavigate = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Which vessel did you use?").assertIsDisplayed()
        composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.LANGUAGE_TOGGLE).performClick()
        composeTestRule.onNodeWithText("Pa long a ddefnyddiwyd gennych?").assertIsDisplayed()
        composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.LANGUAGE_TOGGLE).performClick()
        composeTestRule.onNodeWithText("Which vessel did you use?").assertIsDisplayed()
    }
}
