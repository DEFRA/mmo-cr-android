package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing.NotLandedStraightAwayScreensTest

/**
 * Phase 8, screen 1 (conditional): the late-submission warning. Only the pure, viewModel-free
 * [LateSubmissionWarningScreenContent] is exercised here, per this codebase's `Screen`/`ScreenContent`
 * split (see e.g. [NotLandedStraightAwayScreensTest]) — the ViewModel-driven `LateSubmissionWarningScreen`
 * wrapper (title day-count computation, `resolveSubmissionStep` routing) is covered by
 * `CatchRecordFlowViewModelTests`/`WizardStepTests` at the unit level instead.
 */
class LateSubmissionWarningScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bodyAndCheckTripEndDateLinkAreDisplayed() {
        composeTestRule.setContent {
            MmoTheme {
                LateSubmissionWarningScreenContent(onCheckTripEndDate = {}, onSubmit = {})
            }
        }

        composeTestRule
            .onNodeWithText(
                "Catch records must be submitted within 24 hours of a trip ending.",
            ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.SAVE_ACTION).assertIsDisplayed()
    }

    @Test
    fun tappingCheckTripEndDateLinkInvokesCallback() {
        var checkedTripEndDate = false
        composeTestRule.setContent {
            MmoTheme {
                LateSubmissionWarningScreenContent(
                    onCheckTripEndDate = { checkedTripEndDate = true },
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK).performClick()

        assertTrue(checkedTripEndDate)
    }

    @Test
    fun tappingSaveAndContinueInvokesCallback() {
        var submitted = false
        composeTestRule.setContent {
            MmoTheme {
                LateSubmissionWarningScreenContent(onCheckTripEndDate = {}, onSubmit = { submitted = true })
            }
        }

        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.SAVE_ACTION).performClick()

        assertTrue(submitted)
    }
}
