@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.LongParameterList", "detekt.MaxLineLength")

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxOption
import uk.gov.defra.mmocatchrecord.common.design.GdsLinkAction
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericField
import uk.gov.defra.mmocatchrecord.common.design.GdsNumericFieldKind
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

object GearSummaryScreenTestTags {
    const val SCREEN = "gear_summary_screen"
    const val CHECKBOX_PREFIX = "gear_summary_checkbox"
    const val SHOTS_FIELD_PREFIX = "gear_summary_shots_field"
    const val REMOVE_ACTION = "gear_summary_remove_action"
    const val ADD_ANOTHER_ACTION = "gear_summary_add_another_action"
    const val SAVE_ACTION = "gear_summary_save_action"
    const val ERROR_MESSAGE = "gear_summary_error_message"
}

/**
 * "What gear did you use?" checklist of every gear added so far. Checking a gear confirms it was used on
 * *this* trip (see [GearUse.confirmedUsedOnTrip]) and reveals its "number of times gear was shot on trip"
 * field; "Remove gear" bulk-removes whichever gear(s) are currently checked; "Add another gear" loops back
 * to the gear-search step.
 *
 * Deviation from the screenshots (flagged, non-blocking): the confirmed screenshots show two slightly
 * different instruction strings depending on gear count ("Select all the gears used on your vessel." with
 * one gear added vs "Select all that apply." with two or more) — this always renders the single canonical
 * string [R.string.gear_summary_instruction], per the approved task instruction to pick one and record the
 * inconsistency rather than branch on gear count.
 */
