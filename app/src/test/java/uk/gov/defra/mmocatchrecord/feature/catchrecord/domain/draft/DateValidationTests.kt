package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult

class DateValidationTests {
    @Test
    fun `a genuine calendar date is valid`() {
        val result = DateValidation.validateDate(DmyDate(day = 15, month = 6, year = 2026))

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `29 February in a leap year is valid`() {
        val result = DateValidation.validateDate(DmyDate(day = 29, month = 2, year = 2024))

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `29 February in a non-leap year is invalid`() {
        val result = DateValidation.validateDate(DmyDate(day = 29, month = 2, year = 2025))

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `29 February in a century non-leap year is invalid`() {
        val result = DateValidation.validateDate(DmyDate(day = 29, month = 2, year = 1900))

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `29 February in a 400-divisible century leap year is valid`() {
        val result = DateValidation.validateDate(DmyDate(day = 29, month = 2, year = 2000))

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `month out of range is invalid`() {
        val result = DateValidation.validateDate(DmyDate(day = 1, month = 13, year = 2026))

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `day out of range for month is invalid`() {
        val result = DateValidation.validateDate(DmyDate(day = 31, month = 4, year = 2026))

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `year out of supported range is invalid`() {
        val result = DateValidation.validateDate(DmyDate(day = 1, month = 1, year = 1800))

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `return date on the same day as departure is valid`() {
        val date = DmyDate(day = 10, month = 5, year = 2026)

        val result = DateValidation.validateReturnNotBeforeDeparture(date, date)

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `return date after departure is valid`() {
        val departure = DmyDate(day = 10, month = 5, year = 2026)
        val returnDate = DmyDate(day = 12, month = 5, year = 2026)

        val result = DateValidation.validateReturnNotBeforeDeparture(departure, returnDate)

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `return date before departure is invalid`() {
        val departure = DmyDate(day = 10, month = 5, year = 2026)
        val returnDate = DmyDate(day = 9, month = 5, year = 2026)

        val result = DateValidation.validateReturnNotBeforeDeparture(departure, returnDate)

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            "Return date must not be before the departure date",
            (result as ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `return date across a year boundary after departure is valid`() {
        val departure = DmyDate(day = 30, month = 12, year = 2026)
        val returnDate = DmyDate(day = 2, month = 1, year = 2027)

        val result = DateValidation.validateReturnNotBeforeDeparture(departure, returnDate)

        assertEquals(ValidationResult.Valid, result)
    }
}
