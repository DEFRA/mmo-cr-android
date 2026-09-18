package uk.gov.defra.mmocatchrecord.core.security

/**
 * Repository abstraction over on-device biometric authentication (`androidx.biometric`).
 *
 * **Real implementation contract (Stage 2+):**
 * - Must use `androidx.biometric.BiometricPrompt` backed by a `CryptoObject` bound to a key in the
 *   **Android Keystore** (`AndroidKeyStore` provider), so a successful biometric result is cryptographically
 *   tied to key material that cannot be extracted from the device.
 * - Must never log biometric results, prompt subtitles/descriptions containing user data, or any
 *   correlation identifiers — diagnostics logging must be limited to the sanitised [BiometricError] kind.
 * - Must map every `BiometricPrompt.AuthenticationCallback` error code to a [BiometricError], never leak
 *   raw platform error strings to callers or logs.
 * - Must treat [BiometricError.UserCancelled] as a non-destructive, silent return path (no error UI).
 *
 * Stage 1 provides this interface plus [FakeBiometricRepository] only — no real Keystore/BiometricPrompt
 * calls are made yet.
 */
interface BiometricRepository {
    /** Reports current on-device biometric availability, per BiometricManager. */
    suspend fun checkAvailability(): BiometricAvailability

    /** Prompts for biometric authentication; suspends until the user completes, cancels, or errors. */
    suspend fun authenticate(): Result<Unit>
}

/**
 * In-memory fake for tests and Stage-1 wiring. Always reports [BiometricAvailability.Available] and
 * succeeds authentication, unless configured otherwise via the mutable fields below.
 */
class FakeBiometricRepository(
    var availability: BiometricAvailability = BiometricAvailability.Available,
    var authenticationResult: Result<Unit> = Result.success(Unit),
) : BiometricRepository {
    override suspend fun checkAvailability(): BiometricAvailability = availability

    override suspend fun authenticate(): Result<Unit> = authenticationResult
}
