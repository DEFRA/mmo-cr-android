package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species

/**
 * Pure autocomplete filtering rules for the gear-species search field, mirroring [PortSearch]/
 * [GearTypeSearch] exactly (same min-2-character gate, same "contains, case-insensitive, sorted by name"
 * rule) so the species-search screen behaves identically to the already-shipped port/gear-search screens.
 */
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
            .filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }
}
