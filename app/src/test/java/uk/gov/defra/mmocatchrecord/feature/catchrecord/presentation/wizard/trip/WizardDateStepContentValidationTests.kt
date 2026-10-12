package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

/**
 * Validation-error branch coverage for [WizardDateStepContent]/[DateStepSupport] not already exercised by
 * [WizardDateStepContentTests] (which covers only the dirty-tracking paths): every [DateInputError] message
 * mapping, the error summary's per-field focus `onClick` branches, and clearing errors by editing a field.
 * Each message is rendered twice (error summary item + inline field error), so matches are scoped with
 * [onAllNodesWithText]/[onFirst] - the summary item renders first in the composition.
 */
@RunWith(RobolectricTestRunner::class)
class WizardDateStepContentValidationTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val departureTags =
        WizardDateStepTestTags(
            summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
            dayField = DepartureDateScreenTestTags.DAY_FIELD,
            monthField = DepartureDateScreenTestTags.MONTH_FIELD,
            yearField = DepartureDateScreenTestTags.YEAR_FIELD,
            saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
        )

    @Test
    fun submittingAllBlankFieldsShowsEveryRequiredErrorAndEachSummaryLinkFocusesItsField() {
        var submitted: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(initialDate = null, testTags = departureTags, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Enter a day").onFirst().performClick()
        composeTestRule.onAllNodesWithText("Enter a month").onFirst().performClick()
        composeTestRule.onAllNodesWithText("Enter a year").onFirst().performClick()
        assertNull(submitted)
    }

    @Test
    fun submittingAnImpossibleCalendarDateShowsTheInvalidDateError() {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(initialDate = null, testTags = departureTags, onSubmit = {})
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("31")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.MONTH_FIELD).performTextInput("02")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onAllNodesWithText("Enter a real date").onFirst().assertIsDisplayed()
    }

    @Test
    fun aReturnDateBeforeDepartureShowsTheReturnBeforeDepartureError() {
        val returnTags =
            WizardDateStepTestTags(
                summary = ReturnDateScreenTestTags.ERROR_SUMMARY,
                dayField = ReturnDateScreenTestTags.DAY_FIELD,
                monthField = ReturnDateScreenTestTags.MONTH_FIELD,
                yearField = ReturnDateScreenTestTags.YEAR_FIELD,
                saveAction = ReturnDateScreenTestTags.SAVE_ACTION,
            )
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    departureDate = DmyDate(11, 9, 2026),
                    isReturnDate = true,
                    testTags = returnTags,
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.DAY_FIELD).performTextInput("01")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.MONTH_FIELD).performTextInput("09")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText(
                "Return date must be on or after the departure date",
            ).onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun editingAFieldAfterAFailedSubmitClearsTheErrorSummary() {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(initialDate = null, testTags = departureTags, onSubmit = {})
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("1")

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.ERROR_SUMMARY).assertDoesNotExist()
    }
}
