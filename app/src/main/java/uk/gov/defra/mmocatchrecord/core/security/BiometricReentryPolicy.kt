package uk.gov.defra.mmocatchrecord.core.security

/** Outcome of [BiometricReentryPolicy.evaluate] — whether the app should re-lock or pass straight through. */
enum class ReentryDecision {
    APP_LOCK,
    PASS_THROUGH,
}

/**
 * Pure decision logic for whether returning to the foreground should require biometric re-entry
 * (APP_LOCK) or let the user straight through (PASS_THROUGH).
 *
 * No Android framework dependencies — the caller supplies "now" explicitly so this is trivially
 * table-driven unit-testable without mocking a clock.
 */
class BiometricReentryPolicy(
    /** How long a session remains valid without requiring re-authentication. */
    private val reentryTimeoutMillis: Long = DEFAULT_REENTRY_TIMEOUT_MILLIS,
) {
    /**
     * @param isBiometricEnabled whether the user has opted in to biometric re-entry.
     * @param lastAuthenticatedAtMillis epoch-millis of the last successful authentication, or null if
     *   never authenticated.
     * @param nowMillis the current epoch-millis, supplied by the caller (e.g. an injectable clock) so
     *   this function stays pure and deterministic in tests.
     */
    fun evaluate(
        isBiometricEnabled: Boolean,
        lastAuthenticatedAtMillis: Long?,
        nowMillis: Long,
    ): ReentryDecision {
        if (!isBiometricEnabled) return ReentryDecision.PASS_THROUGH
        if (lastAuthenticatedAtMillis == null) return ReentryDecision.APP_LOCK

        val elapsed = nowMillis - lastAuthenticatedAtMillis
        // A negative "elapsed" means the device clock moved backwards relative to the recorded
        // authentication time. Never treat that as "still within the timeout" — an attacker (or a
        // buggy clock/timezone change) could otherwise use a future-dated authentication timestamp to
        // bypass re-entry indefinitely. Fail closed.
        return if (elapsed < 0 || elapsed >= reentryTimeoutMillis) {
            ReentryDecision.APP_LOCK
        } else {
            ReentryDecision.PASS_THROUGH
        }
    }

    companion object {
        const val DEFAULT_REENTRY_TIMEOUT_MILLIS: Long = 5 * 60 * 1000L
    }
}
