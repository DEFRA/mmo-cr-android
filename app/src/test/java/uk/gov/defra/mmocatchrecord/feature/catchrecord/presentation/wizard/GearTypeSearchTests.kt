package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

class GearTypeSearchTests {
    private val gearTypes =
        listOf(
            GearType(id = "gear-seine-nets", name = "Seine nets (not specified)"),
            GearType(id = "gear-beam-trawls-tbb", name = "Beam trawls (TBB)"),
            GearType(id = "gear-bottom-pair-trawls-ptb", name = "Bottom pair trawls (PTB)"),
            GearType(id = "gear-bottom-otter-trawls-tb", name = "Bottom otter trawls (TB)"),
        )

    @Test
    fun `query shorter than two characters returns no suggestions`() {
        assertTrue(GearTypeSearch.filterSuggestions("S", gearTypes).isEmpty())
    }

    @Test
    fun `typing Se surfaces seine nets`() {
        val results = GearTypeSearch.filterSuggestions("Se", gearTypes)
        assertEquals(listOf("Seine nets (not specified)"), results.map { it.name })
    }

    @Test
    fun `typing Be surfaces beam trawls`() {
        val results = GearTypeSearch.filterSuggestions("Be", gearTypes)
        assertEquals(listOf("Beam trawls (TBB)"), results.map { it.name })
    }

    @Test
    fun `typing Bo surfaces both bottom trawl gear types sorted by name`() {
        val results = GearTypeSearch.filterSuggestions("Bo", gearTypes)
        assertEquals(
            listOf("Bottom otter trawls (TB)", "Bottom pair trawls (PTB)"),
            results.map { it.name },
        )
    }

    @Test
    fun `non matching query returns empty suggestions`() {
        assertTrue(GearTypeSearch.filterSuggestions("zz", gearTypes).isEmpty())
    }

    @Test
    fun `matching is case insensitive`() {
        val results = GearTypeSearch.filterSuggestions("seine", gearTypes)
        assertEquals(listOf("Seine nets (not specified)"), results.map { it.name })
    }

    @Test
    fun `min query length constant mirrors port search`() {
        assertEquals(PortSearch.MIN_QUERY_LENGTH, GearTypeSearch.MIN_QUERY_LENGTH)
    }
}
