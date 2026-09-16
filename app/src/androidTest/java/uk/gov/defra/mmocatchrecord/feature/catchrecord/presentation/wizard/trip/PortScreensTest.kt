package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DeparturePortEntryMode

class PortScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val allPorts =
        listOf(
            Port("port-hastings", "Hastings", "AREA-HASTINGS"),
            Port("port-dover", "Dover", "AREA-DOVER"),
        )

    @Test
    fun samePortShortcutYesAcceptsAndSkips() {
        var accepted = false
        composeTestRule.setContent {
            MmoTheme {
                DeparturePortScreenContent(
                    draft =
                        CatchRecordDraft(
                            "draft-1",
                            "vessel-achilles",
                            status = DraftStatus.Draft,
                            modifiedAtEpochMillis = 1L,
                        ),
                    allPorts = allPorts,
                    favouritePorts = allPorts,
                    entryMode = DeparturePortEntryMode.SamePortShortcut,
                    samePortCandidate = allPorts.first(),
                    onSamePortAccepted = { accepted = true },
                    onSamePortDeclined = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.SAME_PORT_OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        assertTrue(accepted)
    }

    @Test
    fun departurePortAutocompleteHappyPathSubmitsFirstTimeSelection() {
        var selection: PortSelection? = null
        composeTestRule.setContent {
            MmoTheme {
                PortSelectionStepContent(
                    initialSelection = null,
                    allPorts = allPorts,
                    favouritePorts = emptyList(),
                    autocompleteLabel = "Enter port",
                    autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
                    optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
                    errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = { selection = it },
                )
            }
        }

        composeTestRule.onAllNodesWithTag("${DeparturePortScreenTestTags.SUGGESTION_PREFIX}_0").assertCountEquals(0)
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Ha")
        composeTestRule.onAllNodesWithTag("${DeparturePortScreenTestTags.SUGGESTION_PREFIX}_0").assertCountEquals(1)
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(PortSelection("port-hastings", PortSelectionMode.FirstTime), selection)
    }

    @Test
    fun returnPortFavouritesHappyPathSubmitsFavouriteSelection() {
        var selection: PortSelection? = null
        composeTestRule.setContent {
            MmoTheme {
                PortSelectionStepContent(
                    initialSelection = null,
                    allPorts = allPorts,
                    favouritePorts = allPorts,
                    autocompleteLabel = "Enter port",
                    autocompleteFieldTag = ReturnPortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = ReturnPortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = ReturnPortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = ReturnPortScreenTestTags.NO_MATCHES,
                    optionPrefix = ReturnPortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    addPortTag = ReturnPortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = ReturnPortScreenTestTags.SAVE_ACTION,
                    errorTag = ReturnPortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = { selection = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${ReturnPortScreenTestTags.FAVOURITE_OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(ReturnPortScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(PortSelection("port-dover", PortSelectionMode.Favourite), selection)
    }
}
