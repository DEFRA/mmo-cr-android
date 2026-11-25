package uk.gov.defra.mmocatchrecord.feature.signin.data

import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetToken
import java.io.IOException

/**
 * Plain facts read from an SDK reset error, so the mapping below is pure and needs no MSAL import.
 * [serverError] and [subError] are the raw server strings; they are only compared, never surfaced.
 */
internal data class ResetErrorFacts(
    val isBrowserRequired: Boolean = false,
    val hasNetworkCause: Boolean = false,
    val isUserNotFound: Boolean = false,
    val isInvalidUsername: Boolean = false,
    val isInvalidCode: Boolean = false,
    val isInvalidPassword: Boolean = false,
    val isPasswordResetFailed: Boolean = false,
    val serverError: String? = null,
    val subError: String? = null,
)

/**
 * Server strings recognised beyond the SDK's own predicates. These are documented assumptions awaiting the
 * DEV-tenant spike (ADR 0027): adjust here only. Matching is case-insensitive.
 */
internal object ResetServerStrings {
    val expired: Set<String> = setOf("expired_token")
    val throttled: Set<String> = setOf("too_many_requests", "throttled", "slow_down")
    val invalidCode: Set<String> = setOf("invalid_oob_value")
    val invalidPassword: Set<String> =
        setOf(
            "password_too_weak",
            "password_too_short",
            "password_too_long",
            "password_recently_used",
            "password_banned",
            "password_is_invalid",
        )

    fun ResetErrorFacts.matches(known: Set<String>): Boolean =
        listOf(serverError, subError).any { it != null && it.lowercase() in known }
}

/** Account-not-found stand-in: any code submitted against it is an incorrect code (AC3). */
internal object DeadEndResetToken : PasswordResetToken {
    override fun toString(): String = "PasswordResetToken"
}

internal const val RESET_FAILED = "password reset failed"
internal const val RESET_START_ERROR = "unclassified password reset start error"
internal const val RESET_CODE_ERROR = "unclassified password reset code error"
internal const val RESET_PASSWORD_ERROR = "unclassified password reset password error"

/** Order: browser, network, unknown/invalid account (looks like "code sent"), expired, throttled, unknown. */
internal fun ResetErrorFacts.toStartOutcome(): NativeResetOutcome =
    with(ResetServerStrings) {
        when {
            isBrowserRequired -> NativeResetOutcome.BrowserRequired
            hasNetworkCause -> NativeResetOutcome.NetworkUnavailable
            // Unknown and invalid accounts are indistinguishable from a sent code (ADR 0027).
            isUserNotFound || isInvalidUsername -> NativeResetOutcome.CodeRequired(DeadEndResetToken)
            matches(throttled) -> NativeResetOutcome.Throttled
            else -> NativeResetOutcome.Failed(RESET_START_ERROR)
        }
    }

/** Order: browser, network, expired, throttled, invalid code, unknown. */
internal fun ResetErrorFacts.toSubmitCodeOutcome(): NativeResetOutcome =
    with(ResetServerStrings) {
        when {
            isBrowserRequired -> NativeResetOutcome.BrowserRequired
            hasNetworkCause -> NativeResetOutcome.NetworkUnavailable
            matches(expired) -> NativeResetOutcome.ExpiredSession
            matches(throttled) -> NativeResetOutcome.Throttled
            isInvalidCode || matches(invalidCode) -> NativeResetOutcome.InvalidCode
            else -> NativeResetOutcome.Failed(RESET_CODE_ERROR)
        }
    }

/** Order: browser, network, expired, throttled, invalid password, reset failed, unknown. */
internal fun ResetErrorFacts.toSubmitPasswordOutcome(): NativeResetOutcome =
    with(ResetServerStrings) {
        when {
            isBrowserRequired -> NativeResetOutcome.BrowserRequired
            hasNetworkCause -> NativeResetOutcome.NetworkUnavailable
            matches(expired) -> NativeResetOutcome.ExpiredSession
            matches(throttled) -> NativeResetOutcome.Throttled
            isInvalidPassword || matches(invalidPassword) -> NativeResetOutcome.InvalidPassword
            isPasswordResetFailed -> NativeResetOutcome.Failed(RESET_FAILED)
            else -> NativeResetOutcome.Failed(RESET_PASSWORD_ERROR)
        }
    }

/** Maps a thrown exception to an outcome without ever reading its message. */
internal fun thrownResetOutcome(
    hasNetworkCause: Boolean,
    safeDiagnostic: String,
): NativeResetOutcome =
    if (hasNetworkCause) NativeResetOutcome.NetworkUnavailable else NativeResetOutcome.Failed(safeDiagnostic)

/** True when [thrown] or any of its causes is an [IOException] (transient connectivity failure). */
internal fun hasNetworkCause(thrown: Throwable?): Boolean {
    var cause = thrown
    while (cause != null) {
        if (cause is IOException) return true
        cause = cause.cause
    }
    return false
}
