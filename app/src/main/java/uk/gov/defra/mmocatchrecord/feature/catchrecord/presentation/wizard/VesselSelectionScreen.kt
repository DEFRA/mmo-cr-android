@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioOption
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

object VesselSelectionScreenTestTags {
    const val SCREEN = "vessel_selection_screen"
    const val OPTION_PREFIX = "vessel_selection_option"
    const val SAVE_ACTION = "vessel_selection_save_action"
    const val ERROR_MESSAGE = "vessel_selection_error_message"
}

@Suppress("FunctionNaming")
@Composable
fun VesselSelectionScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = VesselSelectionScreenTestTags.SCREEN,
        title = stringResource(R.string.vessel_selection_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        VesselSelectionScreenContent(
            vessels = state.vessels,
            onSubmit = {
                viewModel.dispatch(CatchRecordFlowEvent.VesselSelected(it))
                onNavigate(WizardStep.TripToday)
            },
        )
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
    var showError by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsRadioGroup(
            options = vessels.map { GdsRadioOption(it.id, it.name) },
            selectedOptionId = selectedVesselId,
            onOptionSelected = {
                selectedVesselId = it
                showError = false
            },
            optionTestTagPrefix = VesselSelectionScreenTestTags.OPTION_PREFIX,
        )
        if (showError) {
            Text(
                text = stringResource(R.string.vessel_selection_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(VesselSelectionScreenTestTags.ERROR_MESSAGE),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val vesselId = selectedVesselId
                if (vesselId == null) {
                    showError = true
                } else {
                    onSubmit(vesselId)
                }
            },
            modifier = Modifier.testTag(VesselSelectionScreenTestTags.SAVE_ACTION),
        )
    }
}
