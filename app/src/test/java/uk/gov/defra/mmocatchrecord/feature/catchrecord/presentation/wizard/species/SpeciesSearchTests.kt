package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species

class SpeciesSearchTests {
    private val cod = Species(id = "species-cod", name = "Atlantic cod (COD)", faoCode = "COD")
    private val haddock = Species(id = "species-haddock", name = "Haddock (HAD)", faoCode = "HAD")
    private val herring = Species(id = "species-herring", name = "Herring (HER)", faoCode = "HER")
    private val codling = Species(id = "species-codling", name = "Codling (COL)", faoCode = "COL")
    private val allSpecies = listOf(cod, haddock, herring, codling)

    @Test
    fun `query shorter than 2 characters yields no suggestions`() {
        assertEquals(emptyList<Species>(), SpeciesSearch.filterSuggestions("c", allSpecies))
    }

    @Test
    fun `blank query yields no suggestions`() {
        assertEquals(emptyList<Species>(), SpeciesSearch.filterSuggestions("   ", allSpecies))
    }

    @Test
    fun `two character query matches case insensitively`() {
        val result = SpeciesSearch.filterSuggestions("CO", allSpecies)

        assertEquals(listOf(cod, codling), result)
    }

    @Test
    fun `query matching multiple species returns them sorted by name`() {
        val hake = Species(id = "species-hake", name = "Hake (HKE)", faoCode = "HKE")

        val result = SpeciesSearch.filterSuggestions("ha", listOf(hake) + allSpecies)

        assertEquals(listOf(haddock, hake), result)
    }

    @Test
    fun `query matches the start of any word in the name`() {
        assertEquals(listOf(cod, codling), SpeciesSearch.filterSuggestions("cod", allSpecies))
        assertEquals(listOf(cod), SpeciesSearch.filterSuggestions("atlantic co", allSpecies))
        assertEquals(listOf(haddock), SpeciesSearch.filterSuggestions("had)", allSpecies))
    }

    @Test
    fun `query does not match text in the middle of a word`() {
        assertTrue(SpeciesSearch.filterSuggestions("lantic", allSpecies).isEmpty())
        assertTrue(SpeciesSearch.filterSuggestions("dling", allSpecies).isEmpty())
    }

    @Test
    fun `query matching nothing returns an empty list`() {
        assertTrue(SpeciesSearch.filterSuggestions("zz", allSpecies).isEmpty())
    }

    @Test
    fun `query is trimmed before matching`() {
        val result = SpeciesSearch.filterSuggestions("  cod  ", allSpecies)

        assertEquals(listOf(cod, codling), result)
    }
}
