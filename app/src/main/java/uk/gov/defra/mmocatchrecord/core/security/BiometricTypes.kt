package uk.gov.defra.mmocatchrecord.core.security

/** Kind of biometric authenticator available/requested on-device. */
enum class BiometryKind {
    FINGERPRINT,
    FACE,
    IRIS,

    /** Device credential (PIN/pattern/password) used as a biometric fallback. */
    DEVICE_CREDENTIAL,
}

/** Availability of biometric authentication on the current device, as reported by BiometricManager. */
sealed class BiometricAvailability {
    data object Available : BiometricAvailability()

    data object NoHardware : BiometricAvailability()

    data object HardwareUnavailable : BiometricAvailability()

    data object NoneEnrolled : BiometricAvailability()

    data object SecurityUpdateRequired : BiometricAvailability()

    data class Unknown(
        val reason: String,
    ) : BiometricAvailability()
}

/**
 * Terminal/transient failure reasons from a biometric authentication attempt.
 *
 * Per the error-handling standard: distinguish retryable (transient) from terminal failures, and never
 * include PII or raw platform exception messages here — [Unknown.reason] must be a sanitised,
 * developer-diagnostic string only (never surfaced verbatim to the end user).
 */
sealed class BiometricError {
    /** User cancelled the prompt — not an error state that should be surfaced destructively. */
    data object UserCancelled : BiometricError()

    /** Too many failed attempts; retry should be locked out for a cooldown period (transient/retryable). */
    data object LockedOutTemporarily : BiometricError()

    /** Permanently locked out until device-credential re-authentication (terminal for biometric path). */
    data object LockedOutPermanently : BiometricError()

    /** No biometrics enrolled on this device (terminal until the user enrols in device settings). */
    data object NotEnrolled : BiometricError()

    data class Unknown(
        val reason: String,
    ) : BiometricError()
}
