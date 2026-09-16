package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.PortSearch

/**
 * Pure autocomplete filtering rules for the "Other" free-text statistical sub-rectangle search (Phase 4,
 * screen 3) — mirrors [PortSearch]/[GearTypeSearch]. Unlike the "nearby" grid/radio-list, this searches the
 * full/global rectangle code list, since the local reference-data stub only supplies a placeholder subset
 * and a real code entered here need not be present in it (see [StatisticalSubRectangleFormatValidator]).
 */
object StatisticalSubRectangleSearch {
    const val MIN_QUERY_LENGTH = PortSearch.MIN_QUERY_LENGTH

    fun filterSuggestions(
        query: String,
        allRectangles: List<StatisticalSubRectangle>,
    ): List<StatisticalSubRectangle> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }

        return allRectangles
            .filter { it.code.contains(normalizedQuery, ignoreCase = true) }
            .sortedBy { it.code }
    }
}
