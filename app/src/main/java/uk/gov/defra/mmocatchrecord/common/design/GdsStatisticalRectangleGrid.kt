@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A schematic, Compose-drawn grid of labelled statistical sub-rectangle cells — see `GearStatRectangleScreen`
 * (Phase 4). Deliberately **not** a real map: no map tiles, no coastline imagery, and no external mapping
 * SDK (e.g. Google Maps/Mapbox) is used, which also avoids any device location-permission surface. Codes
 * are laid out in reading-order rows of [columns] cells; the reference-data stub currently only supplies a
 * flat ordered code list (not precise lat/long grid coordinates), so this is an honest simplification of
 * "laid out in their relative grid positions" rather than a geographically exact rendering — a future
 * real-map iteration would need actual coordinates from the backend to improve on this.
 *
 * Modelled as a mutually-exclusive single-selection group (like [GdsRadioGroup]), since only one
 * sub-rectangle may be selected at a time — each cell uses [Role.RadioButton] semantics so TalkBack
 * announces its selected/unselected state and position within the group.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun GdsStatisticalRectangleGrid(
    codes: List<String>,
    selectedCode: String?,
    onCodeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    cellTestTagPrefix: String = "",
    columns: Int = 3,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        codes.chunked(columns).forEachIndexed { rowIndex, rowCodes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                rowCodes.forEachIndexed { columnIndex, code ->
                    val index = rowIndex * columns + columnIndex
                    GdsStatisticalRectangleCell(
                        code = code,
                        selected = code == selectedCode,
                        onClick = { onCodeSelected(code) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(
                                    if (cellTestTagPrefix.isBlank()) {
                                        Modifier
                                    } else {
                                        Modifier.testTag("${cellTestTagPrefix}_$index")
                                    },
                                ),
                    )
                }
                // Pad a partial final row so its cells keep the same width as full rows above.
                repeat(columns - rowCodes.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun GdsStatisticalRectangleCell(
    code: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .heightIn(min = Spacing.minTouchTarget)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .focusable()
                .onFocusChanged { hasFocus = it.hasFocus }
                .then(if (hasFocus) Modifier.govukFocusIndicator() else Modifier)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MmoColors.GovBlue else MmoColors.Grey2,
                ).background(if (selected) MmoColors.SelectedTint else MmoColors.White)
                .padding(Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}
