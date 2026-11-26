package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.connectivity.ConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.di.MonotonicClock
import uk.gov.defra.mmocatchrecord.core.di.PasswordResetStage
import uk.gov.defra.mmocatchrecord.core.di.ResetCodeLifetimeMinutes
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.BrowserContinuationResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ContinueResetInBrowserUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicy
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetToken
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.RequestPasswordResetUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.RequestResetOutcome
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.StartResetResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitCodeOutcome
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitCodeResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitNewPasswordOutcome
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitNewPasswordUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitPasswordResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitResetCodeUseCase
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The reset use cases, grouped so the view model stays within the constructor parameter limit. */
class ResetPasswordUseCases
    @Inject
    constructor(
        val requestReset: RequestPasswordResetUseCase,
        val submitCode: SubmitResetCodeUseCase,
        val submitNewPassword: SubmitNewPasswordUseCase,
        val continueInBrowser: ContinueResetInBrowserUseCase,
        val passwordPolicy: PasswordPolicy,
    )

/**
 * Drives the four reset screens, scoped to the reset graph (ADR 0027); the token and typed values
 * never reach UI state or logs.
 */
@HiltViewModel
@Suppress("TooManyFunctions")
class ResetPasswordViewModel
    @Inject
    constructor(
        private val useCases: ResetPasswordUseCases,
        connectivityObserver: ConnectivityObserver,
        private val connectivityChecker: NetworkConnectivityChecker,
        @PasswordResetStage private val stageObserver: SignInStageObserver,
        @MonotonicClock private val clock: () -> Long,
        @ResetCodeLifetimeMinutes codeLifetimeMinutes: Int,
        defaultDispatcher: CoroutineDispatcher,
    ) : BaseViewModel<ResetPasswordViewState, ResetPasswordEvent>(
            initialState =
                ResetPasswordViewState(
                    isOffline = !connectivityObserver.isOnline.value,
                    codeLifetimeMinutes = codeLifetimeMinutes,
                ),
            defaultDispatcher = defaultDispatcher,
        ) {
        val emailState = TextFieldState()
        val codeState = TextFieldState()
        val passwordPolicy: PasswordPolicy get() = useCases.passwordPolicy
        val newPasswordState = TextFieldState()
        val confirmPasswordState = TextFieldState()

        private val codeLifetimeMillis = TimeUnit.MINUTES.toMillis(codeLifetimeMinutes.toLong())

        @Volatile
        private var token: PasswordResetToken? = null

        @Volatile
        private var codeSentAtMillis = 0L

        private var activeJob: Job? = null

        init {
            launchInViewModelScope {
                connectivityObserver.isOnline.collect { online -> updateState { it.copy(isOffline = !online) } }
            }
            launchInViewModelScope {
                stageObserver.stage.collect { stage ->
                    updateState { it.copy(isRedirectingToBrowser = stage == SignInStage.RedirectingToBrowser) }
                }
            }
        }

        override fun dispatch(event: ResetPasswordEvent) {
            when (event) {
                ResetPasswordEvent.EmailSubmitted -> submitEmail()
                ResetPasswordEvent.CodeSubmitted -> submitCode()
                ResetPasswordEvent.NewPasswordSubmitted -> submitNewPassword()
                ResetPasswordEvent.SendNewCodeRequested -> sendNewCode()
                is ResetPasswordEvent.NewPasswordVisibilityChanged ->
                    updateState { it.copy(newPasswordVisible = event.visible) }
                is ResetPasswordEvent.ConfirmPasswordVisibilityChanged ->
                    updateState { it.copy(confirmPasswordVisible = event.visible) }
                ResetPasswordEvent.BrowserNoticeShown -> stageObserver.acknowledgeBrowserNoticeShown()
                ResetPasswordEvent.ConnectionRecheckRequested ->
                    updateState { it.copy(isOffline = !connectivityChecker.isConnected()) }
            }
        }

        override fun onCleared() {
            abandon()
        }

        /** Cancels any request and wipes the token, the timer and every typed value. */
        internal fun abandon() {
            activeJob?.cancel()
            clearReset()
        }

        private fun submitEmail() {
            if (!beginRequest(ResetStep.EmailEntry)) return
            val email = emailState.text.toString()
            launchRequest {
                when (val outcome = useCases.requestReset(email)) {
                    is RequestResetOutcome.Rejected ->
                        failFields(
                            listOf(ResetFieldError(ResetField.Email, outcome.error.messageRes())),
                        )
                    is RequestResetOutcome.Attempted -> onStartResult(outcome.result)
                }
            }
        }

        private suspend fun onStartResult(result: StartResetResult) {
            when (result) {
                // An unknown account lands here too, so the next screen is identical (AC3).
                is StartResetResult.CodeSent -> {
                    token = result.token
                    codeSentAtMillis = clock()
                    emailState.clearText()
                    codeState.clearText()
                    advanceTo(ResetStep.CodeEntry)
                }
                StartResetResult.BrowserRequired -> handOffToBrowser()
                is StartResetResult.Failed -> failPage(result.failure)
            }
        }

        private fun submitCode() {
            val current = token ?: return
            if (!beginRequest(ResetStep.CodeEntry)) return
            val code = codeState.text.toString()
            launchRequest {
                when (val outcome = useCases.submitCode(current, code)) {
                    is SubmitCodeOutcome.Rejected ->
                        failFields(
                            listOf(ResetFieldError(ResetField.Code, outcome.error.messageRes())),
                        )
                    is SubmitCodeOutcome.Attempted -> onCodeResult(outcome.result)
                }
            }
        }

        private suspend fun onCodeResult(result: SubmitCodeResult) {
            when (result) {
                is SubmitCodeResult.Accepted -> {
                    token = result.token
                    codeState.clearText()
                    advanceTo(ResetStep.NewPassword)
                }
                SubmitCodeResult.InvalidCode -> failCode(expired = isCodeExpired())
                SubmitCodeResult.Expired -> failCode(expired = true)
                SubmitCodeResult.BrowserRequired -> handOffToBrowser()
                is SubmitCodeResult.Failed -> failPage(result.failure)
            }
        }

        /** Entra cannot tell us a code expired, so a rejection after the lifetime is reported as expired (Q2). */
        private fun isCodeExpired(): Boolean = clock() - codeSentAtMillis > codeLifetimeMillis

        private fun failCode(expired: Boolean) {
            val message =
                if (expired) R.string.reset_password_code_error_expired else R.string.reset_password_code_error_invalid
            failFields(listOf(ResetFieldError(ResetField.Code, message)))
        }

        private fun submitNewPassword() {
            val current = token ?: return
            if (!beginRequest(ResetStep.NewPassword)) return
            val newPassword = newPasswordState.text.toCharArrayCopy()
            val confirmation = confirmPasswordState.text.toCharArrayCopy()
            launchRequest(onNotStarted = { wipe(newPassword, confirmation) }) {
                when (val outcome = useCases.submitNewPassword(current, newPassword, confirmation)) {
                    is SubmitNewPasswordOutcome.Rejected -> failFields(rejectedFieldErrors(outcome))
                    is SubmitNewPasswordOutcome.Attempted -> onPasswordResult(outcome.result)
                }
            }
        }

        private fun wipe(vararg secrets: CharArray) = secrets.forEach { it.fill('\u0000') }

        private fun rejectedFieldErrors(outcome: SubmitNewPasswordOutcome.Rejected): List<ResetFieldError> =
            listOfNotNull(
                outcome.newPassword?.let {
                    ResetFieldError(ResetField.NewPassword, it.messageRes(), it.formatArgs(useCases.passwordPolicy))
                },
                outcome.confirmation?.let { ResetFieldError(ResetField.ConfirmPassword, it.messageRes()) },
            )

        private suspend fun onPasswordResult(result: SubmitPasswordResult) {
            when (result) {
                SubmitPasswordResult.Reset -> {
                    clearReset()
                    advanceTo(ResetStep.Success)
                }
                SubmitPasswordResult.PasswordRejected ->
                    failFields(
                        listOf(ResetFieldError(ResetField.NewPassword, R.string.reset_password_new_error_requirements)),
                    )
                SubmitPasswordResult.Expired -> restartWithPageError(R.string.reset_password_error_session_expired)
                SubmitPasswordResult.BrowserRequired -> handOffToBrowser()
                is SubmitPasswordResult.Failed -> {
                    clearPasswords()
                    failPage(result.failure)
                }
            }
        }

        private suspend fun handOffToBrowser() {
            Timber.i("Password reset needs the browser fallback")
            when (val result = useCases.continueInBrowser()) {
                is BrowserContinuationResult.SignedIn -> {
                    clearReset()
                    updateState { it.copy(isLoading = false, signedInViaBrowser = true) }
                }
                BrowserContinuationResult.Cancelled -> updateState { it.copy(isLoading = false) }
                is BrowserContinuationResult.Failed -> failPage(result.failure)
            }
        }

        private fun sendNewCode() {
            activeJob?.cancel()
            clearReset()
            updateState {
                it.copy(
                    step = ResetStep.EmailEntry,
                    fieldErrors = emptyList(),
                    pageErrorRes = null,
                    isLoading = false,
                    newPasswordVisible = false,
                    confirmPasswordVisible = false,
                )
            }
            Timber.d("Password reset restarted from a blank email screen")
        }

        private fun restartWithPageError(
            @StringRes messageRes: Int,
        ) {
            clearReset()
            updateState {
                it.copy(
                    step = ResetStep.EmailEntry,
                    fieldErrors = emptyList(),
                    pageErrorRes = messageRes,
                    errorSequence = it.errorSequence + 1,
                    isLoading = false,
                    newPasswordVisible = false,
                    confirmPasswordVisible = false,
                )
            }
        }

        /** Accepts a request only on the right step, when online and when no other request is in flight. */
        private fun beginRequest(step: ResetStep): Boolean {
            val snapshot = currentState
            if (snapshot.isLoading || snapshot.isOffline || snapshot.step != step) return false
            updateState { it.copy(isLoading = true, fieldErrors = emptyList(), pageErrorRes = null) }
            return true
        }

        private fun launchRequest(
            onNotStarted: () -> Unit = {},
            block: suspend () -> Unit,
        ) {
            val job = viewModelScope.launch(defaultDispatcher, start = CoroutineStart.ATOMIC) { block() }
            job.invokeOnCompletion { cause -> if (cause is CancellationException) onNotStarted() }
            activeJob = job
        }

        private fun advanceTo(step: ResetStep) {
            Timber.d("Password reset moved to %s", step::class.simpleName)
            updateState {
                it.copy(
                    step = step,
                    fieldErrors = emptyList(),
                    pageErrorRes = null,
                    isLoading = false,
                    newPasswordVisible = false,
                    confirmPasswordVisible = false,
                )
            }
        }

        private fun failFields(errors: List<ResetFieldError>) {
            updateState {
                it.copy(
                    isLoading = false,
                    fieldErrors = errors,
                    pageErrorRes = null,
                    errorSequence = it.errorSequence + 1,
                )
            }
        }

        private fun failPage(failure: PasswordResetFailure) {
            Timber.w("Password reset step failed: %s", failure.logName())
            updateState {
                it.copy(
                    isLoading = false,
                    fieldErrors = emptyList(),
                    pageErrorRes = failure.messageRes(),
                    errorSequence = it.errorSequence + 1,
                )
            }
        }

        private fun clearPasswords() {
            newPasswordState.clearText()
            confirmPasswordState.clearText()
        }

        private fun clearReset() {
            token = null
            codeSentAtMillis = 0L
            emailState.clearText()
            codeState.clearText()
            clearPasswords()
        }

        /** Copies without building a `String`, so the password is not duplicated in immutable memory. */
        private fun CharSequence.toCharArrayCopy(): CharArray = CharArray(length) { this[it] }
    }
