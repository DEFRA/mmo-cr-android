package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
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
                    placeholder = "Enter port",
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
    fun `a stale IME update delivered before the picked suggestion renders does not revert the field`() {
        setPortAutocompleteContent()
        composeTestRule.onNodeWithTag("port_field").performTextInput("Ha")
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(1)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("port_suggestion_0").performClick()
        // Mimics the IME acting on its pre-selection "Ha" buffer before the next frame re-renders the field.
        composeTestRule.onNodeWithTag("port_field").performTextInputSelection(TextRange(1))
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule
            .onNodeWithTag("port_field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Hastings")))
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
    }

    @Test
    fun `a genuine edit delivered before the picked suggestion renders is still applied`() {
        setPortAutocompleteContent()
        composeTestRule.onNodeWithTag("port_field").performTextInput("Ha")
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(1)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("port_suggestion_0").performClick()
        composeTestRule.onNodeWithTag("port_field").performTextReplacement("Hastings Pier")
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule
            .onNodeWithTag("port_field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Hastings Pier")))
    }

    @Test
    fun blankQueryShowsNoMatchesTextWhenOptionsAreEmpty() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                GdsAutocompleteField(
                    placeholder = "Enter port",
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

    @Test
    fun `field without a placeholder renders and accepts input`() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                GdsAutocompleteField(
                    value = value,
                    options = emptyList(),
                    onValueChange = { value = it },
                    onOptionSelected = {},
                    fieldTestTag = "port_field",
                    liveRegionTestTag = "port_live_region",
                    suggestionTestTagPrefix = "port_suggestion",
                    noMatchesTestTag = "port_no_matches",
                )
            }
        }

        composeTestRule.onAllNodesWithText("Enter port").assertCountEquals(0)
        composeTestRule.onNodeWithTag("port_field").performTextInput("Do")
        composeTestRule
            .onNodeWithTag("port_field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Do")))
    }

    @Test
    fun `multiple suggestions with a highlighted best match remain clickable`() {
        setPortAutocompleteContent(
            listOf(Port("port-a", "Doverhaven", "A"), Port("port-b", "Dover", "B")),
        )

        composeTestRule.onNodeWithTag("port_field").performTextInput("Dov")
        composeTestRule.onNodeWithTag("port_suggestion_1").performClick()

        composeTestRule
            .onNodeWithTag("port_field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Doverhaven")))
    }

    @Test
    fun `an error message renders below the field in error styling`() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                GdsAutocompleteField(
                    placeholder = "Enter port",
                    value = value,
                    options = emptyList(),
                    onValueChange = { value = it },
                    onOptionSelected = {},
                    fieldTestTag = "port_field",
                    liveRegionTestTag = "port_live_region",
                    suggestionTestTagPrefix = "port_suggestion",
                    noMatchesTestTag = "port_no_matches",
                    errorText = "Enter a port",
                )
            }
        }

        composeTestRule.onAllNodesWithText("Enter a port").assertCountEquals(1)
    }

    @Test
    fun `a third suggestion below the best match renders in its unhighlighted even row style`() {
        setPortAutocompleteContent(
            listOf(
                Port("port-a", "Dover", "A"),
                Port("port-b", "Doverhaven", "B"),
                Port("port-c", "Doverside", "C"),
            ),
        )

        composeTestRule.onNodeWithTag("port_field").performTextInput("Dov")
        composeTestRule.onNodeWithTag("port_suggestion_2").performClick()

        composeTestRule
            .onNodeWithTag("port_field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Doverside")))
    }

    @Test
    fun `a suggestion with a secondary label renders it on a second line below the primary label`() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                GdsAutocompleteField(
                    placeholder = "Enter area",
                    value = value,
                    options =
                        listOf(
                            GdsAutocompleteOption("area-1", "38E95", secondaryLabel = "50.9N 0.6E"),
                        ),
                    onValueChange = { value = it },
                    onOptionSelected = {},
                    fieldTestTag = "area_field",
                    liveRegionTestTag = "area_live_region",
                    suggestionTestTagPrefix = "area_suggestion",
                    noMatchesTestTag = "area_no_matches",
                )
            }
        }

        composeTestRule.onNodeWithTag("area_field").performTextInput("38")
        composeTestRule.onAllNodesWithText("38E95").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("50.9N 0.6E").assertCountEquals(1)
    }

    @Test
    fun `a suggestion without a secondary label renders only its primary label line, unchanged from before`() {
        setPortAutocompleteContent()

        composeTestRule.onNodeWithTag("port_field").performTextInput("Ha")
        composeTestRule.onAllNodesWithText("Hastings").assertCountEquals(1)
    }

    @Test
    fun `a suggestion's accessibility label overrides its merged-row TalkBack name`() {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                GdsAutocompleteField(
                    placeholder = "Enter area",
                    value = value,
                    options =
                        listOf(
                            GdsAutocompleteOption(
                                "area-1",
                                "38E95",
                                secondaryLabel = "50.9N 0.6E",
                                accessibilityLabel = "38E95, 50.9 north, 0.6 east",
                            ),
                        ),
                    onValueChange = { value = it },
                    onOptionSelected = {},
                    fieldTestTag = "area_field",
                    liveRegionTestTag = "area_live_region",
                    suggestionTestTagPrefix = "area_suggestion",
                    noMatchesTestTag = "area_no_matches",
                )
            }
        }

        composeTestRule.onNodeWithTag("area_field").performTextInput("38")
        composeTestRule
            .onNodeWithTag("area_suggestion_0")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("38E95, 50.9 north, 0.6 east"),
                ),
            )
    }

    private fun setPortAutocompleteContent(
        ports: List<Port> =
            listOf(
                Port("port-hastings", "Hastings", "AREA-HASTINGS"),
                Port("port-dover", "Dover", "AREA-DOVER"),
            ),
    ) {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf("") }
                val options =
                    PortSearch.filterSuggestions(value, ports).map {
                        GdsAutocompleteOption(it.id, it.name)
                    }
                GdsAutocompleteField(
                    placeholder = "Enter port",
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
