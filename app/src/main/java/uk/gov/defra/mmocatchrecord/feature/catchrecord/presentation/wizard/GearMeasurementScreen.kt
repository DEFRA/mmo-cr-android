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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericField
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericFieldKind
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

object GearMeasurementScreenTestTags {
    const val SCREEN = "gear_measurement_screen"
    const val ERROR_SUMMARY = "gear_measurement_error_summary"
    const val INSTRUCTION = "gear_measurement_instruction"
    const val FIELD_PREFIX = "gear_measurement_field"
    const val SAVE_ACTION = "gear_measurement_save_action"
    const val ERROR_MESSAGE = "gear_measurement_error_message"
}

/**
 * Generic, schema-driven "Enter the measurements for {gear}" screen: the fields shown are resolved from
 * the pending gear type's [GearType.measurementFields], not hardcoded per gear type — this is the
 * framework the task asks for, with only the two confirmed schemas (Seine nets, Bottom otter trawls)
 * populated in [uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.StubReferenceDataRepository].
 */
@Suppress("FunctionNaming")
@Composable
fun GearMeasurementScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GearMeasurementScreen(
        state = state,
        onSubmit = { measurements ->
            viewModel.dispatch(CatchRecordFlowEvent.GearMeasurementsSubmitted(measurements))
            onNavigate(WizardStep.GearSummary)
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun GearMeasurementScreen(
    state: CatchRecordFlowViewState,
    onSubmit: (Map<String, MeasurementValue>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gearType = state.gearTypes.firstOrNull { it.id == state.pendingGearTypeId }
    CatchRecordWizardScaffold(
        screenTestTag = GearMeasurementScreenTestTags.SCREEN,
        title =
            gearType?.let {
                stringResource(
                    R.string.gear_measurement_title,
                    GearMeasurementSupport.titleGearNameFor(it),
                )
            }
                ?: stringResource(R.string.gear_measurement_title_fallback),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, GearMeasurementScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                if (gearType == null) {
                    // Defensive only: normal navigation always sets pendingGearTypeId before reaching this
                    // screen, and a resumed-after-process-death draft with no gear uses yet routes to
                    // GearSearch, never here — see nextWizardStepForDraft.
                    WizardErrorState(
                        stringResource(R.string.gear_measurement_missing_gear_type),
                        GearMeasurementScreenTestTags.ERROR_MESSAGE,
                    )
                } else {
                    GearMeasurementScreenContent(gearType = gearType, onSubmit = onSubmit)
                }
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun GearMeasurementScreenContent(
    gearType: GearType,
    onSubmit: (Map<String, MeasurementValue>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rawValues by
        rememberSaveable(gearType.id) {
            mutableStateOf(gearType.measurementFields.associate { it.key to "" })
        }
    var errors by remember(gearType.id) { mutableStateOf<Map<String, GearMeasurementFieldError>>(emptyMap()) }
    var focusSummary by remember { mutableStateOf(false) }
    val summaryFocusRequester = remember { FocusRequester() }
    val fieldFocusRequesters =
        remember(gearType.id) { gearType.measurementFields.associate { it.key to FocusRequester() } }

    LaunchedEffect(focusSummary) {
        if (focusSummary) {
            summaryFocusRequester.requestFocus()
            focusSummary = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (errors.isNotEmpty()) {
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    gearType.measurementFields.mapNotNull { field ->
                        val error = errors[field.key] ?: return@mapNotNull null
                        WizardErrorSummaryItem(
                            message = gearMeasurementErrorMessage(field, error),
                            onClick = { fieldFocusRequesters[field.key]?.requestFocus() },
                        )
                    },
                focusRequester = summaryFocusRequester,
                testTag = GearMeasurementScreenTestTags.ERROR_SUMMARY,
            )
        }
        Text(
            text = stringResource(R.string.gear_measurement_whole_number_instruction),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(GearMeasurementScreenTestTags.INSTRUCTION),
        )
        gearType.measurementFields.forEachIndexed { index, field ->
            GdsNumericField(
                label = gearMeasurementFieldLabel(field),
                value = rawValues[field.key].orEmpty(),
                onValueChange = { newValue ->
                    rawValues = rawValues + (field.key to newValue)
                    errors = errors - field.key
                },
                testTag = "${GearMeasurementScreenTestTags.FIELD_PREFIX}_$index",
                kind =
                    if (field.type ==
                        GearMeasurementFieldType.Integer
                    ) {
                        GdsNumericFieldKind.Integer
                    } else {
                        GdsNumericFieldKind.Decimal
                    },
                errorText = errors[field.key]?.let { gearMeasurementErrorMessage(field, it) },
                modifier = Modifier.focusRequester(fieldFocusRequesters.getValue(field.key)),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val result = GearMeasurementInputValidator.validate(gearType.measurementFields, rawValues)
                if (result.isValid) {
                    errors = emptyMap()
                    onSubmit(result.measurements)
                } else {
                    errors = result.errors
                    focusSummary = true
                }
            },
            modifier = Modifier.testTag(GearMeasurementScreenTestTags.SAVE_ACTION),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun gearMeasurementFieldLabel(field: GearMeasurementField): String =
    when (field.key) {
        GearMeasurementFieldKeys.MESH_SIZE_MM -> stringResource(R.string.gear_measurement_field_mesh_size_mm)
        GearMeasurementFieldKeys.NUMBER_OF_TRAWL_NETS ->
            stringResource(
                R.string.gear_measurement_field_number_of_trawl_nets,
            )
        GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_HAULED ->
            stringResource(R.string.gear_measurement_field_total_pots_or_traps_hauled)
        GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_LEFT_IN_WATER ->
            stringResource(R.string.gear_measurement_field_total_pots_or_traps_left_in_water)
        GearMeasurementFieldKeys.NUMBER_OF_RODS_AND_LINES ->
            stringResource(R.string.gear_measurement_field_number_of_rods_and_lines)
        GearMeasurementFieldKeys.TOTAL_HOOKS_HAULED ->
            stringResource(R.string.gear_measurement_field_total_hooks_hauled)
        GearMeasurementFieldKeys.TOTAL_HOOKS_LEFT_IN_WATER ->
            stringResource(R.string.gear_measurement_field_total_hooks_left_in_water)
        GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_HAULED_M ->
            stringResource(R.string.gear_measurement_field_total_length_of_nets_hauled_m)
        GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_LEFT_IN_WATER_M ->
            stringResource(R.string.gear_measurement_field_total_length_of_nets_left_in_water_m)
        // Defensive fallback for any future schema field added before its own string resource is wired up.
        else -> field.label
    }

@Suppress("FunctionNaming")
@Composable
private fun gearMeasurementErrorMessage(
    field: GearMeasurementField,
    error: GearMeasurementFieldError,
): String {
    val label = gearMeasurementFieldLabel(field)
    return when (error) {
        GearMeasurementFieldError.Required -> stringResource(R.string.gear_measurement_error_required, label)
        GearMeasurementFieldError.Numeric -> stringResource(R.string.gear_measurement_error_numeric, label)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GearMeasurementScreen_SeineNetsPreview() {
    val gearType =
        GearType(
            id = "gear-seine-nets",
            name = "Seine nets (not specified)",
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                        label = "Mesh size (mm)",
                        type = GearMeasurementFieldType.Integer,
                        unit = "mm",
                    ),
                ),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            gearTypes = listOf(gearType),
            pendingGearTypeId = gearType.id,
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            GearMeasurementScreen(state = state, onSubmit = {}, onBack = {})
        }
    }
}
