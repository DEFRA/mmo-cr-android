package uk.gov.defra.mmocatchrecord.feature.signin.data

import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetToken

/**
 * Narrow internal port over MSAL native-auth password reset (ADR 0027). Implemented by
 * [MsalNativeAuthClient] (the only MSAL-importing file); results never carry an SDK message, email or code.
 */
internal interface NativePasswordResetClient {
    /** An unknown account returns [NativeResetOutcome.CodeRequired] with a dead-end token (AC3). */
    suspend fun startPasswordReset(email: String): NativeResetOutcome

    suspend fun submitResetCode(
        token: PasswordResetToken,
        code: String,
    ): NativeResetOutcome

    /** Takes ownership of [password] and wipes it once the request has completed. */
    suspend fun submitNewPassword(
        token: PasswordResetToken,
        password: CharArray,
    ): NativeResetOutcome
}

/** Mapped outcome of one reset step. Which values are reachable depends on the step. */
internal sealed interface NativeResetOutcome {
    /** A code was (or appears to have been) sent; [token] drives the next step. */
    data class CodeRequired(
        val token: PasswordResetToken,
    ) : NativeResetOutcome

    /** The code was accepted; [token] drives the new-password step. */
    data class PasswordRequired(
        val token: PasswordResetToken,
    ) : NativeResetOutcome

    data object Completed : NativeResetOutcome

    data object InvalidCode : NativeResetOutcome

    /** The service refused the password (policy, banned or recently used). */
    data object InvalidPassword : NativeResetOutcome

    /** The reset session (or code) has expired. */
    data object ExpiredSession : NativeResetOutcome

    data object BrowserRequired : NativeResetOutcome

    data object NetworkUnavailable : NativeResetOutcome

    data object Throttled : NativeResetOutcome

    /** [safeDiagnostic] is a mapped constant — never an SDK exception/error message. */
    data class Failed(
        val safeDiagnostic: String,
    ) : NativeResetOutcome
}
