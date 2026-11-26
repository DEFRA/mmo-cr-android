package uk.gov.defra.mmocatchrecord.feature.signin.data

import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetToken

/**
 * Hand-written [NativeAuthClient] double (ADR 0019); `reset*` properties script the reset port (ADR 0027).
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
    ) : NativeAuthClient,
        NativePasswordResetClient {
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

        var startOutcome: NativeResetOutcome = NativeResetOutcome.CodeRequired(FakeResetToken("start"))
        var submitCodeOutcome: NativeResetOutcome = NativeResetOutcome.PasswordRequired(FakeResetToken("code"))
        var submitPasswordOutcome: NativeResetOutcome = NativeResetOutcome.Completed
        var resetThrown: Throwable? = null
        val resetEmails = mutableListOf<String>()
        val resetCodes = mutableListOf<String>()
        var lastResetToken: PasswordResetToken? = null
            private set
        var lastResetPasswordReference: CharArray? = null
            private set
        var lastResetPasswordAtCall: String? = null
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

        override suspend fun startPasswordReset(email: String): NativeResetOutcome {
            resetEmails += email
            resetThrown?.let { throw it }
            return startOutcome
        }

        override suspend fun submitResetCode(
            token: PasswordResetToken,
            code: String,
        ): NativeResetOutcome {
            lastResetToken = token
            resetCodes += code
            resetThrown?.let { throw it }
            return submitCodeOutcome
        }

        override suspend fun submitNewPassword(
            token: PasswordResetToken,
            password: CharArray,
        ): NativeResetOutcome {
            lastResetToken = token
            lastResetPasswordReference = password
            lastResetPasswordAtCall = String(password)
            resetThrown?.let { throw it }
            return submitPasswordOutcome
        }
    }

/** Opaque token for the fake reset client; reveals nothing in `toString()`. */
internal class FakeResetToken(
    val label: String,
) : PasswordResetToken {
    override fun toString(): String = "FakeResetToken"
}
