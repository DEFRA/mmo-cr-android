@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle

class GearStatRectangleScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val samplePort = Port("port-hastings", "Hastings", "AREA-HASTINGS")
    private val sampleRectangles =
        listOf(
            StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-2", "38E98", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-3", "38F02", "AREA-HASTINGS"),
        )

    private fun pendingGearUse(confirmed: Boolean = true) =
        GearUse(
            id = "gear-use-1",
            gearTypeId = seineNets.id,
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = confirmed,
        )

    private fun stateWith(
        gearUse: GearUse,
        rectangles: List<StatisticalSubRectangle> = sampleRectangles,
    ): CatchRecordFlowViewState {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                departurePort = PortSelection(samplePort.id, PortSelectionMode.FirstTime),
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        return CatchRecordFlowViewState(
            status = UiStatus.Content(draft),
            gearTypes = listOf(seineNets),
            ports = listOf(samplePort),
            statisticalSubRectangles = rectangles,
        )
    }

    // --- Screen 1: schematic grid ---------------------------------------------------------------------

    @Test
    fun gridScreenShowsIdentifyingMeasurementInTitleAndSubmitsTappedCell() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using seine nets (mesh size 100mm)?")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.GRID_CELL_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_SAVE_ACTION).performClick()

        assertEquals("38E95", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun gridScreenWithNoIdentifyingMeasurementOmitsParenthetical() {
        val handlines =
            GearType(
                id = "gear-handlines",
                name = "Handlines and pole lines (hand operated)",
                measurementTitleOverride = "handlines",
            )
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = handlines.id,
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                departurePort = PortSelection(samplePort.id, PortSelectionMode.FirstTime),
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        val state =
            CatchRecordFlowViewState(
                status = UiStatus.Content(draft),
                gearTypes = listOf(handlines),
                ports = listOf(samplePort),
                statisticalSubRectangles = sampleRectangles,
            )
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using handlines?")
            .assertIsDisplayed()
    }

    @Test
    fun gridScreenWithNoSelectionShowsRequiredErrorAndDoesNotSubmit() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedDraft)
    }

    // --- Screen 2: "Other" -> radio list ---------------------------------------------------------------

    @Test
    fun gridOtherLinkNavigatesToRadioListWithNearbyCodesAndOtherOption() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()

        composeTestRule
            .onNodeWithText(
                "Select the statistical sub area where the majority of your catch was caught using seine nets (mesh size 100mm)?",
            ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.RADIO_OPTION_PREFIX}_0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Other").assertIsDisplayed()
    }

    @Test
    fun radioListSelectionSubmitsTheChosenCode() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.RADIO_OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.RADIO_SAVE_ACTION).performClick()

        assertEquals("38E98", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun radioListWithNoSelectionShowsRequiredError() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.RADIO_SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    // --- Screen 3: "Other" (again) -> autocomplete search ----------------------------------------------

    @Test
    fun selectingOtherOnRadioListNavigatesToAutocompleteSearch() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        // Radio option index 3 is the trailing "Other" entry (3 nearby codes at indices 0-2).
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.RADIO_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_FIELD).assertIsDisplayed()
    }

    @Test
    fun autocompleteScreenSubmitsACorrectlyFormattedTypedCodeNotJustSuggestionListEntries() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.RADIO_SAVE_ACTION).performClick()

        // A code not present in the local nearby/stub list, entered via free text.
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("99Z99")
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        assertEquals("99Z99", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun autocompleteScreenWithBlankInputShowsRequiredErrorSummary() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.RADIO_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a statistical subrectangle").assertIsDisplayed()
    }

    @Test
    fun autocompleteScreenWithIncorrectlyFormattedInputShowsFormatErrorSummary() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.RADIO_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("not-a-code")
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        composeTestRule
            .onNodeWithText("Enter a statistical subrectangle in the correct format, for example 38E84")
            .assertIsDisplayed()
    }

    // --- Defensive fallback -----------------------------------------------------------------------------

    @Test
    fun noPendingGearShowsDefensiveFallbackMessage() {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = emptyList(),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        val state = CatchRecordFlowViewState(status = UiStatus.Content(draft))
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }
}
