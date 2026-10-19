package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

@RunWith(RobolectricTestRunner::class)
class VesselSelectionScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

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
}
