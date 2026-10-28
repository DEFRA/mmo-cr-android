package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision

/**
 * JVM/Robolectric port of the androidTest `RemoveSpeciesScreenTest` — exercises the public,
 * state-independent [RemoveSpeciesScreenContent] directly, for the `testDebugUnitTest` coverage gate.
 */
@RunWith(RobolectricTestRunner::class)
class RemoveSpeciesScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")

    private val cod =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
            weightAboveMinimumSizeMandatory = true,
        )

    private val plaice =
        Species(
            id = "species-plaice",
            name = "Plaice (PLE)",
            faoCode = "PLE",
            weightPrecision = SpeciesWeightPrecision.WholeNumber,
            weightAboveMinimumSizeMandatory = false,
        )

    private val speciesList = listOf(cod, plaice)

    private fun gearUseWithAddedSpecies(speciesIds: List<String>) =
        GearUse(
            id = "gear-use-1",
            gearTypeId = seineNets.id,
            statisticalSubRectangleCode = "38E95",
            speciesWeights =
                speciesIds.mapIndexed { index, id ->
                    SpeciesWeightEntry(id = "sw-$index", speciesId = id, confirmedCaught = true)
                },
            confirmedUsedOnTrip = true,
        )

    private fun draftWith(gearUse: GearUse) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = listOf(gearUse),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    @Test
    fun displaysACheckboxForEverySpeciesAddedToTheGearUse() {
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id, plaice.id))
        composeTestRule.setContent {
            MmoTheme {
                RemoveSpeciesScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onDelete = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${RemoveSpeciesScreenTestTags.CHECKBOX_PREFIX}_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("${RemoveSpeciesScreenTestTags.CHECKBOX_PREFIX}_1").assertIsDisplayed()
    }

    @Test
    fun deletingWithNothingCheckedShowsAnErrorAndDoesNotDelete() {
        var deleted: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                RemoveSpeciesScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onDelete = { deleted = it },
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.DELETE_ACTION).performClick()

        composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(deleted)
    }

    @Test
    fun deletingOnlyCheckedSpeciesRemovesThemFromTheGearUse() {
        var deleted: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id, plaice.id))
        composeTestRule.setContent {
            MmoTheme {
                RemoveSpeciesScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onDelete = { deleted = it },
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${RemoveSpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.DELETE_ACTION).performClick()

        val remainingSpeciesIds =
            deleted
                ?.gearUses
                ?.single()
                ?.speciesWeights
                ?.map { it.speciesId }
        assertEquals(listOf(plaice.id), remainingSpeciesIds)
    }

    @Test
    fun cancelInvokesOnCancelWithoutDeleting() {
        var cancelled = false
        var deleted: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                RemoveSpeciesScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onDelete = { deleted = it },
                    onCancel = { cancelled = true },
                )
            }
        }

        composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.CANCEL_ACTION).performClick()

        assertTrue(cancelled)
        assertNull(deleted)
    }
}
