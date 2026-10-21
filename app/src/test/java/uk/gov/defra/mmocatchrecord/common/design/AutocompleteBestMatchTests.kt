package uk.gov.defra.mmocatchrecord.common.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutocompleteBestMatchTests {
    @Test
    fun `blank query or no labels has no highlight`() {
        assertNull(AutocompleteBestMatch.indexOf("  ", listOf("Dover")))
        assertNull(AutocompleteBestMatch.indexOf("Do", emptyList()))
    }

    @Test
    fun `exact match wins over a prefix match`() {
        assertEquals(1, AutocompleteBestMatch.indexOf("dover", listOf("Dover Harbour", "Dover")))
    }

    @Test
    fun `prefix match wins over word and substring matches, preferring the closest spelling`() {
        val labels = listOf("Bottom otter trawls (TB)", "Beam trawls (TBB)", "Bottom pair trawls (PTB)")
        assertEquals(1, AutocompleteBestMatch.indexOf("B", labels))
        assertEquals(0, AutocompleteBestMatch.indexOf("Bottom o", labels))
    }

    @Test
    fun `word prefix wins over substring, and substring wins over no match`() {
        assertEquals(1, AutocompleteBestMatch.indexOf("net", listOf("Gillnets", "Seine nets")))
        assertEquals(1, AutocompleteBestMatch.indexOf("ll", listOf("Zzz", "Gillnets")))
    }

    @Test
    fun `ties are resolved by list order`() {
        assertEquals(0, AutocompleteBestMatch.indexOf("xy", listOf("abcd", "efgh")))
    }

    @Test
    fun `edit distance counts insertions, deletions and substitutions`() {
        assertEquals(3, AutocompleteBestMatch.editDistance("kitten", "sitting"))
        assertEquals(4, AutocompleteBestMatch.editDistance("", "abcd"))
    }
}
