package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision

class NotLandedStraightAwayScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val cod =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
        )
    private val haddock =
        Species(
            id = "species-haddock",
            name = "Haddock (HAD)",
            faoCode = "HAD",
            weightPrecision = SpeciesWeightPrecision.WholeNumber,
        )

    // --- Screen 3: Yes/No decision -------------------------------------------------------------

    @Test
    fun selectingYesSubmitsTrue() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwayDecisionScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(true, submitted)
    }

    @Test
    fun selectingNoSubmitsFalse() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwayDecisionScreenTestTags.OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(false, submitted)
    }

    @Test
    fun noSelectionOnSaveShowsRequiredErrorAndDoesNotSubmit() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submitted)
    }

    // --- Screen 4: species checklist ------------------------------------------------------------

    private fun draftWithConfirmedSpecies(speciesIds: List<String>) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-seine-nets",
                        statisticalSubRectangleCode = "38E95",
                        speciesWeights =
                            speciesIds.mapIndexed { index, id ->
                                SpeciesWeightEntry(id = "sw-$index", speciesId = id, confirmedCaught = true)
                            },
                        confirmedUsedOnTrip = true,
                    ),
                ),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    @Test
    fun checklistShowsEveryDistinctConfirmedSpeciesFromTheDraft() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id, haddock.id)),
                    speciesList = listOf(cod, haddock),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Atlantic cod (COD)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Haddock (HAD)").assertIsDisplayed()
    }

    @Test
    fun checkingASpeciesRevealsTheKeptOnboardFieldAndSubmittingSavesIt() {
        var submitted: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("3.5")
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        val entry = submitted?.notLandedSpeciesEntries?.single()
        assertEquals(cod.id, entry?.speciesId)
        assertEquals(3.5, entry?.weightAboveMinimumSizeKeptOnboardKg)
    }

    @Test
    fun noSpeciesCheckedOnSaveShowsChecklistRequiredError() {
        var submitted: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.CHECKLIST_ERROR).assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun blankKeptOnboardFieldForACheckedSpeciesShowsRequiredErrorInSummary() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Enter the weight above minimum size kept onboard or in keep pots in kilograms")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun zeroKeptOnboardFieldShowsBelowMinimumRangeError() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("0")
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Atlantic cod must be more than 0kg")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun keptOnboardFieldOverTheMaximumShowsAboveMaximumRangeError() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("10000.1")
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Atlantic cod must be 10,000kg or less")
            .onFirst()
            .assertIsDisplayed()
    }
}
