package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port

/** Pure autocomplete filtering rules shared by the port search field and its unit tests. */
object PortSearch {
    const val MIN_QUERY_LENGTH = 2

    fun filterSuggestions(
        query: String,
        allPorts: List<Port>,
    ): List<Port> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        return allPorts
            .filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }
}
