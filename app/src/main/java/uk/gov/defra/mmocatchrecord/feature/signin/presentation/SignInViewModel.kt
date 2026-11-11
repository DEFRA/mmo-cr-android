package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.annotation.StringRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInUseCase
import javax.inject.Inject

/** No-op default so existing call sites need not pass one; dev/prod get the real observer via Hilt. */
private object NoOpSignInStageObserver : SignInStageObserver {
    override val stage: StateFlow<SignInStage> = MutableStateFlow(SignInStage.Idle)
}

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
        private val signInStageObserver: SignInStageObserver = NoOpSignInStageObserver,
    ) : BaseViewModel<SignInViewState, SignInEvent>(
            initialState = SignInViewState(),
            defaultDispatcher = defaultDispatcher,
        ) {
        init {
            launchInViewModelScope {
                signInStageObserver.stage.collect { stage ->
                    updateState { it.copy(isRedirectingToBrowser = stage == SignInStage.RedirectingToBrowser) }
                }
            }
        }

        override fun dispatch(event: SignInEvent) {
            when (event) {
                is SignInEvent.UsernameChanged -> updateState { it.copy(username = event.value) }
                is SignInEvent.PasswordChanged -> updateState { it.copy(password = event.value) }
                SignInEvent.SubmitRequested -> submit()
                SignInEvent.BrowserNoticeShown -> signInStageObserver.acknowledgeBrowserNoticeShown()
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
        private fun signInError(error: Throwable): SignInError? {
            val failure = (error as? SignInException)?.failure ?: SignInFailure.Unknown(UNMAPPED_FAILURE)
            // A user-cancelled browser hand-off is not an error: no message, no stuck spinner either way.
            if (failure is SignInFailure.Cancelled) {
                Timber.i("Sign-in cancelled by the user during the browser hand-off")
                return null
            }
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
                SignInFailure.VerificationNotSupported -> R.string.signin_error_verification_not_supported
                // Unreachable: signInError() returns null for Cancelled before this is ever called.
                SignInFailure.Cancelled -> R.string.signin_error_generic
                is SignInFailure.Unknown -> R.string.signin_error_generic
            }

        /** Class name only: [SignInFailure.Unknown] is free text an MSAL error could fill with a username. */
        private fun SignInFailure.diagnostic(): String = this::class.simpleName ?: "SignInFailure"

        private companion object {
            const val UNMAPPED_FAILURE = "repository returned a non-SignInException failure"
        }
    }
