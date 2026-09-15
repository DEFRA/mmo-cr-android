@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.LongParameterList", "detekt.MaxLineLength")

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxOption
import uk.gov.defra.mmocatchrecord.common.design.GdsLinkAction
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericField
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericFieldKind
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision

object GearSpeciesChecklistScreenTestTags {
    const val SCREEN = "gear_species_checklist_screen"
    const val ERROR_SUMMARY = "gear_species_checklist_error_summary"
    const val CHECKLIST_ERROR = "gear_species_checklist_error_message"
    const val CHECKBOX_PREFIX = "gear_species_checklist_checkbox"
    const val ABOVE_MIN_FIELD_PREFIX = "gear_species_checklist_above_min_field"
    const val BELOW_MIN_FIELD_PREFIX = "gear_species_checklist_below_min_field"
    const val DISCARDED_FIELD_PREFIX = "gear_species_checklist_discarded_field"
    const val ADD_BELOW_MIN_ACTION_PREFIX = "gear_species_checklist_add_below_min_action"
    const val REMOVE_BELOW_MIN_ACTION_PREFIX = "gear_species_checklist_remove_below_min_action"
    const val ADD_DISCARDED_ACTION_PREFIX = "gear_species_checklist_add_discarded_action"
    const val REMOVE_DISCARDED_ACTION_PREFIX = "gear_species_checklist_remove_discarded_action"
    const val ADD_SPECIES_ACTION = "gear_species_checklist_add_species_action"
    const val REMOVE_SPECIES_ACTION = "gear_species_checklist_remove_species_action"
    const val SAVE_ACTION = "gear_species_checklist_save_action"
}

private fun rawValueKey(
    speciesId: String,
    kind: SpeciesWeightFieldKind,
): String = "$speciesId::${kind.name}"

/**
 * "Which species did you catch with {gear}?" checklist + progressive-disclosure weight capture (Phase 5A,
 * screen 2) — mirrors [GearSummaryScreenContent]'s checklist shape (checking a row reveals fields; "Add
 * a species"/"Remove a species" mirror "Add another gear"/"Remove gear" exactly), plus two new
 * add/remove-link progressive-disclosure cycles for the optional below-minimum/discarded weight fields.
 */
