package uk.gov.defra.mmocatchrecord.common.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose route for the splash screen — see [uk.gov.defra.mmocatchrecord.core.root.RootPhase].
 */
@Serializable
data object SplashRoute

/** Type-safe route for the sign-in screen. */
@Serializable
data object SignInRoute

/** Type-safe route for the biometric app-lock re-entry screen. */
@Serializable
data object AppLockRoute

/** Type-safe route for the home screen. */
@Serializable
data object HomeRoute

/**
 * Type-safe route for the nested catch-record wizard graph (see ADR 0007/0010) — its own destinations are
 * declared in `feature.catchrecord.presentation.wizard.flow.CatchRecordRoutes`.
 */
@Serializable
data object CatchRecordGraphRoute
