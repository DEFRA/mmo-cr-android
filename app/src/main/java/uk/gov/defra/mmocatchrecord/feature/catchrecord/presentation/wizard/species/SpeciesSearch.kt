package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearTypeSearch
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.PortSearch

/** Gear-species autocomplete rules: min 2 chars, case-insensitive match at the start of any word (so "cod"
 * finds "Atlantic cod"), sorted by name. Unlike [PortSearch]/[GearTypeSearch], not whole-name starts-with. */
object SpeciesSearch {
    const val MIN_QUERY_LENGTH = PortSearch.MIN_QUERY_LENGTH

    fun filterSuggestions(
        query: String,
        allSpecies: List<Species>,
    ): List<Species> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        return allSpecies
            .filter { matchesAtWordStart(it.name, normalizedQuery) }
            .sortedBy { it.name }
    }

    private fun matchesAtWordStart(
        name: String,
        query: String,
    ): Boolean =
        name.indices.any { index ->
            val isWordStart = index == 0 || !name[index - 1].isLetterOrDigit()
            isWordStart && name.regionMatches(index, query, 0, query.length, ignoreCase = true)
        }
}
