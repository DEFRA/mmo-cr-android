package uk.gov.defra.mmocatchrecord.core.security

/**
 * The user's persisted biometric-re-entry preference. Stage 2+ must persist it encrypted per ADR 0006 —
 * see `DataStoreSessionStore`; never log it alongside user-identifying context.
 */
interface BiometricPreferenceStore {
    suspend fun isBiometricReentryEnabled(): Boolean

    suspend fun setBiometricReentryEnabled(enabled: Boolean)
}

/** In-memory [BiometricPreferenceStore] fake for tests and Stage-1 wiring. */
class InMemoryBiometricPreferenceStore(
    initiallyEnabled: Boolean = false,
) : BiometricPreferenceStore {
    @Volatile
    private var enabled: Boolean = initiallyEnabled

    override suspend fun isBiometricReentryEnabled(): Boolean = enabled

    override suspend fun setBiometricReentryEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
