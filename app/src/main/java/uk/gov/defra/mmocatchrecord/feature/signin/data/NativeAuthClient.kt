package uk.gov.defra.mmocatchrecord.feature.signin.data

/**
 * Narrow internal port over MSAL native authentication (ADR 0019 Phase 2.1b) — the only abstraction
 * [MsalSignInRepository] depends on, so it stays JVM-unit-testable with a hand-written fake.
 */
internal interface NativeAuthClient {
    suspend fun signIn(
        username: String,
        password: CharArray,
        scopes: List<String>,
    ): NativeAuthOutcome

    suspend fun currentAccountUserId(): String?

    suspend fun accessToken(
        scopes: List<String>,
        forceRefresh: Boolean,
    ): String?

    suspend fun signOut()
}

/** Mapped outcome of a native-auth sign-in attempt. Never carries an SDK message or username. */
internal sealed interface NativeAuthOutcome {
    data class Succeeded(
        val userId: String,
    ) : NativeAuthOutcome

    /** MSAL requires the interactive browser leg (ADR 0019 Phase 2.1c); not yet wired. */
    data object BrowserRequired : NativeAuthOutcome

    /** A one-time-passcode challenge was issued; this build renders no OTP UI. */
    data object CodeRequired : NativeAuthOutcome

    /** Fuses "no such account" and "wrong password" — the account-enumeration guard (ADR 0019). */
    data object InvalidCredentialsOrUnknownAccount : NativeAuthOutcome

    data object NetworkUnavailable : NativeAuthOutcome

    /** [safeDiagnostic] is a mapped constant — never an SDK exception/error message. */
    data class Failed(
        val safeDiagnostic: String,
    ) : NativeAuthOutcome
}
