package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

/** Port-only projection of a past submitted trip, used to derive the vessel's previously used ports. */
data class TripPortsRow(
    val departurePortId: String?,
    val returnPortId: String?,
)
