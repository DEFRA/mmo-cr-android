package uk.gov.defra.mmocatchrecord.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import uk.gov.defra.mmocatchrecord.core.root.AppLockScreen
import uk.gov.defra.mmocatchrecord.core.root.HomeScreen
import uk.gov.defra.mmocatchrecord.core.root.RootEvent
import uk.gov.defra.mmocatchrecord.core.root.SignInScreen

/**
 * App-wide [NavHost] wiring the Stage-1 placeholder destinations. Real feature graphs are added in
 * later stages as additional `composable(...)` entries / nested graphs.
 */
@Composable
fun MmoNavHost(
    navController: NavHostController,
    startDestination: Destination,
    onRootEvent: (RootEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
    ) {
        composable(Destination.SignIn.route) {
            SignInScreen(onSignIn = { onRootEvent(RootEvent.SignedIn) })
        }
        composable(Destination.AppLock.route) {
            AppLockScreen(onUnlock = { onRootEvent(RootEvent.BiometricUnlockRequested) })
        }
        composable(Destination.Home.route) {
            HomeScreen(onSignOut = { onRootEvent(RootEvent.SignedOut) })
        }
    }
}
