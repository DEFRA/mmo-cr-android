package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResetCodeValidatorTests {
    private val validator = ResetCodeValidator()

    @Test
    fun `blank and whitespace-only codes are reported as blank`() {
        assertEquals(ResetCodeError.Blank, validator.validate(""))
        assertEquals(ResetCodeError.Blank, validator.validate(" \t\n"))
    }

    @Test
    fun `any non-blank code is accepted with no length check`() {
        assertNull(validator.validate("1"))
        assertNull(validator.validate("12345678"))
        assertNull(validator.validate("1".repeat(40)))
    }

    @Test
    fun `normalise removes all whitespace`() {
        assertEquals("12345678", validator.normalise(" 1234 5678\n"))
        assertEquals("", validator.normalise("   "))
    }
}
