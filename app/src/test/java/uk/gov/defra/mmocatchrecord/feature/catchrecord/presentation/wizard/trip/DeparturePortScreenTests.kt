package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/** ADR 0016: a live vessel with no previously-used ports must show the search field directly, with no
 *  empty favourites heading/section — see the Phase 6 `getPreviouslyUsedPorts` fix. */
@RunWith(RobolectricTestRunner::class)
class DeparturePortScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `an empty favourite ports list goes straight to search with no favourites section`() {
        composeTestRule.setContent {
            MmoTheme {
                PortSelectionStepContent(
                    initialSelection = null,
                    allPorts = emptyList(),
                    favouritePorts = emptyList(),
                    autocompleteLabel = "Departure port",
                    autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
                    optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
                    errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(DeparturePortScreenTestTags.ADD_PORT_ACTION).assertCountEquals(0)
    }
}
