package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult
import kotlin.math.roundToLong

/**
 * Pure weight-precision and zero-value validation rules for [SpeciesWeightEntry] fields, per the
 * confirmed business rule:
 *
 * - Weights **below 10kg** must be recorded to **0.1kg increments**.
 * - Weights **at or above 10kg** must be recorded as **whole kilograms**.
 * - `discardedKg` may be zero.
 * - At least one of `retainedAboveMcrsKg` / `retainedBelowMcrsKg` must be greater than zero — a species
 *   entry with both retained categories at zero (nothing retained, nothing to report) is invalid.
 *
 * Kept free of Android/framework types so it is trivially unit-testable, per `core/architecture`
 * [ValidationResult] convention.
 */
object WeightValidation {
    private const val WHOLE_KG_THRESHOLD = 10.0
    private const val INCREMENT_BELOW_THRESHOLD = 0.1
    private const val INCREMENT_TOLERANCE = 1e-6

    /**
     * Validates a single weight value's precision rule in isolation: 0.1kg increments below 10kg, whole
     * kilograms at/above 10kg. Does not enforce sign — use alongside a non-negative check where required.
     */
    fun validatePrecision(
        weightKg: Double,
        fieldName: String,
    ): ValidationResult {
        if (weightKg < 0.0) {
            return ValidationResult.Invalid("$fieldName must not be negative")
        }
        val increment = if (weightKg < WHOLE_KG_THRESHOLD) INCREMENT_BELOW_THRESHOLD else 1.0
        val steps = weightKg / increment
        val roundedSteps = steps.roundToLong()
        val remainder = kotlin.math.abs(steps - roundedSteps)
        return if (remainder <= INCREMENT_TOLERANCE) {
            ValidationResult.Valid
        } else {
            val expected =
                if (weightKg < WHOLE_KG_THRESHOLD) {
                    "0.1kg increments below 10kg"
                } else {
                    "whole kilograms at or above 10kg"
                }
            ValidationResult.Invalid("$fieldName must be recorded in $expected")
        }
    }

    /**
     * Validates a full [SpeciesWeightEntry]'s weight fields: precision of all three weights, and the
     * "at least one retained category > 0" rule. Returns every failing [ValidationResult.Invalid] found
     * (empty list means the entry is fully valid).
     */
    fun validateEntry(
        retainedAboveMcrsKg: Double,
        retainedBelowMcrsKg: Double,
        discardedKg: Double,
    ): List<ValidationResult.Invalid> {
        val failures = mutableListOf<ValidationResult.Invalid>()

        (validatePrecision(retainedAboveMcrsKg, "Retained above MCRS weight") as? ValidationResult.Invalid)
            ?.let(failures::add)
        (validatePrecision(retainedBelowMcrsKg, "Retained below MCRS weight") as? ValidationResult.Invalid)
            ?.let(failures::add)
        (validatePrecision(discardedKg, "Discarded weight") as? ValidationResult.Invalid)
            ?.let(failures::add)

        if (retainedAboveMcrsKg <= 0.0 && retainedBelowMcrsKg <= 0.0) {
            failures.add(
                ValidationResult.Invalid(
                    "At least one of retained above MCRS or retained below MCRS weight must be greater than zero",
                ),
            )
        }

        return failures
    }
}
