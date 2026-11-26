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

/**
 * Nested graph for resetting a forgotten password (ADR 0027), opened from Sign in. None of these routes
 * carries an argument, so no email address, code or token can appear in a route or its saved state.
 */
@Serializable
data object ResetPasswordGraphRoute

@Serializable
data object ResetPasswordEmailRoute

@Serializable
data object ResetPasswordCodeRoute

@Serializable
data object ResetPasswordNewPasswordRoute

@Serializable
data object ResetPasswordSuccessRoute

/** Type-safe route for the biometric app-lock re-entry screen. */
@Serializable
data object AppLockRoute

/**
 * Type-safe route for the home screen. [initialTab] selects which Home-shell tab is shown on arrival
 * (0 Home, 1 Notifications, 2 Settings — see [uk.gov.defra.mmocatchrecord.common.design.MmoBottomNavigationBar]).
 * Used when exiting the catch-record wizard via its own bottom navigation (see
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold]).
 */
@Serializable
data class HomeRoute(
    val initialTab: Int = 0,
)

/**
 * Type-safe route for the nested catch-record wizard graph (see ADR 0007/0010) — its own destinations are
 * declared in `feature.catchrecord.presentation.wizard.flow.CatchRecordRoutes`.
 */
@Serializable
data object CatchRecordGraphRoute
