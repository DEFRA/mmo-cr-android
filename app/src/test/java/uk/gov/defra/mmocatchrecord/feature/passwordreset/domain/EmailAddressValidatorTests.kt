package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailAddressValidatorTests {
    private val validator = EmailAddressValidator()

    @Test
    fun `blank and whitespace are reported as blank`() {
        assertEquals(EmailAddressError.Blank, validator.validate(""))
        assertEquals(EmailAddressError.Blank, validator.validate("   \t"))
    }

    @Test
    fun `well formed addresses are accepted, including surrounding whitespace`() {
        assertNull(validator.validate("name@example.com"))
        assertNull(validator.validate("  first.last+tag@sub.example.co.uk  "))
    }

    @Test
    fun `malformed addresses are reported as invalid format`() {
        listOf(
            "plainaddress",
            "@example.com",
            "name@",
            "name@example",
            "name@@example.com",
            "na me@example.com",
            "name@.com",
            "name@example.",
            "name@exa..mple.com",
            "a@b@example.com",
        ).forEach { assertEquals(it, EmailAddressError.InvalidFormat, validator.validate(it)) }
    }

    @Test
    fun `an address longer than 254 characters is invalid`() {
        val tooLong = "a".repeat(250) + "@b.co"

        assertEquals(EmailAddressError.InvalidFormat, validator.validate(tooLong))
    }
}
