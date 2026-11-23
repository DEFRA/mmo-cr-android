package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightFieldError
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightFieldKind

class GearSpeciesChecklistSupportTests {
    private val gearUse =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-seine-nets",
            statisticalSubRectangleCode = "38E95",
            speciesWeights =
                listOf(
                    SpeciesWeightEntry(id = "sw-0", speciesId = "species-cod", confirmedCaught = true),
                    SpeciesWeightEntry(id = "sw-1", speciesId = "species-unknown", confirmedCaught = true),
                ),
            confirmedUsedOnTrip = true,
        )

    @Test
    fun `checkbox options resolve each entry's species name when the species is known`() {
        val cod = Species(id = "species-cod", name = "Atlantic cod (COD)", faoCode = "COD")

        val options = GearSpeciesChecklistSupport.checkboxOptionsFor(gearUse, listOf(cod))

        assertEquals("Atlantic cod (COD)", options.single { it.id == "species-cod" }.label)
    }

    @Test
    fun `checkbox options fall back to the raw species id when the species is not found`() {
        val options = GearSpeciesChecklistSupport.checkboxOptionsFor(gearUse, speciesList = emptyList())

        assertEquals("species-unknown", options.single { it.id == "species-unknown" }.label)
    }

    @Test
    fun `a ticked species with a blank above-min weight yields a Required error whatever its precision`() {
        val cod = Species("species-cod", "Atlantic cod (COD)", "COD", SpeciesWeightPrecision.OneDecimalPlace)
        val plaice = Species("species-plaice", "Plaice (PLE)", "PLE", SpeciesWeightPrecision.WholeNumber)
        val unconfirmed =
            gearUse.copy(
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(id = "sw-0", speciesId = cod.id),
                        SpeciesWeightEntry(id = "sw-1", speciesId = plaice.id),
                    ),
            )
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(unconfirmed),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )

        val result =
            GearSpeciesChecklistSupport.validateChecklist(
                draft = draft,
                gearUse = unconfirmed,
                speciesList = listOf(cod, plaice),
                checkedIds = setOf(cod.id, plaice.id),
                rawValues = emptyMap(),
                disclosed = emptySet(),
            )

        listOf(cod, plaice).forEach { species ->
            assertEquals(
                SpeciesWeightFieldError.Required,
                result.errors[
                    GearSpeciesChecklistSupport.rawValueKey(
                        species.id,
                        SpeciesWeightFieldKind.AboveMinimumSize,
                    ),
                ],
            )
        }
    }
}
