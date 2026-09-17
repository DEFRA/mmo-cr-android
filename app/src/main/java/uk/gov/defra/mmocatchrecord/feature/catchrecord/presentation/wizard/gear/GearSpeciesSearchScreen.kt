@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteField
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteOption
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextGearUsePendingSpecies
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesSearch
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesSearchInputError
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesSearchValidator

object GearSpeciesSearchScreenTestTags {
    const val SCREEN = "gear_species_search_screen"
    const val AUTOCOMPLETE_FIELD = "gear_species_search_autocomplete_field"
    const val LIVE_REGION = "gear_species_search_live_region"
    const val SUGGESTION_PREFIX = "gear_species_search_suggestion"
    const val NO_MATCHES = "gear_species_search_no_matches"
    const val SAVE_ACTION = "gear_species_search_save_action"
    const val ERROR_MESSAGE = "gear_species_search_error_message"
}

/**
 * "Which species did you catch with {gear}?" — reuses the exact same accessible autocomplete pattern as
 * gear/port search. The current gear (and its display title) is derived from [nextGearUsePendingSpecies]
 * for the normal add-species flow, or explicitly from [editGearUseId] when editing an already-completed
 * gear's species via a check-your-answers "Change" link (finding: "Completed gear measurement/stat/species
 * must be editable through Change and back flows") — mirroring [GearStatRectangleScreen]'s own "current
 * gear" derivation.
 */
@Suppress("FunctionNaming")
@Composable
fun GearSpeciesSearchScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    editGearUseId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GearSpeciesSearchScreen(
        state = state,
        editGearUseId = editGearUseId,
        onSubmit = { speciesId ->
            if (editGearUseId != null) {
                viewModel.dispatch(CatchRecordFlowEvent.SpeciesAddedToGearUse(editGearUseId, speciesId))
            } else {
                viewModel.dispatch(CatchRecordFlowEvent.SpeciesAddedToCurrentGear(speciesId))
            }
            onNavigate(WizardStep.GearSpeciesChecklist)
        },
        onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun GearSpeciesSearchScreen(
    state: CatchRecordFlowViewState,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    editGearUseId: String? = null,
    onRetry: () -> Unit = {},
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val currentGearUse =
        if (editGearUseId != null) {
            draft?.gearUses?.firstOrNull { it.id == editGearUseId }
        } else {
            draft?.let(::nextGearUsePendingSpecies)
        }
    val gearType = currentGearUse?.let { gearUse -> state.gearTypes.firstOrNull { it.id == gearUse.gearTypeId } }
    val gearNameWithMeasurement =
        currentGearUse?.let { GearStatRectangleSupport.gearNameWithIdentifyingMeasurementFor(gearType, it) }

    CatchRecordWizardScaffold(
        screenTestTag = GearSpeciesSearchScreenTestTags.SCREEN,
        title =
            gearNameWithMeasurement?.let { stringResource(R.string.gear_species_search_title, it) }
                ?: stringResource(R.string.gear_species_search_title_fallback),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = GearSpeciesSearchScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = onRetry,
                )
            is UiStatus.Content ->
                if (draft == null || currentGearUse == null) {
                    // Defensive only: normal navigation only reaches this screen while
                    // nextGearUsePendingSpecies(draft) is non-null (add path), or editGearUseId resolves to
                    // a real gear use (check-your-answers edit path) — see nextWizardStepForDraft/editRouteFor.
                    WizardErrorState(
                        stringResource(R.string.gear_species_search_title_fallback),
                        GearSpeciesSearchScreenTestTags.ERROR_MESSAGE,
                    )
                } else {
                    GearSpeciesSearchScreenContent(species = state.species, onSubmit = onSubmit)
                }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun GearSpeciesSearchScreenContent(
    species: List<Species>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedSpeciesId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<SpeciesSearchInputError?>(null) }
    val suggestions = remember(searchQuery, species) { SpeciesSearch.filterSuggestions(searchQuery, species) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsAutocompleteField(
            label = stringResource(R.string.gear_species_search_autocomplete_label),
            value = searchQuery,
            options = suggestions.map { GdsAutocompleteOption(it.id, it.name) },
            onValueChange = {
                searchQuery = it
                selectedSpeciesId = species.firstOrNull { s -> s.name.equals(it, ignoreCase = true) }?.id
                error = null
            },
            onOptionSelected = {
                searchQuery = it.label
                selectedSpeciesId = it.id
                error = null
            },
            fieldTestTag = GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD,
            liveRegionTestTag = GearSpeciesSearchScreenTestTags.LIVE_REGION,
            suggestionTestTagPrefix = GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX,
            noMatchesTestTag = GearSpeciesSearchScreenTestTags.NO_MATCHES,
            noMatchesText = stringResource(R.string.gear_species_search_no_matches_found),
        )
        error?.let {
            Text(
                text =
                    when (it) {
                        SpeciesSearchInputError.EmptyQuery -> stringResource(R.string.gear_species_search_error_empty)
                        SpeciesSearchInputError.NoValidSelection ->
                            stringResource(R.string.gear_species_search_error_no_selection)
                    },
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val id = selectedSpeciesId
                val validationError = SpeciesSearchValidator.validate(searchQuery, id)
                if (validationError != null || id == null) {
                    error = validationError
                } else {
                    onSubmit(id)
                }
            },
            modifier = Modifier.testTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION),
        )
    }
}
