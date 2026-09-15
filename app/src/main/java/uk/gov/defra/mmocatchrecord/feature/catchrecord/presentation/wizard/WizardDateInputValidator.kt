@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DateValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

enum class DateInputField {
    Day,
    Month,
    Year,
}

enum class DateInputError {
    DayRequired,
    MonthRequired,
    YearRequired,
    DayNumeric,
    MonthNumeric,
    YearNumeric,
    InvalidDate,
    ReturnBeforeDeparture,
}

data class WizardDateValidationResult(
    val date: DmyDate? = null,
    val errors: Map<DateInputField, DateInputError> = emptyMap(),
) {
    val isValid: Boolean
        get() = date != null && errors.isEmpty()
}

/** Pure validator bridging the screen's text-field inputs to the domain-layer [DateValidation] rules. */
object WizardDateInputValidator {
    fun validate(
        day: String,
        month: String,
        year: String,
        departureDate: DmyDate? = null,
        isReturnDate: Boolean = false,
    ): WizardDateValidationResult {
        val errors = linkedMapOf<DateInputField, DateInputError>()
        val parsedDay =
            parsePart(day, DateInputField.Day, DateInputError.DayRequired, DateInputError.DayNumeric, errors)
        val parsedMonth =
            parsePart(month, DateInputField.Month, DateInputError.MonthRequired, DateInputError.MonthNumeric, errors)
        val parsedYear =
            parsePart(year, DateInputField.Year, DateInputError.YearRequired, DateInputError.YearNumeric, errors)

        if (errors.isNotEmpty()) {
            return WizardDateValidationResult(errors = errors)
        }

        val date = DmyDate(parsedDay!!, parsedMonth!!, parsedYear!!)
        if (DateValidation.validateDate(date) is ValidationResult.Invalid) {
            return WizardDateValidationResult(errors = mapOf(DateInputField.Day to DateInputError.InvalidDate))
        }
        if (isReturnDate &&
            departureDate != null &&
            DateValidation.validateReturnNotBeforeDeparture(departureDate, date) is ValidationResult.Invalid
        ) {
            return WizardDateValidationResult(
                errors = mapOf(DateInputField.Day to DateInputError.ReturnBeforeDeparture),
            )
        }
        return WizardDateValidationResult(date = date)
    }

    private fun parsePart(
        rawValue: String,
        field: DateInputField,
        requiredError: DateInputError,
        numericError: DateInputError,
        errors: MutableMap<DateInputField, DateInputError>,
    ): Int? {
        val trimmedValue = rawValue.trim()
        if (trimmedValue.isEmpty()) {
            errors[field] = requiredError
            return null
        }
        if (!trimmedValue.all(Char::isDigit)) {
            errors[field] = numericError
            return null
        }
        return trimmedValue.toIntOrNull()
    }
}
