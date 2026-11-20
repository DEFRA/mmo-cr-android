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

// Acceptance tests for the trip departure/return date story (AC1–AC5, EC1); "today" is fixed for determinism.
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

        assertWheelState(departureTags.dayWheel, "16")
        assertWheelState(departureTags.monthWheel, "October")
        assertWheelState(departureTags.yearWheel, "2026")
    }

    @Test
    fun returnDateDefaultsToToday() {
        showReturnStep(departureDate = DmyDate(10, 10, 2026))

        assertWheelState(returnTags.dayWheel, "16")
        assertWheelState(returnTags.monthWheel, "October")
        assertWheelState(returnTags.yearWheel, "2026")
    }

    @Test
    fun ac1_validDepartureDateIsSaved() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(11, 9, 2026))
        saveAndContinue(departureTags)

        assertEquals(DmyDate(11, 9, 2026), submittedDate)
    }

    @Test
    fun ac1_departureDateOfTodayIsSaved() {
        showDepartureStep()

        saveAndContinue(departureTags)

        assertEquals(DmyDate(16, 10, 2026), submittedDate)
    }

    @Test
    fun ac2_departureDateOnEarliestAllowedDateIsSaved() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(24, 7, 2025))
        saveAndContinue(departureTags)

        assertEquals(DmyDate(24, 7, 2025), submittedDate)
    }

    @Test
    fun ac2_departureDateBefore24July2025IsRejected() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(23, 7, 2025))
        saveAndContinue(departureTags)

        assertBlockedWithError(departureTags, DEPARTURE_BEFORE_ALLOWED)
    }

    @Test
    fun ac3_departureWheelCannotSelectFutureDay() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(17, 10, 2026))
        assertWheelShows(departureTags, day = "16", month = "October", year = "2026")
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, DmyDate(16, 10, 2026))
    }

    @Test
    fun ac3_departureWheelCannotSelectFutureMonth() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(1, 12, 2026))
        assertWheelShows(departureTags, day = "1", month = "October", year = "2026")
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, DmyDate(1, 10, 2026))
    }

    @Test
    fun ac3_departureWheelCannotSelectFutureYear() {
        showDepartureStep()

        spinTo(departureTags, DmyDate(16, 10, 2027))
        assertWheelShows(departureTags, day = "16", month = "October", year = "2026")
        saveAndContinue(departureTags)

        assertSavedWithoutError(departureTags, DmyDate(16, 10, 2026))
    }

    @Test
    fun ac4_returnWheelCannotSelectFutureDay() {
        showReturnStep(departureDate = DmyDate(10, 10, 2026))

        spinTo(returnTags, DmyDate(17, 10, 2026))
        assertWheelShows(returnTags, day = "16", month = "October", year = "2026")
        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, DmyDate(16, 10, 2026))
    }

    @Test
    fun ac4_returnWheelCannotSelectFutureMonth() {
        showReturnStep(departureDate = DmyDate(10, 10, 2026))

        spinTo(returnTags, DmyDate(10, 11, 2026))
        assertWheelShows(returnTags, day = "10", month = "October", year = "2026")
        saveAndContinue(returnTags)

        assertSavedWithoutError(returnTags, DmyDate(10, 10, 2026))
    }

    @Test
    fun ac4_returnDateOfTodayIsSaved() {
        showReturnStep(departureDate = DmyDate(10, 10, 2026))

        saveAndContinue(returnTags)

        assertEquals(DmyDate(16, 10, 2026), submittedDate)
    }

    @Test
    fun ac5_returnDateBeforeDepartureIsRejected() {
        showReturnStep(departureDate = DmyDate(11, 9, 2026))

        spinTo(returnTags, DmyDate(10, 9, 2026))
        saveAndContinue(returnTags)

        assertBlockedWithError(returnTags, RETURN_BEFORE_DEPARTURE)
    }

    @Test
    fun ac5_returnDateSameAsDepartureIsSaved() {
        showReturnStep(departureDate = DmyDate(11, 9, 2026))

        spinTo(returnTags, DmyDate(11, 9, 2026))
        saveAndContinue(returnTags)

        assertEquals(DmyDate(11, 9, 2026), submittedDate)
    }

    @Test
    fun ec1_leapDay29February2028IsAccepted() {
        showDepartureStep(today = LocalDate.of(2028, 3, 1))

        spinTo(departureTags, DmyDate(29, 2, 2028))
        assertWheelState(departureTags.dayWheel, "29")
        saveAndContinue(departureTags)

        assertEquals(DmyDate(29, 2, 2028), submittedDate)
    }

    @Test
    fun ec1_nonLeapDay29February2027IsRejected() {
        showDepartureStep(today = LocalDate.of(2027, 3, 1))

        spinTo(departureTags, DmyDate(29, 2, 2027))
        // The wheel only offers real dates, so 29 Feb 2027 cannot be selected and clamps to 28.
        assertWheelState(departureTags.dayWheel, "28")
        saveAndContinue(departureTags)

        assertEquals(DmyDate(28, 2, 2027), submittedDate)
    }

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
    ) {
        assertNull("User must not be able to continue", submittedDate)
        composeTestRule.onNodeWithTag(tags.summary).assertIsDisplayed()
        composeTestRule
            .onNode(hasText(message) and hasAnyAncestor(hasTestTag(tags.summary)), useUnmergedTree = true)
            .assertIsDisplayed()
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
        const val DEPARTURE_BEFORE_ALLOWED = "Date you left for your trip must be on or after 24 July 2025"
        const val RETURN_BEFORE_DEPARTURE =
            "Date you returned from your trip must be the same as or after the date you left"
    }
}
