@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

object CatchRecordFlowEntryScreenTestTags {
    const val SCREEN = "catch_record_flow_entry_screen"
    const val ERROR_MESSAGE = "catch_record_flow_entry_error"
}

object PhaseOneTwoCompleteScreenTestTags {
    const val SCREEN = "catch_record_phase_one_two_complete_screen"
}

@Suppress("FunctionNaming")
@Composable
fun CatchRecordFlowEntryScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
    }

    LaunchedEffect(state.currentStep, state.status, hasNavigated) {
        val shouldNavigate = state.status !is UiStatus.Loading && state.status !is UiStatus.Error
        if (shouldNavigate && !hasNavigated) {
            hasNavigated = true
            onNavigate(routeFor(state.currentStep))
        }
    }

    CatchRecordWizardScaffold(
        screenTestTag = CatchRecordFlowEntryScreenTestTags.SCREEN,
        title = stringResource(R.string.create_catch_record),
        onBack = {},
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, CatchRecordFlowEntryScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content -> WizardLoadingState()
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun PhaseOneTwoCompleteScreen(
    viewModel: CatchRecordFlowViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = PhaseOneTwoCompleteScreenTestTags.SCREEN,
        title = stringResource(R.string.phase_one_two_complete_title),
        onBack = onBack,
    ) {
        when (val status = state.status) {
            UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, CatchRecordFlowEntryScreenTestTags.ERROR_MESSAGE)
            UiStatus.Idle, is UiStatus.Content -> {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        text = stringResource(R.string.phase_one_two_complete_body),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
