@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.LongMethod", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.PortSearch

data class GdsAutocompleteOption(
    val id: String,
    val label: String,
    /** An optional second line (e.g. coordinates/ICES rectangle) — `null` renders exactly as before. */
    val secondaryLabel: String? = null,
    /** Replaces the merged row's TalkBack name when the visual text alone reads poorly (e.g. coordinates). */
    val accessibilityLabel: String? = null,
)

@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
fun GdsAutocompleteField(
    value: String,
    options: List<GdsAutocompleteOption>,
    onValueChange: (String) -> Unit,
    onOptionSelected: (GdsAutocompleteOption) -> Unit,
    fieldTestTag: String,
    liveRegionTestTag: String,
    suggestionTestTagPrefix: String,
    noMatchesTestTag: String,
    modifier: Modifier = Modifier,
    // Shown as grey hint text inside the box (not a label above it) — the wizard heading above the field
    // already serves as the GDS question/label for these single-question-per-page screens.
    placeholder: String? = null,
    errorText: String? = null,
    // Both default to the existing port-search behaviour so every current call site (port screens,
    // GdsAutocompleteFieldTest) is unaffected; gear search overrides only [noMatchesText] since the
    // "type more characters"/"N suggestions available" wording is already generic.
    minQueryLength: Int = PortSearch.MIN_QUERY_LENGTH,
    noMatchesText: String = stringResource(R.string.port_search_no_matches_found),
) {
    var dismissedQuery by remember { mutableStateOf<String?>(null) }
    // Holds the cursor/selection alongside the hoisted String. The String-based OutlinedTextField keeps
    // the previous cursor offset when the text is replaced programmatically, which left the cursor
    // mid-word after a suggestion was picked; any externally-driven text change now places it at the end.
    var textFieldValueState by remember { mutableStateOf(value.withCursorAtEnd()) }
    val textFieldValue = textFieldValueState.syncedWith(value)
    // Structural-equality state: writing an unchanged value is a no-op, so no extra recomposition.
    // Typed text replaced by a picked suggestion, until the next composition hands the IME the new label.
    var preSelectionText by remember { mutableStateOf<String?>(null) }
    SideEffect {
        textFieldValueState = textFieldValue
        preSelectionText = null
    }
    val trimmedQuery = value.trim()
    val isQueryLongEnough = trimmedQuery.length >= minQueryLength
    val showSuggestions = isQueryLongEnough && dismissedQuery != value && options.isNotEmpty()
    val showNoMatches = isQueryLongEnough && dismissedQuery != value && options.isEmpty()
    val announcement =
        autocompleteAnnouncementFor(
            isQueryLongEnough = isQueryLongEnough,
            showSuggestions = showSuggestions,
            minQueryLength = minQueryLength,
            optionCount = options.size,
            noMatchesText = noMatchesText,
        )

    // No spacing: the suggestion list must sit flush against the bottom edge of the field.
    Column(modifier = modifier) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = onValueChange@{ newValue ->
                // An IME echo of the stale pre-selection buffer would otherwise revert the pick.
                if (newValue.text == preSelectionText) return@onValueChange
                val hasTextChanged = newValue.text != textFieldValueState.text
                textFieldValueState = newValue
                // Cursor/selection-only changes must not re-open dismissed suggestions or reset callers.
                if (hasTextChanged) {
                    onValueChange(newValue.text)
                }
            },
            singleLine = true,
            isError = errorText != null,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            placeholder =
                placeholder?.let {
                    {
                        Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MmoColors.Grey1)
                    }
                },
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
        if (showSuggestions) {
            AutocompleteSuggestions(
                options = options,
                highlightedIndex = AutocompleteBestMatch.indexOf(trimmedQuery, options.map { it.label }),
                suggestionTestTagPrefix = suggestionTestTagPrefix,
                onOptionSelected = { option ->
                    // Also covers picking an option whose label equals the typed text.
                    dismissedQuery = option.label
                    preSelectionText = textFieldValueState.text
                    textFieldValueState = option.label.withCursorAtEnd()
                    onValueChange(option.label)
                    onOptionSelected(option)
                },
            )
        }
        if (showNoMatches) {
            Text(
                text = noMatchesText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs).testTag(noMatchesTestTag),
            )
        }
        if (errorText != null) {
            Text(
                text = errorText,
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Text(
            text = announcement,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(0f).testTag(liveRegionTestTag).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The polite live-region announcement for the current query/results state — see [GdsAutocompleteField]. */
@Composable
private fun autocompleteAnnouncementFor(
    isQueryLongEnough: Boolean,
    showSuggestions: Boolean,
    minQueryLength: Int,
    optionCount: Int,
    noMatchesText: String,
): String =
    when {
        !isQueryLongEnough ->
            pluralStringResource(R.plurals.port_search_type_more_characters, minQueryLength, minQueryLength)
        showSuggestions -> pluralStringResource(R.plurals.port_search_suggestions_available, optionCount, optionCount)
        else -> noMatchesText
    }

/** The suggestions dropdown list rendered below the field when matches are available — see [GdsAutocompleteField]. */
@Composable
private fun AutocompleteSuggestions(
    options: List<GdsAutocompleteOption>,
    highlightedIndex: Int?,
    suggestionTestTagPrefix: String,
    onOptionSelected: (GdsAutocompleteOption) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MmoColors.Text)
                .background(MmoColors.White),
    ) {
        options.forEachIndexed { index, option ->
            val isHighlighted = index == highlightedIndex
            val rowBackground =
                when {
                    isHighlighted -> MmoColors.GovBlue
                    index % 2 == 1 -> MmoColors.AutocompleteOddRow
                    else -> MmoColors.White
                }
            if (index > 0) {
                HorizontalDivider(thickness = 1.dp, color = MmoColors.Text)
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .heightIn(min = Spacing.minTouchTarget)
                        .clickable { onOptionSelected(option) }
                        .padding(
                            horizontal = Spacing.s,
                            vertical = Spacing.xs,
                        ).testTag("${suggestionTestTagPrefix}_$index")
                        .semantics {
                            role = Role.Button
                            option.accessibilityLabel?.let { contentDescription = it }
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val textColor = if (isHighlighted) MmoColors.White else MmoColors.Text
                Column {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                    )
                    option.secondaryLabel?.let { secondaryLabel ->
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}

private fun String.withCursorAtEnd(): TextFieldValue = TextFieldValue(text = this, selection = TextRange(length))

/** Keeps the user's cursor/selection while the text is unchanged; otherwise places the cursor at the end of [text]. */
private fun TextFieldValue.syncedWith(text: String): TextFieldValue =
    if (this.text == text) this else text.withCursorAtEnd()
