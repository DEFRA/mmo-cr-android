package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.annotation.StringRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInUseCase
import javax.inject.Inject

/**
 * ViewModel for the sign-in feature. Delegates validation + authentication to [SignInUseCase]; never
 * calls a repository directly.
 */
@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val signInUseCase: SignInUseCase,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<SignInViewState, SignInEvent>(
            initialState = SignInViewState(),
            defaultDispatcher = defaultDispatcher,
        ) {
        override fun dispatch(event: SignInEvent) {
            when (event) {
                is SignInEvent.UsernameChanged -> updateState { it.copy(username = event.value) }
                is SignInEvent.PasswordChanged -> updateState { it.copy(password = event.value) }
                SignInEvent.SubmitRequested -> submit()
            }
        }

        private fun submit() {
            val username = currentState.username
            val password = currentState.password
            updateState { it.copy(status = UiStatus.Loading, error = null) }

            launchInViewModelScope {
                val result = signInUseCase(username, password)
                result.fold(
                    onSuccess = { session ->
                        updateState { it.copy(status = UiStatus.Content(session), error = null) }
                    },
                    onFailure = { error ->
                        updateState { it.copy(status = UiStatus.Idle, error = signInError(error)) }
                    },
                )
            }
        }

        /** Carries only a resource id — neither [Throwable.message] nor a resolved string reaches the state. */
        private fun signInError(error: Throwable): SignInError {
            val failure = (error as? SignInException)?.failure ?: SignInFailure.Unknown(UNMAPPED_FAILURE)
            Timber.w("Sign-in failed: %s", failure.diagnostic())
            return SignInError(
                messageRes = failure.messageRes(),
                // Only a transport failure is worth retrying unchanged; the rest are terminal (auth/validation).
                isRetryable = failure is SignInFailure.Network,
            )
        }

        @StringRes
        private fun SignInFailure.messageRes(): Int =
            when (this) {
                SignInFailure.InvalidCredentials -> R.string.signin_error_invalid_credentials
                SignInFailure.Network -> R.string.signin_error_network
                is SignInFailure.Unknown -> R.string.signin_error_generic
            }

        /** Class name only: [SignInFailure.Unknown] is free text an MSAL error could fill with a username. */
        private fun SignInFailure.diagnostic(): String = this::class.simpleName ?: "SignInFailure"

        private companion object {
            const val UNMAPPED_FAILURE = "repository returned a non-SignInException failure"
        }
    }
