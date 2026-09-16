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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEntryScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DraftResumeScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.PhaseThreeCompleteScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.applySubmissionResultNavOptions
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.routeFor
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearSearchScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearSpeciesChecklistScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearSpeciesSearchScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearStatRectangleScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearSummaryScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing.NotLandedStraightAwayDecisionScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing.NotLandedStraightAwaySpeciesScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.CheckYourAnswersScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.LateSubmissionWarningScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.SubmissionPendingSyncScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.SubmissionSuccessScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.DepartureDateScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.DeparturePortScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.ReturnDateScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.ReturnPortScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.TripTodayScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.VesselSelectionScreen
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
            composable(Destination.CatchRecordFlow.GEAR_SEARCH_ROUTE) { backStackEntry ->
                GearSearchScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.GEAR_MEASUREMENT_ROUTE) { backStackEntry ->
                GearMeasurementScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.GEAR_SUMMARY_ROUTE) { backStackEntry ->
                GearSummaryScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.GEAR_STAT_RECTANGLE_ROUTE) { backStackEntry ->
                GearStatRectangleScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.GEAR_SPECIES_SEARCH_ROUTE) { backStackEntry ->
                GearSpeciesSearchScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.GEAR_SPECIES_CHECKLIST_ROUTE) { backStackEntry ->
                GearSpeciesChecklistScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.NOT_LANDED_STRAIGHT_AWAY_DECISION_ROUTE) { backStackEntry ->
                NotLandedStraightAwayDecisionScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.NOT_LANDED_STRAIGHT_AWAY_SPECIES_ROUTE) { backStackEntry ->
                NotLandedStraightAwaySpeciesScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.PHASE_THREE_COMPLETE_ROUTE) { backStackEntry ->
                PhaseThreeCompleteScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.LATE_SUBMISSION_WARNING_ROUTE) { backStackEntry ->
                LateSubmissionWarningScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.CHECK_YOUR_ANSWERS_ROUTE) { backStackEntry ->
                CheckYourAnswersScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { step ->
                        navController.navigate(routeFor(step)) { applySubmissionResultNavOptions(step) }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.SUBMISSION_SUCCESS_ROUTE) { backStackEntry ->
                SubmissionSuccessScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    // Placeholder destination: no "my catch records" list screen exists yet (out of scope
                    // for Phase 8) — navigates to the app's existing Home screen instead, clearing the
                    // whole wizard graph off the back stack so the user cannot navigate "back" into a
                    // now-submitted draft.
                    onViewRecords = {
                        navController.navigate(Destination.Home.route) {
                            popUpTo(Destination.CatchRecordFlow.GRAPH_ROUTE) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.CatchRecordFlow.SUBMISSION_PENDING_SYNC_ROUTE) { backStackEntry ->
                SubmissionPendingSyncScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    // Same placeholder destination as SubmissionSuccessScreen above.
                    onViewRecords = {
                        navController.navigate(Destination.Home.route) {
                            popUpTo(Destination.CatchRecordFlow.GRAPH_ROUTE) { inclusive = true }
                        }
                    },
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
