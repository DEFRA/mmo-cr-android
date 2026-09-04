package uk.gov.defra.mmocatchrecord.common.navigation

/**
 * Type-safe navigation destinations for Navigation Compose.
 *
 * Stage 1 wires only placeholder destinations for the three [uk.gov.defra.mmocatchrecord.core.root.RootPhase]
 * states; real feature destinations are added in later stages.
 */
sealed class Destination(
    val route: String,
) {
    data object SignIn : Destination("sign_in")

    data object AppLock : Destination("app_lock")

    data object Home : Destination("home")
}
