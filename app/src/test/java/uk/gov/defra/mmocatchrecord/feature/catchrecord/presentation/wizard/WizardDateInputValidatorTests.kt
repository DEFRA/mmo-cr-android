@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

class WizardDateInputValidatorTests {
    @Test
    fun `missing date parts return field specific errors`() {
        val result = WizardDateInputValidator.validate(day = "", month = "09", year = "")
        assertEquals(
            mapOf(
                DateInputField.Day to DateInputError.DayRequired,
                DateInputField.Year to DateInputError.YearRequired,
            ),
            result.errors,
        )
    }

    @Test
    fun `invalid calendar date is rejected`() {
        val result = WizardDateInputValidator.validate(day = "31", month = "02", year = "2026")
        assertEquals(DateInputError.InvalidDate, result.errors[DateInputField.Day])
    }

    @Test
    fun `return date before departure is rejected`() {
        val result =
            WizardDateInputValidator.validate(
                day = "10",
                month = "09",
                year = "2026",
                departureDate = DmyDate(11, 9, 2026),
                isReturnDate = true,
            )
        assertEquals(DateInputError.ReturnBeforeDeparture, result.errors[DateInputField.Day])
    }

    @Test
    fun `valid date passes and returns parsed date`() {
        val result = WizardDateInputValidator.validate(day = "11", month = "09", year = "2026")
        assertTrue(result.isValid)
        assertEquals(DmyDate(11, 9, 2026), result.date)
    }
}
