// Same file-level suppression rationale as `GearSpeciesChecklistScreen.kt`'s: this is a single
// progressive-disclosure checklist + weight-capture composable whose branch count is inherent to the
// checklist/validation UI shape, not accidental complexity.
@file:Suppress(
    "detekt.FunctionNaming",
    "detekt.LongMethod",
    "detekt.MaxLineLength",
    "detekt.CyclomaticComplexMethod",
)

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing

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
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericField
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericFieldKind
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioOption
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummary
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummaryItem
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextWizardStepForDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementSupport
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.speciesWeightErrorMessage
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightFieldError
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightFieldKind
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightSupport
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightValidator
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.TripTodayScreenContent

object NotLandedStraightAwayDecisionScreenTestTags {
    const val SCREEN = "not_landed_straight_away_decision_screen"
    const val OPTION_PREFIX = "not_landed_straight_away_decision_option"
    const val SAVE_ACTION = "not_landed_straight_away_decision_save_action"
    const val ERROR_MESSAGE = "not_landed_straight_away_decision_error_message"
}

object NotLandedStraightAwaySpeciesScreenTestTags {
    const val SCREEN = "not_landed_straight_away_species_screen"
    const val CHECKBOX_PREFIX = "not_landed_straight_away_species_checkbox"
    const val WEIGHT_FIELD_PREFIX = "not_landed_straight_away_species_weight_field"
    const val SAVE_ACTION = "not_landed_straight_away_species_save_action"
    const val ERROR_SUMMARY = "not_landed_straight_away_species_error_summary"
    const val ERROR_MESSAGE = "not_landed_straight_away_species_error_message"
    const val CHECKLIST_ERROR = "not_landed_straight_away_species_checklist_error"
}

/**
 * Phase 5B, screen 3 — asked once (not per-gear): "Is there any catch from this trip that you will not be
 * landing straight away?". Mirrors [TripTodayScreenContent]'s Yes/No shape exactly, reusing [R.string.yes]/
 * [R.string.no]. `No` skips straight past screen 4 (see [nextWizardStepForDraft]); `Yes` routes there.
 */
@Suppress("FunctionNaming")
@Composable
fun NotLandedStraightAwayDecisionScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = NotLandedStraightAwayDecisionScreenTestTags.SCREEN,
        title = stringResource(R.string.not_landed_straight_away_decision_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = NotLandedStraightAwayDecisionScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            is UiStatus.Content ->
                NotLandedStraightAwayDecisionScreenContent(
                    initialValue = status.value.notLandedStraightAway,
                    onSubmit = { answer ->
                        val updatedDraft = status.value.copy(notLandedStraightAway = answer)
                        val nextStep = nextWizardStepForDraft(updatedDraft)
                        viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep))
                        onNavigate(nextStep)
                    },
                )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun NotLandedStraightAwayDecisionScreenContent(
    initialValue: Boolean?,
    onSubmit: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedOptionId by rememberSaveable { mutableStateOf(initialValue?.toString()) }
    var showError by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsRadioGroup(
            options =
                listOf(
                    GdsRadioOption("true", stringResource(R.string.yes)),
                    GdsRadioOption("false", stringResource(R.string.no)),
                ),
            selectedOptionId = selectedOptionId,
            onOptionSelected = {
                selectedOptionId = it
                showError = false
            },
            optionTestTagPrefix = NotLandedStraightAwayDecisionScreenTestTags.OPTION_PREFIX,
        )
        if (showError) {
            Text(
                text = stringResource(R.string.not_landed_straight_away_decision_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(NotLandedStraightAwayDecisionScreenTestTags.ERROR_MESSAGE),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                when (selectedOptionId) {
                    "true" -> onSubmit(true)
                    "false" -> onSubmit(false)
                    else -> showError = true
                }
            },
            modifier = Modifier.testTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION),
        )
    }
}

private fun notLandedWeightKey(speciesId: String): String = speciesId

/**
 * Phase 5B, screen 4 — shown only when screen 3 was answered `true`: a checklist of every distinct species
 * confirmed-caught anywhere in the draft (see [SpeciesWeightSupport.distinctConfirmedSpeciesIds]), each
 * revealing a single mandatory "Weight above minimum size kept onboard or in keep pots (kg)" field. Reuses
 * [SpeciesWeightValidator] with [SpeciesWeightFieldKind.KeptOnboard] and `applyQuotaCheck = false` (the
 * confirmed quota rule sums only the three Phase 5A fields, not this one).
 */
