package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port

/** BR-XX (ADR 0014, Phase D) dirty-state coverage for [PortSelectionStepContent], search mode only. */
@RunWith(RobolectricTestRunner::class)
class PortSelectionStepContentTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val allPorts =
        listOf(
            Port("port-hastings", "Hastings", "AREA-HASTINGS"),
            Port("port-dover", "Dover", "AREA-DOVER"),
        )

    private fun setContent(
        initialSelection: PortSelection?,
        favouritePorts: List<Port>,
        onDirtyChanged: (Boolean) -> Unit = {},
        onSubmit: (PortSelection) -> Unit = {},
        onShowingPortConfirmationChanged: (Boolean) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MmoTheme {
                PortSelectionStepContent(
                    initialSelection = initialSelection,
                    allPorts = allPorts,
                    favouritePorts = favouritePorts,
                    autocompletePlaceholder = "Enter port",
                    autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
                    optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    confirmOptionPrefix = DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX,
                    addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
                    errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = onSubmit,
                    onDirtyChanged = onDirtyChanged,
                    onShowingPortConfirmationChanged = onShowingPortConfirmationChanged,
                )
            }
        }
    }

    @Test
    fun openingSearchModeWithAPrefilledPortNeverReportsDirty() {
        var lastDirty = true
        setContent(
            initialSelection = PortSelection("port-hastings", PortSelectionMode.FirstTime),
            favouritePorts = emptyList(),
            onDirtyChanged = { lastDirty = it },
        )

        assertFalse(lastDirty)
    }

    @Test
    fun typingInTheSearchFieldReportsDirty() {
        var lastDirty = false
        setContent(
            initialSelection = PortSelection("port-hastings", PortSelectionMode.FirstTime),
            favouritePorts = emptyList(),
            onDirtyChanged = { lastDirty = it },
        )

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Do")
        composeTestRule.waitForIdle()

        assertTrue(lastDirty)
    }

    @Test
    fun caseInsensitiveReclearedTextReportsClean() {
        var lastDirty = false
        setContent(
            initialSelection = PortSelection("port-hastings", PortSelectionMode.FirstTime),
            favouritePorts = emptyList(),
            onDirtyChanged = { lastDirty = it },
        )

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("HASTINGS")
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }

    @Test
    fun favouritesRadioModeNeverReportsDirty() {
        var lastDirty = false
        setContent(
            initialSelection = null,
            favouritePorts = allPorts,
            onDirtyChanged = { lastDirty = it },
        )

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0").performClick()
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }

    @Test
    fun singleFavouriteRendersConfirmationNotFavouritesList() {
        setContent(initialSelection = null, favouritePorts = listOf(allPorts[0]))

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_0").assertExists()
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0").assertDoesNotExist()
    }

    @Test
    fun confirmationYesSubmitsTheSoleFavouriteAsFavouriteSelection() {
        var submitted: PortSelection? = null
        setContent(
            initialSelection = null,
            favouritePorts = listOf(allPorts[0]),
            onSubmit = { submitted = it },
        )

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertTrue(submitted == PortSelection(allPorts[0].id, PortSelectionMode.Favourite))
    }

    @Test
    fun confirmationNoRevealsTheSearchFieldAndHidesConfirmation() {
        setContent(initialSelection = null, favouritePorts = listOf(allPorts[0]))

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).assertExists()
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_0").assertDoesNotExist()
    }

    @Test
    fun saveButtonIsHiddenUntilTheConfirmationIsAnswered() {
        setContent(initialSelection = null, favouritePorts = listOf(allPorts[0]))

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).assertDoesNotExist()

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_0").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).assertExists()
    }

    @Test
    fun reportsConfirmationVisibilityAndClearsItOnceSearchIsRevealed() {
        val visibilityChanges = mutableListOf<Boolean>()
        setContent(
            initialSelection = null,
            favouritePorts = listOf(allPorts[0]),
            onShowingPortConfirmationChanged = { visibilityChanges.add(it) },
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_1").performClick()
        composeTestRule.waitForIdle()

        assertTrue(visibilityChanges.first())
        assertFalse(visibilityChanges.last())
    }

    @Test
    fun confirmationRadioGroupIsLabelledWithTheQuestionForAssistiveTech() {
        setContent(initialSelection = null, favouritePorts = listOf(allPorts[0]))

        composeTestRule
            .onNode(
                hasContentDescription(
                    "Was Hastings the port or the closest port you set off from and returned to?",
                ),
            ).assertExists()
        composeTestRule
            .onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun twoOrMoreFavouritesStillRenderTheFavouritesListNotConfirmation() {
        setContent(initialSelection = null, favouritePorts = allPorts)

        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0").assertExists()
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX}_0").assertDoesNotExist()
    }
}
