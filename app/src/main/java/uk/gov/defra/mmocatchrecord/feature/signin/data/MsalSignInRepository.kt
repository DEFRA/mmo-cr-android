package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession
import java.util.Arrays
import kotlin.coroutines.cancellation.CancellationException

/**
 * MSAL-free [SignInRepository] depending only on [NativeAuthClient] (ADR 0019 Phase 2.1b) —
 * token delivery (2.1d) and the browser fallback (2.1c) land in later phases.
 */
internal class MsalSignInRepository(
    private val nativeAuthClient: NativeAuthClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SignInRepository {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun signIn(
        username: String,
        password: String,
    ): Result<SignInSession> =
        withContext(ioDispatcher) {
            // The domain hands us a String, which can't be wiped; the CharArray copy is zeroised below.
            val passwordChars = password.toCharArray()
            try {
                nativeAuthClient
                    .signIn(username, passwordChars, listOf(API_SCOPE))
                    .toResult()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (thrown: Exception) {
                // thrown is deliberately not surfaced — its message could carry PII (ADR 0019 §security).
                Result.failure(SignInException(SignInFailure.Unknown(UNEXPECTED_SIGN_IN_FAILURE)))
            } finally {
                Arrays.fill(passwordChars, '\u0000')
            }
        }

    private fun NativeAuthOutcome.toResult(): Result<SignInSession> =
        when (this) {
            is NativeAuthOutcome.Succeeded ->
                Result.success(SignInSession(userId = userId, signedInAtEpochMillis = 0L))
            NativeAuthOutcome.InvalidCredentialsOrUnknownAccount ->
                Result.failure(SignInException(SignInFailure.InvalidCredentials))
            NativeAuthOutcome.CodeRequired ->
                Result.failure(SignInException(SignInFailure.VerificationNotSupported))
            // Interim mapping until the browser web fallback (2.1c) is wired.
            NativeAuthOutcome.BrowserRequired ->
                Result.failure(SignInException(SignInFailure.Unknown(BROWSER_REQUIRED_INTERIM)))
            NativeAuthOutcome.NetworkUnavailable ->
                Result.failure(SignInException(SignInFailure.Network))
            is NativeAuthOutcome.Failed ->
                Result.failure(SignInException(SignInFailure.Unknown(safeDiagnostic)))
        }

    private companion object {
        const val API_SCOPE = "api://8bc065bf-046f-4d18-834a-753b325e5221/access_as_user"
        const val BROWSER_REQUIRED_INTERIM = "native-auth requires the browser web fallback (not yet available)"
        const val UNEXPECTED_SIGN_IN_FAILURE = "native-auth client threw an unexpected exception"
    }
}
