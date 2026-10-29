package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species

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
}
