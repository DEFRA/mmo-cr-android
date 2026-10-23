package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapRectangleFormatValidatorTests {
    @Test
    fun `blank input yields a required error`() {
        val result = MapRectangleFormatValidator.validate("   ")
        assertEquals(MapRectangleInputError.Required, result.error)
        assertNull(result.code)
        assertEquals(false, result.isValid)
    }

    @Test
    fun `correctly formatted code is valid and normalised to uppercase`() {
        val result = MapRectangleFormatValidator.validate("38e84")
        assertEquals("38E84", result.code)
        assertNull(result.error)
        assertEquals(true, result.isValid)
    }

    @Test
    fun `already uppercase code is valid unchanged`() {
        val result = MapRectangleFormatValidator.validate("38E84")
        assertEquals("38E84", result.code)
        assertEquals(true, result.isValid)
    }

    @Test
    fun `code with surrounding whitespace is trimmed before validating`() {
        val result = MapRectangleFormatValidator.validate("  38E84  ")
        assertEquals("38E84", result.code)
        assertEquals(true, result.isValid)
    }

    @Test
    fun `too few digits yields a format error`() {
        val result = MapRectangleFormatValidator.validate("3E84")
        assertEquals(MapRectangleInputError.Format, result.error)
        assertNull(result.code)
    }

    @Test
    fun `too many letters yields a format error`() {
        val result = MapRectangleFormatValidator.validate("38EE84")
        assertEquals(MapRectangleInputError.Format, result.error)
    }

    @Test
    fun `non alphanumeric characters yield a format error`() {
        val result = MapRectangleFormatValidator.validate("38-84")
        assertEquals(MapRectangleInputError.Format, result.error)
    }

    @Test
    fun `a well-formatted code present in knownCodes is valid`() {
        val result = MapRectangleFormatValidator.validate("38e84", knownCodes = setOf("38E84"))
        assertEquals("38E84", result.code)
        assertNull(result.error)
    }

    @Test
    fun `a well-formatted code absent from knownCodes yields a not-found error`() {
        val result = MapRectangleFormatValidator.validate("99Z99", knownCodes = setOf("38E84"))
        assertEquals(MapRectangleInputError.NotFound, result.error)
        assertNull(result.code)
        assertEquals(false, result.isValid)
    }

    @Test
    fun `a null knownCodes skips the existence check entirely`() {
        val result = MapRectangleFormatValidator.validate("99Z99", knownCodes = null)
        assertEquals("99Z99", result.code)
        assertNull(result.error)
    }

    @Test
    fun `format errors take precedence over the not-found existence check`() {
        val result = MapRectangleFormatValidator.validate("not-a-code", knownCodes = setOf("38E84"))
        assertEquals(MapRectangleInputError.Format, result.error)
    }
}
