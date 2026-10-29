package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.LocalDate

/** Covers the save rules of the date wheel step: the wheel's date is the one saved.
 * Assertions target first matches because summary and inline errors render the same text. */
@RunWith(RobolectricTestRunner::class)
class WizardDateStepContentValidationTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val departureTags = DepartureDateScreenTestTags.stepTags
    private val returnTags = ReturnDateScreenTestTags.stepTags
    private val today = LocalDate.of(2026, 10, 16)
    private val returnBeforeDepartureMessage =
        "Date you returned from your trip must be the same as or after the date you left"

    private fun setDepartureContent(
        initialDate: DmyDate? = null,
        onSubmit: (DmyDate) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = initialDate,
                    testTags = departureTags,
                    onSubmit = onSubmit,
                    today = today,
                )
            }
        }
    }

    private fun setReturnContent(onSubmit: (DmyDate) -> Unit = {}) {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    departureDate = DmyDate(11, 9, 2026),
                    isReturnDate = true,
                    testTags = returnTags,
                    onSubmit = onSubmit,
                    today = today,
                )
            }
        }
    }

    @Test
    fun savingWithoutMovingTheWheelSubmitsToday() {
        var submitted: DmyDate? = null
        setDepartureContent(onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(16, 10, 2026), submitted)
    }

    @Test
    fun anInvalidlySavedDateFallsBackToTheWheelStartingOnToday() {
        var submitted: DmyDate? = null
        // 31 February can never be a valid LocalDate, exercising toLocalDateOrNull's failure path.
        setDepartureContent(initialDate = DmyDate(31, 2, 2026), onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(16, 10, 2026), submitted)
    }

    @Test
    fun spinningThenSavingSubmitsTheWheelDate() {
        var submitted: DmyDate? = null
        setDepartureContent(onSubmit = { submitted = it })

        composeTestRule.spinTo(departureTags, DmyDate(31, 1, 2026))
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(31, 1, 2026), submitted)
    }

    @Test
    fun aSavedDateIsSubmittedUnchanged() {
        var submitted: DmyDate? = null
        setDepartureContent(initialDate = DmyDate(3, 7, 2026), onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(3, 7, 2026), submitted)
    }

    @Test
    fun aReturnDateBeforeDepartureShowsTheErrorAndTheSummaryLinkFocusesTheWheel() {
        var submitted: DmyDate? = null
        setReturnContent(onSubmit = { submitted = it })

        composeTestRule.spinTo(returnTags, DmyDate(1, 9, 2026))
        composeTestRule.onNodeWithTag(returnTags.saveAction).performClick()

        composeTestRule.onNodeWithTag(returnTags.summary).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(returnBeforeDepartureMessage).onFirst().performClick()
        composeTestRule.onNodeWithTag(returnTags.monthWheel).assertIsFocused()
        assertNull(submitted)
    }

    @Test
    fun theWheelDateSurvivesRecreation() {
        var submitted: DmyDate? = null
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    testTags = departureTags,
                    onSubmit = { submitted = it },
                    today = today,
                )
            }
        }

        composeTestRule.spinTo(departureTags, DmyDate(31, 1, 2026))
        composeTestRule.waitForIdle()
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.saveAndContinue(departureTags)

        assertEquals(DmyDate(31, 1, 2026), submitted)
    }

    @Test
    fun movingTheWheelAfterAFailedSubmitClearsTheErrorSummary() {
        setReturnContent()

        composeTestRule.spinTo(returnTags, DmyDate(1, 9, 2026))
        composeTestRule.onNodeWithTag(returnTags.saveAction).performClick()
        composeTestRule.onNodeWithTag(returnTags.summary).assertIsDisplayed()

        composeTestRule.setWheel(returnTags.dayWheel, 20)

        composeTestRule.onNodeWithTag(returnTags.summary).assertDoesNotExist()
    }
}
