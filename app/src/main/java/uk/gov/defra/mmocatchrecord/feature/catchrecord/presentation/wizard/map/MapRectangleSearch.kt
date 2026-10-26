package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.PortSearch

/**
 * Pure autocomplete filtering rules for the "Other" free-text statistical sub-rectangle search (Phase 4,
 * screen 3) — mirrors [PortSearch]/[GearTypeSearch]. Unlike the "nearby" grid/radio-list, this searches the
 * full/global rectangle code list — the comprehensive offline map dataset once loaded (see
 * [MapSupport.allRectanglesFromDataset]), or the known-incomplete reference-data stub otherwise (see
 * [MapRectangleFormatValidator]'s `knownCodes` existence check, which is only enforced in the former case).
 */
object MapRectangleSearch {
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
