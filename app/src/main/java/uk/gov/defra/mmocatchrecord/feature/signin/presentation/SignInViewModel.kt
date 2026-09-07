package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
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
        updateState { it.copy(status = UiStatus.Loading) }

        launchInViewModelScope {
            val result = signInUseCase(username, password)
            result.fold(
                onSuccess = { session ->
                    updateState { it.copy(status = UiStatus.Content(session)) }
                },
                onFailure = { error ->
                    updateState {
                        it.copy(
                            status =
                                UiStatus.Error(
                                    message = error.message ?: "Sign in failed. Please try again.",
                                    isRetryable = true,
                                ),
                        )
                    }
                },
            )
        }
    }
}
