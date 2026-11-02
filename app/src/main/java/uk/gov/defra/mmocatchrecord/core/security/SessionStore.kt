package uk.gov.defra.mmocatchrecord.core.security

/**
 * Timing metadata for the current session. Holds **no** token, token fragment or account identifier, so
 * nothing here can identify a user; implementations must never write any of it to a log (ADR 0019).
 */
interface SessionStore {
    /** Epoch millis of the last successful authentication *or* app-lock re-entry, or null if never. */
    suspend fun getLastAuthenticatedAtMillis(): Long?

    /** Records app-lock re-entry; a local biometric unlock may refresh this. See [BiometricReentryPolicy]. */
    suspend fun recordAuthenticatedNow(nowMillis: Long)

    /** Epoch millis of the last authentication that actually reached the identity provider, or null. */
    suspend fun getLastVerifiedOnlineAtMillis(): Long?

    /**
     * Records a successful **online** authentication/token acquisition. The offline authorisation grace
     * period is evaluated against this alone, so a local unlock can never extend it (ADR 0019 Q4).
     */
    suspend fun recordVerifiedOnlineNow(nowMillis: Long)

    /** Clears session state (e.g. on sign-out). */
    suspend fun clear()
}

/** In-memory [SessionStore] fake for tests and previews — not persisted across process death. */
class InMemorySessionStore(
    initialLastAuthenticatedAtMillis: Long? = null,
    initialLastVerifiedOnlineAtMillis: Long? = null,
) : SessionStore {
    @Volatile
    private var lastAuthenticatedAtMillis: Long? = initialLastAuthenticatedAtMillis

    @Volatile
    private var lastVerifiedOnlineAtMillis: Long? = initialLastVerifiedOnlineAtMillis

    override suspend fun getLastAuthenticatedAtMillis(): Long? = lastAuthenticatedAtMillis

    override suspend fun recordAuthenticatedNow(nowMillis: Long) {
        lastAuthenticatedAtMillis = nowMillis
    }

    override suspend fun getLastVerifiedOnlineAtMillis(): Long? = lastVerifiedOnlineAtMillis

    override suspend fun recordVerifiedOnlineNow(nowMillis: Long) {
        lastVerifiedOnlineAtMillis = nowMillis
    }

    override suspend fun clear() {
        lastAuthenticatedAtMillis = null
        lastVerifiedOnlineAtMillis = null
    }
}
