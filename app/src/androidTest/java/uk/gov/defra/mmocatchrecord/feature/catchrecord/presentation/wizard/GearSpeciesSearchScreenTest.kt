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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species

class GearSpeciesSearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val cod = Species(id = "species-cod", name = "Atlantic cod (COD)", faoCode = "COD")
    private val haddock = Species(id = "species-haddock", name = "Haddock (HAD)", faoCode = "HAD")

    private fun gearUsePendingSpecies() =
        GearUse(
            id = "gear-use-1",
            gearTypeId = seineNets.id,
            statisticalSubRectangleCode = "38E95",
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = true,
        )

    private fun stateWith(gearUse: GearUse): CatchRecordFlowViewState {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        return CatchRecordFlowViewState(
            status = UiStatus.Content(draft),
            gearTypes = listOf(seineNets),
            species = listOf(cod, haddock),
        )
    }

    @Test
    fun titleShowsGearNameWithIdentifyingMeasurement() {
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesSearchScreen(state = stateWith(gearUsePendingSpecies()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Which species did you catch with seine nets (mesh size 100mm)?")
            .assertIsDisplayed()
    }

    @Test
    fun typingTwoCharactersShowsMatchingSuggestionsAndSelectingOneSubmitsItsId() {
        var submittedId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesSearchScreen(
                    state = stateWith(gearUsePendingSpecies()),
                    onSubmit = { submittedId = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Co")
        composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()

        assertEquals("species-cod", submittedId)
    }

    @Test
    fun blankQueryOnSaveShowsEmptyQueryErrorAndDoesNotSubmit() {
        var submittedId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesSearchScreen(
                    state = stateWith(gearUsePendingSpecies()),
                    onSubmit = { submittedId = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedId)
    }

    @Test
    fun typedTextWithNoMatchingSelectionShowsNoValidSelectionErrorAndDoesNotSubmit() {
        var submittedId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesSearchScreen(
                    state = stateWith(gearUsePendingSpecies()),
                    onSubmit = { submittedId = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("zz")
        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedId)
    }

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
                GearSpeciesSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }
}
