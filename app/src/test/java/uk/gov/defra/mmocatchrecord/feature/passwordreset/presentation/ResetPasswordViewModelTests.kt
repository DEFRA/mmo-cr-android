package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.connectivity.FakeConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.BrowserContinuationResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ContinueResetInBrowserUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.EmailAddressValidator
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicy
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicyValidator
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetToken
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.RecordingPasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.RequestPasswordResetUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.ResetCodeValidator
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.StartResetResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitCodeResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitNewPasswordUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitPasswordResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitResetCodeUseCase
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.TEST_SESSION
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.TestResetToken
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import java.util.concurrent.TimeUnit

private const val EMAIL = "sentinel.user@example.com"
private const val CODE = "246810"
private const val PASSWORD = "Sup3rSecret!pw"
private val LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(5)

private class TestStageObserver : SignInStageObserver {
    override val stage: MutableStateFlow<SignInStage> = MutableStateFlow(SignInStage.Idle)
    var acknowledgements = 0

    override fun acknowledgeBrowserNoticeShown() {
        acknowledgements++
    }
}

/** Repository whose first call suspends until [gate] opens, to hold a request in flight. */
private class GatedRepository : PasswordResetRepository {
    val gate = CompletableDeferred<Unit>()
    var startCalls = 0

    override suspend fun start(email: String): StartResetResult {
        startCalls++
        gate.await()
        return StartResetResult.CodeSent(TestResetToken())
    }

    override suspend fun submitCode(
        token: PasswordResetToken,
        code: String,
    ): SubmitCodeResult = error("not used")

    override suspend fun submitPassword(
        token: PasswordResetToken,
        password: CharArray,
    ): SubmitPasswordResult = error("not used")

    override suspend fun continueInBrowser(): BrowserContinuationResult = error("not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions")
class ResetPasswordViewModelTests {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = RecordingPasswordResetRepository()
    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val stageObserver = TestStageObserver()
    private var connected = true
    private var now = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Timber.uprootAll()
    }

    private fun viewModel(
        repo: PasswordResetRepository = repository,
        lifetimeMinutes: Int = 5,
    ) = ResetPasswordViewModel(
        useCases =
            ResetPasswordUseCases(
                RequestPasswordResetUseCase(repo, EmailAddressValidator()),
                SubmitResetCodeUseCase(repo, ResetCodeValidator()),
                SubmitNewPasswordUseCase(repo, PasswordPolicyValidator(PasswordPolicy())),
                ContinueResetInBrowserUseCase(repo),
                PasswordPolicy(),
            ),
        connectivityObserver = connectivity,
        connectivityChecker = NetworkConnectivityChecker { connected },
        stageObserver = stageObserver,
        clock = { now },
        codeLifetimeMinutes = lifetimeMinutes,
        defaultDispatcher = dispatcher,
    )

    private fun TextFieldState.type(value: String) = setTextAndPlaceCursorAtEnd(value)

    private fun ResetPasswordViewModel.sendEmail(email: String = EMAIL) {
        emailState.type(email)
        dispatch(ResetPasswordEvent.EmailSubmitted)
    }

    private fun ResetPasswordViewModel.sendCode(code: String = CODE) {
        codeState.type(code)
        dispatch(ResetPasswordEvent.CodeSubmitted)
    }

    private fun ResetPasswordViewModel.sendPasswords(
        password: String = PASSWORD,
        confirmation: String = password,
    ) {
        newPasswordState.type(password)
        confirmPasswordState.type(confirmation)
        dispatch(ResetPasswordEvent.NewPasswordSubmitted)
    }

    private fun ResetPasswordViewModel.atCodeStep() = apply { sendEmail() }

    private fun ResetPasswordViewModel.atNewPasswordStep() =
        apply {
            sendEmail()
            sendCode()
        }

    private val ResetPasswordViewModel.current get() = state.value

    // ---- Email step (AC1, AC2, AC3) ----

    @Test
    fun `starts on a blank email step with no errors`() {
        val viewModel = viewModel()

        assertEquals(ResetStep.EmailEntry, viewModel.current.step)
        assertTrue(viewModel.current.fieldErrors.isEmpty())
        assertNull(viewModel.current.pageErrorRes)
        assertEquals("", viewModel.emailState.text.toString())
    }

