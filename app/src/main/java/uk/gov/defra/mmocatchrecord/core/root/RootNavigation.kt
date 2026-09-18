package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import uk.gov.defra.mmocatchrecord.common.navigation.AppLockRoute
import uk.gov.defra.mmocatchrecord.common.navigation.HomeRoute
import uk.gov.defra.mmocatchrecord.common.navigation.MmoNavHost
import uk.gov.defra.mmocatchrecord.common.navigation.SignInRoute
import uk.gov.defra.mmocatchrecord.common.navigation.SplashRoute

private fun RootPhase.toRoute(): Any =
    when (this) {
        RootPhase.SPLASH -> SplashRoute
        RootPhase.SIGN_IN -> SignInRoute
        RootPhase.APP_LOCK -> AppLockRoute
        RootPhase.HOME -> HomeRoute
    }

/**
 * Root composable: observes [SessionCoordinator]'s derived [RootPhase] and keeps the nav graph's current
 * destination in sync with it, hosting the Stage-1 placeholder screens via [MmoNavHost].
 *
 * Also re-evaluates the re-entry decision whenever the app returns to the foreground
 * ([Lifecycle.Event.ON_RESUME]) — otherwise an elapsed biometric re-entry timeout would only ever be
 * enforced once, at [SessionCoordinator] construction, and a resumed app would stay on HOME forever.
 */
@Composable
fun RootNavigation(
    sessionCoordinator: SessionCoordinator,
    modifier: Modifier = Modifier,
) {
    val uiState by sessionCoordinator.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, sessionCoordinator) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    sessionCoordinator.dispatch(RootEvent.AppResumed)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.phase) {
        val destination = uiState.phase.toRoute()
        navController.navigate(destination) {
            // popUpTo(0) is a fragile idiom: id 0 never matches a real (hash-based) destination id, so
            // NavController silently pops nothing — every phase change (now SPLASH, SIGN_IN, APP_LOCK,
            // HOME) just pushes a new entry, and the back stack grows unbounded across the app's
            // lifetime. popUpTo(navController.graph.id) is the documented way to clear the entire back
            // stack: the graph's own id is always valid, so this reliably removes every prior phase
            // destination (including SPLASH, which must never be reachable via back navigation once the
            // real phase resolves).
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    MmoNavHost(
        navController = navController,
        startDestination = uiState.phase.toRoute(),
        onRootEvent = sessionCoordinator::dispatch,
        modifier = modifier,
    )
}
