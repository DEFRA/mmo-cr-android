package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession

/** Immutable UI state for [SignInViewModel]. */
data class SignInViewState(
    val username: String = "",
    val password: String = "",
    val status: UiStatus<SignInSession> = UiStatus.Idle,
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