@Suppress("FunctionNaming")
@Composable
fun GearSpeciesChecklistScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GearSpeciesChecklistScreen(
        state = state,
        onRemoveSpecies = { updatedDraft -> viewModel.dispatch(CatchRecordFlowEvent.SpeciesRemoved(updatedDraft)) },
        onAddAnotherSpecies = { onNavigate(WizardStep.GearSpeciesSearch) },
        onSubmit = { updatedDraft ->
            val nextStep = nextWizardStepForDraft(updatedDraft)
            viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep))
            onNavigate(nextStep)
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun GearSpeciesChecklistScreen(
    state: CatchRecordFlowViewState,
    onRemoveSpecies: (CatchRecordDraft) -> Unit,
    onAddAnotherSpecies: () -> Unit,
    onSubmit: (CatchRecordDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val currentGearUse = draft?.let(::nextGearUsePendingSpecies)
    val gearType = currentGearUse?.let { gearUse -> state.gearTypes.firstOrNull { it.id == gearUse.gearTypeId } }
    val gearNameWithMeasurement =
        currentGearUse?.let { GearStatRectangleSupport.gearNameWithIdentifyingMeasurementFor(gearType, it) }

    CatchRecordWizardScaffold(
        screenTestTag = GearSpeciesChecklistScreenTestTags.SCREEN,
        title =
            gearNameWithMeasurement?.let { stringResource(R.string.gear_species_search_title, it) }
                ?: stringResource(R.string.gear_species_search_title_fallback),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, GearSpeciesChecklistScreenTestTags.CHECKLIST_ERROR)
            is UiStatus.Content ->
                if (draft == null || currentGearUse == null) {
                    // Defensive only — see the equivalent branch in GearSpeciesSearchScreen.
                    WizardErrorState(
                        stringResource(R.string.gear_species_search_title_fallback),
                        GearSpeciesChecklistScreenTestTags.CHECKLIST_ERROR,
                    )
                } else {
                    GearSpeciesChecklistScreenContent(
                        draft = draft,
                        gearUse = currentGearUse,
                        speciesList = state.species,
                        onRemoveSpecies = onRemoveSpecies,
                        onAddAnotherSpecies = onAddAnotherSpecies,
                        onSubmit = onSubmit,
                    )
                }
        }
    }
}

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun GearSpeciesChecklistScreenContent(
    draft: CatchRecordDraft,
    gearUse: GearUse,
    speciesList: List<Species>,
    onRemoveSpecies: (CatchRecordDraft) -> Unit,
    onAddAnotherSpecies: () -> Unit,
    onSubmit: (CatchRecordDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var checkedIds by
        rememberSaveable(gearUse.id) {
            mutableStateOf(
                gearUse.speciesWeights
                    .filter { it.confirmedCaught }
                    .map { it.speciesId }
                    .toSet(),
            )
        }
    var rawValues by
        rememberSaveable(gearUse.id) {
            mutableStateOf(
                gearUse.speciesWeights
                    .flatMap { entry ->
                        listOfNotNull(
                            entry.weightAboveMinimumSizeKg?.let {
                                rawValueKey(entry.speciesId, SpeciesWeightFieldKind.AboveMinimumSize) to
                                    GearMeasurementSupport.formatNumber(it)
                            },
                            entry.weightBelowMinimumSizeKg?.let {
                                rawValueKey(entry.speciesId, SpeciesWeightFieldKind.BelowMinimumSize) to
                                    GearMeasurementSupport.formatNumber(it)
                            },
                            entry.weightLegallyDiscardedKg?.let {
                                rawValueKey(entry.speciesId, SpeciesWeightFieldKind.LegallyDiscarded) to
                                    GearMeasurementSupport.formatNumber(it)
                            },
                        )
                    }.toMap(),
            )
        }
    var disclosed by
        rememberSaveable(gearUse.id) {
            mutableStateOf(
                gearUse.speciesWeights
                    .flatMap { entry ->
                        listOfNotNull(
                            if (entry.weightBelowMinimumSizeKg != null) {
                                rawValueKey(entry.speciesId, SpeciesWeightFieldKind.BelowMinimumSize)
                            } else {
                                null
                            },
                            if (entry.weightLegallyDiscardedKg != null) {
                                rawValueKey(entry.speciesId, SpeciesWeightFieldKind.LegallyDiscarded)
                            } else {
                                null
                            },
                        )
                    }.toSet(),
            )
        }
    var errors by remember(gearUse.id) { mutableStateOf<Map<String, SpeciesWeightFieldError>>(emptyMap()) }
    var checklistError by remember(gearUse.id) { mutableStateOf(false) }
    var focusSummary by remember { mutableStateOf(false) }
    val summaryFocusRequester = remember { FocusRequester() }
    val fieldFocusRequesters =
        remember(checkedIds) {
            checkedIds
                .flatMap { speciesId -> SpeciesWeightFieldKind.entries.map { rawValueKey(speciesId, it) } }
                .associateWith { FocusRequester() }
        }

    LaunchedEffect(focusSummary) {
        if (focusSummary) {
            summaryFocusRequester.requestFocus()
            focusSummary = false
        }
    }

    val options =
        gearUse.speciesWeights.map { entry ->
            val species = speciesList.firstOrNull { it.id == entry.speciesId }
            GdsCheckboxOption(id = entry.speciesId, label = species?.name ?: entry.speciesId)
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (errors.isNotEmpty()) {
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    errors.mapNotNull { (key, error) ->
                        val speciesId = key.substringBefore("::")
                        val kindName = key.substringAfter("::")
                        val kind =
                            SpeciesWeightFieldKind.entries.firstOrNull { it.name == kindName } ?: return@mapNotNull null
                        val species = speciesList.firstOrNull { it.id == speciesId } ?: return@mapNotNull null
                        WizardErrorSummaryItem(
                            message = speciesWeightErrorMessage(species, error, kind),
                            onClick = { fieldFocusRequesters[key]?.requestFocus() },
                        )
                    },
                focusRequester = summaryFocusRequester,
                testTag = GearSpeciesChecklistScreenTestTags.ERROR_SUMMARY,
            )
        }
        if (checklistError) {
            Text(
                text = stringResource(R.string.species_checklist_error_required),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(GearSpeciesChecklistScreenTestTags.CHECKLIST_ERROR),
            )
        }
        Text(
            text = stringResource(R.string.gear_species_checklist_instruction),
            style = MaterialTheme.typography.bodyLarge,
        )
        GdsCheckboxGroup(
            options = options,
            checkedOptionIds = checkedIds,
            onCheckedChange = { id, checked ->
                checkedIds = if (checked) checkedIds + id else checkedIds - id
                checklistError = false
            },
            optionTestTagPrefix = GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX,
            conditionalContent = { speciesId ->
                val species = speciesList.firstOrNull { it.id == speciesId }
                if (species != null) {
                    SpeciesWeightFields(
                        speciesId = speciesId,
                        species = species,
                        rawValues = rawValues,
                        disclosed = disclosed,
                        errors = errors,
                        fieldFocusRequesters = fieldFocusRequesters,
                        onValueChange = { key, value ->
                            rawValues = rawValues + (key to value)
                            errors = errors - key
                        },
                        onToggleDisclosed = { key ->
                            val wasDisclosed = key in disclosed
                            disclosed = if (wasDisclosed) disclosed - key else disclosed + key
                            if (wasDisclosed) {
                                rawValues = rawValues - key
                                errors = errors - key
                            }
                        },
                    )
                }
            },
        )
        GdsLinkAction(
            text = stringResource(R.string.remove_a_species_action),
            onClick = {
                val updatedGearUse =
                    gearUse.copy(speciesWeights = gearUse.speciesWeights.filterNot { it.speciesId in checkedIds })
                checkedIds = emptySet()
                onRemoveSpecies(replaceGearUse(draft, updatedGearUse))
            },
            testTag = GearSpeciesChecklistScreenTestTags.REMOVE_SPECIES_ACTION,
            enabled = checkedIds.isNotEmpty(),
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                if (checkedIds.isEmpty()) {
                    checklistError = true
                } else {
                    val validationErrors = mutableMapOf<String, SpeciesWeightFieldError>()
                    val validatedEntries =
                        gearUse.speciesWeights.map { entry ->
                            if (entry.speciesId !in checkedIds) {
                                entry
                            } else {
                                val species = speciesList.firstOrNull { it.id == entry.speciesId }
                                if (species == null) {
                                    entry
                                } else {
                                    val result =
                                        validateSpeciesRow(
                                            draft = draft,
                                            gearUse = gearUse,
                                            species = species,
                                            rawValues = rawValues,
                                            disclosed = disclosed,
                                        )
                                    validationErrors +=
                                        result.errors.mapKeys { (kind, _) -> rawValueKey(entry.speciesId, kind) }
                                    entry.copy(
                                        confirmedCaught = true,
                                        weightAboveMinimumSizeKg =
                                            result.values[SpeciesWeightFieldKind.AboveMinimumSize],
                                        weightBelowMinimumSizeKg =
                                            result.values[SpeciesWeightFieldKind.BelowMinimumSize],
                                        weightLegallyDiscardedKg =
                                            result.values[SpeciesWeightFieldKind.LegallyDiscarded],
                                    )
                                }
                            }
                        }
                    if (validationErrors.isNotEmpty()) {
                        errors = validationErrors
                        focusSummary = true
                    } else {
                        errors = emptyMap()
                        onSubmit(replaceGearUse(draft, gearUse.copy(speciesWeights = validatedEntries)))
                    }
                }
            },
            modifier = Modifier.testTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION),
        )
        SecondaryActionButton(
            text = stringResource(R.string.add_a_species_action),
            onClick = onAddAnotherSpecies,
            modifier = Modifier.testTag(GearSpeciesChecklistScreenTestTags.ADD_SPECIES_ACTION),
        )
    }
}

