package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DateValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.LocalDate

enum class DateInputError {
    InvalidDate,
    DepartureBeforeEarliest,
    DepartureInFuture,
    ReturnInFuture,
    ReturnBeforeDeparture,
}

data class WizardDateValidationResult(
    val date: DmyDate? = null,
    val error: DateInputError? = null,
) {
    val isValid: Boolean
        get() = date != null && error == null
}

/** Pure validator for the date wheel: the wheel's current date is the one saved. */
object WizardDateInputValidator {
    /** Earliest departure date permitted by the confirmed business rule. */
    private val earliestDepartureDate = DmyDate(24, 7, 2025)

    fun validate(
        date: DmyDate,
        today: LocalDate,
        departureDate: DmyDate? = null,
        isReturnDate: Boolean = false,
    ): WizardDateValidationResult {
        val error = findError(date, today, departureDate, isReturnDate)
        return if (error != null) WizardDateValidationResult(error = error) else WizardDateValidationResult(date = date)
    }

    private fun findError(
        date: DmyDate,
        today: LocalDate,
        departureDate: DmyDate?,
        isReturnDate: Boolean,
    ): DateInputError? {
        val todayAsDmyDate = DmyDate(today.dayOfMonth, today.monthValue, today.year)
        return when {
            DateValidation.validateDate(date) is ValidationResult.Invalid -> DateInputError.InvalidDate
            !isReturnDate && date < earliestDepartureDate -> DateInputError.DepartureBeforeEarliest
            date > todayAsDmyDate ->
                if (isReturnDate) DateInputError.ReturnInFuture else DateInputError.DepartureInFuture
            isReturnDate &&
                departureDate != null &&
                DateValidation.validateReturnNotBeforeDeparture(departureDate, date) is ValidationResult.Invalid
            -> DateInputError.ReturnBeforeDeparture
            else -> null
        }
    }
}
