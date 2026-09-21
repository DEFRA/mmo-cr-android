package uk.gov.defra.mmocatchrecord.feature.signin.data

import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession

/**
 * In-memory fake [SignInRepository] for Stage-1 wiring and tests. Always succeeds unless configured
 * otherwise via [result]. Replaced by a Retrofit-backed implementation calling the MMO identity endpoint
 * in a later stage.
 */
class FakeSignInRepository(
    var result: (username: String) -> Result<SignInSession> = { username ->
        Result.success(SignInSession(userId = username, signedInAtEpochMillis = 0L))
    },
) : SignInRepository {
    override suspend fun signIn(
        username: String,
        password: String,
    ): Result<SignInSession> = result(username)
}
