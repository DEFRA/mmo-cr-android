package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

class WizardSelectionScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun wizardScaffoldExposesHeadingSemantics() {
        composeTestRule.setContent {
            MmoTheme {
                CatchRecordWizardScaffold(
                    screenTestTag = "scaffold",
                    title = "Which vessel did you use?",
                    onBack = {},
                ) {
                    Text("Body")
                }
            }
        }

        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
            .assertIsDisplayed()
    }

    @Test
    fun draftResumeDeleteRequiresConfirmationBeforeCallback() {
        var deleted = false
        composeTestRule.setContent {
            MmoTheme {
                DraftResumeScreenContent(
                    onResume = {},
                    onDeleteConfirmed = { deleted = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
        composeTestRule
            .onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DIALOG).assertIsDisplayed()
        assertTrue(!deleted)
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DELETE).performClick()
        assertTrue(deleted)
    }

    @Test
    fun vesselSelectionHappyPathSubmitsSelectedVessel() {
        var submittedVesselId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                VesselSelectionScreenContent(
                    vessels =
                        listOf(
                            Vessel("vessel-achilles", "ACHILLES"),
                            Vessel("vessel-hercules", "HERCULES"),
                        ),
                    onSubmit = { submittedVesselId = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${VesselSelectionScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag(VesselSelectionScreenTestTags.SAVE_ACTION)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals("vessel-achilles", submittedVesselId)
    }
}
