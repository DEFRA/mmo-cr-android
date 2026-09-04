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
import uk.gov.defra.mmocatchrecord.common.navigation.Destination
import uk.gov.defra.mmocatchrecord.common.navigation.MmoNavHost

private fun RootPhase.toDestination(): Destination =
    when (this) {
        RootPhase.SIGN_IN -> Destination.SignIn
        RootPhase.APP_LOCK -> Destination.AppLock
        RootPhase.HOME -> Destination.Home
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
        val destination = uiState.phase.toDestination()
        navController.navigate(destination.route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    MmoNavHost(
        navController = navController,
        startDestination = uiState.phase.toDestination(),
        onRootEvent = sessionCoordinator::dispatch,
        modifier = modifier,
    )
}
