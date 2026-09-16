package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision

class SpeciesWeightValidatorTests {
    private val wholeNumberSpecies =
        Species(
            id = "species-plaice",
            name = "Plaice (PLE)",
            faoCode = "PLE",
            weightPrecision = SpeciesWeightPrecision.WholeNumber,
            weightAboveMinimumSizeMandatory = false,
        )

    private val oneDecimalPlaceSpecies =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
            weightAboveMinimumSizeMandatory = true,
        )

    // --- Required -----------------------------------------------------------------------------

    @Test
    fun `blank mandatory field yields a required error`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = oneDecimalPlaceSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "  "),
                mandatoryFields = setOf(SpeciesWeightFieldKind.AboveMinimumSize),
            )

        assertEquals(SpeciesWeightFieldError.Required, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
        assertTrue(!result.isValid)
    }

    @Test
    fun `blank optional field is simply recorded as a null value with no error`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to ""),
                mandatoryFields = emptySet(),
            )

        assertTrue(result.isValid)
        assertNull(result.values[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    // --- Range ----------------------------------------------------------------------------------

    @Test
    fun `zero value fails the range check as below minimum`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "0"),
            )

        assertEquals(SpeciesWeightFieldError.BelowMinimum, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `negative value fails the range check as below minimum`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "-5"),
            )

        assertEquals(SpeciesWeightFieldError.BelowMinimum, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `value above the maximum fails the range check as above maximum`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "10001"),
            )

        assertEquals(SpeciesWeightFieldError.AboveMaximum, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `value at the maximum boundary is valid`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "10000"),
            )

        assertTrue(result.isValid)
        assertEquals(10_000.0, result.values[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `value just above the maximum boundary fails as above maximum`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "10000.01"),
            )

        assertEquals(SpeciesWeightFieldError.AboveMaximum, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `non numeric value fails the range check as below minimum`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "abc"),
            )

        assertEquals(SpeciesWeightFieldError.BelowMinimum, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    // --- Precision --------------------------------------------------------------------------------

    @Test
    fun `whole number species rejects a fractional value with a precision error`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "5.5"),
            )

        assertEquals(SpeciesWeightFieldError.Precision, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `whole number species accepts an integer value`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = wholeNumberSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "5"),
            )

        assertTrue(result.isValid)
        assertEquals(5.0, result.values[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `one decimal place species accepts a value with a single decimal place`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = oneDecimalPlaceSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "5.5"),
            )

        assertTrue(result.isValid)
        assertEquals(5.5, result.values[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `one decimal place species rejects a value with two decimal places`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = oneDecimalPlaceSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "5.55"),
            )

        assertEquals(SpeciesWeightFieldError.Precision, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    @Test
    fun `one decimal place species accepts a whole number too`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = oneDecimalPlaceSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "5"),
            )

        assertTrue(result.isValid)
    }

    // --- Quota: monthly / annual ---------------------------------------------------------------

    @Test
    fun `entry that would exceed the monthly quota is rejected with a monthly quota error`() {
        val species = oneDecimalPlaceSpecies.copy(monthlyQuotaKg = 10.0, annualQuotaKg = 100.0)

        val result =
            SpeciesWeightValidator.validateEntry(
                species = species,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "11"),
                cumulativeOtherEntriesKg = 0.0,
            )

        assertEquals(
            SpeciesWeightFieldError.MonthlyQuotaExceeded,
            result.errors[SpeciesWeightFieldKind.AboveMinimumSize],
        )
    }

    @Test
    fun `entry that would exceed the annual quota but not the monthly quota is rejected with an annual quota error`() {
        val species = oneDecimalPlaceSpecies.copy(monthlyQuotaKg = null, annualQuotaKg = 10.0)

        val result =
            SpeciesWeightValidator.validateEntry(
                species = species,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "11"),
            )

        assertEquals(
            SpeciesWeightFieldError.AnnualQuotaExceeded,
            result.errors[SpeciesWeightFieldKind.AboveMinimumSize],
        )
    }

    @Test
    fun `quota check sums the cumulative other entries with the newly entered values`() {
        val species = oneDecimalPlaceSpecies.copy(monthlyQuotaKg = 10.0)

        val result =
            SpeciesWeightValidator.validateEntry(
                species = species,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "5"),
                cumulativeOtherEntriesKg = 6.0,
            )

        assertEquals(
            SpeciesWeightFieldError.MonthlyQuotaExceeded,
            result.errors[SpeciesWeightFieldKind.AboveMinimumSize],
        )
    }

    @Test
    fun `quota check applies the same error to every populated field`() {
        val species = oneDecimalPlaceSpecies.copy(monthlyQuotaKg = 10.0)

        val result =
            SpeciesWeightValidator.validateEntry(
                species = species,
                rawValuesByField =
                    mapOf(
                        SpeciesWeightFieldKind.AboveMinimumSize to "6",
                        SpeciesWeightFieldKind.BelowMinimumSize to "6",
                    ),
            )

        assertEquals(
            SpeciesWeightFieldError.MonthlyQuotaExceeded,
            result.errors[SpeciesWeightFieldKind.AboveMinimumSize],
        )
        assertEquals(
            SpeciesWeightFieldError.MonthlyQuotaExceeded,
            result.errors[SpeciesWeightFieldKind.BelowMinimumSize],
        )
    }

    @Test
    fun `no quota configured never yields a quota error`() {
        val species = oneDecimalPlaceSpecies.copy(monthlyQuotaKg = null, annualQuotaKg = null)

        val result =
            SpeciesWeightValidator.validateEntry(
                species = species,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "9000"),
                cumulativeOtherEntriesKg = 500.0,
            )

        assertTrue(result.isValid)
    }

    @Test
    fun `quota check is skipped entirely when applyQuotaCheck is false`() {
        val species = oneDecimalPlaceSpecies.copy(monthlyQuotaKg = 10.0)

        val result =
            SpeciesWeightValidator.validateEntry(
                species = species,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.KeptOnboard to "50"),
                mandatoryFields = setOf(SpeciesWeightFieldKind.KeptOnboard),
                cumulativeOtherEntriesKg = 100.0,
                applyQuotaCheck = false,
            )

        assertTrue(result.isValid)
        assertEquals(50.0, result.values[SpeciesWeightFieldKind.KeptOnboard])
    }

    @Test
    fun `quota check does not run when a field already failed range or precision validation`() {
        val species = oneDecimalPlaceSpecies.copy(monthlyQuotaKg = 1.0)

        val result =
            SpeciesWeightValidator.validateEntry(
                species = species,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "abc"),
            )

        assertEquals(SpeciesWeightFieldError.BelowMinimum, result.errors[SpeciesWeightFieldKind.AboveMinimumSize])
    }

    // --- Multiple fields / values map behaviour --------------------------------------------------

    @Test
    fun `only fields present in rawValuesByField are validated or returned`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = oneDecimalPlaceSpecies,
                rawValuesByField = mapOf(SpeciesWeightFieldKind.AboveMinimumSize to "5"),
            )

        assertTrue(SpeciesWeightFieldKind.BelowMinimumSize !in result.values)
        assertTrue(SpeciesWeightFieldKind.BelowMinimumSize !in result.errors)
    }

    @Test
    fun `values map is empty when any field has an error`() {
        val result =
            SpeciesWeightValidator.validateEntry(
                species = oneDecimalPlaceSpecies,
                rawValuesByField =
                    mapOf(
                        SpeciesWeightFieldKind.AboveMinimumSize to "5",
                        SpeciesWeightFieldKind.BelowMinimumSize to "not-a-number",
                    ),
            )

        assertTrue(result.values.isEmpty())
        assertTrue(!result.isValid)
    }
}
