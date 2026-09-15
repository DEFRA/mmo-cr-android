@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import uk.gov.defra.mmocatchrecord.core.root.AppLockScreen
import uk.gov.defra.mmocatchrecord.core.root.RootEvent
import uk.gov.defra.mmocatchrecord.core.root.SplashScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.CatchRecordFlowEntryScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.DepartureDateScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.DeparturePortScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.DraftResumeScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.PhaseOneTwoCompleteScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.ReturnDateScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.ReturnPortScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.TripTodayScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.VesselSelectionScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.routeFor
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeScreen
import uk.gov.defra.mmocatchrecord.feature.signin.presentation.SignInScreen

@Suppress("FunctionNaming", "LongMethod")
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
        composable(Destination.Splash.route) {
            SplashScreen()
        }
        composable(Destination.SignIn.route) {
            SignInScreen(onSignedIn = { onRootEvent(RootEvent.SignedIn) })
        }
        composable(Destination.AppLock.route) {
            AppLockScreen(onUnlock = { onRootEvent(RootEvent.BiometricUnlockRequested) })
        }
        composable(Destination.Home.route) {
            HomeScreen(
                onSignOut = { onRootEvent(RootEvent.SignedOut) },
                onCreateCatchRecord = { navController.navigate(Destination.CatchRecordFlow.GRAPH_ROUTE) },
            )
        }
        navigation(
            startDestination = Destination.CatchRecordFlow.ENTRY_ROUTE,
            route = Destination.CatchRecordFlow.GRAPH_ROUTE,
        ) {
            composable(Destination.CatchRecordFlow.ENTRY_ROUTE) { backStackEntry ->
                val flowViewModel = catchRecordFlowViewModel(navController, backStackEntry)
                CatchRecordFlowEntryScreen(
                    viewModel = flowViewModel,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Destination.CatchRecordFlow.ENTRY_ROUTE) { inclusive = true }
                        }
                    },
                )
            }
            composable(Destination.CatchRecordFlow.DRAFT_RESUME_ROUTE) { backStackEntry ->
                DraftResumeScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.VESSEL_SELECTION_ROUTE) { backStackEntry ->
                VesselSelectionScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.TRIP_TODAY_ROUTE) { backStackEntry ->
                TripTodayScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.DEPARTURE_DATE_ROUTE) { backStackEntry ->
                DepartureDateScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.RETURN_DATE_ROUTE) { backStackEntry ->
                ReturnDateScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.DEPARTURE_PORT_ROUTE) { backStackEntry ->
                DeparturePortScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.RETURN_PORT_ROUTE) { backStackEntry ->
                ReturnPortScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.GEAR_LOOP_ROUTE) { backStackEntry ->
                PhaseOneTwoCompleteScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun catchRecordFlowViewModel(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
): CatchRecordFlowViewModel {
    val graphEntry =
        remember(backStackEntry) { navController.getBackStackEntry(Destination.CatchRecordFlow.GRAPH_ROUTE) }
    return hiltViewModel(graphEntry)
}
