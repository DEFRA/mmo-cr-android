package uk.gov.defra.mmocatchrecord.core.architecture

/**
 * Result of a pure validation rule. No Android framework dependencies — safe to unit-test as plain JUnit
 * with no mocks/instrumentation.
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()

    data class Invalid(
        val reason: String,
    ) : ValidationResult()
}

/**
 * Pure, reusable validation functions shared across feature ViewModels/use-cases.
 *
 * Keep every rule here free of Android/framework types and side effects so it stays trivially testable
 * and reusable from both `domain` use-cases and presentation-layer input validation.
 */
object ValidationHelper {
    /** A required text field must be non-blank. */
    fun requireNotBlank(
        value: String,
        fieldName: String,
    ): ValidationResult =
        if (value.isNotBlank()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("$fieldName must not be empty")
        }

    /** A value must not exceed [maxLength] characters. */
    fun maxLength(
        value: String,
        maxLength: Int,
        fieldName: String,
    ): ValidationResult =
        if (value.length <= maxLength) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("$fieldName must be $maxLength characters or fewer")
        }

    /** A numeric quantity must be strictly positive (e.g. catch weight, count). */
    fun requirePositive(
        value: Double,
        fieldName: String,
    ): ValidationResult =
        if (value > 0.0) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("$fieldName must be greater than zero")
        }
}
