@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteField
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteOption
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioOption
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port

/** Whether [PortSelectionStepContent] initially renders its single-previous-port Yes/No confirmation,
 *  so each screen can seed its own lifted title state with the same answer. */
internal fun isSinglePortConfirmationInitiallyShown(
    favouritePorts: List<Port>,
    initialSelection: PortSelection?,
): Boolean = favouritePorts.size == 1 && initialSelection?.selectionMode != PortSelectionMode.FirstTime

/** Lifted single-port-confirmation visibility, seeded once and kept in sync by
 *  `onShowingPortConfirmationChanged` so each screen can pick its scaffold title reactively. */
@Composable
internal fun rememberPortConfirmationVisible(
    favouritePorts: List<Port>,
    initialSelection: PortSelection?,
) = rememberSaveable(favouritePorts.size) {
    mutableStateOf(isSinglePortConfirmationInitiallyShown(favouritePorts, initialSelection))
}

/** The scaffold title: the shared confirm-question wording naming the sole favourite port, or [defaultTitleRes]. */
@Composable
internal fun portSelectionScaffoldTitle(
    showingPortConfirmation: Boolean,
    favouritePorts: List<Port>,
    defaultTitleRes: Int,
): String =
    if (showingPortConfirmation) {
        stringResource(R.string.port_selection_confirm_title, favouritePorts.single().name)
    } else {
        stringResource(defaultTitleRes)
    }

/** The Yes/No radio pair for the single-port confirmation; [question] labels the group for TalkBack. */
@Composable
internal fun SinglePortConfirmationOptions(
    question: String,
    isSelectedYes: Boolean,
    onAnswered: (confirmedYes: Boolean) -> Unit,
    optionTestTagPrefix: String,
) {
    GdsRadioGroup(
        options =
            listOf(
                GdsRadioOption("true", stringResource(R.string.yes)),
                GdsRadioOption("false", stringResource(R.string.no)),
            ),
        selectedOptionId = if (isSelectedYes) "true" else null,
        onOptionSelected = { onAnswered(it == "true") },
        optionTestTagPrefix = optionTestTagPrefix,
        groupLabel = question,
    )
}

/** The multi-favourite radio list plus its "Add port" escape hatch into search (see
 *  [PortSelectionStepContent]'s two-or-more-favourites branch). */
@Composable
internal fun PortSelectionFavouritesList(
    favouritePorts: List<Port>,
    selectedFavouriteId: String?,
    onOptionSelected: (String) -> Unit,
    onAddPort: () -> Unit,
    optionTestTagPrefix: String,
    addPortTag: String,
) {
    GdsRadioGroup(
        options = favouritePorts.map { GdsRadioOption(it.id, it.name) },
        selectedOptionId = selectedFavouriteId,
        onOptionSelected = onOptionSelected,
        optionTestTagPrefix = optionTestTagPrefix,
    )
    SecondaryActionButton(
        text = stringResource(R.string.add_port),
        onClick = onAddPort,
        modifier = Modifier.testTag(addPortTag),
    )
}

/** The free-text autocomplete search field; resolves a typed exact name match to its port id itself. */
@Suppress("detekt.LongParameterList")
@Composable
internal fun PortSelectionSearchField(
    autocompletePlaceholder: String,
    allPorts: List<Port>,
    suggestions: List<Port>,
    searchQuery: String,
    onQueryChanged: (query: String, matchedPortId: String?) -> Unit,
    onSuggestionSelected: (GdsAutocompleteOption) -> Unit,
    fieldTestTag: String,
    liveRegionTestTag: String,
    suggestionTestTagPrefix: String,
    noMatchesTestTag: String,
) {
    GdsAutocompleteField(
        placeholder = autocompletePlaceholder,
        value = searchQuery,
        options = suggestions.map { GdsAutocompleteOption(it.id, it.name) },
        onValueChange = { query ->
            val matchedPortId = allPorts.firstOrNull { port -> port.name.equals(query, ignoreCase = true) }?.id
            onQueryChanged(query, matchedPortId)
        },
        onOptionSelected = onSuggestionSelected,
        fieldTestTag = fieldTestTag,
        liveRegionTestTag = liveRegionTestTag,
        suggestionTestTagPrefix = suggestionTestTagPrefix,
        noMatchesTestTag = noMatchesTestTag,
    )
}

/** Resolves the current committed [PortSelection], or `null` if unchosen, for whichever mode
 *  [PortSelectionStepContent] is in — kept separate to avoid adding to that composable's complexity. */
internal fun resolvePortSelection(
    showSearch: Boolean,
    selectedSearchId: String?,
    selectedFavouriteId: String?,
): PortSelection? =
    if (showSearch) {
        selectedSearchId?.let { PortSelection(it, PortSelectionMode.FirstTime) }
    } else {
        selectedFavouriteId?.let { PortSelection(it, PortSelectionMode.Favourite) }
    }
