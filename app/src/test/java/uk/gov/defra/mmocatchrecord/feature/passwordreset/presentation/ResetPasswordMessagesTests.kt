package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ConfirmPasswordError
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.EmailAddressError
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordRuleFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ResetCodeError

class ResetPasswordMessagesTests {
    @Test
    fun `every email error maps to its own message`() {
        assertEquals(R.string.reset_password_email_error_blank, EmailAddressError.Blank.messageRes())
        assertEquals(R.string.reset_password_email_error_format, EmailAddressError.InvalidFormat.messageRes())
    }

    @Test
    fun `a blank code maps to the code message`() {
        assertEquals(R.string.reset_password_code_error_blank, ResetCodeError.Blank.messageRes())
    }

    @Test
    fun `password rule failures share the requirements message except blank`() {
        assertEquals(R.string.reset_password_new_error_blank, PasswordRuleFailure.Blank.messageRes())
        listOf(
            PasswordRuleFailure.TooShort,
            PasswordRuleFailure.TooLong,
            PasswordRuleFailure.TooFewCategories,
        ).forEach { assertEquals(R.string.reset_password_new_error_requirements, it.messageRes()) }
    }

    @Test
    fun `confirm errors map to their own messages`() {
        assertEquals(R.string.reset_password_confirm_error_blank, ConfirmPasswordError.Blank.messageRes())
        assertEquals(R.string.reset_password_confirm_error_mismatch, ConfirmPasswordError.Mismatch.messageRes())
    }

    @Test
    fun `failures map to network, throttled and generic copy`() {
        assertEquals(R.string.reset_password_error_network, PasswordResetFailure.Network.messageRes())
        assertEquals(R.string.reset_password_error_throttled, PasswordResetFailure.Throttled.messageRes())
        assertEquals(R.string.reset_password_error_generic, PasswordResetFailure.Unknown("detail").messageRes())
    }

    @Test
    fun `log names are class names and never carry failure detail`() {
        assertEquals("Network", PasswordResetFailure.Network.logName())
        assertEquals("Unknown:mapped_constant", PasswordResetFailure.Unknown("mapped_constant").logName())
    }
}
