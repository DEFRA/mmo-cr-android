package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.LocalDate

class WizardDateInputValidatorTests {
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun `invalid calendar date is rejected`() {
        val result = WizardDateInputValidator.validate(date = DmyDate(31, 2, 2026), today = today)
        assertEquals(DateInputError.InvalidDate, result.error)
    }

    @Test
    fun `return date before departure is rejected`() {
        val result =
            WizardDateInputValidator.validate(
                date = DmyDate(10, 9, 2026),
                today = today,
                departureDate = DmyDate(11, 9, 2026),
                isReturnDate = true,
            )
        assertEquals(DateInputError.ReturnBeforeDeparture, result.error)
    }

    @Test
    fun `return date on departure day passes`() {
        val result =
            WizardDateInputValidator.validate(
                date = DmyDate(11, 9, 2026),
                today = today,
                departureDate = DmyDate(11, 9, 2026),
                isReturnDate = true,
            )
        assertTrue(result.isValid)
    }

    @Test
    fun `valid date passes`() {
        val result = WizardDateInputValidator.validate(date = DmyDate(11, 9, 2026), today = today)
        assertTrue(result.isValid)
        assertEquals(DmyDate(11, 9, 2026), result.date)
    }

    @Test
    fun `departure date before 24 July 2025 is rejected`() {
        val result = WizardDateInputValidator.validate(date = DmyDate(23, 7, 2025), today = today)
        assertEquals(DateInputError.DepartureBeforeEarliest, result.error)
    }

    @Test
    fun `departure date of exactly 24 July 2025 passes`() {
        val result = WizardDateInputValidator.validate(date = DmyDate(24, 7, 2025), today = today)
        assertTrue(result.isValid)
    }

    @Test
    fun `departure date in the future is rejected`() {
        val result = WizardDateInputValidator.validate(date = DmyDate(17, 10, 2026), today = today)
        assertEquals(DateInputError.DepartureInFuture, result.error)
    }

    @Test
    fun `return date in the future is rejected`() {
        val result =
            WizardDateInputValidator.validate(
                date = DmyDate(17, 10, 2026),
                today = today,
                departureDate = DmyDate(10, 10, 2026),
                isReturnDate = true,
            )
        assertEquals(DateInputError.ReturnInFuture, result.error)
    }

    @Test
    fun `departure date of today passes`() {
        val result = WizardDateInputValidator.validate(date = DmyDate(16, 10, 2026), today = today)
        assertTrue(result.isValid)
    }

    @Test
    fun `return date of today passes`() {
        val result =
            WizardDateInputValidator.validate(
                date = DmyDate(16, 10, 2026),
                today = today,
                departureDate = DmyDate(10, 10, 2026),
                isReturnDate = true,
            )
        assertTrue(result.isValid)
    }

    @Test
    fun `year range spans last year to this year and never extends into the future`() {
        assertEquals(2025..2026, pickerYearRange(LocalDate.of(2026, 10, 16), null))
    }

    @Test
    fun `year range widens to include a saved date outside the window`() {
        assertEquals(2022..2026, pickerYearRange(LocalDate.of(2026, 10, 16), DmyDate(1, 1, 2022)))
        assertEquals(2025..2029, pickerYearRange(LocalDate.of(2026, 10, 16), DmyDate(1, 1, 2029)))
    }
}