    @Test
    fun `a blank email shows the blank error and does not call the service`() {
        val viewModel = viewModel()

        viewModel.sendEmail("")

        assertEquals(
            listOf(ResetFieldError(ResetField.Email, R.string.reset_password_email_error_blank)),
            viewModel.current.fieldErrors,
        )
        assertEquals(1, viewModel.current.errorSequence)
        assertTrue(repository.startedEmails.isEmpty())
        assertFalse(viewModel.current.isLoading)
    }

    @Test
    fun `a malformed email shows the format error and does not call the service`() {
        val viewModel = viewModel()

        viewModel.sendEmail("not-an-email")

        assertEquals(
            R.string.reset_password_email_error_format,
            viewModel.current.fieldErrors
                .single()
                .messageRes,
        )
        assertTrue(repository.startedEmails.isEmpty())
    }

    @Test
    fun `a valid email moves to the code step and clears the email`() {
        val viewModel = viewModel()

        viewModel.sendEmail()

        assertEquals(ResetStep.CodeEntry, viewModel.current.step)
        assertEquals(listOf(EMAIL), repository.startedEmails)
        assertEquals("", viewModel.emailState.text.toString())
        assertFalse(viewModel.current.isLoading)
    }

    @Test
    fun `any email leads to an identical code step state so an account cannot be probed`() {
        val known = viewModel().atCodeStep()
        val unknown = viewModel()

        unknown.sendEmail("nobody.here@example.org")

        assertEquals(known.current, unknown.current)
    }

    @Test
    fun `state exposes no sent-to address and no code length`() {
        val viewModel = viewModel().atCodeStep()

        assertFalse(viewModel.current.toString().contains(EMAIL))
        assertFalse(viewModel.current.toString().contains("sentTo", ignoreCase = true))
        assertFalse(viewModel.current.toString().contains("codeLength", ignoreCase = true))
    }

    // ---- Code step (AC4 to AC9) ----

    @Test
    fun `a blank code shows the blank error without calling the service`() {
        val viewModel = viewModel().atCodeStep()

        viewModel.sendCode("   ")

        assertEquals(
            R.string.reset_password_code_error_blank,
            viewModel.current.fieldErrors
                .single()
                .messageRes,
        )
        assertTrue(repository.submittedCodes.isEmpty())
    }

    @Test
    fun `a wrong code inside the lifetime shows the incorrect error and stays on the step`() {
        repository.submitCodeResult = SubmitCodeResult.InvalidCode
        val viewModel = viewModel().atCodeStep()
        now = LIFETIME_MILLIS

        viewModel.sendCode()

        assertEquals(ResetStep.CodeEntry, viewModel.current.step)
        assertEquals(
            R.string.reset_password_code_error_invalid,
            viewModel.current.fieldErrors
                .single()
                .messageRes,
        )
    }

    @Test
    fun `a rejected code after the lifetime is reported as expired`() {
        repository.submitCodeResult = SubmitCodeResult.InvalidCode
        val viewModel = viewModel().atCodeStep()
        now = LIFETIME_MILLIS + 1

        viewModel.sendCode()

        assertEquals(
            R.string.reset_password_code_error_expired,
            viewModel.current.fieldErrors
                .single()
                .messageRes,
        )
    }

    @Test
    fun `an explicit expired result shows the expired error`() {
        repository.submitCodeResult = SubmitCodeResult.Expired
        val viewModel = viewModel().atCodeStep()

        viewModel.sendCode()

        assertEquals(
            R.string.reset_password_code_error_expired,
            viewModel.current.fieldErrors
                .single()
                .messageRes,
        )
    }

    @Test
    fun `the code lifetime comes from the injected configuration`() {
        repository.submitCodeResult = SubmitCodeResult.InvalidCode
        val viewModel = viewModel(lifetimeMinutes = 10).atCodeStep()
        now = LIFETIME_MILLIS + 1

        viewModel.sendCode()

        assertEquals(10, viewModel.current.codeLifetimeMinutes)
        assertEquals(
            R.string.reset_password_code_error_invalid,
            viewModel.current.fieldErrors
                .single()
                .messageRes,
        )
    }

