package geopipeline

/**
 * Plain (build-time only) geometry models used while precomputing the derived map-geometry asset — see
 * `GeoPrecomputeTask`. These are intentionally separate from the app's runtime domain types (which live in
 * `app/src/main`, not visible from `buildSrc`); the derived binary asset is the seam between the two.
 */
data class LatLng(
    val lat: Double,
    val lng: Double,
)

data class RawPolygon(
    /** Rings: index 0 is the exterior ring, any further entries are interior holes. */
    val rings: List<List<LatLng>>,
)

data class RawSubRectangle(
    val subCode: String,
    val icesName: String,
    val polygons: List<RawPolygon>,
)

data class RawPort(
    val name: String,
    val location: LatLng,
)
