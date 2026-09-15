package uk.gov.defra.mmocatchrecord.core.root

/**
 * High-level phase of the app's root navigation graph, derived by [SessionCoordinator] from session and
 * biometric-preference state (see [uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy]).
 */
enum class RootPhase {
    /**
     * Initial, resolving state shown while [SessionCoordinator] derives the real phase from session and
     * biometric-preference state. This is local-only and fast (no network, no artificial delay) — it is
     * shown only for the brief window until [SIGN_IN]/[APP_LOCK]/[HOME] is resolved, then never revisited.
     */
    SPLASH,

    /** No valid session — show the sign-in placeholder. */
    SIGN_IN,

    /** A session exists but biometric re-entry is required before content is shown. */
    APP_LOCK,

    /** Authenticated and unlocked — show the home placeholder. */
    HOME,
}
