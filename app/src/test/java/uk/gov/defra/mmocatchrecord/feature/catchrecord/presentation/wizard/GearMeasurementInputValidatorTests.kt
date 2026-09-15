package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType

class GearMeasurementInputValidatorTests {
    private val meshSizeField =
        GearMeasurementField(
            key = GearMeasurementFieldKeys.MESH_SIZE_MM,
            label = "Mesh size (mm)",
            type = GearMeasurementFieldType.Decimal,
            unit = "mm",
        )
    private val trawlNetsField =
        GearMeasurementField(
            key = GearMeasurementFieldKeys.NUMBER_OF_TRAWL_NETS,
            label = "Number of trawl nets",
            type = GearMeasurementFieldType.Integer,
        )

    @Test
    fun `single mesh size field with a valid decimal value is valid`() {
        val result =
            GearMeasurementInputValidator.validate(listOf(meshSizeField), mapOf(meshSizeField.key to "100"))
        assertTrue(result.isValid)
        assertEquals(MeasurementValue.Numeric(100.0, "mm"), result.measurements[meshSizeField.key])
    }

    @Test
    fun `single mesh size field accepts a decimal point value`() {
        val result =
            GearMeasurementInputValidator.validate(listOf(meshSizeField), mapOf(meshSizeField.key to "12.5"))
        assertTrue(result.isValid)
        assertEquals(MeasurementValue.Numeric(12.5, "mm"), result.measurements[meshSizeField.key])
    }

    @Test
    fun `blank mesh size field is required`() {
        val result = GearMeasurementInputValidator.validate(listOf(meshSizeField), mapOf(meshSizeField.key to ""))
        assertEquals(GearMeasurementFieldError.Required, result.errors[meshSizeField.key])
        assertTrue(!result.isValid)
    }

    @Test
    fun `missing mesh size field entry is required`() {
        val result = GearMeasurementInputValidator.validate(listOf(meshSizeField), emptyMap())
        assertEquals(GearMeasurementFieldError.Required, result.errors[meshSizeField.key])
    }

    @Test
    fun `non numeric mesh size value is a numeric error`() {
        val result =
            GearMeasurementInputValidator.validate(listOf(meshSizeField), mapOf(meshSizeField.key to "abc"))
        assertEquals(GearMeasurementFieldError.Numeric, result.errors[meshSizeField.key])
    }

    @Test
    fun `bottom otter trawl schema validates both fields independently`() {
        val fields = listOf(trawlNetsField, meshSizeField)
        val result =
            GearMeasurementInputValidator.validate(
                fields,
                mapOf(trawlNetsField.key to "2", meshSizeField.key to "80"),
            )
        assertTrue(result.isValid)
        assertEquals(MeasurementValue.Numeric(2.0, ""), result.measurements[trawlNetsField.key])
        assertEquals(MeasurementValue.Numeric(80.0, "mm"), result.measurements[meshSizeField.key])
    }

    @Test
    fun `bottom otter trawl schema reports both errors when both fields are invalid`() {
        val fields = listOf(trawlNetsField, meshSizeField)
        val result =
            GearMeasurementInputValidator.validate(
                fields,
                mapOf(trawlNetsField.key to "", meshSizeField.key to "not-a-number"),
            )
        assertEquals(GearMeasurementFieldError.Required, result.errors[trawlNetsField.key])
        assertEquals(GearMeasurementFieldError.Numeric, result.errors[meshSizeField.key])
        assertTrue(!result.isValid)
    }

    @Test
    fun `an integer field rejects a decimal value`() {
        val result =
            GearMeasurementInputValidator.validate(listOf(trawlNetsField), mapOf(trawlNetsField.key to "2.5"))
        assertEquals(GearMeasurementFieldError.Numeric, result.errors[trawlNetsField.key])
    }

    @Test
    fun `whitespace-only value is treated as required, not numeric`() {
        val result =
            GearMeasurementInputValidator.validate(listOf(meshSizeField), mapOf(meshSizeField.key to "   "))
        assertEquals(GearMeasurementFieldError.Required, result.errors[meshSizeField.key])
    }
}
