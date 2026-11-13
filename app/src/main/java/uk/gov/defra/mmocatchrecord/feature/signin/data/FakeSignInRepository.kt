package uk.gov.defra.mmocatchrecord.feature.signin.data

import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInException
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInFailure
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignOutRepository

/**
 * Stage-1 [SignInRepository] fake, replaced by the MSAL-backed implementation in ADR 0019 Phase 2. With no
 * [accounts] configured every attempt succeeds; [forcedResult] overrides everything, for failure paths.
 */
class FakeSignInRepository(
    var accounts: Map<String, String> = emptyMap(),
    var forcedResult: Result<SignInSession>? = null,
) : SignInRepository,
    SignOutRepository {
    /** Nothing to clear — there is no MSAL account behind this fake. */
    override suspend fun signOut() = true

    override suspend fun signIn(
        username: String,
        password: String,
    ): Result<SignInSession> {
        forcedResult?.let { return it }
        if (accounts.isEmpty()) {
            return Result.success(SignInSession(userId = username, signedInAtEpochMillis = 0L))
        }
        // An unknown account and a wrong password must be indistinguishable to the caller — see
        // SignInFailure.InvalidCredentials; the real MSAL repository must preserve this behaviour.
        return if (accounts[username] == password) {
            Result.success(SignInSession(userId = username, signedInAtEpochMillis = 0L))
        } else {
            Result.failure(SignInException(SignInFailure.InvalidCredentials))
        }
    }
}