private fun validateSpeciesRow(
    draft: CatchRecordDraft,
    gearUse: GearUse,
    species: Species,
    rawValues: Map<String, String>,
    disclosed: Set<String>,
): SpeciesWeightEntryValidationResult {
    val aboveKey = rawValueKey(species.id, SpeciesWeightFieldKind.AboveMinimumSize)
    val belowKey = rawValueKey(species.id, SpeciesWeightFieldKind.BelowMinimumSize)
    val discardedKey = rawValueKey(species.id, SpeciesWeightFieldKind.LegallyDiscarded)
    val rawValuesByField =
        buildMap {
            put(SpeciesWeightFieldKind.AboveMinimumSize, rawValues[aboveKey].orEmpty())
            if (belowKey in disclosed) put(SpeciesWeightFieldKind.BelowMinimumSize, rawValues[belowKey].orEmpty())
            if (discardedKey in
                disclosed
            ) {
                put(SpeciesWeightFieldKind.LegallyDiscarded, rawValues[discardedKey].orEmpty())
            }
        }
    return SpeciesWeightValidator.validateEntry(
        species = species,
        rawValuesByField = rawValuesByField,
        mandatoryFields =
            if (species.weightAboveMinimumSizeMandatory) {
                setOf(SpeciesWeightFieldKind.AboveMinimumSize)
            } else {
                emptySet()
            },
        cumulativeOtherEntriesKg =
            SpeciesWeightSupport.cumulativeWeightForSpeciesInOtherGearUses(draft, species.id, gearUse.id),
    )
}

