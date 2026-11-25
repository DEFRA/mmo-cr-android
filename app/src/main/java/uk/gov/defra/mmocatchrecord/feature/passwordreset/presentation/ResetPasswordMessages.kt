package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.annotation.StringRes
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ConfirmPasswordError
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.EmailAddressError
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordRuleFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ResetCodeError

/** Pure mappings from domain outcomes to unresolved copy; kept out of the view model so they test to 100%. */
@StringRes
internal fun EmailAddressError.messageRes(): Int =
    when (this) {
        EmailAddressError.Blank -> R.string.reset_password_email_error_blank
        EmailAddressError.InvalidFormat -> R.string.reset_password_email_error_format
    }

@StringRes
internal fun ResetCodeError.messageRes(): Int =
    when (this) {
        ResetCodeError.Blank -> R.string.reset_password_code_error_blank
    }

/** Every rule failure shows the same requirements message; the hint above the field explains the rules. */
@StringRes
internal fun PasswordRuleFailure.messageRes(): Int =
    when (this) {
        PasswordRuleFailure.Blank -> R.string.reset_password_new_error_blank
        PasswordRuleFailure.TooShort,
        PasswordRuleFailure.TooLong,
        PasswordRuleFailure.TooFewCategories,
        -> R.string.reset_password_new_error_requirements
    }

@StringRes
internal fun ConfirmPasswordError.messageRes(): Int =
    when (this) {
        ConfirmPasswordError.Blank -> R.string.reset_password_confirm_error_blank
        ConfirmPasswordError.Mismatch -> R.string.reset_password_confirm_error_mismatch
    }

@StringRes
internal fun PasswordResetFailure.messageRes(): Int =
    when (this) {
        PasswordResetFailure.Network -> R.string.reset_password_error_network
        PasswordResetFailure.Throttled -> R.string.reset_password_error_throttled
        is PasswordResetFailure.Unknown -> R.string.reset_password_error_generic
    }

/** Class name plus the mapped-constant diagnostic for Unknown; never SDK text (ADR 0027). */
internal fun PasswordResetFailure.logName(): String =
    when (this) {
        is PasswordResetFailure.Unknown -> "Unknown:$safeDiagnostic"
        else -> this::class.simpleName ?: "PasswordResetFailure"
    }
