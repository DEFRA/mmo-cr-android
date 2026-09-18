@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port

class PortSearchTests {
    private val ports =
        listOf(Port("port-hastings", "Hastings", "AREA-HASTINGS"), Port("port-dover", "Dover", "AREA-DOVER"))

    @Test
    fun `query shorter than two characters returns no suggestions`() {
        assertTrue(PortSearch.filterSuggestions("H", ports).isEmpty())
    }

    @Test
    fun `query of two or more characters returns matching suggestions ignoring case`() {
        val results = PortSearch.filterSuggestions("ha", ports)
        assertEquals(listOf("Hastings"), results.map { it.name })
    }

    @Test
    fun `non matching query returns empty suggestions`() {
        assertTrue(PortSearch.filterSuggestions("zz", ports).isEmpty())
    }
}
