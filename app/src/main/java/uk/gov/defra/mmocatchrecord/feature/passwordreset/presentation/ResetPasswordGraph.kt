@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordCodeRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordEmailRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordGraphRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordNewPasswordRoute
import uk.gov.defra.mmocatchrecord.common.navigation.ResetPasswordSuccessRoute

/**
 * Adds the reset graph (ADR 0027); the view model is scoped to the graph entry and cleared on exit.
 */
internal fun NavGraphBuilder.resetPasswordGraph(
    navController: NavHostController,
    onSignedIn: () -> Unit,
    viewModelFor: @Composable (NavBackStackEntry) -> ResetPasswordViewModel = { graphViewModel(navController, it) },
) {
    navigation<ResetPasswordGraphRoute>(startDestination = ResetPasswordEmailRoute) {
        composable<ResetPasswordEmailRoute> { entry ->
            ResetStepHost(ResetStep.EmailEntry, viewModelFor(entry), navController, onSignedIn) { host ->
                ResetPasswordEmailContent(
                    state = host.state,
                    emailState = host.viewModel.emailState,
                    chrome = host.chrome,
                    onSubmit = { host.viewModel.dispatch(ResetPasswordEvent.EmailSubmitted) },
                    actions = host.actions { exitResetGraph(navController) },
                )
            }
        }
        composable<ResetPasswordCodeRoute> { entry ->
            ResetStepHost(ResetStep.CodeEntry, viewModelFor(entry), navController, onSignedIn) { host ->
                CodeStep(host)
            }
        }
        composable<ResetPasswordNewPasswordRoute> { entry ->
            ResetStepHost(ResetStep.NewPassword, viewModelFor(entry), navController, onSignedIn) { host ->
                CreateNewPasswordContent(
                    state = host.state,
                    fields = host.viewModel.passwordFields(),
                    chrome = host.chrome,
                    onSubmit = { host.viewModel.dispatch(ResetPasswordEvent.NewPasswordSubmitted) },
                    // Reached only after the "Cancel resetting your password?" dialog is confirmed.
                    actions = host.actions { exitResetGraph(navController) },
                )
            }
        }
        composable<ResetPasswordSuccessRoute> { entry ->
            ResetStepHost(ResetStep.Success, viewModelFor(entry), navController, onSignedIn) { host ->
                PasswordResetSuccessContent(
                    state = host.state,
                    chrome = host.chrome,
                    onSignIn = { exitResetGraph(navController) },
                    actions = host.actions { exitResetGraph(navController) },
                )
            }
        }
    }
}

/** What a step destination hands to its screen: the view model, its current state and the shared chrome. */
internal class ResetStepHostScope(
    val viewModel: ResetPasswordViewModel,
    val state: ResetPasswordViewState,
    val chrome: ResetChrome,
) {
    fun actions(onBack: () -> Unit) =
        ResetScreenActions(
            onBack = onBack,
            onRecheckConnection = { viewModel.dispatch(ResetPasswordEvent.ConnectionRecheckRequested) },
            onBrowserNoticeShown = { viewModel.dispatch(ResetPasswordEvent.BrowserNoticeShown) },
        )
}

/**
 * Keeps navigation a function of the view model's step: if the step is not the one this destination shows,
 * the matching destination is opened. Also hands over to the session if the browser fallback signed in.
 */
@Composable
private fun ResetStepHost(
    ownStep: ResetStep,
    viewModel: ResetPasswordViewModel,
    navController: NavController,
    onSignedIn: () -> Unit,
    content: @Composable (ResetStepHostScope) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) {
        if (state.step != ownStep) navigateToStep(navController, state.step)
    }
    LaunchedEffect(state.signedInViaBrowser) {
        if (state.signedInViaBrowser) onSignedIn()
    }
    content(ResetStepHostScope(viewModel, state, rememberResetChrome()))
}

@Composable
private fun CodeStep(host: ResetStepHostScope) {
    val sendNewCode = { host.viewModel.dispatch(ResetPasswordEvent.SendNewCodeRequested) }
    ResetPasswordCodeContent(
        state = host.state,
        codeState = host.viewModel.codeState,
        chrome = host.chrome,
        onSubmit = { host.viewModel.dispatch(ResetPasswordEvent.CodeSubmitted) },
        onSendNewCode = sendNewCode,
        actions = host.actions(sendNewCode),
    )
}

private fun ResetPasswordViewModel.passwordFields() =
    NewPasswordFields(
        newPassword = newPasswordState,
        confirmPassword = confirmPasswordState,
        onNewPasswordVisibilityChange = { dispatch(ResetPasswordEvent.NewPasswordVisibilityChanged(it)) },
        onConfirmPasswordVisibilityChange = { dispatch(ResetPasswordEvent.ConfirmPasswordVisibilityChanged(it)) },
        policy = passwordPolicy,
    )

/** Opens the destination for [step]; each branch also trims the back stack so Back cannot revisit a finished step. */
internal fun navigateToStep(
    navController: NavController,
    step: ResetStep,
) {
    when (step) {
        ResetStep.EmailEntry -> navController.popBackStack(ResetPasswordEmailRoute, inclusive = false)
        ResetStep.CodeEntry -> navController.navigate(ResetPasswordCodeRoute) { launchSingleTop = true }
        ResetStep.NewPassword ->
            navController.navigate(ResetPasswordNewPasswordRoute) {
                popUpTo<ResetPasswordCodeRoute> { inclusive = true }
                launchSingleTop = true
            }
        ResetStep.Success ->
            navController.navigate(ResetPasswordSuccessRoute) {
                popUpTo<ResetPasswordNewPasswordRoute> { inclusive = true }
                launchSingleTop = true
            }
    }
}

/** Leaves the journey for Sign in; clearing the graph from the stack clears its view model and so the reset. */
internal fun exitResetGraph(navController: NavController) {
    navController.popBackStack(ResetPasswordGraphRoute, inclusive = true)
}

@Composable
private fun graphViewModel(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
): ResetPasswordViewModel {
    val graphEntry = remember(backStackEntry) { navController.getBackStackEntry<ResetPasswordGraphRoute>() }
    return hiltViewModel(graphEntry)
}
