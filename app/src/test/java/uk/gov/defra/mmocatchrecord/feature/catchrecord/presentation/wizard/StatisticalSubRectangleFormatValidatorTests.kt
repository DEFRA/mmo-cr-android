package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticalSubRectangleFormatValidatorTests {
    @Test
    fun `blank input yields a required error`() {
        val result = StatisticalSubRectangleFormatValidator.validate("   ")
        assertEquals(StatisticalSubRectangleInputError.Required, result.error)
        assertNull(result.code)
        assertEquals(false, result.isValid)
    }

    @Test
    fun `correctly formatted code is valid and normalised to uppercase`() {
        val result = StatisticalSubRectangleFormatValidator.validate("38e84")
        assertEquals("38E84", result.code)
        assertNull(result.error)
        assertEquals(true, result.isValid)
    }

    @Test
    fun `already uppercase code is valid unchanged`() {
        val result = StatisticalSubRectangleFormatValidator.validate("38E84")
        assertEquals("38E84", result.code)
        assertEquals(true, result.isValid)
    }

    @Test
    fun `code with surrounding whitespace is trimmed before validating`() {
        val result = StatisticalSubRectangleFormatValidator.validate("  38E84  ")
        assertEquals("38E84", result.code)
        assertEquals(true, result.isValid)
    }

    @Test
    fun `too few digits yields a format error`() {
        val result = StatisticalSubRectangleFormatValidator.validate("3E84")
        assertEquals(StatisticalSubRectangleInputError.Format, result.error)
        assertNull(result.code)
    }

    @Test
    fun `too many letters yields a format error`() {
        val result = StatisticalSubRectangleFormatValidator.validate("38EE84")
        assertEquals(StatisticalSubRectangleInputError.Format, result.error)
    }

    @Test
    fun `non alphanumeric characters yield a format error`() {
        val result = StatisticalSubRectangleFormatValidator.validate("38-84")
        assertEquals(StatisticalSubRectangleInputError.Format, result.error)
    }
}
