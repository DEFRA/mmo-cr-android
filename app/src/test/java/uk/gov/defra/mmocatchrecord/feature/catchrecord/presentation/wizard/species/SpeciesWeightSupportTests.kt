package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry

class SpeciesWeightSupportTests {
    private fun draftWith(gearUses: List<GearUse>) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = gearUses,
            modifiedAtEpochMillis = 0L,
        )

    @Test
    fun `cumulative weight sums all three 5A fields across other gear uses only`() {
        val gearOne =
            GearUse(
                id = "gear-1",
                gearTypeId = "gear-type-seine",
                statisticalSubRectangleCode = "38E95",
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(
                            id = "sw-1",
                            speciesId = "species-cod",
                            weightAboveMinimumSizeKg = 10.0,
                            weightBelowMinimumSizeKg = 2.0,
                            weightLegallyDiscardedKg = 1.0,
                            confirmedCaught = true,
                        ),
                    ),
            )
        val gearTwo =
            GearUse(
                id = "gear-2",
                gearTypeId = "gear-type-seine",
                statisticalSubRectangleCode = "38E96",
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(
                            id = "sw-2",
                            speciesId = "species-cod",
                            weightAboveMinimumSizeKg = 5.0,
                            confirmedCaught = true,
                        ),
                    ),
            )
        val draft = draftWith(listOf(gearOne, gearTwo))

        val result =
            SpeciesWeightSupport.cumulativeWeightForSpeciesInOtherGearUses(
                draft = draft,
                speciesId = "species-cod",
                excludingGearUseId = "gear-2",
            )

        assertEquals(13.0, result, 0.0001)
    }

    @Test
    fun `cumulative weight excludes the given gear use even if it has entries`() {
        val gearOne =
            GearUse(
                id = "gear-1",
                gearTypeId = "gear-type-seine",
                statisticalSubRectangleCode = "38E95",
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(
                            id = "sw-1",
                            speciesId = "species-cod",
                            weightAboveMinimumSizeKg = 100.0,
                            confirmedCaught = true,
                        ),
                    ),
            )
        val draft = draftWith(listOf(gearOne))

        val result =
            SpeciesWeightSupport.cumulativeWeightForSpeciesInOtherGearUses(
                draft = draft,
                speciesId = "species-cod",
                excludingGearUseId = "gear-1",
            )

        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun `cumulative weight ignores entries for a different species`() {
        val gearOne =
            GearUse(
                id = "gear-1",
                gearTypeId = "gear-type-seine",
                statisticalSubRectangleCode = "38E95",
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(
                            id = "sw-1",
                            speciesId = "species-haddock",
                            weightAboveMinimumSizeKg = 100.0,
                            confirmedCaught = true,
                        ),
                    ),
            )
        val draft = draftWith(listOf(gearOne))

        val result =
            SpeciesWeightSupport.cumulativeWeightForSpeciesInOtherGearUses(
                draft = draft,
                speciesId = "species-cod",
                excludingGearUseId = "gear-2",
            )

        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun `distinct confirmed species ids returns only confirmed entries in first seen order without duplicates`() {
        val gearOne =
            GearUse(
                id = "gear-1",
                gearTypeId = "gear-type-seine",
                statisticalSubRectangleCode = "38E95",
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod", confirmedCaught = true),
                        SpeciesWeightEntry(id = "sw-2", speciesId = "species-haddock", confirmedCaught = false),
                    ),
            )
        val gearTwo =
            GearUse(
                id = "gear-2",
                gearTypeId = "gear-type-seine",
                statisticalSubRectangleCode = "38E96",
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(id = "sw-3", speciesId = "species-cod", confirmedCaught = true),
                        SpeciesWeightEntry(id = "sw-4", speciesId = "species-herring", confirmedCaught = true),
                    ),
            )
        val draft = draftWith(listOf(gearOne, gearTwo))

        val result = SpeciesWeightSupport.distinctConfirmedSpeciesIds(draft)

        assertEquals(listOf("species-cod", "species-herring"), result)
    }

    @Test
    fun `distinct confirmed species ids is empty when no species have been confirmed`() {
        val draft = draftWith(emptyList())

        assertTrue(SpeciesWeightSupport.distinctConfirmedSpeciesIds(draft).isEmpty())
    }
}
