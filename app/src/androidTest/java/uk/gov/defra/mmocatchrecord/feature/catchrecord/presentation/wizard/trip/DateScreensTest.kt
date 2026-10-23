package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.LocalDate

class DateScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val departureTags = DepartureDateScreenTestTags.stepTags
    private val returnTags = ReturnDateScreenTestTags.stepTags
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun departureDateHappyPathSubmitsConfirmedDate() {
        var submittedDate: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    testTags = departureTags,
                    onSubmit = { submittedDate = it },
                    today = today,
                )
            }
        }

        spinTo(departureTags, DmyDate(11, 9, 2026))
        composeTestRule.onNodeWithTag(departureTags.okAction).performClick()
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()
        assertEquals(DmyDate(11, 9, 2026), submittedDate)
    }

    @Test
    fun departureDateUnconfirmedSubmitShowsErrorSummary() {
        var submittedDate: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    testTags = departureTags,
                    onSubmit = { submittedDate = it },
                    today = today,
                )
            }
        }

        spinTo(departureTags, DmyDate(11, 9, 2026))
        composeTestRule.onNodeWithTag(departureTags.saveAction).performClick()
        composeTestRule.onNodeWithTag(departureTags.summary).assertIsDisplayed()
        assertNull(submittedDate)
    }

    @Test
    fun returnDateBeforeDepartureShowsErrorSummary() {
        var submittedDate: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    departureDate = DmyDate(11, 9, 2026),
                    isReturnDate = true,
                    testTags = returnTags,
                    onSubmit = { submittedDate = it },
                    today = today,
                )
            }
        }

        spinTo(returnTags, DmyDate(10, 9, 2026))
        composeTestRule.onNodeWithTag(returnTags.okAction).performClick()
        composeTestRule.onNodeWithTag(returnTags.saveAction).performClick()
        composeTestRule.onNodeWithTag(returnTags.summary).assertIsDisplayed()
        assertNull(submittedDate)
    }

    /** Regression: re-entering the step (Back, draft resume, "Change") shows the saved date on the wheel. */
    @Test
    fun returnDateStepShowsPreviouslySavedValueOnTheWheel() {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = DmyDate(5, 7, 2026),
                    departureDate = DmyDate(3, 7, 2026),
                    isReturnDate = true,
                    testTags = returnTags,
                    onSubmit = {},
                    today = today,
                )
            }
        }

        assertWheelState(returnTags.dayWheel, "5")
        assertWheelState(returnTags.monthWheel, "July")
        assertWheelState(returnTags.yearWheel, "2026")
    }

    private fun spinTo(
        tags: WizardDateStepTestTags,
        date: DmyDate,
    ) {
        setWheel(tags.yearWheel, date.year)
        setWheel(tags.monthWheel, date.month)
        setWheel(tags.dayWheel, date.day)
    }

    private fun setWheel(
        tag: String,
        value: Int,
    ) {
        composeTestRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
        composeTestRule.waitForIdle()
    }

    private fun assertWheelState(
        tag: String,
        expected: String,
    ) {
        val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
        assertEquals(expected, node.config.getOrNull(SemanticsProperties.StateDescription))
    }
}
