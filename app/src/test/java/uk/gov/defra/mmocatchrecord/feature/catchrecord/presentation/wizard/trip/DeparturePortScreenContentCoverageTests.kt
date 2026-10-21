package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port

/** Covers departure-port content branches not reached by the direct screen/content tests. */
@RunWith(RobolectricTestRunner::class)
class DeparturePortScreenContentCoverageTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val allPorts =
        listOf(
            Port("port-hastings", "Hastings", "AREA-HASTINGS"),
            Port("port-dover", "Dover", "AREA-DOVER"),
        )

    private fun sampleDraft(departurePort: PortSelection? = null) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-achilles",
            departurePort = departurePort,
            status = DraftStatus.Draft,
            modifiedAtEpochMillis = 1L,
        )

    @Test
    fun favouritesModeWithNoSelectionShowsErrorThenSubmitsOnceChosen() {
        var submitted: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                DeparturePortScreenContent(
                    draft = sampleDraft(),
                    allPorts = allPorts,
                    favouritePorts = allPorts,
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(PortSelection("port-hastings", PortSelectionMode.Favourite), submitted?.departurePort)
    }

    @Test
    fun noFavouritesDelegatesToPortSelectionStepContentAndWrapsTheSubmittedDraft() {
        var submitted: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                DeparturePortScreenContent(
                    draft = sampleDraft(),
                    allPorts = allPorts,
                    favouritePorts = emptyList(),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Do")
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(PortSelection("port-dover", PortSelectionMode.FirstTime), submitted?.departurePort)
    }

    @Test
    fun favouriteListEntryModeWithNoSelectionShowsErrorThenSubmitsOnceChosen() {
        var submitted: PortSelection? = null
        composeTestRule.setContent {
            MmoTheme {
                PortSelectionStepContent(
                    initialSelection = null,
                    allPorts = allPorts,
                    favouritePorts = allPorts,
                    autocompletePlaceholder = "Enter port",
                    autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
                    optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
                    errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(PortSelection("port-hastings", PortSelectionMode.Favourite), submitted)
    }

    @Test
    fun addPortActionSwitchesFromFavouritesToSearch() {
        composeTestRule.setContent {
            MmoTheme {
                PortSelectionStepContent(
                    initialSelection = null,
                    allPorts = allPorts,
                    favouritePorts = allPorts,
                    autocompletePlaceholder = "Enter port",
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

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.ADD_PORT_ACTION).performClick()

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).assertIsDisplayed()
    }

    @Test
    fun aQueryWithNoMatchesShowsTheNoMatchesMessageAndBlocksSubmit() {
        var submitted: PortSelection? = null
        composeTestRule.setContent {
            MmoTheme {
                PortSelectionStepContent(
                    initialSelection = null,
                    allPorts = allPorts,
                    favouritePorts = emptyList(),
                    autocompletePlaceholder = "Enter port",
                    autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
                    optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
                    errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("zz")
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.NO_MATCHES).assertIsDisplayed()

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        assertNull(submitted)
    }

    @Test
    fun theSearchPreviewRendersWithoutThrowing() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                DeparturePortScreen_SearchPreview()
            }
        }
    }

    @Test
    fun theFavouritesPreviewRendersWithoutThrowing() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                DeparturePortScreen_FavouritesPreview()
            }
        }
    }

    @Test
    fun theErrorPreviewRendersWithoutThrowing() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                DeparturePortScreen_ErrorPreview()
            }
        }
    }

    @Test
    fun theConfirmPreviousPortPreviewShowsTheQuestionAsTheTitle() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                DeparturePortScreenConfirmPreviousPortPreview()
            }
        }

        composeTestRule
            .onAllNodesWithText("Was Hastings the port or the closest port you set off from and returned to?")
            .onFirst()
            .assertIsDisplayed()
    }
}
