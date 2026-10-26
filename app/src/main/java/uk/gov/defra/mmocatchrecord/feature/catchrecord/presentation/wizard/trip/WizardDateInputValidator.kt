package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DateValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

enum class DateInputError {
    InvalidDate,
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
    fun validate(
        date: DmyDate,
        departureDate: DmyDate? = null,
        isReturnDate: Boolean = false,
    ): WizardDateValidationResult {
        if (DateValidation.validateDate(date) is ValidationResult.Invalid) {
            return WizardDateValidationResult(error = DateInputError.InvalidDate)
        }
        if (isReturnDate &&
            departureDate != null &&
            DateValidation.validateReturnNotBeforeDeparture(departureDate, date) is ValidationResult.Invalid
        ) {
            return WizardDateValidationResult(error = DateInputError.ReturnBeforeDeparture)
        }
        return WizardDateValidationResult(date = date)
    }
}
