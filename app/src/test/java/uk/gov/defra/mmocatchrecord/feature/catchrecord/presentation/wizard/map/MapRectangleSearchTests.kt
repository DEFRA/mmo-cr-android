package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle

class MapRectangleSearchTests {
    private val rectangles =
        listOf(
            StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-2", "38E98", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-3", "30F10", "AREA-DOVER"),
        )

    @Test
    fun `query shorter than two characters returns no suggestions`() {
        assertTrue(MapRectangleSearch.filterSuggestions("3", rectangles).isEmpty())
    }

    @Test
    fun `query of two or more characters returns matching suggestions ignoring case`() {
        val results = MapRectangleSearch.filterSuggestions("38e9", rectangles)
        assertEquals(listOf("38E95", "38E98"), results.map { it.code })
    }

    @Test
    fun `non matching query returns empty suggestions`() {
        assertTrue(MapRectangleSearch.filterSuggestions("zz", rectangles).isEmpty())
    }

    @Test
    fun `searches the full list, not just one port's nearby subset`() {
        val results = MapRectangleSearch.filterSuggestions("30f10", rectangles)
        assertEquals(listOf("30F10"), results.map { it.code })
    }
}
