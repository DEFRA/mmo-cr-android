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
import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxOption
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference

object RemoveGearScreenTestTags {
    const val SCREEN = "remove_gear_screen"
    const val ERROR_MESSAGE = "remove_gear_error_message"
    const val CHECKBOX_PREFIX = "remove_gear_checkbox"
    const val CONTINUE_ACTION = "remove_gear_continue_action"
}

/** Dedicated "Remove gear from vessel {vessel}" page, reached from [GearSummaryScreen]'s "Remove gear" link. */
@Composable
fun RemoveGearScreen(
    viewModel: CatchRecordFlowViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemoveGearScreen(
        state = state,
        onRemove = { updatedDraft ->
            viewModel.dispatch(CatchRecordFlowEvent.GearRemoved(updatedDraft))
            onBack()
        },
        onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun RemoveGearScreen(
    state: CatchRecordFlowViewState,
    onRemove: (CatchRecordDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val vesselName = draft?.let { d -> state.vessels.firstOrNull { it.id == d.vesselId }?.name }

    CatchRecordWizardScaffold(
        screenTestTag = RemoveGearScreenTestTags.SCREEN,
        title =
            vesselName?.let { stringResource(R.string.remove_gear_title, it) }
                ?: stringResource(R.string.remove_gear),
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = RemoveGearScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = onRetry,
                )
            is UiStatus.Content ->
                RemoveGearScreenContent(
                    draft = status.value,
                    gearTypes = state.gearTypes,
                    onRemove = onRemove,
                )
        }
    }
}

@Composable
fun RemoveGearScreenContent(
    draft: CatchRecordDraft,
    gearTypes: List<GearType>,
    onRemove: (CatchRecordDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var checkedIds by rememberSaveable(draft.id) { mutableStateOf(emptySet<String>()) }
    var showError by rememberSaveable(draft.id) { mutableStateOf(false) }

    val options =
        draft.gearUses.map { gearUse ->
            GdsCheckboxOption(
                id = gearUse.id,
                label = removeGearLabelFor(gearTypes.firstOrNull { it.id == gearUse.gearTypeId }, gearUse),
            )
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (showError) {
            Text(
                text = stringResource(R.string.remove_gear_error_required),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .testTag(RemoveGearScreenTestTags.ERROR_MESSAGE)
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
            optionTestTagPrefix = RemoveGearScreenTestTags.CHECKBOX_PREFIX,
        )
        PrimaryActionButton(
            text = stringResource(R.string.continue_action),
            onClick = {
                if (checkedIds.isEmpty()) {
                    showError = true
                } else {
                    onRemove(draft.copy(gearUses = draft.gearUses.filterNot { it.id in checkedIds }))
                }
            },
            modifier = Modifier.testTag(RemoveGearScreenTestTags.CONTINUE_ACTION),
        )
    }
}

/** e.g. "Two boat operated purse seine - (mesh size 20 mm)", or just the gear name when it has no mesh size. */
internal fun removeGearLabelFor(
    gearType: GearType?,
    gearUse: GearUse,
): String {
    val gearName = gearType?.let(GearMeasurementSupport::displayNameFor) ?: gearUse.gearTypeId
    val meshSizeMm =
        (gearUse.measurements[GearMeasurementFieldKeys.MESH_SIZE_MM] as? MeasurementValue.Numeric)?.value
            ?: return gearName
    return "$gearName - (mesh size ${GearMeasurementSupport.formatNumber(meshSizeMm)} mm)"
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun RemoveGearScreen_Preview() {
    val pots = GearType(id = "gear-pots", name = "Pots (FPO)")
    val purseSeine = GearType(id = "gear-ptm", name = "Two boat operated purse seine (PS2)")
    val gearUses =
        listOf(
            GearUse(id = "gear-use-1", gearTypeId = pots.id, statisticalSubRectangleCode = null),
            GearUse(
                id = "gear-use-2",
                gearTypeId = purseSeine.id,
                statisticalSubRectangleCode = null,
                measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(20.0, "mm")),
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
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            vessels = listOf(Vessel(id = "vessel-1", name = "515 CB PRO")),
            gearTypes = listOf(pots, purseSeine),
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            RemoveGearScreen(state = state, onRemove = {}, onBack = {})
        }
    }
}
