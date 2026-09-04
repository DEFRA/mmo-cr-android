package uk.gov.defra.mmocatchrecord.core.security

/**
 * Abstraction over the current authenticated session's timing metadata, used to decide app-lock
 * re-entry (see [BiometricReentryPolicy]).
 *
 * **Real implementation contract (Stage 2+):** session tokens/identifiers themselves must be stored via
 * **Android Keystore-backed encryption (Tink)** at rest, exposed through **Jetpack DataStore**, never
 * through `EncryptedSharedPreferences` (deprecated/discouraged for new code) and never in plain
 * `SharedPreferences`. This interface only exposes timing metadata (not the token/session content) so it
 * is safe to keep simple for Stage-1 in-memory fakes; the real implementation must ensure no PII, token
 * value, or session identifier is ever written to logs.
 */
interface SessionStore {
    /** Epoch-millis timestamp of the last successful authentication, or null if never authenticated. */
    suspend fun getLastAuthenticatedAtMillis(): Long?

    /** Records a new successful authentication timestamp. */
    suspend fun recordAuthenticatedNow(nowMillis: Long)

    /** Clears session state (e.g. on sign-out). */
    suspend fun clear()
}

/** In-memory [SessionStore] fake for tests and Stage-1 wiring — not persisted across process death. */
class InMemorySessionStore : SessionStore {
    @Volatile
    private var lastAuthenticatedAtMillis: Long? = null

    override suspend fun getLastAuthenticatedAtMillis(): Long? = lastAuthenticatedAtMillis

    override suspend fun recordAuthenticatedNow(nowMillis: Long) {
        lastAuthenticatedAtMillis = nowMillis
    }

    override suspend fun clear() {
        lastAuthenticatedAtMillis = null
    }
}
