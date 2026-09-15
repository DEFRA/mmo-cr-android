package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteField
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteOption
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port

class GdsAutocompleteFieldTest {
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

        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
        composeTestRule.onNodeWithTag("port_field").performTextInput("H")
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
        composeTestRule.onNodeWithTag("port_field").performTextInput("a")
        composeTestRule.onAllNodesWithTag("port_live_region").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(1)
        composeTestRule.onNodeWithTag("port_suggestion_0").performClick()
        composeTestRule.onAllNodesWithTag("port_suggestion_0").assertCountEquals(0)
    }
}
