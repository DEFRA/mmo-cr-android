package uk.gov.defra.mmocatchrecord.core.root

/**
 * High-level phase of the app's root navigation graph, derived by [SessionCoordinator] from session and
 * biometric-preference state (see [uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy]).
 */
enum class RootPhase {
    /** No valid session — show the sign-in placeholder. */
    SIGN_IN,

    /** A session exists but biometric re-entry is required before content is shown. */
    APP_LOCK,

    /** Authenticated and unlocked — show the home placeholder. */
    HOME,
}
