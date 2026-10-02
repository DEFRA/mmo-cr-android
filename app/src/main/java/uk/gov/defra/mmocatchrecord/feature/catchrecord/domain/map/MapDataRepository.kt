package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map

import uk.gov.defra.mmocatchrecord.mapdata.MapDataset

/**
 * Loads the offline fisheries-map dataset (land / statistical sub-rectangles / ports) — see
 * docs/development/offline-map.md. Backed primarily by the build-time-generated asset, with an on-device
 * fallback parse of the bundled source GeoJSON if that asset is missing, corrupt, or a stale format
 * version; never throws — failures are returned as a typed [Result] failure for the UI to handle.
 */
fun interface MapDataRepository {
    suspend fun loadDataset(): Result<MapDataset>
}

/**
 * Thrown (wrapped in a [Result] failure, never propagated as an unhandled exception) when both the
 * generated asset and the on-device fallback parse are unavailable.
 */
class MapDataUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