@Suppress("FunctionNaming")
@Composable
fun NotLandedStraightAwaySpeciesScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = NotLandedStraightAwaySpeciesScreenTestTags.SCREEN,
        title = stringResource(R.string.not_landed_straight_away_species_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = NotLandedStraightAwaySpeciesScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            is UiStatus.Content ->
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = status.value,
                    speciesList = state.species,
                    onSubmit = { updatedDraft ->
                        val nextStep = nextWizardStepForDraft(updatedDraft)
                        viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep))
                        onNavigate(nextStep)
                    },
                )
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun NotLandedStraightAwaySpeciesScreenContent(
    draft: CatchRecordDraft,
    speciesList: List<Species>,
    onSubmit: (CatchRecordDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val distinctSpeciesIds = remember(draft) { SpeciesWeightSupport.distinctConfirmedSpeciesIds(draft) }
    var checkedIds by
        rememberSaveable(draft.id) {
            mutableStateOf(draft.notLandedSpeciesEntries.map { it.speciesId }.toSet())
        }
    var rawValues by
        rememberSaveable(draft.id) {
            mutableStateOf(
                draft.notLandedSpeciesEntries
                    .mapNotNull { entry ->
                        entry.weightAboveMinimumSizeKeptOnboardKg?.let {
                            notLandedWeightKey(entry.speciesId) to GearMeasurementSupport.formatNumber(it)
                        }
                    }.toMap(),
            )
        }
    var errors by remember(draft.id) { mutableStateOf<Map<String, SpeciesWeightFieldError>>(emptyMap()) }
    var checklistError by remember(draft.id) { mutableStateOf(false) }
    var focusSummary by remember { mutableStateOf(false) }
    val summaryFocusRequester = remember { FocusRequester() }
    val fieldFocusRequesters =
        remember(checkedIds) { checkedIds.associateWith { FocusRequester() } }

    LaunchedEffect(focusSummary) {
        if (focusSummary) {
            summaryFocusRequester.requestFocus()
            focusSummary = false
        }
    }

    val options =
        distinctSpeciesIds.map { speciesId ->
            val species = speciesList.firstOrNull { it.id == speciesId }
            GdsCheckboxOption(id = speciesId, label = species?.name ?: speciesId)
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (errors.isNotEmpty()) {
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    errors.mapNotNull { (speciesId, error) ->
                        val species = speciesList.firstOrNull { it.id == speciesId } ?: return@mapNotNull null
                        WizardErrorSummaryItem(
                            message = speciesWeightErrorMessage(species, error, SpeciesWeightFieldKind.KeptOnboard),
                            onClick = { fieldFocusRequesters[speciesId]?.requestFocus() },
                        )
                    },
                focusRequester = summaryFocusRequester,
                testTag = NotLandedStraightAwaySpeciesScreenTestTags.ERROR_SUMMARY,
            )
        }
        if (checklistError) {
            Text(
                text = stringResource(R.string.species_checklist_error_required),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(NotLandedStraightAwaySpeciesScreenTestTags.CHECKLIST_ERROR),
            )
        }
        Text(
            text = stringResource(R.string.not_landed_straight_away_species_helper),
            style = MaterialTheme.typography.bodyLarge,
        )
        GdsCheckboxGroup(
            options = options,
            checkedOptionIds = checkedIds,
            onCheckedChange = { id, checked ->
                checkedIds = if (checked) checkedIds + id else checkedIds - id
                checklistError = false
            },
            optionTestTagPrefix = NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX,
            conditionalContent = { speciesId ->
                val species = speciesList.firstOrNull { it.id == speciesId }
                if (species != null) {
                    val numericKind =
                        if (species.weightPrecision == SpeciesWeightPrecision.WholeNumber) {
                            GdsNumericFieldKind.Integer
                        } else {
                            GdsNumericFieldKind.Decimal
                        }
                    GdsNumericField(
                        label = stringResource(R.string.weight_above_minimum_size_kept_onboard_label),
                        value = rawValues[speciesId].orEmpty(),
                        onValueChange = { value ->
                            rawValues = rawValues + (speciesId to value)
                            errors = errors - speciesId
                        },
                        testTag = "${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_$speciesId",
                        kind = numericKind,
                        errorText =
                            errors[speciesId]?.let {
                                speciesWeightErrorMessage(
                                    species,
                                    it,
                                    SpeciesWeightFieldKind.KeptOnboard,
                                )
                            },
                        modifier = Modifier.focusRequester(fieldFocusRequesters.getValue(speciesId)),
                    )
                }
            },
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                if (checkedIds.isEmpty()) {
                    checklistError = true
                } else {
                    val validationErrors = mutableMapOf<String, SpeciesWeightFieldError>()
                    val entries =
                        checkedIds.mapNotNull { speciesId ->
                            val species = speciesList.firstOrNull { it.id == speciesId }
                            if (species == null) {
                                null
                            } else {
                                val result =
                                    SpeciesWeightValidator.validateEntry(
                                        species = species,
                                        rawValuesByField =
                                            mapOf(
                                                SpeciesWeightFieldKind.KeptOnboard to rawValues[speciesId].orEmpty(),
                                            ),
                                        mandatoryFields = setOf(SpeciesWeightFieldKind.KeptOnboard),
                                        applyQuotaCheck = false,
                                    )
                                if (result.errors.isNotEmpty()) {
                                    validationErrors[speciesId] =
                                        result.errors.getValue(SpeciesWeightFieldKind.KeptOnboard)
                                    null
                                } else {
                                    NotLandedSpeciesEntry(
                                        speciesId = speciesId,
                                        weightAboveMinimumSizeKeptOnboardKg =
                                            result.values[SpeciesWeightFieldKind.KeptOnboard],
                                    )
                                }
                            }
                        }
                    if (validationErrors.isNotEmpty()) {
                        errors = validationErrors
                        focusSummary = true
                    } else {
                        errors = emptyMap()
                        onSubmit(draft.copy(notLandedSpeciesEntries = entries))
                    }
                }
            },
            modifier = Modifier.testTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION),
        )
    }
}
