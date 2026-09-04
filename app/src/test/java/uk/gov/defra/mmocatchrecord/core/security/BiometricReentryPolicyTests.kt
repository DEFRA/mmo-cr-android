package uk.gov.defra.mmocatchrecord.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure, table-driven tests for [BiometricReentryPolicy]. No mocks/Android dependencies — the caller
 * supplies "now" explicitly so every case is deterministic.
 */
class BiometricReentryPolicyTests {
    private val timeoutMillis = 60_000L
    private val policy = BiometricReentryPolicy(reentryTimeoutMillis = timeoutMillis)

    @Test
    fun `biometric disabled always passes through regardless of last auth`() {
        assertEquals(
            ReentryDecision.PASS_THROUGH,
            policy.evaluate(isBiometricEnabled = false, lastAuthenticatedAtMillis = null, nowMillis = 1_000L),
        )
        assertEquals(
            ReentryDecision.PASS_THROUGH,
            policy.evaluate(isBiometricEnabled = false, lastAuthenticatedAtMillis = 0L, nowMillis = 1_000_000L),
        )
    }

    @Test
    fun `biometric enabled with no prior authentication requires app lock`() {
        assertEquals(
            ReentryDecision.APP_LOCK,
            policy.evaluate(isBiometricEnabled = true, lastAuthenticatedAtMillis = null, nowMillis = 1_000L),
        )
    }

    @Test
    fun `biometric enabled within timeout window passes through`() {
        val lastAuthenticatedAt = 10_000L
        val now = lastAuthenticatedAt + timeoutMillis - 1

        assertEquals(
            ReentryDecision.PASS_THROUGH,
            policy.evaluate(
                isBiometricEnabled = true,
                lastAuthenticatedAtMillis = lastAuthenticatedAt,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `biometric enabled exactly at timeout boundary requires app lock`() {
        val lastAuthenticatedAt = 10_000L
        val now = lastAuthenticatedAt + timeoutMillis

        assertEquals(
            ReentryDecision.APP_LOCK,
            policy.evaluate(
                isBiometricEnabled = true,
                lastAuthenticatedAtMillis = lastAuthenticatedAt,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `biometric enabled past timeout window requires app lock`() {
        val lastAuthenticatedAt = 10_000L
        val now = lastAuthenticatedAt + timeoutMillis + 5_000L

        assertEquals(
            ReentryDecision.APP_LOCK,
            policy.evaluate(
                isBiometricEnabled = true,
                lastAuthenticatedAtMillis = lastAuthenticatedAt,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `default policy uses the five minute default timeout`() {
        val defaultPolicy = BiometricReentryPolicy()
        val lastAuthenticatedAt = 0L
        val justBeforeTimeout = BiometricReentryPolicy.DEFAULT_REENTRY_TIMEOUT_MILLIS - 1
        val atTimeout = BiometricReentryPolicy.DEFAULT_REENTRY_TIMEOUT_MILLIS

        assertEquals(
            ReentryDecision.PASS_THROUGH,
            defaultPolicy.evaluate(
                isBiometricEnabled = true,
                lastAuthenticatedAtMillis = lastAuthenticatedAt,
                nowMillis = justBeforeTimeout,
            ),
        )
        assertEquals(
            ReentryDecision.APP_LOCK,
            defaultPolicy.evaluate(
                isBiometricEnabled = true,
                lastAuthenticatedAtMillis = lastAuthenticatedAt,
                nowMillis = atTimeout,
            ),
        )
    }

    @Test
    fun `clock moved backwards relative to last authentication requires app lock`() {
        // nowMillis before lastAuthenticatedAtMillis => negative elapsed => must fail closed, never
        // treated as "still within timeout".
        assertEquals(
            ReentryDecision.APP_LOCK,
            policy.evaluate(
                isBiometricEnabled = true,
                lastAuthenticatedAtMillis = 100_000L,
                nowMillis = 50_000L,
            ),
        )
    }
}
