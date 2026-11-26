package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.annotation.StringRes
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState

/** The four screens of the reset journey, in order. Typed values and the token are never held here. */
sealed interface ResetStep {
    data object EmailEntry : ResetStep

    data object CodeEntry : ResetStep

    data object NewPassword : ResetStep

    data object Success : ResetStep
}

/** Fields that can show an inline error; also the focus targets of the error summary links. */
enum class ResetField { Email, Code, NewPassword, ConfirmPassword }

/** One inline error: the field it belongs to, its unresolved copy and any numeric format arguments. */
data class ResetFieldError(
    val field: ResetField,
    @StringRes val messageRes: Int,
    val formatArgs: List<Int> = emptyList(),
)

/**
 * Immutable UI state for the reset journey; holds no email, code, password or token (ADR 0027).
 * [errorSequence] re-focuses the error summary on a repeat error.
 */
data class ResetPasswordViewState(
    val step: ResetStep = ResetStep.EmailEntry,
    val fieldErrors: List<ResetFieldError> = emptyList(),
    @StringRes val pageErrorRes: Int? = null,
    val errorSequence: Int = 0,
    val isLoading: Boolean = false,
    val newPasswordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val isOffline: Boolean = false,
    val isRedirectingToBrowser: Boolean = false,
    val signedInViaBrowser: Boolean = false,
    val codeLifetimeMinutes: Int = 0,
) : ViewState

/** Events the screens send to [ResetPasswordViewModel]. */
sealed interface ResetPasswordEvent {
    data object EmailSubmitted : ResetPasswordEvent

    data object CodeSubmitted : ResetPasswordEvent

    data object NewPasswordSubmitted : ResetPasswordEvent

    data object SendNewCodeRequested : ResetPasswordEvent

    data class NewPasswordVisibilityChanged(
        val visible: Boolean,
    ) : ResetPasswordEvent

    data class ConfirmPasswordVisibilityChanged(
        val visible: Boolean,
    ) : ResetPasswordEvent

    data object BrowserNoticeShown : ResetPasswordEvent

    data object ConnectionRecheckRequested : ResetPasswordEvent
}