    @Test
    fun `an accepted code moves to the new password step and clears the code`() {
        val viewModel = viewModel().atCodeStep()

        viewModel.sendCode()

        assertEquals(ResetStep.NewPassword, viewModel.current.step)
        assertEquals(listOf(CODE), repository.submittedCodes)
        assertEquals("", viewModel.codeState.text.toString())
    }

    @Test
    fun `a code with pasted whitespace is normalised before it is sent`() {
        val viewModel = viewModel().atCodeStep()

        viewModel.sendCode(" 246 810 ")

        assertEquals(listOf(CODE), repository.submittedCodes)
    }

    @Test
    fun `Send a new code returns to a blank email step without asking the service for a resend`() {
        val viewModel = viewModel().atCodeStep()
        viewModel.codeState.type("111")

        viewModel.dispatch(ResetPasswordEvent.SendNewCodeRequested)

        assertEquals(ResetStep.EmailEntry, viewModel.current.step)
        assertEquals("", viewModel.emailState.text.toString())
        assertEquals("", viewModel.codeState.text.toString())
        assertEquals(1, repository.startedEmails.size)
        assertTrue(viewModel.current.fieldErrors.isEmpty())
    }

    @Test
    fun `after Send a new code the old reset cannot be continued`() {
        val viewModel = viewModel().atCodeStep()
        viewModel.dispatch(ResetPasswordEvent.SendNewCodeRequested)

        viewModel.dispatch(ResetPasswordEvent.CodeSubmitted)
        viewModel.dispatch(ResetPasswordEvent.NewPasswordSubmitted)

        assertTrue(repository.submittedCodes.isEmpty())
        assertNull(repository.lastPasswordAtCall)
    }

    // ---- New password step (AC11 to AC17) ----

    @Test
    fun `blank passwords show both blank errors`() {
        val viewModel = viewModel().atNewPasswordStep()

        viewModel.sendPasswords("", "")

        assertEquals(
            listOf(
                ResetFieldError(ResetField.NewPassword, R.string.reset_password_new_error_blank),
                ResetFieldError(ResetField.ConfirmPassword, R.string.reset_password_confirm_error_blank),
            ),
            viewModel.current.fieldErrors,
        )
        assertNull(repository.lastPasswordAtCall)
    }

    @Test
    fun `a password that breaks the rules shows the error for the rule it broke`() {
        val viewModel = viewModel().atNewPasswordStep()

        viewModel.sendPasswords("short1A")

        assertEquals(
            listOf(ResetFieldError(ResetField.NewPassword, R.string.reset_password_new_error_too_short, listOf(8))),
            viewModel.current.fieldErrors,
        )
    }

    @Test
    fun `mismatching passwords show the mismatch error against the confirm field`() {
        val viewModel = viewModel().atNewPasswordStep()

        viewModel.sendPasswords(PASSWORD, "Different1!pw")

        assertEquals(
            listOf(ResetFieldError(ResetField.ConfirmPassword, R.string.reset_password_confirm_error_mismatch)),
            viewModel.current.fieldErrors,
        )
    }

    @Test
    fun `a valid new password completes the reset, wipes the arrays and clears every field`() {
        val viewModel = viewModel().atNewPasswordStep()

        viewModel.sendPasswords()

        assertEquals(ResetStep.Success, viewModel.current.step)
        assertEquals(PASSWORD, repository.lastPasswordAtCall)
        assertTrue(repository.lastPasswordReference!!.all { it == '\u0000' })
        assertEquals("", viewModel.newPasswordState.text.toString())
        assertEquals("", viewModel.confirmPasswordState.text.toString())
    }

    @Test
    fun `a password the service rejects keeps the user on the step with the requirements error`() {
        repository.submitPasswordResult = SubmitPasswordResult.PasswordRejected
        val viewModel = viewModel().atNewPasswordStep()

        viewModel.sendPasswords()

        assertEquals(ResetStep.NewPassword, viewModel.current.step)
        assertEquals(
            R.string.reset_password_new_error_requirements,
            viewModel.current.fieldErrors
                .single()
                .messageRes,
        )
    }

