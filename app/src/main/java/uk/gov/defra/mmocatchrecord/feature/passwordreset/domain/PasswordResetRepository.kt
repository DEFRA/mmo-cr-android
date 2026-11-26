package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

/**
 * Stateless port over the identity provider's password reset. Callers hold the [PasswordResetToken]
 * between steps; implementations must never log or persist an email, code or password.
 */
interface PasswordResetRepository {
    suspend fun start(email: String): StartResetResult

    suspend fun submitCode(
        token: PasswordResetToken,
        code: String,
    ): SubmitCodeResult

    /** Takes ownership of [password]: implementations wipe it once the request has completed. */
    suspend fun submitPassword(
        token: PasswordResetToken,
        password: CharArray,
    ): SubmitPasswordResult

    /** Hands off to the system browser sign-in after a `BrowserRequired` result. */
    suspend fun continueInBrowser(): BrowserContinuationResult
}
