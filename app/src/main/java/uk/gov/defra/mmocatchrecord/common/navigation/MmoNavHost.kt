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
import androidx.navigation.toRoute
import uk.gov.defra.mmocatchrecord.core.root.AppLockScreen
import uk.gov.defra.mmocatchrecord.core.root.RootEvent
import uk.gov.defra.mmocatchrecord.core.root.SplashScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordEntryRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEntryScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CheckYourAnswersRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DepartureDateRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DeparturePortRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DraftResumeRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DraftResumeScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.GearMeasurementRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.GearSearchRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.GearSpeciesChecklistRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.GearSpeciesSearchRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.GearStatRectangleRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.GearSummaryRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.LateSubmissionWarningRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.NotLandedStraightAwayDecisionRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.NotLandedStraightAwaySpeciesRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.PhaseThreeCompleteRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.PhaseThreeCompleteScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.ReturnDateRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.ReturnPortRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.SubmissionPendingSyncRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.SubmissionSuccessRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.TripTodayRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.VesselSelectionRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.applySubmissionResultNavOptions
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.editRouteFor
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

/**
 * Type-safe Navigation Compose routes (ADR 0007/0010, kotlinx.serialization `@Serializable` route
 * classes/objects — see `AppRoutes.kt` and `feature.catchrecord.presentation.wizard.flow.CatchRecordRoutes.kt`)
 * replace what were previously hand-built string routes throughout this app.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun MmoNavHost(
    navController: NavHostController,
    startDestination: Any,
    onRootEvent: (RootEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<SplashRoute> {
            SplashScreen()
        }
        composable<SignInRoute> {
            SignInScreen(onSignedIn = { onRootEvent(RootEvent.SignedIn) })
        }
        composable<AppLockRoute> {
            AppLockScreen(onUnlock = { onRootEvent(RootEvent.BiometricUnlockRequested) })
        }
        composable<HomeRoute> {
            HomeScreen(
                onSignOut = { onRootEvent(RootEvent.SignedOut) },
                onCreateCatchRecord = { navController.navigate(CatchRecordGraphRoute) },
            )
        }
        navigation<CatchRecordGraphRoute>(startDestination = CatchRecordEntryRoute) {
            composable<CatchRecordEntryRoute> { backStackEntry ->
                val flowViewModel = catchRecordFlowViewModel(navController, backStackEntry)
                CatchRecordFlowEntryScreen(
                    viewModel = flowViewModel,
                    onNavigate = { step ->
                        navController.navigate(routeFor(step)) {
                            popUpTo<CatchRecordEntryRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<DraftResumeRoute> { backStackEntry ->
                DraftResumeScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<VesselSelectionRoute> { backStackEntry ->
                VesselSelectionScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<TripTodayRoute> { backStackEntry ->
                TripTodayScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<DepartureDateRoute> { backStackEntry ->
                DepartureDateScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ReturnDateRoute> { backStackEntry ->
                ReturnDateScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<DeparturePortRoute> { backStackEntry ->
                DeparturePortScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ReturnPortRoute> { backStackEntry ->
                ReturnPortScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GearSearchRoute> { backStackEntry ->
                GearSearchScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GearMeasurementRoute> { backStackEntry ->
                val editGearUseId = backStackEntry.toRoute<GearMeasurementRoute>().editGearUseId
                GearMeasurementScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    editGearUseId = editGearUseId,
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GearSummaryRoute> { backStackEntry ->
                GearSummaryScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GearStatRectangleRoute> { backStackEntry ->
                val editGearUseId = backStackEntry.toRoute<GearStatRectangleRoute>().editGearUseId
                GearStatRectangleScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    editGearUseId = editGearUseId,
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GearSpeciesSearchRoute> { backStackEntry ->
                val editGearUseId = backStackEntry.toRoute<GearSpeciesSearchRoute>().editGearUseId
                GearSpeciesSearchScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    editGearUseId = editGearUseId,
                    onNavigate = { step ->
                        navController.navigate(
                            if (editGearUseId != null) editRouteFor(step, editGearUseId) else routeFor(step),
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GearSpeciesChecklistRoute> { backStackEntry ->
                val editGearUseId = backStackEntry.toRoute<GearSpeciesChecklistRoute>().editGearUseId
                GearSpeciesChecklistScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    editGearUseId = editGearUseId,
                    onNavigate = { step ->
                        navController.navigate(
                            if (editGearUseId != null) editRouteFor(step, editGearUseId) else routeFor(step),
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NotLandedStraightAwayDecisionRoute> { backStackEntry ->
                NotLandedStraightAwayDecisionScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NotLandedStraightAwaySpeciesRoute> { backStackEntry ->
                NotLandedStraightAwaySpeciesScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<PhaseThreeCompleteRoute> { backStackEntry ->
                PhaseThreeCompleteScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onBack = { navController.popBackStack() },
                )
            }
            composable<LateSubmissionWarningRoute> { backStackEntry ->
                LateSubmissionWarningScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { navController.navigate(routeFor(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<CheckYourAnswersRoute> { backStackEntry ->
                CheckYourAnswersScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    onNavigate = { step ->
                        navController.navigate(routeFor(step)) { applySubmissionResultNavOptions(step) }
                    },
                    onNavigateToEdit = { step, gearUseId -> navController.navigate(editRouteFor(step, gearUseId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SubmissionSuccessRoute> { backStackEntry ->
                SubmissionSuccessScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    // Placeholder destination: no "my catch records" list screen exists yet (out of scope
                    // for Phase 8) — navigates to the app's existing Home screen instead, clearing the
                    // whole wizard graph off the back stack so the user cannot navigate "back" into a
                    // now-submitted draft.
                    onViewRecords = {
                        navController.navigate(HomeRoute) {
                            popUpTo<CatchRecordGraphRoute> { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SubmissionPendingSyncRoute> { backStackEntry ->
                SubmissionPendingSyncScreen(
                    viewModel = catchRecordFlowViewModel(navController, backStackEntry),
                    // Same placeholder destination as SubmissionSuccessScreen above.
                    onViewRecords = {
                        navController.navigate(HomeRoute) {
                            popUpTo<CatchRecordGraphRoute> { inclusive = true }
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
    val graphEntry = remember(backStackEntry) { navController.getBackStackEntry<CatchRecordGraphRoute>() }
    return hiltViewModel(graphEntry)
}
