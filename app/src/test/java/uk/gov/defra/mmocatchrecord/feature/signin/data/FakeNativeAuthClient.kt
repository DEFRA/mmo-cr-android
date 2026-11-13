package uk.gov.defra.mmocatchrecord.feature.signin.data

/**
 * Hand-written test double for [NativeAuthClient] — MSAL's own types are too wide to usefully mock
 * (ADR 0019), so [MsalSignInRepository] is unit-tested entirely against this fake.
 */
internal class FakeNativeAuthClient
    @Suppress("LongParameterList")
    constructor(
        var outcome: NativeAuthOutcome = NativeAuthOutcome.Succeeded(userId = "fixture-user-id"),
        var interactiveOutcome: NativeAuthOutcome = NativeAuthOutcome.Succeeded(userId = "fixture-user-id"),
        var interactiveThrown: Throwable? = null,
        var thrown: Throwable? = null,
        var accessTokenResult: String? = null,
        var accessTokenThrown: Throwable? = null,
        var currentAccountUserIdResult: String? = null,
        var signOutResult: Boolean = true,
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
        var lastAccessTokenScopes: List<String>? = null
            private set
        var lastForceRefresh: Boolean? = null
            private set
        var signOutCallCount: Int = 0
            private set
        var warmUpCallCount: Int = 0
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

        override suspend fun currentAccountUserId(): String? = currentAccountUserIdResult

        override suspend fun accessToken(
            scopes: List<String>,
            forceRefresh: Boolean,
        ): String? {
            lastAccessTokenScopes = scopes
            lastForceRefresh = forceRefresh
            accessTokenThrown?.let { throw it }
            return accessTokenResult
        }

        override suspend fun signOut(): Boolean {
            signOutCallCount++
            return signOutResult
        }

        override suspend fun warmUp() {
            warmUpCallCount++
        }
    }
