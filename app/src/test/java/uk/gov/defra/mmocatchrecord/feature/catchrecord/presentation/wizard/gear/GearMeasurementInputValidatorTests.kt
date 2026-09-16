package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            type = GearMeasurementFieldType.Integer,
            unit = "mm",
        )
    private val trawlNetsField =
        GearMeasurementField(
            key = GearMeasurementFieldKeys.NUMBER_OF_TRAWL_NETS,
            label = "Number of trawl nets",
            type = GearMeasurementFieldType.Integer,
        )

    // A synthetic Decimal-typed field (not tied to any real, currently-confirmed gear-type schema) kept
    // purely to exercise GearMeasurementInputValidator's Decimal-type support in isolation, now that every
    // confirmed gear type's mesh-size field is Integer — see the retrofit note in
    // StubReferenceDataRepository. GearMeasurementFieldType.Decimal remains a supported schema type for any
    // future gear type that turns out to need fractional precision.
    private val genericDecimalField =
        GearMeasurementField(
            key = "generic_decimal_field",
            label = "Generic decimal field",
            type = GearMeasurementFieldType.Decimal,
        )

    @Test
    fun `single mesh size field with a valid whole number value is valid`() {
        val result =
            GearMeasurementInputValidator.validate(listOf(meshSizeField), mapOf(meshSizeField.key to "100"))
        assertTrue(result.isValid)
        assertEquals(MeasurementValue.Numeric(100.0, "mm"), result.measurements[meshSizeField.key])
    }

    @Test
    fun `mesh size field is whole-number typed and rejects a decimal value`() {
        val result =
            GearMeasurementInputValidator.validate(listOf(meshSizeField), mapOf(meshSizeField.key to "12.5"))
        assertEquals(GearMeasurementFieldError.Numeric, result.errors[meshSizeField.key])
        assertTrue(!result.isValid)
    }

    @Test
    fun `a decimal-typed field still accepts a decimal point value`() {
        val result =
            GearMeasurementInputValidator.validate(
                listOf(genericDecimalField),
                mapOf(genericDecimalField.key to "12.5"),
            )
        assertTrue(result.isValid)
        assertEquals(MeasurementValue.Numeric(12.5, ""), result.measurements[genericDecimalField.key])
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

    @Test
    fun `pots or traps hauled field is a whole number and rejects a decimal value`() {
        val hauledField =
            GearMeasurementField(
                key = GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_HAULED,
                label = "Total pots or traps hauled",
                type = GearMeasurementFieldType.Integer,
            )
        val validResult =
            GearMeasurementInputValidator.validate(listOf(hauledField), mapOf(hauledField.key to "12"))
        assertTrue(validResult.isValid)
        assertEquals(MeasurementValue.Numeric(12.0, ""), validResult.measurements[hauledField.key])

        val invalidResult =
            GearMeasurementInputValidator.validate(listOf(hauledField), mapOf(hauledField.key to "12.5"))
        assertEquals(GearMeasurementFieldError.Numeric, invalidResult.errors[hauledField.key])
    }

    @Test
    fun `drifting longlines schema of two whole-number hook fields validates independently`() {
        val hooksHauledField =
            GearMeasurementField(
                key = GearMeasurementFieldKeys.TOTAL_HOOKS_HAULED,
                label = "Total hooks hauled",
                type = GearMeasurementFieldType.Integer,
            )
        val hooksLeftField =
            GearMeasurementField(
                key = GearMeasurementFieldKeys.TOTAL_HOOKS_LEFT_IN_WATER,
                label = "Total hooks left in water",
                type = GearMeasurementFieldType.Integer,
            )
        val fields = listOf(hooksHauledField, hooksLeftField)
        val validResult =
            GearMeasurementInputValidator.validate(
                fields,
                mapOf(hooksHauledField.key to "500", hooksLeftField.key to "10"),
            )
        assertTrue(validResult.isValid)
        assertEquals(MeasurementValue.Numeric(500.0, ""), validResult.measurements[hooksHauledField.key])
        assertEquals(MeasurementValue.Numeric(10.0, ""), validResult.measurements[hooksLeftField.key])

        // Each field is still validated independently: a decimal in one whole-number field is rejected
        // for that field specifically, while the other field's own valid/invalid state is unaffected.
        val invalidResult =
            GearMeasurementInputValidator.validate(
                fields,
                mapOf(hooksHauledField.key to "500", hooksLeftField.key to "10.5"),
            )
        assertTrue(!invalidResult.isValid)
        assertEquals(GearMeasurementFieldError.Numeric, invalidResult.errors[hooksLeftField.key])
        assertNull(invalidResult.errors[hooksHauledField.key])
    }

    @Test
    fun `gillnets mesh size field is whole-number typed, consistent with every other confirmed gear type`() {
        val gillnetsMeshSizeField =
            GearMeasurementField(
                key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                label = "Mesh size (mm)",
                type = GearMeasurementFieldType.Integer,
                unit = "mm",
            )
        val result =
            GearMeasurementInputValidator.validate(
                listOf(gillnetsMeshSizeField),
                mapOf(gillnetsMeshSizeField.key to "60.5"),
            )
        assertEquals(GearMeasurementFieldError.Numeric, result.errors[gillnetsMeshSizeField.key])
    }
}
