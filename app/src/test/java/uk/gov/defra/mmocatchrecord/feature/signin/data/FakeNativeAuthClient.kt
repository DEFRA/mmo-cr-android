package uk.gov.defra.mmocatchrecord.feature.signin.data

/**
 * Hand-written test double for [NativeAuthClient] — MSAL's own types are too wide to usefully mock
 * (ADR 0019), so [MsalSignInRepository] is unit-tested entirely against this fake.
 */
internal class FakeNativeAuthClient(
    var outcome: NativeAuthOutcome = NativeAuthOutcome.Succeeded(userId = "fixture-user-id"),
    var interactiveOutcome: NativeAuthOutcome = NativeAuthOutcome.Succeeded(userId = "fixture-user-id"),
    var interactiveThrown: Throwable? = null,
    var thrown: Throwable? = null,
) : NativeAuthClient {
    var lastUsername: String? = null
        private set
    var lastPasswordReference: CharArray? = null
        private set
    var lastScopes: List<String>? = null
        private set
    var lastInteractiveScopes: List<String>? = null
        private set
    var interactiveCallCount: Int = 0
        private set

    override suspend fun signIn(
        username: String,
        password: CharArray,
        scopes: List<String>,
    ): NativeAuthOutcome {
        lastUsername = username
        lastScopes = scopes
        lastPasswordReference = password
        thrown?.let { throw it }
        return outcome
    }

    override suspend fun signInInteractively(scopes: List<String>): NativeAuthOutcome {
        interactiveCallCount++
        lastInteractiveScopes = scopes
        interactiveThrown?.let { throw it }
        return interactiveOutcome
    }

    override suspend fun currentAccountUserId(): String? = null

    override suspend fun accessToken(
        scopes: List<String>,
        forceRefresh: Boolean,
    ): String? = null

    override suspend fun signOut() = Unit
}
