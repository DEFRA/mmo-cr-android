@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioOption
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep

object VesselSelectionScreenTestTags {
    const val SCREEN = "vessel_selection_screen"
    const val OPTION_PREFIX = "vessel_selection_option"
    const val ERROR_MESSAGE = "vessel_selection_error_message"
}

/**
 * Selecting a vessel now always writes a new draft (ADR 0018), so navigation waits for that write to
 * succeed — a failure must surface here rather than on the next screen.
 */
@Suppress("FunctionNaming")
@Composable
fun VesselSelectionScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Not rememberSaveable: back-navigation must clear it, or the user is bounced straight forward again.
    var isAwaitingDraft by remember { mutableStateOf(false) }

    LaunchedEffect(isAwaitingDraft, state.status, state.currentStep) {
        if (isAwaitingDraft && state.currentStep == WizardStep.TripToday && state.status is UiStatus.Content) {
            isAwaitingDraft = false
            onNavigate(WizardStep.TripToday)
        }
    }

    CatchRecordWizardScaffold(
        screenTestTag = VesselSelectionScreenTestTags.SCREEN,
        title = stringResource(R.string.vessel_selection_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = VesselSelectionScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            UiStatus.Idle, is UiStatus.Content ->
                VesselSelectionScreenContent(
                    vessels = state.vessels,
                    onSubmit = {
                        isAwaitingDraft = true
                        viewModel.dispatch(CatchRecordFlowEvent.VesselSelected(it))
                    },
                )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun VesselSelectionScreenContent(
    vessels: List<Vessel>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedVesselId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsRadioGroup(
            options = vessels.map { GdsRadioOption(it.id, it.name) },
            selectedOptionId = selectedVesselId,
            onOptionSelected = {
                selectedVesselId = it
                onSubmit(it)
            },
            optionTestTagPrefix = VesselSelectionScreenTestTags.OPTION_PREFIX,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun VesselSelectionScreenContentPreview() {
    MmoTheme {
        VesselSelectionScreenContent(
            vessels = listOf(Vessel("vessel-achilles", "ACHILLES"), Vessel("vessel-hercules", "HERCULES")),
            onSubmit = {},
        )
    }
}
