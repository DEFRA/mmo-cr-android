package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.LocalDate

class WizardDateInputValidatorTests {
    @Test
    fun `nothing confirmed is rejected`() {
        val result = WizardDateInputValidator.validate(confirmedDate = null, wheelDate = DmyDate(11, 9, 2026))
        assertEquals(DateInputError.NotConfirmed, result.error)
    }

    @Test
    fun `wheel moved after confirming is rejected`() {
        val result =
            WizardDateInputValidator.validate(confirmedDate = DmyDate(11, 9, 2026), wheelDate = DmyDate(12, 9, 2026))
        assertEquals(DateInputError.NotConfirmed, result.error)
    }

    @Test
    fun `invalid calendar date is rejected`() {
        val result =
            WizardDateInputValidator.validate(confirmedDate = DmyDate(31, 2, 2026), wheelDate = DmyDate(31, 2, 2026))
        assertEquals(DateInputError.InvalidDate, result.error)
    }

    @Test
    fun `return date before departure is rejected`() {
        val result =
            WizardDateInputValidator.validate(
                confirmedDate = DmyDate(10, 9, 2026),
                wheelDate = DmyDate(10, 9, 2026),
                departureDate = DmyDate(11, 9, 2026),
                isReturnDate = true,
            )
        assertEquals(DateInputError.ReturnBeforeDeparture, result.error)
    }

    @Test
    fun `confirmed date matching the wheel passes`() {
        val result =
            WizardDateInputValidator.validate(confirmedDate = DmyDate(11, 9, 2026), wheelDate = DmyDate(11, 9, 2026))
        assertTrue(result.isValid)
        assertEquals(DmyDate(11, 9, 2026), result.date)
    }

    @Test
    fun `year range spans last year to next year`() {
        assertEquals(2025..2027, pickerYearRange(LocalDate.of(2026, 10, 16), null))
    }

    @Test
    fun `year range widens to include a saved date outside the window`() {
        assertEquals(2022..2027, pickerYearRange(LocalDate.of(2026, 10, 16), DmyDate(1, 1, 2022)))
        assertEquals(2025..2029, pickerYearRange(LocalDate.of(2026, 10, 16), DmyDate(1, 1, 2029)))
    }
}
