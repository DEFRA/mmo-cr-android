package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DateValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DateInputError {
    InvalidDate,
    DepartureBeforeEarliest,
    DepartureInFuture,
    ReturnInFuture,
    ReturnBeforeDeparture,
    TripTooLong,
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
    private const val MAX_BACKDATE_DAYS = 365L
    private const val MAX_TRIP_DAYS = 30L

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
            !isReturnDate && date < todayAsDmyDate.minusDays(MAX_BACKDATE_DAYS) ->
                DateInputError.DepartureBeforeEarliest
            date > todayAsDmyDate ->
                if (isReturnDate) DateInputError.ReturnInFuture else DateInputError.DepartureInFuture
            isReturnDate &&
                departureDate != null &&
                DateValidation.validateReturnNotBeforeDeparture(departureDate, date) is ValidationResult.Invalid
            -> DateInputError.ReturnBeforeDeparture
            isReturnDate && departureDate != null && date.daysAfter(departureDate) > MAX_TRIP_DAYS ->
                DateInputError.TripTooLong
            else -> null
        }
    }

    private fun DmyDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)

    private fun DmyDate.minusDays(days: Long): DmyDate =
        toLocalDate().minusDays(days).let { DmyDate(it.dayOfMonth, it.monthValue, it.year) }

    private fun DmyDate.daysAfter(other: DmyDate): Long = ChronoUnit.DAYS.between(other.toLocalDate(), toLocalDate())
}
