@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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

data class GdsCheckboxOption(
    val id: String,
    val label: String,
    /** Secondary, greyed summary line rendered under [label] (e.g. a gear's measurement summary). */
    val secondaryText: String? = null,
)

/**
 * A multi-select checkbox list, in the same spirit as [GdsRadioGroup] but: (a) any number of options may
 * be checked at once, (b) each row may show a secondary/greyed summary line under its label, and (c)
 * checking a row can reveal an indented conditional composable — [conditionalContent] — directly below
 * that specific row (e.g. the gear-summary checklist's "Number of times gear was shot on trip" field).
 *
 * The conditional content is a normal sibling in the same [Column] immediately after its row, not an
 * overlay, so TalkBack/keyboard focus order follows visual order and a newly revealed field is reachable
 * without any extra focus-management wiring.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun GdsCheckboxGroup(
    options: List<GdsCheckboxOption>,
    checkedOptionIds: Set<String>,
    onCheckedChange: (optionId: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTagPrefix: String = "",
    conditionalContent: @Composable (optionId: String) -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEachIndexed { index, option ->
            val checked = option.id in checkedOptionIds
            GdsCheckboxOptionRow(
                option = option,
                index = index,
                checked = checked,
                optionTestTagPrefix = optionTestTagPrefix,
                onCheckedChange = { onCheckedChange(option.id, it) },
            )
            if (checked) {
                Column(modifier = Modifier.padding(start = Spacing.l)) {
                    conditionalContent(option.id)
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun GdsCheckboxOptionRow(
    option: GdsCheckboxOption,
    index: Int,
    checked: Boolean,
    optionTestTagPrefix: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox)
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
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MmoColors.Text, uncheckedColor = MmoColors.Text),
        )
        Column {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            if (option.secondaryText != null) {
                Text(
                    text = option.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MmoColors.Grey1,
                )
            }
        }
    }
}
