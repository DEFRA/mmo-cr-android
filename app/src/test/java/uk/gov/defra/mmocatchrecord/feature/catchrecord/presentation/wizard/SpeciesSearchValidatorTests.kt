package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeciesSearchValidatorTests {
    @Test
    fun `a selected species id is always valid regardless of query text`() {
        assertNull(SpeciesSearchValidator.validate(query = "", selectedSpeciesId = "species-cod"))
        assertNull(SpeciesSearchValidator.validate(query = "cod", selectedSpeciesId = "species-cod"))
    }

    @Test
    fun `blank query with no selection yields an empty query error`() {
        val result = SpeciesSearchValidator.validate(query = "   ", selectedSpeciesId = null)

        assertEquals(SpeciesSearchInputError.EmptyQuery, result)
    }

    @Test
    fun `non blank query with no selection yields a no valid selection error`() {
        val result = SpeciesSearchValidator.validate(query = "cod", selectedSpeciesId = null)

        assertEquals(SpeciesSearchInputError.NoValidSelection, result)
    }
}
