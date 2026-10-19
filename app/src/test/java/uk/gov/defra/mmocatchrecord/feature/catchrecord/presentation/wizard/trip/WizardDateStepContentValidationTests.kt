package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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

/** Covers the confirm (Ok) / cancel / save rules of the date wheel step.
 * Assertions target first matches because summary and inline errors render the same text. */
@RunWith(RobolectricTestRunner::class)
class WizardDateStepContentValidationTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val departureTags = DepartureDateScreenTestTags.stepTags
    private val returnTags = ReturnDateScreenTestTags.stepTags
    private val today = LocalDate.of(2026, 10, 16)

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

    @Test
    fun savingWithoutPressingOkShowsTheNotConfirmedErrorAndTheSummaryLinkWorks() {
        var submitted: DmyDate? = null
        setDepartureContent(onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        composeTestRule.onNodeWithTag(departureTags.summary).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Select a date and press OK").onFirst().performClick()
        assertNull(submitted)
    }

    @Test
    fun theWheelStartsOnTodayWhenNoDateIsSaved() {
        var submitted: DmyDate? = null
        setDepartureContent(onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(departureTags.okAction).performClick()
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(16, 10, 2026), submitted)
    }

    @Test
    fun anInvalidlySavedDateFallsBackToTheWheelStartingOnToday() {
        var submitted: DmyDate? = null
        // 31 February can never be a valid LocalDate, exercising toLocalDateOrNull's failure path.
        setDepartureContent(initialDate = DmyDate(31, 2, 2026), onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(departureTags.okAction).performClick()
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(16, 10, 2026), submitted)
    }

    @Test
    fun cancellingAfterConfirmingViaOkRevertsToTheConfirmedDateNotTheStartDate() {
        var submitted: DmyDate? = null
        setDepartureContent(onSubmit = { submitted = it })

        composeTestRule.spinToAndConfirm(departureTags, DmyDate(20, 3, 2026))
        composeTestRule.setWheel(departureTags.dayWheel, 25)
        composeTestRule.onNodeWithTag(departureTags.cancelAction).performClick()
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(20, 3, 2026), submitted)
    }

    @Test
    fun okThenSaveSubmitsTheConfirmedDateAndShowsIt() {
        var submitted: DmyDate? = null
        setDepartureContent(onSubmit = { submitted = it })

        composeTestRule.spinToAndConfirm(departureTags, DmyDate(31, 1, 2026))
        composeTestRule
            .onNodeWithTag(departureTags.confirmedDate)
            .assertTextContains("31 January 2026", substring = true)
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(31, 1, 2026), submitted)
    }

    @Test
    fun movingTheWheelAfterOkBlocksSavingUntilConfirmedAgain() {
        var submitted: DmyDate? = null
        setDepartureContent(onSubmit = { submitted = it })

        composeTestRule.spinToAndConfirm(departureTags, DmyDate(31, 1, 2026))
        composeTestRule.setWheel(departureTags.dayWheel, 30)
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        composeTestRule.onNodeWithTag(departureTags.summary).assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun cancelRevertsTheWheelSoTheConfirmedDateCanBeSaved() {
        var submitted: DmyDate? = null
        setDepartureContent(initialDate = DmyDate(3, 7, 2026), onSubmit = { submitted = it })

        composeTestRule.setWheel(departureTags.dayWheel, 20)
        composeTestRule.onNodeWithTag(departureTags.cancelAction).performClick()
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(3, 7, 2026), submitted)
    }

    @Test
    fun aSavedDateIsTreatedAsConfirmed() {
        var submitted: DmyDate? = null
        setDepartureContent(initialDate = DmyDate(3, 7, 2026), onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()

        assertEquals(DmyDate(3, 7, 2026), submitted)
    }

    @Test
    fun aReturnDateBeforeDepartureShowsTheReturnBeforeDepartureError() {
        var submitted: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    departureDate = DmyDate(11, 9, 2026),
                    isReturnDate = true,
                    testTags = returnTags,
                    onSubmit = { submitted = it },
                    today = today,
                )
            }
        }

        composeTestRule.spinToAndConfirm(returnTags, DmyDate(1, 9, 2026))
        composeTestRule.onNodeWithTag(returnTags.saveAction).performClick()

        composeTestRule
            .onAllNodesWithText("Return date must be on or after the departure date")
            .onFirst()
            .assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun theConfirmedAndUnconfirmedWheelDatesSurviveRecreation() {
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

        composeTestRule.spinToAndConfirm(departureTags, DmyDate(31, 1, 2026))
        composeTestRule.setWheel(departureTags.dayWheel, 30)
        composeTestRule.waitForIdle()
        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule
            .onNodeWithTag(departureTags.confirmedDate)
            .assertTextContains("31 January 2026", substring = true)
        composeTestRule.saveAndContinue(departureTags)
        assertNull("the unconfirmed wheel move must survive recreation", submitted)
        composeTestRule.onNodeWithTag(departureTags.cancelAction).performClick()
        composeTestRule.saveAndContinue(departureTags)
        assertEquals(DmyDate(31, 1, 2026), submitted)
    }

    @Test
    fun movingTheWheelAfterAFailedSubmitClearsTheErrorSummary() {
        setDepartureContent()

        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()
        composeTestRule.onNodeWithTag(departureTags.summary).assertIsDisplayed()

        composeTestRule.setWheel(departureTags.dayWheel, 1)

        composeTestRule.onNodeWithTag(departureTags.summary).assertDoesNotExist()
    }
}
