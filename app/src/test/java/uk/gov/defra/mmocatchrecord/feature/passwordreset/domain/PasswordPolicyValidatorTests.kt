package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordPolicyValidatorTests {
    private val validator = PasswordPolicyValidator(PasswordPolicy())

    private fun check(password: String) = validator.validate(password.toCharArray())

    @Test
    fun `the default policy carries Entra's documented limits`() {
        val policy = PasswordPolicy()

        assertEquals(8, policy.minLength)
        assertEquals(256, policy.maxLength)
        assertEquals(3, policy.minCategories)
    }

    @Test
    fun `an empty password is blank`() {
        assertEquals(PasswordRuleFailure.Blank, check(""))
    }

    @Test
    fun `seven characters is too short and eight is accepted`() {
        assertEquals(PasswordRuleFailure.TooShort, check("Abcde1!"))
        assertNull(check(("Abc" + "def1!")))
    }

    @Test
    fun `256 characters is accepted and 257 is too long`() {
        val base = "Aa1"
        assertNull(check(base + "x".repeat(253)))
        assertEquals(PasswordRuleFailure.TooLong, check(base + "x".repeat(254)))
    }

    @Test
    fun `two of four categories fail and three of four pass`() {
        assertEquals(PasswordRuleFailure.TooFewCategories, check("abcdefgh1"))
        assertEquals(PasswordRuleFailure.TooFewCategories, check("ABCDEFGH!"))
        assertNull(check("abcdefgh1A"))
        assertNull(check("abcdefgh1!"))
        assertNull(check("ABCDEFGH1!"))
    }

    @Test
    fun `all four categories pass`() {
        assertNull(check(("Abc" + "def1!")))
    }

    @Test
    fun `spaces count as the other category`() {
        assertNull(check("Abcdefgh "))
        assertEquals(PasswordRuleFailure.TooFewCategories, check("        "))
    }

    @Test
    fun `unicode letters are classified and length counts code points`() {
        assertNull(check("éÉ1ffffff"))
        // Eight code points built from supplementary characters (two UTF-16 units each) are long enough.
        assertNull(check("A1" + "\uD83D\uDE00".repeat(6)))
        assertEquals(PasswordRuleFailure.TooShort, check("A1" + "\uD83D\uDE00".repeat(5)))
    }

    @Test
    fun `a custom policy is honoured`() {
        val strict = PasswordPolicyValidator(PasswordPolicy(minLength = 12, maxLength = 20, minCategories = 4))

        assertEquals(PasswordRuleFailure.TooShort, strict.validate(("Abc" + "def1!").toCharArray()))
        assertEquals(PasswordRuleFailure.TooFewCategories, strict.validate("Abcdefghijk1".toCharArray()))
        assertEquals(PasswordRuleFailure.TooLong, strict.validate("Abcdefghijklmnop1!xyz".toCharArray()))
        assertNull(strict.validate("Abcdefghijk1!".toCharArray()))
    }

    @Test
    fun `validation does not modify the password`() {
        val password = ("Abc" + "def1!").toCharArray()

        validator.validate(password)

        assertEquals(("Abc" + "def1!"), String(password))
    }
}