    @Test
    fun `an expired reset on the password step restarts at a blank email with a page error`() {
        repository.submitPasswordResult = SubmitPasswordResult.Expired
        val viewModel = viewModel().atNewPasswordStep()

        viewModel.sendPasswords()

        assertEquals(ResetStep.EmailEntry, viewModel.current.step)
        assertEquals(R.string.reset_password_error_session_expired, viewModel.current.pageErrorRes)
        assertEquals("", viewModel.newPasswordState.text.toString())
    }

    @Test
    fun `password visibility toggles are tracked independently`() {
        val viewModel = viewModel()

        viewModel.dispatch(ResetPasswordEvent.NewPasswordVisibilityChanged(true))
        assertTrue(viewModel.current.newPasswordVisible)
        assertFalse(viewModel.current.confirmPasswordVisible)

        viewModel.dispatch(ResetPasswordEvent.ConfirmPasswordVisibilityChanged(true))
        viewModel.dispatch(ResetPasswordEvent.NewPasswordVisibilityChanged(false))
        assertFalse(viewModel.current.newPasswordVisible)
        assertTrue(viewModel.current.confirmPasswordVisible)
    }

    @Test
    fun `moving to the next step hides the passwords again`() {
        val viewModel = viewModel().atNewPasswordStep()
        viewModel.dispatch(ResetPasswordEvent.NewPasswordVisibilityChanged(true))

        viewModel.sendPasswords()

        assertFalse(viewModel.current.newPasswordVisible)
    }

    // ---- Failure handling at every step (AC19) ----

    private val failures =
        listOf(
            PasswordResetFailure.Network to R.string.reset_password_error_network,
            PasswordResetFailure.Throttled to R.string.reset_password_error_throttled,
            PasswordResetFailure.Unknown("test") to R.string.reset_password_error_generic,
        )

    @Test
    fun `every failure on the email step shows a page error and does not progress`() {
        for ((failure, message) in failures) {
            repository.startResult = StartResetResult.Failed(failure)
            val viewModel = viewModel()

            viewModel.sendEmail()

            assertEquals(ResetStep.EmailEntry, viewModel.current.step)
            assertEquals(message, viewModel.current.pageErrorRes)
            assertFalse(viewModel.current.isLoading)
            assertEquals(EMAIL, viewModel.emailState.text.toString())
        }
    }

    @Test
    fun `every failure on the code step keeps the step and the token so a retry works`() {
        for ((failure, message) in failures) {
            val viewModel = viewModel().atCodeStep()
            repository.submitCodeResult = SubmitCodeResult.Failed(failure)

            viewModel.sendCode()

            assertEquals(ResetStep.CodeEntry, viewModel.current.step)
            assertEquals(message, viewModel.current.pageErrorRes)

            repository.submitCodeResult = SubmitCodeResult.Accepted(TestResetToken())
            viewModel.dispatch(ResetPasswordEvent.CodeSubmitted)
            assertEquals(ResetStep.NewPassword, viewModel.current.step)
        }
    }

    @Test
    fun `every failure on the password step keeps the step, clears the passwords and allows a retry`() {
        for ((failure, message) in failures) {
            val viewModel = viewModel().atNewPasswordStep()
            repository.submitPasswordResult = SubmitPasswordResult.Failed(failure)

            viewModel.sendPasswords()

            assertEquals(ResetStep.NewPassword, viewModel.current.step)
            assertEquals(message, viewModel.current.pageErrorRes)
            assertEquals("", viewModel.newPasswordState.text.toString())

            repository.submitPasswordResult = SubmitPasswordResult.Reset
            viewModel.sendPasswords()
            assertEquals(ResetStep.Success, viewModel.current.step)
        }
    }

    @Test
    fun `a repository exception becomes a generic page error and does not crash or progress`() {
        repository.thrown = IllegalStateException("boom with $EMAIL")
        val viewModel = viewModel()

        viewModel.sendEmail()

        assertEquals(ResetStep.EmailEntry, viewModel.current.step)
        assertEquals(R.string.reset_password_error_generic, viewModel.current.pageErrorRes)
        assertFalse(viewModel.current.isLoading)
    }

