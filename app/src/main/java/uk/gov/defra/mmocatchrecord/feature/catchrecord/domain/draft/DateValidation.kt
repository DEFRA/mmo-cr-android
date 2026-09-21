package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult

/**
 * Pure date validation rules for trip departure/return dates, per the confirmed business rules: a date
 * must be a genuine calendar day-month-year (including leap years), and the return date must not be
 * earlier than the departure date.
 */
object DateValidation {
    private val daysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2200
    private const val MIN_MONTH = 1
    private const val MAX_MONTH = 12
    private const val FEBRUARY = 2
    private const val DAYS_IN_LEAP_FEBRUARY = 29
    private const val LEAP_YEAR_DIVISOR = 4
    private const val CENTURY_DIVISOR = 100
    private const val LEAP_CENTURY_DIVISOR = 400

    /** Validates that [date] is a genuine calendar date (correct day count for month/leap year). */
    fun validateDate(
        date: DmyDate,
        fieldName: String = "Date",
    ): ValidationResult {
        if (date.year < MIN_YEAR || date.year > MAX_YEAR) {
            return ValidationResult.Invalid("$fieldName year must be between $MIN_YEAR and $MAX_YEAR")
        }
        if (date.month < MIN_MONTH || date.month > MAX_MONTH) {
            return ValidationResult.Invalid("$fieldName must have a month between $MIN_MONTH and $MAX_MONTH")
        }
        val maxDay = daysInMonthFor(date.month, date.year)
        return if (date.day in 1..maxDay) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("$fieldName must be a valid day for the given month and year")
        }
    }

    /** Validates that [returnDate] is on or after [departureDate], assuming both are individually valid. */
    fun validateReturnNotBeforeDeparture(
        departureDate: DmyDate,
        returnDate: DmyDate,
    ): ValidationResult =
        if (returnDate >= departureDate) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Return date must not be before the departure date")
        }

    private fun daysInMonthFor(
        month: Int,
        year: Int,
    ): Int =
        if (month == FEBRUARY && isLeapYear(year)) {
            DAYS_IN_LEAP_FEBRUARY
        } else {
            daysInMonth[month - 1]
        }

    private fun isLeapYear(year: Int): Boolean =
        (year % LEAP_YEAR_DIVISOR == 0 && year % CENTURY_DIVISOR != 0) || year % LEAP_CENTURY_DIVISOR == 0
}
