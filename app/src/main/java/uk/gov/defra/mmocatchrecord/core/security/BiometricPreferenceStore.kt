package uk.gov.defra.mmocatchrecord.core.security

/**
 * Abstraction over the user's persisted biometric-re-entry preference (opted in/out of biometric app
 * lock).
 *
 * **Real implementation contract (Stage 2+):** persist via **Jetpack DataStore**, encrypting the value
 * with [uk.gov.defra.mmocatchrecord.core.security.KeystoreAesGcmCipher] behind a `Serializer`, exactly as
 * `DataStoreSessionStore` does (ADR 0006). This is a user preference flag, not a secret, but is still
 * security-relevant configuration and must not be stored in plain `SharedPreferences`. Do **not** use Tink
 * or `androidx.security-crypto`. Never log the preference value alongside user-identifying context.
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
