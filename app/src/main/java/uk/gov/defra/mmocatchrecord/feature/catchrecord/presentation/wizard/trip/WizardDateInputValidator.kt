package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DateValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

enum class DateInputError {
    NotConfirmed,
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

/** Pure validator for the date wheel: only an Ok-confirmed date that still matches the wheel can be saved. */
object WizardDateInputValidator {
    fun validate(
        confirmedDate: DmyDate?,
        wheelDate: DmyDate,
        departureDate: DmyDate? = null,
        isReturnDate: Boolean = false,
    ): WizardDateValidationResult {
        if (confirmedDate == null || confirmedDate != wheelDate) {
            return WizardDateValidationResult(error = DateInputError.NotConfirmed)
        }
        if (DateValidation.validateDate(confirmedDate) is ValidationResult.Invalid) {
            return WizardDateValidationResult(error = DateInputError.InvalidDate)
        }
        if (isReturnDate &&
            departureDate != null &&
            DateValidation.validateReturnNotBeforeDeparture(departureDate, confirmedDate) is ValidationResult.Invalid
        ) {
            return WizardDateValidationResult(error = DateInputError.ReturnBeforeDeparture)
        }
        return WizardDateValidationResult(date = confirmedDate)
    }
}
