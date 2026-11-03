package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.annotation.StringRes
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession

/** Unresolved error copy: resolved in composition so the in-app language toggle re-resolves it (ADR 0012). */
data class SignInError(
    @param:StringRes val messageRes: Int,
    val isRetryable: Boolean,
)

/** Immutable UI state for [SignInViewModel]. A non-null [error] always pairs with [UiStatus.Idle]. */
data class SignInViewState(
    val username: String = "",
    val password: String = "",
    val status: UiStatus<SignInSession> = UiStatus.Idle,
    val error: SignInError? = null,
) : ViewState

/** UI-originated events for the sign-in screen. */
sealed interface SignInEvent {
    data class UsernameChanged(
        val value: String,
    ) : SignInEvent

    data class PasswordChanged(
        val value: String,
    ) : SignInEvent

    data object SubmitRequested : SignInEvent
}
