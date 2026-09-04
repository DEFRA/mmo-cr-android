package uk.gov.defra.mmocatchrecord.core.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure [ValidationHelper] rule tests, exercised via a "select vessel" style form (vessel name required,
 * bounded length; catch weight must be positive). No mocks/Android dependencies — plain JUnit.
 */
class SelectVesselValidationTests {
    @Test
    fun `vessel name blank is invalid`() {
        val result = ValidationHelper.requireNotBlank("", "Vessel name")

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            "Vessel name must not be empty",
            (result as ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `vessel name whitespace only is invalid`() {
        val result = ValidationHelper.requireNotBlank("   ", "Vessel name")

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `vessel name with content is valid`() {
        val result = ValidationHelper.requireNotBlank("Northern Star", "Vessel name")

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `vessel name within max length is valid`() {
        val result = ValidationHelper.maxLength("Northern Star", maxLength = 50, fieldName = "Vessel name")

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `vessel name exceeding max length is invalid`() {
        val longName = "N".repeat(51)

        val result = ValidationHelper.maxLength(longName, maxLength = 50, fieldName = "Vessel name")

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            "Vessel name must be 50 characters or fewer",
            (result as ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `vessel name at exact max length is valid`() {
        val exactName = "N".repeat(50)

        val result = ValidationHelper.maxLength(exactName, maxLength = 50, fieldName = "Vessel name")

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `catch weight greater than zero is valid`() {
        val result = ValidationHelper.requirePositive(12.5, "Catch weight")

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `catch weight of zero is invalid`() {
        val result = ValidationHelper.requirePositive(0.0, "Catch weight")

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            "Catch weight must be greater than zero",
            (result as ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `negative catch weight is invalid`() {
        val result = ValidationHelper.requirePositive(-3.0, "Catch weight")

        assertTrue(result is ValidationResult.Invalid)
    }
}
