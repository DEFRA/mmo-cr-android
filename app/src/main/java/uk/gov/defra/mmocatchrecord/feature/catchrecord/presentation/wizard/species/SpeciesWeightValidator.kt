package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementInputValidator
import kotlin.math.abs
import kotlin.math.round

/** Which captured weight field a value belongs to — shared by Phase 5A's per-species checklist and 5B's. */
enum class SpeciesWeightFieldKind {
    AboveMinimumSize,
    BelowMinimumSize,
    LegallyDiscarded,

    /** Phase 5B only — the "kept onboard or in keep pots" field, deliberately excluded from quota checks. */
    KeptOnboard,
}

/** Errors a single species weight field can fail with — see [SpeciesWeightValidator]. */
enum class SpeciesWeightFieldError {
    /** Left blank while mandatory for this field/species combination. */
    Required,

    /** Parsed value is not `> 0` (or wasn't parseable at all — defensive only). */
    BelowMinimum,

    /** Parsed value is `> MAX_WEIGHT_KG`. */
    AboveMaximum,

    /** Value doesn't match the species' [SpeciesWeightPrecision] (whole number vs up to 1 decimal place). */
    Precision,

    /** Cumulative weight for this species across the whole draft would exceed its monthly quota. */
    MonthlyQuotaExceeded,

    /** Cumulative weight for this species across the whole draft would exceed its annual quota. */
    AnnualQuotaExceeded,
}

/** Result of validating one species' full set of active weight fields — see [SpeciesWeightValidator.validateEntry]. */
data class SpeciesWeightEntryValidationResult(
    val values: Map<SpeciesWeightFieldKind, Double?> = emptyMap(),
    val errors: Map<SpeciesWeightFieldKind, SpeciesWeightFieldError> = emptyMap(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Pure validator for a species' captured weight fields (Phase 5A's checklist screen and Phase 5B's
 * follow-up screen). Deliberately batch-shaped (validates every *active* field for one species entry in
 * one call, mirroring [GearMeasurementInputValidator]'s batch-validation pattern) since the quota check
 * only makes sense evaluated across the species' whole set of populated fields at once.
 *
 * Range (`> 0kg` and `<= 10,000kg` — reported as two distinct [SpeciesWeightFieldError] values,
 * [SpeciesWeightFieldError.BelowMinimum]/[SpeciesWeightFieldError.AboveMaximum], since a single parsed value
 * can only ever violate one bound) and precision (whole-number vs one-decimal-place, per
 * [Species.weightPrecision]) are validated independently per field first; the quota check then only runs
 * if every field passed those, and — if it fails — is attached to every field that has a non-null value
 * (there is no single "correct" field to blame when several combine to exceed a quota, so this is a
 * deliberate, simple, deterministic choice over an order-dependent "first field to tip it over" rule).
 */
object SpeciesWeightValidator {
    const val MIN_WEIGHT_KG = 0.0
    const val MAX_WEIGHT_KG = 10_000.0
    private const val PRECISION_TOLERANCE = 1e-6
    private const val DECIMAL_PLACES_ALLOWED = 10.0

    /**
     * @param rawValuesByField the raw text of every *active* (shown/relevant) field for this species entry
     *   — only keys present here are validated/returned; a field the caller doesn't consider active (e.g.
     *   an un-revealed "below minimum size" field) should simply be omitted.
     * @param mandatoryFields which of [rawValuesByField]'s keys are required to be non-blank — typically
     *   just [SpeciesWeightFieldKind.AboveMinimumSize] when [Species.weightAboveMinimumSizeMandatory] is
     *   true (Phase 5A), or [SpeciesWeightFieldKind.KeptOnboard] always (Phase 5B; assumption — see the
     *   change summary).
     * @param cumulativeOtherEntriesKg the sum of this species' three 5A weight fields already saved
     *   elsewhere in the draft (every *other* gear use) — see [SpeciesWeightSupport]. Deliberately excludes
     *   this same field's own prior persisted value (the candidate value being validated here replaces it).
     * @param applyQuotaCheck `false` for Phase 5B's "kept onboard" field, which is explicitly out of the
     *   quota check's scope per the confirmed requirements ("summing all three [5A] weight fields").
     */
    fun validateEntry(
        species: Species,
        rawValuesByField: Map<SpeciesWeightFieldKind, String>,
        mandatoryFields: Set<SpeciesWeightFieldKind> = emptySet(),
        cumulativeOtherEntriesKg: Double = 0.0,
        applyQuotaCheck: Boolean = true,
    ): SpeciesWeightEntryValidationResult {
        val values = linkedMapOf<SpeciesWeightFieldKind, Double?>()
        val errors = linkedMapOf<SpeciesWeightFieldKind, SpeciesWeightFieldError>()

        rawValuesByField.forEach { (fieldKind, rawValue) ->
            val trimmed = rawValue.trim()
            when {
                trimmed.isEmpty() && fieldKind in mandatoryFields ->
                    errors[fieldKind] = SpeciesWeightFieldError.Required

                trimmed.isEmpty() -> values[fieldKind] = null

                else -> {
                    val parsed = trimmed.toDoubleOrNull()
                    when {
                        parsed == null || parsed <= MIN_WEIGHT_KG ->
                            errors[fieldKind] = SpeciesWeightFieldError.BelowMinimum

                        parsed > MAX_WEIGHT_KG ->
                            errors[fieldKind] = SpeciesWeightFieldError.AboveMaximum

                        !matchesPrecision(parsed, species.weightPrecision) ->
                            errors[fieldKind] = SpeciesWeightFieldError.Precision

                        else -> values[fieldKind] = parsed
                    }
                }
            }
        }

        if (applyQuotaCheck && errors.isEmpty()) {
            applyQuotaCheckIfNeeded(species, values, cumulativeOtherEntriesKg, errors)
        }

        return SpeciesWeightEntryValidationResult(
            values = if (errors.isEmpty()) values else emptyMap(),
            errors = errors,
        )
    }

    private fun applyQuotaCheckIfNeeded(
        species: Species,
        values: Map<SpeciesWeightFieldKind, Double?>,
        cumulativeOtherEntriesKg: Double,
        errors: MutableMap<SpeciesWeightFieldKind, SpeciesWeightFieldError>,
    ) {
        val enteredTotal = values.values.filterNotNull().sum()
        val total = cumulativeOtherEntriesKg + enteredTotal
        val monthlyQuota = species.monthlyQuotaKg
        val annualQuota = species.annualQuotaKg
        val quotaError =
            when {
                monthlyQuota != null && total > monthlyQuota -> SpeciesWeightFieldError.MonthlyQuotaExceeded
                annualQuota != null && total > annualQuota -> SpeciesWeightFieldError.AnnualQuotaExceeded
                else -> null
            } ?: return
        values.forEach { (fieldKind, value) -> if (value != null) errors[fieldKind] = quotaError }
    }

    private fun matchesPrecision(
        value: Double,
        precision: SpeciesWeightPrecision,
    ): Boolean =
        when (precision) {
            SpeciesWeightPrecision.WholeNumber -> abs(value - round(value)) < PRECISION_TOLERANCE
            SpeciesWeightPrecision.OneDecimalPlace -> {
                val scaled = value * DECIMAL_PLACES_ALLOWED
                abs(scaled - round(scaled)) < PRECISION_TOLERANCE
            }
        }
}