    @Test
    fun `a new request clears the previous error`() {
        repository.startResult = StartResetResult.Failed(PasswordResetFailure.Network)
        val viewModel = viewModel()
        viewModel.sendEmail()
        repository.startResult = StartResetResult.CodeSent(TestResetToken())

        viewModel.dispatch(ResetPasswordEvent.EmailSubmitted)

        assertNull(viewModel.current.pageErrorRes)
        assertEquals(ResetStep.CodeEntry, viewModel.current.step)
    }

    // ---- Browser fallback (AC19) ----

    @Test
    fun `a browser-required start that signs in hands over to the session and clears the reset`() {
        repository.startResult = StartResetResult.BrowserRequired
        repository.browserResult = BrowserContinuationResult.SignedIn(TEST_SESSION)
        val viewModel = viewModel()

        viewModel.sendEmail()

        assertTrue(viewModel.current.signedInViaBrowser)
        assertFalse(viewModel.current.isLoading)
        assertEquals(1, repository.browserCallCount)
        assertEquals("", viewModel.emailState.text.toString())
    }

    @Test
    fun `a cancelled browser hand-off stops loading and leaves the step unchanged`() {
        repository.submitCodeResult = SubmitCodeResult.BrowserRequired
        repository.browserResult = BrowserContinuationResult.Cancelled
        val viewModel = viewModel().atCodeStep()

        viewModel.sendCode()

        assertEquals(ResetStep.CodeEntry, viewModel.current.step)
        assertFalse(viewModel.current.isLoading)
        assertFalse(viewModel.current.signedInViaBrowser)
    }

    @Test
    fun `a failed browser hand-off on the password step shows the failure message`() {
        repository.submitPasswordResult = SubmitPasswordResult.BrowserRequired
        repository.browserResult = BrowserContinuationResult.Failed(PasswordResetFailure.Network)
        val viewModel = viewModel().atNewPasswordStep()

        viewModel.sendPasswords()

        assertEquals(R.string.reset_password_error_network, viewModel.current.pageErrorRes)
        assertEquals(ResetStep.NewPassword, viewModel.current.step)
    }

    @Test
    fun `the redirecting notice follows the stage and the notice acknowledgement is forwarded`() {
        val viewModel = viewModel()

        stageObserver.stage.value = SignInStage.RedirectingToBrowser
        assertTrue(viewModel.current.isRedirectingToBrowser)
        viewModel.dispatch(ResetPasswordEvent.BrowserNoticeShown)
        stageObserver.stage.value = SignInStage.Idle

        assertEquals(1, stageObserver.acknowledgements)
        assertFalse(viewModel.current.isRedirectingToBrowser)
    }

    // ---- Offline (AC18) ----

    @Test
    fun `an offline start blocks every submit and calls no service`() {
        connectivity.setOnline(false)
        val viewModel = viewModel()
        assertTrue(viewModel.current.isOffline)

        viewModel.sendEmail()

        assertTrue(repository.startedEmails.isEmpty())
        assertEquals(ResetStep.EmailEntry, viewModel.current.step)
    }

    @Test
    fun `going offline blocks the next submit and reconnecting clears the notice`() {
        val viewModel = viewModel().atCodeStep()

        connectivity.setOnline(false)
        viewModel.sendCode()
        assertTrue(viewModel.current.isOffline)
        assertTrue(repository.submittedCodes.isEmpty())

        connectivity.setOnline(true)
        assertFalse(viewModel.current.isOffline)
        viewModel.dispatch(ResetPasswordEvent.CodeSubmitted)
        assertEquals(listOf(CODE), repository.submittedCodes)
    }

    @Test
    fun `the notice action re-checks the connection`() {
        connectivity.setOnline(false)
        val viewModel = viewModel()

        connected = true
        viewModel.dispatch(ResetPasswordEvent.ConnectionRecheckRequested)
        assertFalse(viewModel.current.isOffline)

        connected = false
        viewModel.dispatch(ResetPasswordEvent.ConnectionRecheckRequested)
        assertTrue(viewModel.current.isOffline)
    }

    // ---- In-flight guard ----

