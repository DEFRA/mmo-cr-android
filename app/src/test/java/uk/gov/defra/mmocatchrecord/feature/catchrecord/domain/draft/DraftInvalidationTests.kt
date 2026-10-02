package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the FR10 "dependent-data invalidation" reducer helper — see [DraftInvalidation]'s doc
 * comment for the rules under test.
 */
class DraftInvalidationTests {
    private fun draft(
        gearUses: List<GearUse> = emptyList(),
        notLandedStraightAway: Boolean? = null,
        notLandedSpeciesEntries: List<NotLandedSpeciesEntry> = emptyList(),
    ) = CatchRecordDraft(
        id = "draft-1",
        vesselId = "vessel-1",
        gearUses = gearUses,
        notLandedStraightAway = notLandedStraightAway,
        notLandedSpeciesEntries = notLandedSpeciesEntries,
        modifiedAtEpochMillis = 0L,
    )

    @Test
    fun `reconcile with no previous draft returns next gear uses unchanged`() {
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                confirmedUsedOnTrip = true,
            )
        val next = draft(gearUses = listOf(gearUse))

        val result = DraftInvalidation.reconcile(previous = null, next = next)

        assertEquals(gearUse, result.gearUses.single())
    }

    @Test
    fun `reconcile ignores a gear use absent from the previous draft`() {
        val gearUse =
            GearUse(
                id = "gear-use-new",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                confirmedUsedOnTrip = false,
            )
        val previous = draft(gearUses = emptyList())
        val next = draft(gearUses = listOf(gearUse))

        val result = DraftInvalidation.reconcile(previous, next)

        assertEquals(gearUse, result.gearUses.single())
    }

    @Test
    fun `unconfirming a gear use clears its stat rectangle and species weights`() {
        val previousGearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                confirmedUsedOnTrip = true,
                speciesWeights = listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod")),
            )
        val nextGearUse = previousGearUse.copy(confirmedUsedOnTrip = false)
        val previous = draft(gearUses = listOf(previousGearUse))
        val next = draft(gearUses = listOf(nextGearUse))

        val result = DraftInvalidation.reconcile(previous, next)

        val reconciled = result.gearUses.single()
        assertNull(reconciled.statisticalSubRectangleCode)
        assertTrue(reconciled.speciesWeights.isEmpty())
    }

    @Test
    fun `changing the statistical sub rectangle clears species weights`() {
        val previousGearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                confirmedUsedOnTrip = true,
                speciesWeights = listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod")),
            )
        val nextGearUse = previousGearUse.copy(statisticalSubRectangleCode = "30F10")
        val previous = draft(gearUses = listOf(previousGearUse))
        val next = draft(gearUses = listOf(nextGearUse))

        val result = DraftInvalidation.reconcile(previous, next)

        val reconciled = result.gearUses.single()
        assertEquals("30F10", reconciled.statisticalSubRectangleCode)
        assertTrue(reconciled.speciesWeights.isEmpty())
    }

    @Test
    fun `setting the statistical sub rectangle for the first time keeps species weights`() {
        val previousGearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        val speciesWeights = listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod"))
        val nextGearUse = previousGearUse.copy(statisticalSubRectangleCode = "38E95", speciesWeights = speciesWeights)
        val previous = draft(gearUses = listOf(previousGearUse))
        val next = draft(gearUses = listOf(nextGearUse))

        val result = DraftInvalidation.reconcile(previous, next)

        assertEquals(speciesWeights, result.gearUses.single().speciesWeights)
    }

    @Test
    fun `an unrelated gear use change is left untouched`() {
        val previousGearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                confirmedUsedOnTrip = true,
                numberOfShots = 1,
            )
        val nextGearUse = previousGearUse.copy(numberOfShots = 4)
        val previous = draft(gearUses = listOf(previousGearUse))
        val next = draft(gearUses = listOf(nextGearUse))

        val result = DraftInvalidation.reconcile(previous, next)

        assertEquals(nextGearUse, result.gearUses.single())
    }

    @Test
    fun `not landed species entries are cleared outright when not landing straight away is not true`() {
        val entries = listOf(NotLandedSpeciesEntry(speciesId = "species-cod", weightAboveMinimumSizeKeptOnboardKg = 1.0))
        val next = draft(notLandedStraightAway = false, notLandedSpeciesEntries = entries)

        val result = DraftInvalidation.reconcile(previous = null, next = next)

        assertTrue(result.notLandedSpeciesEntries.isEmpty())
    }

    @Test
    fun `an already-empty not landed species list is returned as the same draft instance when not landing straight away is false`() {
        val next = draft(notLandedStraightAway = false, notLandedSpeciesEntries = emptyList())

        val result = DraftInvalidation.reconcile(previous = null, next = next)

        assertEquals(next, result)
    }

    @Test
    fun `orphaned not landed species entries are pruned when their species is no longer confirmed caught`() {
        val confirmedGearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                confirmedUsedOnTrip = true,
                speciesWeights =
                    listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod", confirmedCaught = true)),
            )
        val entries =
            listOf(
                NotLandedSpeciesEntry(speciesId = "species-cod", weightAboveMinimumSizeKeptOnboardKg = 1.0),
                NotLandedSpeciesEntry(speciesId = "species-haddock", weightAboveMinimumSizeKeptOnboardKg = 2.0),
            )
        val next =
            draft(
                gearUses = listOf(confirmedGearUse),
                notLandedStraightAway = true,
                notLandedSpeciesEntries = entries,
            )

        val result = DraftInvalidation.reconcile(previous = null, next = next)

        assertEquals(listOf("species-cod"), result.notLandedSpeciesEntries.map { it.speciesId })
    }

    @Test
    fun `not landed species entries are unchanged when every entry is still confirmed caught`() {
        val confirmedGearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                confirmedUsedOnTrip = true,
                speciesWeights =
                    listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod", confirmedCaught = true)),
            )
        val entries = listOf(NotLandedSpeciesEntry(speciesId = "species-cod", weightAboveMinimumSizeKeptOnboardKg = 1.0))
        val next =
            draft(
                gearUses = listOf(confirmedGearUse),
                notLandedStraightAway = true,
                notLandedSpeciesEntries = entries,
            )

        val result = DraftInvalidation.reconcile(previous = null, next = next)

        assertEquals(next, result)
    }
}
