@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsLinkAction
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearSpeciesChecklistSupport.replaceGearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map.MapSupport

object RemoveSpeciesScreenTestTags {
    const val SCREEN = "remove_species_screen"
    const val ERROR_MESSAGE = "remove_species_error_message"
    const val CHECKBOX_PREFIX = "remove_species_checkbox"
    const val DELETE_ACTION = "remove_species_delete_action"
    const val CANCEL_ACTION = "remove_species_cancel_action"
}

/** Dedicated "Remove a species caught with {gear}" page, reached from [GearSpeciesChecklistScreen]'s link. */
@Suppress("FunctionNaming")
@Composable
fun RemoveSpeciesScreen(
    viewModel: CatchRecordFlowViewModel,
    gearUseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemoveSpeciesScreen(
        state = state,
        gearUseId = gearUseId,
        onDelete = { updatedDraft ->
            viewModel.dispatch(CatchRecordFlowEvent.SpeciesRemoved(updatedDraft))
            onBack()
        },
        onCancel = onBack,
        onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RemoveSpeciesScreen(
    state: CatchRecordFlowViewState,
    gearUseId: String,
    onDelete: (CatchRecordDraft) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val gearUse = draft?.gearUses?.firstOrNull { it.id == gearUseId }
    val gearType = gearUse?.let { use -> state.gearTypes.firstOrNull { it.id == use.gearTypeId } }
    val gearNameWithMeasurement = gearUse?.let { MapSupport.gearNameWithIdentifyingMeasurementFor(gearType, it) }

    CatchRecordWizardScaffold(
        screenTestTag = RemoveSpeciesScreenTestTags.SCREEN,
        title =
            gearNameWithMeasurement?.let { stringResource(R.string.remove_species_title, it) }
                ?: stringResource(R.string.remove_species_title_fallback),
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = RemoveSpeciesScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = onRetry,
                )
            is UiStatus.Content ->
                if (draft == null || gearUse == null) {
                    // Defensive only — see the equivalent branch in GearSpeciesChecklistScreen.
                    WizardErrorState(
                        stringResource(R.string.remove_species_title_fallback),
                        RemoveSpeciesScreenTestTags.ERROR_MESSAGE,
                    )
                } else {
                    RemoveSpeciesScreenContent(
                        draft = draft,
                        gearUse = gearUse,
                        speciesList = state.species,
                        onDelete = onDelete,
                        onCancel = onCancel,
                    )
                }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun RemoveSpeciesScreenContent(
    draft: CatchRecordDraft,
    gearUse: GearUse,
    speciesList: List<Species>,
    onDelete: (CatchRecordDraft) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var checkedIds by rememberSaveable(gearUse.id) { mutableStateOf(emptySet<String>()) }
    var showError by remember(gearUse.id) { mutableStateOf(false) }

    val options = GearSpeciesChecklistSupport.checkboxOptionsFor(gearUse, speciesList)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            text = stringResource(R.string.remove_species_instruction),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (showError) {
            Text(
                text = stringResource(R.string.remove_species_error_required),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(RemoveSpeciesScreenTestTags.ERROR_MESSAGE),
            )
        }
        GdsCheckboxGroup(
            options = options,
            checkedOptionIds = checkedIds,
            onCheckedChange = { id, checked ->
                checkedIds = if (checked) checkedIds + id else checkedIds - id
                showError = false
            },
            optionTestTagPrefix = RemoveSpeciesScreenTestTags.CHECKBOX_PREFIX,
        )
        SecondaryActionButton(
            text = stringResource(R.string.delete_action),
            onClick = {
                if (checkedIds.isEmpty()) {
                    showError = true
                } else {
                    val updatedGearUse =
                        gearUse.copy(speciesWeights = gearUse.speciesWeights.filterNot { it.speciesId in checkedIds })
                    onDelete(replaceGearUse(draft, updatedGearUse))
                }
            },
            modifier = Modifier.testTag(RemoveSpeciesScreenTestTags.DELETE_ACTION),
        )
        GdsLinkAction(
            text = stringResource(R.string.cancel),
            onClick = onCancel,
            testTag = RemoveSpeciesScreenTestTags.CANCEL_ACTION,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun RemoveSpeciesScreen_Preview() {
    val gearType = GearType(id = "gear-seine-nets", name = "Boat or vessel seine")
    val sampleSpecies = Species(id = "species-crg", name = "Green crab (CRG)", faoCode = "CRG")
    val gearUse =
        GearUse(
            id = "gear-use-1",
            gearTypeId = gearType.id,
            statisticalSubRectangleCode = "38E95",
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = true,
            speciesWeights =
                listOf(
                    SpeciesWeightEntry(
                        id = "weight-1",
                        speciesId = sampleSpecies.id,
                        weightAboveMinimumSizeKg = 12.5,
                        confirmedCaught = true,
                    ),
                ),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = listOf(gearUse),
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            gearTypes = listOf(gearType),
            species = listOf(sampleSpecies),
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            RemoveSpeciesScreen(
                state = state,
                gearUseId = gearUse.id,
                onDelete = {},
                onCancel = {},
                onBack = {},
            )
        }
    }
}
