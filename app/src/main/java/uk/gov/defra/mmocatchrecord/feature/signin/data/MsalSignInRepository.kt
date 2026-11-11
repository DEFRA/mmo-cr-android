package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import java.util.Arrays
import kotlin.coroutines.cancellation.CancellationException

/**
 * MSAL-free [SignInRepository] depending only on [NativeAuthClient] (ADR 0019 Phase 2.1b/c) —
 * token delivery to the API (2.1d) lands in a later phase.
 */
internal class MsalSignInRepository(
    private val nativeAuthClient: NativeAuthClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SignInRepository,
    SignInStageObserver {
    private val mutableStage = MutableStateFlow(SignInStage.Idle)
    override val stage: StateFlow<SignInStage> = mutableStage.asStateFlow()

    @Volatile
    private var pendingBrowserNoticeAck: CompletableDeferred<Unit>? = null

    override fun acknowledgeBrowserNoticeShown() {
        pendingBrowserNoticeAck?.complete(Unit)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun signIn(
        username: String,
        password: String,
    ): Result<SignInSession> =
        withContext(ioDispatcher) {
            try {
                when (val outcome = nativeSignIn(username, password)) {
                    NativeAuthOutcome.BrowserRequired -> handoffToBrowser()
                    else -> outcome.toResult()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (thrown: Exception) {
                // thrown is deliberately not surfaced — its message could carry PII (ADR 0019 §security).
                Result.failure(SignInException(SignInFailure.Unknown(UNEXPECTED_SIGN_IN_FAILURE)))
            }
        }

    /** The domain hands us a String, which can't be wiped; zeroised before any browser hand-off. */
    private suspend fun nativeSignIn(
        username: String,
        password: String,
    ): NativeAuthOutcome {
        val passwordChars = password.toCharArray()
        return try {
            nativeAuthClient.signIn(username, passwordChars, listOf(API_SCOPE))
        } finally {
            Arrays.fill(passwordChars, '\u0000')
        }
    }

    /**
     * Announces the hand-off via [stage], then waits for the UI's acknowledgement (or [ACK_TIMEOUT_MILLIS])
     * plus a short pause, so TalkBack has a guaranteed chance to speak it before the browser takes focus.
     */
    private suspend fun handoffToBrowser(): Result<SignInSession> {
        mutableStage.value = SignInStage.RedirectingToBrowser
        val acknowledgement = CompletableDeferred<Unit>()
        pendingBrowserNoticeAck = acknowledgement
        return try {
            withTimeoutOrNull(ACK_TIMEOUT_MILLIS) { acknowledgement.await() }
            // ~1.2s: comfortably inside the requested 1-1.5s range for a TalkBack announcement to start.
            delay(POST_ACK_PAUSE_MILLIS)
            nativeAuthClient.signInInteractively(listOf(API_SCOPE)).toInteractiveResult()
        } finally {
            pendingBrowserNoticeAck = null
            mutableStage.value = SignInStage.Idle
        }
    }

    private fun NativeAuthOutcome.toInteractiveResult(): Result<SignInSession> =
        when (this) {
            // Guarded against a loop: a second BrowserRequired is terminal, never retried again.
            NativeAuthOutcome.BrowserRequired ->
                Result.failure(SignInException(SignInFailure.Unknown(BROWSER_REQUIRED_LOOP)))
            NativeAuthOutcome.BrowserCancelled ->
                Result.failure(SignInException(SignInFailure.Cancelled))
            else -> toResult()
        }

    private fun NativeAuthOutcome.toResult(): Result<SignInSession> =
        when (this) {
            is NativeAuthOutcome.Succeeded ->
                Result.success(SignInSession(userId = userId, signedInAtEpochMillis = 0L))
            NativeAuthOutcome.InvalidCredentialsOrUnknownAccount ->
                Result.failure(SignInException(SignInFailure.InvalidCredentials))
            NativeAuthOutcome.CodeRequired ->
                Result.failure(SignInException(SignInFailure.VerificationNotSupported))
            NativeAuthOutcome.NetworkUnavailable ->
                Result.failure(SignInException(SignInFailure.Network))
            is NativeAuthOutcome.Failed ->
                Result.failure(SignInException(SignInFailure.Unknown(safeDiagnostic)))
            // Only reachable via toInteractiveResult's else branch; never produced by the native leg itself.
            NativeAuthOutcome.BrowserRequired ->
                Result.failure(SignInException(SignInFailure.Unknown(BROWSER_REQUIRED_LOOP)))
            NativeAuthOutcome.BrowserCancelled ->
                Result.failure(SignInException(SignInFailure.Cancelled))
        }

    private companion object {
        const val API_SCOPE = "api://8bc065bf-046f-4d18-834a-753b325e5221/access_as_user"
        const val UNEXPECTED_SIGN_IN_FAILURE = "native-auth client threw an unexpected exception"
        const val BROWSER_REQUIRED_LOOP = "native-auth required the browser leg again after the browser leg"
        const val ACK_TIMEOUT_MILLIS = 3_000L
        const val POST_ACK_PAUSE_MILLIS = 1_200L
    }
}
