@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxGroup
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearSpeciesChecklistSupport.replaceGearUse

object RemoveSpeciesScreenTestTags {
    const val SCREEN = "remove_species_screen"
    const val ERROR_MESSAGE = "remove_species_error_message"
    const val CHECKBOX_PREFIX = "remove_species_checkbox"
    const val CONTINUE_ACTION = "remove_species_continue_action"
}

/** Dedicated "Remove species from vessel {vessel}" page, reached from [GearSpeciesChecklistScreen]'s link. */
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val gearUse = draft?.gearUses?.firstOrNull { it.id == gearUseId }
    val vesselName = draft?.let { d -> state.vessels.firstOrNull { it.id == d.vesselId }?.name }

    CatchRecordWizardScaffold(
        screenTestTag = RemoveSpeciesScreenTestTags.SCREEN,
        title =
            vesselName?.let { stringResource(R.string.remove_species_title, it) }
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
    modifier: Modifier = Modifier,
) {
    var checkedIds by rememberSaveable(gearUse.id) { mutableStateOf(emptySet<String>()) }
    var showError by rememberSaveable(gearUse.id) { mutableStateOf(false) }

    val options = GearSpeciesChecklistSupport.checkboxOptionsFor(gearUse, speciesList)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (showError) {
            Text(
                text = stringResource(R.string.remove_species_error_required),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .testTag(RemoveSpeciesScreenTestTags.ERROR_MESSAGE)
                        .semantics { liveRegion = LiveRegionMode.Polite },
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
        PrimaryActionButton(
            text = stringResource(R.string.continue_action),
            onClick = {
                if (checkedIds.isEmpty()) {
                    showError = true
                } else {
                    val updatedGearUse =
                        gearUse.copy(speciesWeights = gearUse.speciesWeights.filterNot { it.speciesId in checkedIds })
                    onDelete(replaceGearUse(draft, updatedGearUse))
                }
            },
            modifier = Modifier.testTag(RemoveSpeciesScreenTestTags.CONTINUE_ACTION),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun RemoveSpeciesScreen_Preview() {
    val gearType = GearType(id = "gear-seine-nets", name = "Boat or vessel seine")
    val vessel = Vessel(id = "vessel-1", name = "515 CB PRO")
    val sampleSpecies = Species(id = "species-atp", name = "Sand smelt (ATP)", faoCode = "ATP")
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
            vessels = listOf(vessel),
            gearTypes = listOf(gearType),
            species = listOf(sampleSpecies),
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            RemoveSpeciesScreen(
                state = state,
                gearUseId = gearUse.id,
                onDelete = {},
                onBack = {},
            )
        }
    }
}