    @Test
    fun `a second tap while a request is in flight does not send a duplicate`() {
        val gated = GatedRepository()
        val viewModel = viewModel(repo = gated)
        viewModel.emailState.type(EMAIL)

        viewModel.dispatch(ResetPasswordEvent.EmailSubmitted)
        viewModel.dispatch(ResetPasswordEvent.EmailSubmitted)

        assertTrue(viewModel.current.isLoading)
        assertEquals(1, gated.startCalls)

        gated.gate.complete(Unit)
        assertEquals(ResetStep.CodeEntry, viewModel.current.step)
        assertFalse(viewModel.current.isLoading)
    }

    @Test
    fun `Send a new code cancels an in-flight request and stays blank`() {
        val gated = GatedRepository()
        val viewModel = viewModel(repo = gated)
        viewModel.emailState.type(EMAIL)
        viewModel.dispatch(ResetPasswordEvent.EmailSubmitted)

        viewModel.dispatch(ResetPasswordEvent.SendNewCodeRequested)
        gated.gate.complete(Unit)

        assertEquals(ResetStep.EmailEntry, viewModel.current.step)
        assertFalse(viewModel.current.isLoading)
        assertEquals("", viewModel.emailState.text.toString())
    }

    // ---- Security: nothing sensitive is retained or logged ----

    @Test
    fun `nothing is saved to a SavedStateHandle`() {
        val constructorTypes = ResetPasswordViewModel::class.java.constructors.flatMap { it.parameterTypes.toList() }

        assertFalse(constructorTypes.contains(SavedStateHandle::class.java))
    }

    @Test
    fun `no state value contains the typed email, code or password`() {
        val viewModel = viewModel()
        val seen = mutableListOf<ResetPasswordViewState>()

        viewModel.sendEmail()
        seen += viewModel.current
        viewModel.sendCode()
        seen += viewModel.current
        viewModel.newPasswordState.type(PASSWORD)
        seen += viewModel.current
        viewModel.sendPasswords()
        seen += viewModel.current

        seen.forEach { state ->
            listOf(EMAIL, CODE, PASSWORD, "TestResetToken").forEach { secret ->
                assertFalse(state.toString().contains(secret))
            }
        }
    }

    @Test
    fun `no log line contains the email, code, password or exception detail`() {
        val lines = mutableListOf<String>()
        Timber.plant(
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    lines += message + t?.message.orEmpty()
                }
            },
        )
        val viewModel = viewModel()
        viewModel.sendEmail()
        repository.submitCodeResult = SubmitCodeResult.Failed(PasswordResetFailure.Unknown("detail"))
        viewModel.sendCode()
        repository.submitCodeResult = SubmitCodeResult.Accepted(TestResetToken())
        viewModel.dispatch(ResetPasswordEvent.CodeSubmitted)
        repository.submitPasswordResult = SubmitPasswordResult.BrowserRequired
        viewModel.sendPasswords()
        repository.thrown = IllegalStateException("leak $EMAIL $CODE $PASSWORD")
        viewModel.dispatch(ResetPasswordEvent.SendNewCodeRequested)
        viewModel.sendEmail()

        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            listOf(EMAIL, CODE, PASSWORD, "leak").forEach { secret -> assertFalse(line, line.contains(secret)) }
        }
    }

    @Test
    fun `abandoning wipes every typed value and the reset`() {
        val viewModel = viewModel().atNewPasswordStep()
        viewModel.newPasswordState.type(PASSWORD)
        viewModel.confirmPasswordState.type(PASSWORD)

        viewModel.abandon()
        viewModel.dispatch(ResetPasswordEvent.NewPasswordSubmitted)

        assertEquals("", viewModel.newPasswordState.text.toString())
        assertEquals("", viewModel.confirmPasswordState.text.toString())
        assertNull(repository.lastPasswordAtCall)
    }

    @Test
    fun `clearing the owner wipes the reset and a new instance starts blank`() {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel() as T
            }
        val first = ViewModelProvider(store, factory)[ResetPasswordViewModel::class.java]
        first.sendEmail()
        first.codeState.type(CODE)

        store.clear()

        assertEquals("", first.codeState.text.toString())
        val second = ViewModelProvider(ViewModelStore(), factory)[ResetPasswordViewModel::class.java]
        assertEquals(ResetStep.EmailEntry, second.current.step)
        assertEquals("", second.emailState.text.toString())
    }
}
