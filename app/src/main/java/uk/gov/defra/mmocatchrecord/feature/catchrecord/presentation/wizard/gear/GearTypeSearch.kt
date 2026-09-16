package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.PortSearch

/**
 * Pure autocomplete filtering rules for the gear-search field, mirroring [PortSearch] exactly (same
 * min-2-character gate, same "contains, case-insensitive, sorted by name" rule) so the gear-search screen
 * behaves identically to the already-shipped port-search screens.
 */
object GearTypeSearch {
    const val MIN_QUERY_LENGTH = PortSearch.MIN_QUERY_LENGTH

    fun filterSuggestions(
        query: String,
        allGearTypes: List<GearType>,
    ): List<GearType> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        return allGearTypes
            .filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }
}
