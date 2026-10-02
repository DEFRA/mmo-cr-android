package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.PortSearch

/**
 * JVM/Robolectric port of the androidTest `GdsAutocompleteFieldTest` — see that file's package-level doc
 * comment on why a `testDebugUnitTest`-reachable equivalent is needed for the Kover/SonarCloud coverage
 * gate (`connectedDebugAndroidTest` coverage is not merged into `new_coverage`).
 */
@RunWith(RobolectricTestRunner::class)
class GdsAutocompleteFieldTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun autocompleteGatesSuggestionsUntilTwoCharactersAndUpdatesLiveRegion() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                val ports =
                    listOf(
                        Port("port-hastings", "Hastings", "AREA-HASTINGS"),
                        Port("port-dover", "Dover", "AREA-DOVER"),
                    )
                val options =
                    if (value.length < 2) {
                        emptyList()
                    } else {
                        PortSearch.filterSuggestions(value, ports).map {
                            GdsAutocompleteOption(it.id, it.name)
                        }
                    }
                GdsAutocompleteField(
                    label = "Enter port",
                    value = value,
                    options = options,
                    onValueChange = { value = it },
                    onOptionSelected = { value = it.label },
                    fieldTestTag = "port_field",
                    liveRegionTestTag = "port_live_region",
                    suggestionTestTagPrefix = "port_suggestion",
                    noMatchesTestTag = "port_no_matches",
                )
            }
        }

        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
        composeTestRule.onNodeWithTag("port_field").performTextInput("H")
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
        composeTestRule.onNodeWithTag("port_field").performTextInput("a")
        composeTestRule.onNodeWithTag("port_live_region").assertExists()
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(1)
        composeTestRule.onNodeWithTag("port_suggestion_0").performClick()
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
    }

    @Test
    fun selectingSuggestionAfterPartialInputPlacesCursorAtEndOfSelectedLabel() {
        setPortAutocompleteContent()

        composeTestRule.onNodeWithTag("port_field").performTextInput("Ha")
        composeTestRule.onNodeWithTag("port_suggestion_0").performClick()

        composeTestRule
            .onNodeWithTag("port_field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Hastings")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange("Hastings".length)))
    }

    @Test
    fun selectingSuggestionMatchingTypedTextMovesCursorFromMiddleToEnd() {
        setPortAutocompleteContent()

        composeTestRule.onNodeWithTag("port_field").performTextInput("Dover")
        composeTestRule.onNodeWithTag("port_field").performTextInputSelection(TextRange(2))
        // A cursor-only move must not dismiss or reset the open suggestion list.
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(1)
        composeTestRule.onNodeWithTag("port_suggestion_0").performClick()

        composeTestRule
            .onNodeWithTag("port_field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange("Dover".length)))
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
    }

    @Test
    fun blankQueryShowsNoMatchesTextWhenOptionsAreEmpty() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                GdsAutocompleteField(
                    label = "Enter port",
                    value = value,
                    options = emptyList(),
                    onValueChange = { value = it },
                    onOptionSelected = {},
                    fieldTestTag = "port_field",
                    liveRegionTestTag = "port_live_region",
                    suggestionTestTagPrefix = "port_suggestion",
                    noMatchesTestTag = "port_no_matches",
                    noMatchesText = "No matches found",
                )
            }
        }

        composeTestRule.onNodeWithTag("port_field").performTextInput("zz")
        composeTestRule.onNodeWithTag("port_no_matches").assertExists()
    }

    private fun setPortAutocompleteContent() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                val ports =
                    listOf(
                        Port("port-hastings", "Hastings", "AREA-HASTINGS"),
                        Port("port-dover", "Dover", "AREA-DOVER"),
                    )
                val options =
                    PortSearch.filterSuggestions(value, ports).map {
                        GdsAutocompleteOption(it.id, it.name)
                    }
                GdsAutocompleteField(
                    label = "Enter port",
                    value = value,
                    options = options,
                    onValueChange = { value = it },
                    onOptionSelected = { value = it.label },
                    fieldTestTag = "port_field",
                    liveRegionTestTag = "port_live_region",
                    suggestionTestTagPrefix = "port_suggestion",
                    noMatchesTestTag = "port_no_matches",
                )
            }
        }
    }
}
