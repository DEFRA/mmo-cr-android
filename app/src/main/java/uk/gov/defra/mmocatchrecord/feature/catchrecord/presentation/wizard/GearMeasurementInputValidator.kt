package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType

enum class GearMeasurementFieldError {
    Required,
    Numeric,
}

data class GearMeasurementValidationResult(
    val measurements: Map<String, MeasurementValue> = emptyMap(),
    val errors: Map<String, GearMeasurementFieldError> = emptyMap(),
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/**
 * Pure validator bridging the gear-measurement screen's raw per-field text input to typed
 * [MeasurementValue]s, driven entirely by the selected gear type's [GearMeasurementField] schema — every
 * schema field is currently required (no gear type has confirmed an optional measurement field).
 */
object GearMeasurementInputValidator {
    fun validate(
        fields: List<GearMeasurementField>,
        rawValues: Map<String, String>,
    ): GearMeasurementValidationResult {
        val errors = linkedMapOf<String, GearMeasurementFieldError>()
        val measurements = linkedMapOf<String, MeasurementValue>()

        fields.forEach { field ->
            val raw = rawValues[field.key].orEmpty().trim()
            when {
                raw.isEmpty() -> errors[field.key] = GearMeasurementFieldError.Required
                !isValidNumber(raw, field.type) -> errors[field.key] = GearMeasurementFieldError.Numeric
                else ->
                    measurements[field.key] =
                        MeasurementValue.Numeric(raw.toDouble(), field.unit.orEmpty())
            }
        }

        return if (errors.isEmpty()) {
            GearMeasurementValidationResult(measurements = measurements)
        } else {
            GearMeasurementValidationResult(errors = errors)
        }
    }

    private fun isValidNumber(
        raw: String,
        type: GearMeasurementFieldType,
    ): Boolean =
        when (type) {
            GearMeasurementFieldType.Integer -> raw.toIntOrNull() != null
            GearMeasurementFieldType.Decimal -> raw.toDoubleOrNull() != null
        }
}
