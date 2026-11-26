package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession

/**
 * Opaque handle to an in-progress reset. Implementations hold SDK state and must hide it from
 * `toString()`; it is never persisted, saved or placed in a route (ADR 0027).
 */
interface PasswordResetToken

/** Why a reset step failed. Never carries SDK text, an email address, a code or a password. */
sealed interface PasswordResetFailure {
    /** Transient connectivity failure; retryable. */
    data object Network : PasswordResetFailure

    /** The service is rate limiting this caller; the user should wait before retrying. */
    data object Throttled : PasswordResetFailure

    /** [safeDiagnostic] is a mapped constant, never shown to the user and never an SDK message. */
    data class Unknown(
        val safeDiagnostic: String,
    ) : PasswordResetFailure
}

/** Result of starting a reset. An unknown account is indistinguishable from [CodeSent] (AC3). */
sealed interface StartResetResult {
    data class CodeSent(
        val token: PasswordResetToken,
    ) : StartResetResult

    data object BrowserRequired : StartResetResult

    data class Failed(
        val failure: PasswordResetFailure,
    ) : StartResetResult
}

sealed interface SubmitCodeResult {
    data class Accepted(
        val token: PasswordResetToken,
    ) : SubmitCodeResult

    data object InvalidCode : SubmitCodeResult

    data object Expired : SubmitCodeResult

    data object BrowserRequired : SubmitCodeResult

    data class Failed(
        val failure: PasswordResetFailure,
    ) : SubmitCodeResult
}

sealed interface SubmitPasswordResult {
    data object Reset : SubmitPasswordResult

    /** Entra refused the password (policy, banned or recently used). */
    data object PasswordRejected : SubmitPasswordResult

    data object Expired : SubmitPasswordResult

    data object BrowserRequired : SubmitPasswordResult

    data class Failed(
        val failure: PasswordResetFailure,
    ) : SubmitPasswordResult
}

/** Outcome of the browser web-fallback leg, which completes as a sign-in rather than a reset. */
sealed interface BrowserContinuationResult {
    data class SignedIn(
        val session: SignInSession,
    ) : BrowserContinuationResult

    data object Cancelled : BrowserContinuationResult

    data class Failed(
        val failure: PasswordResetFailure,
    ) : BrowserContinuationResult
}
