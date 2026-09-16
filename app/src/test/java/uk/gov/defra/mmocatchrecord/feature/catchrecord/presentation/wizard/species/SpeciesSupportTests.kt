package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species

class SpeciesSupportTests {
    @Test
    fun `display name strips the parenthetical FAO code suffix`() {
        val species = Species(id = "species-cod", name = "Atlantic cod (COD)", faoCode = "COD")

        assertEquals("Atlantic cod", SpeciesSupport.displayNameFor(species))
    }

    @Test
    fun `display name with no parenthetical suffix is returned unchanged`() {
        val species = Species(id = "species-x", name = "Plain species name", faoCode = "PLN")

        assertEquals("Plain species name", SpeciesSupport.displayNameFor(species))
    }
}
