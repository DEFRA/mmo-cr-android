@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.LongMethod", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.PortSearch

data class GdsAutocompleteOption(
    val id: String,
    val label: String,
)

@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
fun GdsAutocompleteField(
    label: String,
    value: String,
    options: List<GdsAutocompleteOption>,
    onValueChange: (String) -> Unit,
    onOptionSelected: (GdsAutocompleteOption) -> Unit,
    fieldTestTag: String,
    liveRegionTestTag: String,
    suggestionTestTagPrefix: String,
    noMatchesTestTag: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    var dismissedQuery by remember { mutableStateOf<String?>(null) }
    val trimmedQuery = value.trim()
    val isQueryLongEnough = trimmedQuery.length >= PortSearch.MIN_QUERY_LENGTH
    val showSuggestions = isQueryLongEnough && dismissedQuery != value && options.isNotEmpty()
    val showNoMatches = isQueryLongEnough && dismissedQuery != value && options.isEmpty()
    val announcement =
        when {
            !isQueryLongEnough ->
                stringResource(
                    R.string.port_search_type_more_characters,
                    PortSearch.MIN_QUERY_LENGTH,
                )
            showSuggestions ->
                pluralStringResource(
                    R.plurals.port_search_suggestions_available,
                    options.size,
                    options.size,
                )
            else -> stringResource(R.string.port_search_no_matches_found)
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = value,
            onValueChange = {
                dismissedQuery = null
                onValueChange(it)
            },
            singleLine = true,
            isError = errorText != null,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (errorText == null) MmoColors.Text else MmoColors.ErrorRed,
                    unfocusedBorderColor = if (errorText == null) MmoColors.Text else MmoColors.ErrorRed,
                    focusedContainerColor = MmoColors.White,
                    unfocusedContainerColor = MmoColors.White,
                    cursorColor = MmoColors.Text,
                    focusedTextColor = MmoColors.Text,
                    unfocusedTextColor = MmoColors.Text,
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).testTag(fieldTestTag),
        )
        Text(
            text = announcement,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(0f).testTag(liveRegionTestTag).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (errorText != null) {
            Text(text = errorText, color = MmoColors.ErrorRed, style = MaterialTheme.typography.bodyMedium)
        }
        if (showSuggestions) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MmoColors.Grey2,
                        ).background(MmoColors.White),
            ) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.minTouchTarget)
                                .clickable {
                                    dismissedQuery = option.label
                                    onValueChange(option.label)
                                    onOptionSelected(option)
                                }.padding(
                                    horizontal = Spacing.s,
                                    vertical = Spacing.xs,
                                ).testTag("${suggestionTestTagPrefix}_$index")
                                .semantics {
                                    role =
                                        Role.Button
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (showNoMatches) {
            Text(
                text = stringResource(R.string.port_search_no_matches_found),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(noMatchesTestTag),
            )
        }
    }
}
