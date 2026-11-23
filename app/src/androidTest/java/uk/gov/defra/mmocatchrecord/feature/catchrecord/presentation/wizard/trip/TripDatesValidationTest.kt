package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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

// Trip start date and duration story (AC1–AC9, EC1); "today" is fixed for determinism.
// Future dates are blocked by the wheel itself, so AC5/AC9 assert the clamp to today.
class TripDatesValidationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val departureTags = DepartureDateScreenTestTags.stepTags
    private val returnTags = ReturnDateScreenTestTags.stepTags
    private val today = LocalDate.of(2026, 10, 16)
    private var submittedDate: DmyDate? = null

    @Test
    fun departureDateDefaultsToToday() {
        showDepartureStep()

        assertWheelShows(departureTags, day = "16", month = "October", year = "2026")
    }

    @Test
    fun returnDateDefaultsToToday() {
        showReturnStep(departureDate = daysAgo(6))

        assertWheelShows(returnTags, day = "16", month = "October", year = "2026")
    }

    // --- AC1: accept a valid trip ---

    @Test
    fun ac1_validStartDateIsAccepted() {
        showDepartureStep()

        spinTo(departureTags, daysAgo(2))
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, daysAgo(2))
    }

    @Test
    fun ac1_startDateOfTodayIsAccepted() {
        showDepartureStep()

        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, daysAgo(0))
    }

    @Test
    fun ac1_validEndDateIsAccepted() {
        showReturnStep(departureDate = daysAgo(2))

        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, daysAgo(0))
    }

    // --- AC2 / AC6: 365-day backdating rule ---

    @Test
    fun ac2_startDateMoreThan365DaysAgoIsRejected() {
        showDepartureStep()

        spinTo(departureTags, daysAgo(366))
        saveAndContinue(departureTags)

        assertBlockedWithError(departureTags, START_MORE_THAN_365_DAYS)
    }

    @Test
    fun ac6_startDateExactly365DaysAgoIsAccepted() {
        showDepartureStep()

        spinTo(departureTags, daysAgo(365))
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, daysAgo(365))
    }

    @Test
    fun ac6_endDateOnStartDate365DaysAgoIsAccepted() {
        showReturnStep(departureDate = daysAgo(365))

        spinTo(returnTags, daysAgo(365))
        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, daysAgo(365))
    }

    // --- AC3 / AC7: 30-day maximum trip length (separate from the 365-day rule) ---

    @Test
    fun ac3_tripLongerThan30DaysIsRejected() {
        showReturnStep(departureDate = daysAgo(31))

        saveAndContinue(returnTags)

        assertBlockedWithError(returnTags, TRIP_EXCEEDS_30_DAYS, substring = true)
    }

    @Test
    fun ac7_tripOfExactly30DaysIsAccepted() {
        showReturnStep(departureDate = daysAgo(30))

        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, daysAgo(0))
    }

    // --- AC4: end date before start date ---

    @Test
    fun ac4_endDateBeforeStartDateIsRejected() {
        showReturnStep(departureDate = daysAgo(2))

        spinTo(returnTags, daysAgo(3))
        saveAndContinue(returnTags)

        assertBlockedWithError(returnTags, END_BEFORE_START)
    }

    // --- AC5 / AC9: future dates are restricted by the wheel ---

    @Test
    fun ac5_startWheelCannotSelectFutureDay() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(17, 10, 2026))
        assertWheelShows(departureTags, day = "16", month = "October", year = "2026")
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, DmyDate(16, 10, 2026))
    }

    @Test
    fun ac5_startWheelCannotSelectFutureMonth() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(1, 12, 2026))
        assertWheelShows(departureTags, day = "1", month = "October", year = "2026")
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, DmyDate(1, 10, 2026))
    }

    @Test
    fun ac5_startWheelCannotSelectFutureYear() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(16, 10, 2027))
        assertWheelShows(departureTags, day = "16", month = "October", year = "2026")
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, DmyDate(16, 10, 2026))
    }

    @Test
    fun ac9_endWheelCannotSelectFutureDay() {
        showReturnStep(departureDate = daysAgo(6))

        spinTo(returnTags, DmyDate(17, 10, 2026))
        assertWheelShows(returnTags, day = "16", month = "October", year = "2026")
        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, DmyDate(16, 10, 2026))
    }

    @Test
    fun ac9_endWheelCannotSelectFutureMonth() {
        showReturnStep(departureDate = daysAgo(6))

        spinTo(returnTags, DmyDate(10, 11, 2026))
        assertWheelShows(returnTags, day = "10", month = "October", year = "2026")
        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, DmyDate(10, 10, 2026))
    }

    // --- AC8: same-day trip ---

    @Test
    fun ac8_sameDayTripIsAccepted() {
        showReturnStep(departureDate = daysAgo(5))

        spinTo(returnTags, daysAgo(5))
        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, daysAgo(5))
    }

    // --- EC1: leap years ---

    @Test
    fun ec1_leapDay29February2028IsAccepted() {
        showDepartureStep(today = LocalDate.of(2028, 3, 1))

        spinTo(departureTags, DmyDate(29, 2, 2028))
        assertWheelState(departureTags.dayWheel, "29")
        saveAndContinue(departureTags)

        assertEquals(DmyDate(29, 2, 2028), submittedDate)
    }

    @Test
    fun ec1_nonLeapDay29February2027ClampsTo28() {
        showDepartureStep(today = LocalDate.of(2027, 3, 1))

        spinTo(departureTags, DmyDate(29, 2, 2027))
        assertWheelState(departureTags.dayWheel, "28")
        saveAndContinue(departureTags)

        assertEquals(DmyDate(28, 2, 2027), submittedDate)
    }

    private fun daysAgo(days: Long): DmyDate =
        today.minusDays(days).let { DmyDate(it.dayOfMonth, it.monthValue, it.year) }

    private fun showDepartureStep(today: LocalDate = this.today) {
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
    }

    private fun showReturnStep(departureDate: DmyDate) {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    departureDate = departureDate,
                    isReturnDate = true,
                    testTags = returnTags,
                    onSubmit = { submittedDate = it },
                    today = today,
                )
            }
        }
    }

    private fun saveAndContinue(tags: WizardDateStepTestTags) {
        composeTestRule.onNodeWithTag(tags.saveAction).performClick()
        composeTestRule.waitForIdle()
    }

    private fun assertBlockedWithError(
        tags: WizardDateStepTestTags,
        message: String,
        substring: Boolean = false,
    ) {
        assertNull("User must not be able to continue", submittedDate)
        composeTestRule.onNodeWithTag(tags.summary).assertIsDisplayed()
        composeTestRule
            .onNode(
                hasText(message, substring = substring) and hasAnyAncestor(hasTestTag(tags.summary)),
                useUnmergedTree = true,
            ).assertIsDisplayed()
    }

    private fun assertSavedWithoutError(
        tags: WizardDateStepTestTags,
        expected: DmyDate,
    ) {
        assertEquals(expected, submittedDate)
        composeTestRule.onNodeWithTag(tags.summary).assertDoesNotExist()
    }

    private fun assertWheelShows(
        tags: WizardDateStepTestTags,
        day: String,
        month: String,
        year: String,
    ) {
        assertWheelState(tags.dayWheel, day)
        assertWheelState(tags.monthWheel, month)
        assertWheelState(tags.yearWheel, year)
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

    private companion object {
        const val START_MORE_THAN_365_DAYS = "The trip start date cannot be more than 365 days in the past"
        const val TRIP_EXCEEDS_30_DAYS = "30 days"
        const val END_BEFORE_START = "The trip end date cannot be before the trip start date"
    }
}