private fun replaceGearUse(
    draft: CatchRecordDraft,
    updatedGearUse: GearUse,
): CatchRecordDraft =
    draft.copy(gearUses = draft.gearUses.map { if (it.id == updatedGearUse.id) updatedGearUse else it })

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SpeciesWeightFields(
    speciesId: String,
    species: Species,
    rawValues: Map<String, String>,
    disclosed: Set<String>,
    errors: Map<String, SpeciesWeightFieldError>,
    fieldFocusRequesters: Map<String, FocusRequester>,
    onValueChange: (key: String, value: String) -> Unit,
    onToggleDisclosed: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numericKind =
        if (species.weightPrecision == SpeciesWeightPrecision.WholeNumber) {
            GdsNumericFieldKind.Integer
        } else {
            GdsNumericFieldKind.Decimal
        }
    val aboveKey = rawValueKey(speciesId, SpeciesWeightFieldKind.AboveMinimumSize)
    val belowKey = rawValueKey(speciesId, SpeciesWeightFieldKind.BelowMinimumSize)
    val discardedKey = rawValueKey(speciesId, SpeciesWeightFieldKind.LegallyDiscarded)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        GdsNumericField(
            label = stringResource(R.string.weight_above_minimum_size_label),
            value = rawValues[aboveKey].orEmpty(),
            onValueChange = { onValueChange(aboveKey, it) },
            testTag = "${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_$speciesId",
            kind = numericKind,
            errorText =
                errors[aboveKey]?.let {
                    speciesWeightErrorMessage(
                        species,
                        it,
                        SpeciesWeightFieldKind.AboveMinimumSize,
                    )
                },
            modifier = Modifier.focusRequester(fieldFocusRequesters.getValue(aboveKey)),
        )
        if (belowKey in disclosed) {
            GdsNumericField(
                label = stringResource(R.string.weight_below_minimum_size_label),
                value = rawValues[belowKey].orEmpty(),
                onValueChange = { onValueChange(belowKey, it) },
                testTag = "${GearSpeciesChecklistScreenTestTags.BELOW_MIN_FIELD_PREFIX}_$speciesId",
                kind = numericKind,
                errorText =
                    errors[belowKey]?.let {
                        speciesWeightErrorMessage(
                            species,
                            it,
                            SpeciesWeightFieldKind.BelowMinimumSize,
                        )
                    },
                modifier = Modifier.focusRequester(fieldFocusRequesters.getValue(belowKey)),
            )
            GdsLinkAction(
                text = stringResource(R.string.remove_weight_below_minimum_size_action),
                onClick = { onToggleDisclosed(belowKey) },
                testTag = "${GearSpeciesChecklistScreenTestTags.REMOVE_BELOW_MIN_ACTION_PREFIX}_$speciesId",
            )
        } else {
            GdsLinkAction(
                text = stringResource(R.string.add_weight_below_minimum_size_action),
                onClick = { onToggleDisclosed(belowKey) },
                testTag = "${GearSpeciesChecklistScreenTestTags.ADD_BELOW_MIN_ACTION_PREFIX}_$speciesId",
            )
        }
        if (discardedKey in disclosed) {
            GdsNumericField(
                label = stringResource(R.string.weight_legally_discarded_label),
                value = rawValues[discardedKey].orEmpty(),
                onValueChange = { onValueChange(discardedKey, it) },
                testTag = "${GearSpeciesChecklistScreenTestTags.DISCARDED_FIELD_PREFIX}_$speciesId",
                kind = numericKind,
                errorText =
                    errors[discardedKey]?.let {
                        speciesWeightErrorMessage(species, it, SpeciesWeightFieldKind.LegallyDiscarded)
                    },
                modifier = Modifier.focusRequester(fieldFocusRequesters.getValue(discardedKey)),
            )
            GdsLinkAction(
                text = stringResource(R.string.remove_weight_legally_discarded_action),
                onClick = { onToggleDisclosed(discardedKey) },
                testTag = "${GearSpeciesChecklistScreenTestTags.REMOVE_DISCARDED_ACTION_PREFIX}_$speciesId",
            )
        } else {
            GdsLinkAction(
                text = stringResource(R.string.add_weight_legally_discarded_action),
                onClick = { onToggleDisclosed(discardedKey) },
                testTag = "${GearSpeciesChecklistScreenTestTags.ADD_DISCARDED_ACTION_PREFIX}_$speciesId",
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun speciesWeightErrorMessage(
    species: Species,
    error: SpeciesWeightFieldError,
    kind: SpeciesWeightFieldKind = SpeciesWeightFieldKind.AboveMinimumSize,
): String {
    val name = SpeciesSupport.displayNameFor(species)
    return when (error) {
        SpeciesWeightFieldError.Required ->
            if (kind == SpeciesWeightFieldKind.KeptOnboard) {
                stringResource(R.string.species_weight_error_required_kept_onboard)
            } else {
                stringResource(R.string.species_weight_error_required_above_min)
            }
        SpeciesWeightFieldError.Range -> stringResource(R.string.species_weight_error_range, name)
        SpeciesWeightFieldError.Precision ->
            if (species.weightPrecision == SpeciesWeightPrecision.WholeNumber) {
                stringResource(R.string.species_weight_error_whole_number, name)
            } else {
                stringResource(R.string.species_weight_error_one_decimal, name)
            }
        SpeciesWeightFieldError.MonthlyQuotaExceeded -> stringResource(R.string.species_weight_error_monthly_quota)
        SpeciesWeightFieldError.AnnualQuotaExceeded -> stringResource(R.string.species_weight_error_annual_quota)
    }
}