@Suppress("FunctionNaming")
@Composable
fun GearSummaryScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GearSummaryScreen(
        state = state,
        onRemoveGear = { updatedDraft -> viewModel.dispatch(CatchRecordFlowEvent.GearRemoved(updatedDraft)) },
        onAddAnotherGear = { onNavigate(WizardStep.GearSearch) },
        onSubmit = { updatedDraft ->
            // At least one confirmed gear routes into the general next-step resolver (Phase 4/5's
            // per-gear loops); only an updated draft with zero confirmed gear (edge case: every gear left
            // unticked) skips straight past every per-gear/trip-level step, since there is nothing to loop
            // over — this is a deliberate, pre-existing asymmetry from [nextWizardStepForDraft]'s own
            // fallback (which would otherwise route back to [WizardStep.GearSummary] for a "some gear,
            // none confirmed" resume scenario), preserved unchanged from Phase 4.
            val nextStep =
                if (updatedDraft.gearUses.any { it.confirmedUsedOnTrip }) {
                    nextWizardStepForDraft(updatedDraft)
                } else {
                    WizardStep.LandingStorage
                }
            viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep))
            onNavigate(nextStep)
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun GearSummaryScreen(
    state: CatchRecordFlowViewState,
    onRemoveGear: (CatchRecordDraft) -> Unit,
    onAddAnotherGear: () -> Unit,
    onSubmit: (CatchRecordDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CatchRecordWizardScaffold(
        screenTestTag = GearSummaryScreenTestTags.SCREEN,
        title = stringResource(R.string.gear_summary_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, GearSummaryScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                GearSummaryScreenContent(
                    draft = status.value,
                    gearTypes = state.gearTypes,
                    onRemoveGear = onRemoveGear,
                    onAddAnotherGear = onAddAnotherGear,
                    onSubmit = onSubmit,
                )
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun GearSummaryScreenContent(
    draft: CatchRecordDraft,
    gearTypes: List<GearType>,
    onRemoveGear: (CatchRecordDraft) -> Unit,
    onAddAnotherGear: () -> Unit,
    onSubmit: (CatchRecordDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed only on draft.id (not on the gear-use list) so a "Remove gear" action — which mutates the
    // gear-use list within the same continuous composition — does not reset the other rows' not-yet-saved
    // check/shots edits. A genuinely new draft (different id) still re-seeds from the persisted values.
    var checkedIds by
        rememberSaveable(draft.id) {
            mutableStateOf(
                draft.gearUses
                    .filter { it.confirmedUsedOnTrip }
                    .map { it.id }
                    .toSet(),
            )
        }
    var shotsById by
        rememberSaveable(draft.id) {
            mutableStateOf(draft.gearUses.associate { it.id to (it.numberOfShots?.toString() ?: "") })
        }

    val options =
        draft.gearUses.map { gearUse ->
            val gearType = gearTypes.firstOrNull { it.id == gearUse.gearTypeId }
            GdsCheckboxOption(
                id = gearUse.id,
                label = gearType?.let { GearMeasurementSupport.displayNameFor(it) } ?: gearUse.gearTypeId,
                secondaryText = GearMeasurementSupport.measurementSummaryFor(gearUse),
            )
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(text = stringResource(R.string.gear_summary_instruction), style = MaterialTheme.typography.bodyLarge)
        GdsCheckboxGroup(
            options = options,
            checkedOptionIds = checkedIds,
            onCheckedChange = { id, checked ->
                checkedIds = if (checked) checkedIds + id else checkedIds - id
            },
            optionTestTagPrefix = GearSummaryScreenTestTags.CHECKBOX_PREFIX,
            conditionalContent = { gearUseId ->
                GdsNumericField(
                    label = stringResource(R.string.gear_shots_label),
                    value = shotsById[gearUseId].orEmpty(),
                    onValueChange = { shotsById = shotsById + (gearUseId to it) },
                    testTag = "${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_$gearUseId",
                    kind = GdsNumericFieldKind.Integer,
                )
            },
        )
        RemoveGearLink(
            enabled = checkedIds.isNotEmpty(),
            onClick = {
                val updatedDraft = draft.copy(gearUses = draft.gearUses.filterNot { it.id in checkedIds })
                checkedIds = emptySet()
                onRemoveGear(updatedDraft)
            },
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val updatedDraft =
                    draft.copy(
                        gearUses =
                            draft.gearUses.map { gearUse ->
                                gearUse.copy(
                                    confirmedUsedOnTrip = gearUse.id in checkedIds,
                                    numberOfShots = shotsById[gearUse.id]?.toIntOrNull() ?: gearUse.numberOfShots,
                                )
                            },
                    )
                onSubmit(updatedDraft)
            },
            modifier = Modifier.testTag(GearSummaryScreenTestTags.SAVE_ACTION),
        )
        SecondaryActionButton(
            text = stringResource(R.string.add_another_gear),
            onClick = onAddAnotherGear,
            modifier = Modifier.testTag(GearSummaryScreenTestTags.ADD_ANOTHER_ACTION),
        )
    }
}

/**
 * "Remove gear" is rendered as a link-styled action that is always visible but disabled (dimmed, and
 * unclickable) when nothing is checked, rather than hidden outright — see [GdsLinkAction].
 */
@Suppress("FunctionNaming")
@Composable
private fun RemoveGearLink(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GdsLinkAction(
        text = stringResource(R.string.remove_gear),
        onClick = onClick,
        testTag = GearSummaryScreenTestTags.REMOVE_ACTION,
        enabled = enabled,
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GearSummaryScreen_Preview() {
    val gearTypes =
        listOf(
            GearType(id = "gear-seine-nets", name = "Seine nets (not specified)"),
            GearType(id = "gear-bottom-otter-trawls-tb", name = "Bottom otter trawls (TB)"),
        )
    val gearUses =
        listOf(
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = null,
                measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
                confirmedUsedOnTrip = true,
                numberOfShots = 3,
            ),
            GearUse(
                id = "gear-use-2",
                gearTypeId = "gear-bottom-otter-trawls-tb",
                statisticalSubRectangleCode = null,
                measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(80.0, "mm")),
            ),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = gearUses,
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state = CatchRecordFlowViewState(status = UiStatus.Content(sampleDraft), gearTypes = gearTypes)
    MmoTheme {
        AppLanguageProvider(language = "en") {
            GearSummaryScreen(
                state = state,
                onRemoveGear = {},
                onAddAnotherGear = {},
                onSubmit = {},
                onBack = {},
            )
        }
    }
}
