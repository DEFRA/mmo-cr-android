@file:Suppress("detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.common.design.govukFocusIndicator
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummary
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummaryItem
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearStatRectangleScreenTestTags

/**
 * Screen 2 replacement (approved plan, decisions 1-2): the offline interactive map + synchronised radio
 * list of the **full/global** sea-overlapping statistical sub-rectangle set — see ADR 0013. The map is an
 * enhanced, non-exclusive input; the list beneath it is the authoritative WCAG 2.2 AA accessible/keyboard/
 * TalkBack path (see [StatisticalAreaMapCanvas]'s doc comment).
 *
 * [geometryStatus] mirrors [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState.mapGeometryStatus]
 * — [UiStatus.Idle]/[UiStatus.Loading] while the
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryRepository] load (owned by
 * `CatchRecordFlowViewModel`, per ADR 0007 — see finding "screen must not bypass the ViewModel for
 * screen-owned async state") is still in flight, shown as a loading state; [UiStatus.Error] shown as an
 * accessible, retryable error state (both the primary derived asset *and* its on-device GeoJSON fallback
 * failed — see `AssetMapGeometryRepository`'s doc comment); [UiStatus.Content] the loaded dataset.
 */
@Suppress("FunctionNaming", "LongMethod") // Composable orchestrates map + list + error-summary + actions cohesively.
@Composable
fun GearStatRectangleMapListContent(
    gearUse: GearUse,
    geometryStatus: UiStatus<MapGeometryDataset>,
    departurePortName: String?,
    onCantFindOnMap: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetryGeometry: () -> Unit = {},
) {
    var selectedCode by rememberSaveable(gearUse.id) { mutableStateOf(gearUse.statisticalSubRectangleCode) }
    var showError by rememberSaveable(gearUse.id) { mutableStateOf(false) }
    var focusSummary by remember { mutableStateOf(false) }
    val summaryFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }

    // Accessible focus/announcement on a failed validation (finding: "Validation error lacks accessible
    // focus/announcement") — matches GearMeasurementScreenContent/the Autocomplete sub-screen's
    // summaryFocusRequester + LaunchedEffect(focusSummary) convention exactly.
    LaunchedEffect(focusSummary) {
        if (focusSummary) {
            summaryFocusRequester.requestFocus()
            focusSummary = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            text = stringResource(R.string.gear_stat_rectangle_map_list_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (showError) {
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    listOf(
                        WizardErrorSummaryItem(
                            message = stringResource(R.string.gear_stat_rectangle_map_error_required),
                            onClick = { listFocusRequester.requestFocus() },
                        ),
                    ),
                focusRequester = summaryFocusRequester,
                testTag = GearStatRectangleScreenTestTags.ERROR_SUMMARY,
            )
        }

        // Live-region announcement of selection changes (map or list), independent of the map's own single
        // summarised semantics node — see StatisticalAreaMapCanvas's doc comment.
        Text(
            text =
                selectedCode
                    ?.let { stringResource(R.string.gear_stat_rectangle_map_selected_announcement, it) }
                    .orEmpty(),
            modifier =
                Modifier
                    .testTag(GearStatRectangleScreenTestTags.LIVE_REGION)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelSmall,
        )

        when (geometryStatus) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = geometryStatus.message,
                    testTag = GearStatRectangleScreenTestTags.ERROR_MESSAGE,
                    isRetryable = geometryStatus.isRetryable,
                    onRetry = onRetryGeometry,
                )
            is UiStatus.Content -> {
                val geometry = geometryStatus.value
                val sortedRectangles =
                    remember(geometry) { geometry.subRectangles.filter { it.seaOverlapping }.sortedBy { it.subCode } }
                val initialCentre =
                    remember(geometry, departurePortName) {
                        MapCentring.initialCentreFor(departurePortName, geometry.ports)
                    }

                StatisticalAreaMapCanvas(
                    subRectangles = sortedRectangles,
                    landPolygons = geometry.landPolygons,
                    ports = geometry.ports,
                    selectedCode = selectedCode,
                    onCodeSelected = { code ->
                        if (code != null) {
                            selectedCode = code
                            showError = false
                        }
                    },
                    initialCentre = initialCentre,
                    contentDescription = stringResource(R.string.gear_stat_rectangle_map_content_description),
                    cameraResetKey = gearUse.id,
                    testTag = GearStatRectangleScreenTestTags.MAP_CANVAS,
                )

                StatisticalSubRectangleRadioList(
                    codes = sortedRectangles.map { it.subCode },
                    selectedCode = selectedCode,
                    onCodeSelected = {
                        selectedCode = it
                        showError = false
                    },
                    optionTestTagPrefix = GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX,
                    modifier = Modifier.focusRequester(listFocusRequester),
                )
            }
        }

        SecondaryActionButton(
            text = stringResource(R.string.gear_stat_rectangle_map_cant_find),
            onClick = onCantFindOnMap,
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.MAP_CANT_FIND_ACTION),
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val code = selectedCode
                if (code == null) {
                    showError = true
                    focusSummary = true
                } else {
                    onSubmit(code)
                }
            },
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.MAP_SAVE_ACTION),
        )
    }
}

/**
 * A [LazyColumn]-backed single-selection radio list — visually/semantically equivalent to
 * [uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup] (same `Role.RadioButton`/focus-indicator/
 * touch-target conventions) but lazily composed, since the full/global sea-overlapping sub-rectangle set is
 * ~2,857 rows (see plan risk R3) — eagerly composing that many `Row`s in a plain `Column` would be a
 * real jank risk on lower-end devices.
 */
@Suppress("FunctionNaming")
@Composable
private fun StatisticalSubRectangleRadioList(
    codes: List<String>,
    selectedCode: String?,
    onCodeSelected: (String) -> Unit,
    optionTestTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().height(LIST_HEIGHT).selectableGroup(),
    ) {
        items(items = codes, key = { it }) { code ->
            var hasFocus by remember { mutableStateOf(false) }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.minTouchTarget)
                        .selectable(
                            selected = code == selectedCode,
                            onClick = { onCodeSelected(code) },
                            role = Role.RadioButton,
                        ).focusable()
                        .onFocusChanged { hasFocus = it.hasFocus }
                        .then(if (hasFocus) Modifier.govukFocusIndicator() else Modifier)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                        .testTag("${optionTestTagPrefix}_$code"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                RadioButton(
                    selected = code == selectedCode,
                    onClick = null,
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor = MmoColors.Text,
                            unselectedColor = MmoColors.Text,
                        ),
                )
                Text(text = code, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private val LIST_HEIGHT = 320.dp
