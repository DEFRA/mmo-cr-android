@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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

data class GdsRadioOption(
    val id: String,
    val label: String,
)

@Suppress("FunctionNaming")
@Composable
fun GdsRadioGroup(
    options: List<GdsRadioOption>,
    selectedOptionId: String?,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTagPrefix: String = "",
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEachIndexed { index, option ->
            GdsRadioOptionRow(
                option = option,
                index = index,
                selected = option.id == selectedOptionId,
                optionTestTagPrefix = optionTestTagPrefix,
                onClick = { onOptionSelected(option.id) },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun GdsRadioOptionRow(
    option: GdsRadioOption,
    index: Int,
    selected: Boolean,
    optionTestTagPrefix: String,
    onClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .focusable()
                .onFocusChanged { hasFocus = it.hasFocus }
                .then(if (hasFocus) Modifier.govukFocusIndicator() else Modifier)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                .then(
                    if (optionTestTagPrefix.isBlank()) Modifier else Modifier.testTag("${optionTestTagPrefix}_$index"),
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MmoColors.Text, unselectedColor = MmoColors.Text),
        )
        Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
    }
}
