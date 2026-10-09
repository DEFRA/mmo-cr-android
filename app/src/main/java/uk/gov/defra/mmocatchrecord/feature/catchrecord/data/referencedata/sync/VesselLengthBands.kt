package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

private const val UNDER_10_UPPER_BOUND_METRES = 10.0
private const val OVER_12_LOWER_BOUND_METRES = 12.0

private const val UNDER_10M_REPRESENTATIVE_METRES = 5.0
private const val TEN_TO_12M_REPRESENTATIVE_METRES = 11.0
private const val OVER_12M_REPRESENTATIVE_METRES = 15.0

/**
 * The `gears` endpoint's documented `vesselLengthMetres` bands (api-reference.md), plus an unfiltered
 * [ALL] variant cached for when a draft's vessel length is unknown (ADR 0015).
 */
object VesselLengthBands {
    const val UNDER_10M = "under-10m"
    const val TEN_TO_12M = "10-to-12m"
    const val OVER_12M = "over-12m"
    const val ALL = "all"

    /** `(datasetKey band, representative vesselLengthMetres to send, or null for the unfiltered [ALL])`. */
    val cacheableVariants: List<Pair<String, Double?>> =
        listOf(
            UNDER_10M to UNDER_10M_REPRESENTATIVE_METRES,
            TEN_TO_12M to TEN_TO_12M_REPRESENTATIVE_METRES,
            OVER_12M to OVER_12M_REPRESENTATIVE_METRES,
            ALL to null,
        )

    /** Resolves a vessel's actual length to the band whose cached gears response applies to it. */
    fun bandFor(vesselLengthMetres: Double?): String =
        when {
            vesselLengthMetres == null -> ALL
            vesselLengthMetres < UNDER_10_UPPER_BOUND_METRES -> UNDER_10M
            vesselLengthMetres <= OVER_12_LOWER_BOUND_METRES -> TEN_TO_12M
            else -> OVER_12M
        }
}
