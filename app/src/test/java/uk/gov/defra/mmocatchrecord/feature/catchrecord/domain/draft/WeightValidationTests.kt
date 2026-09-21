package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult

class WeightValidationTests {
    @Test
    fun `weight below 10kg in 0point1kg increments is valid`() {
        assertEquals(ValidationResult.Valid, WeightValidation.validatePrecision(0.1, "Weight"))
        assertEquals(ValidationResult.Valid, WeightValidation.validatePrecision(9.9, "Weight"))
        assertEquals(ValidationResult.Valid, WeightValidation.validatePrecision(0.0, "Weight"))
    }

    @Test
    fun `weight below 10kg not on a 0point1kg increment is invalid`() {
        val result = WeightValidation.validatePrecision(1.23, "Weight")

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            "Weight must be recorded in 0.1kg increments below 10kg",
            (result as ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `weight at or above 10kg must be a whole kilogram`() {
        assertEquals(ValidationResult.Valid, WeightValidation.validatePrecision(10.0, "Weight"))
        assertEquals(ValidationResult.Valid, WeightValidation.validatePrecision(250.0, "Weight"))
    }

    @Test
    fun `weight at or above 10kg with a fractional value is invalid`() {
        val result = WeightValidation.validatePrecision(10.5, "Weight")

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            "Weight must be recorded in whole kilograms at or above 10kg",
            (result as ValidationResult.Invalid).reason,
        )
    }

    @Test
    fun `negative weight is invalid`() {
        val result = WeightValidation.validatePrecision(-0.1, "Weight")

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `discarded weight of zero is allowed alongside a valid entry`() {
        val failures =
            WeightValidation.validateEntry(
                retainedAboveMcrsKg = 5.0,
                retainedBelowMcrsKg = 0.0,
                discardedKg = 0.0,
            )

        assertTrue(failures.isEmpty())
    }

    @Test
    fun `entry with only retained below MCRS greater than zero is valid`() {
        val failures =
            WeightValidation.validateEntry(
                retainedAboveMcrsKg = 0.0,
                retainedBelowMcrsKg = 2.5,
                discardedKg = 1.0,
            )

        assertTrue(failures.isEmpty())
    }

    @Test
    fun `entry with both retained categories at zero is rejected`() {
        val failures =
            WeightValidation.validateEntry(
                retainedAboveMcrsKg = 0.0,
                retainedBelowMcrsKg = 0.0,
                discardedKg = 3.0,
            )

        assertTrue(failures.isNotEmpty())
        assertTrue(
            failures.any {
                it.reason ==
                    "At least one of retained above MCRS or retained below MCRS weight must be greater than zero"
            },
        )
    }

    @Test
    fun `entry with an invalid precision weight surfaces that failure alongside other checks`() {
        val failures =
            WeightValidation.validateEntry(
                retainedAboveMcrsKg = 1.23,
                retainedBelowMcrsKg = 0.0,
                discardedKg = 0.0,
            )

        assertTrue(failures.any { it.reason.contains("Retained above MCRS weight") })
    }
}
