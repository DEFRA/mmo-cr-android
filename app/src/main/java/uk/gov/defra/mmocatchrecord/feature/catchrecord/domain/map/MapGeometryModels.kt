package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map

/**
 * A WGS84 (lat/lng degree) point — the pure, no-Android geometry primitive shared by every map-related
 * domain type below. Kept intentionally separate from
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle] (which
 * stays a plain id/code/statisticalAreaId lookup row, unchanged) — see ADR 0013.
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
)

/** One ring (exterior boundary, or an interior hole) of a polygon — a closed sequence of [GeoPoint]s. */
typealias GeoRing = List<GeoPoint>

/**
 * The map-rendering geometry for one ICES statistical sub-rectangle, keyed by [subCode] (the same stable
 * code persisted as
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse.statisticalSubRectangleCode]).
 *
 * [bboxCentroid] is this sub-rectangle's **own** bounding-box centroid (used for its map label) —
 * deliberately never the source data's shared `stat_x`/`stat_y` parent-rectangle centre, which would stack
 * sibling sub-rectangles' labels on top of one another (see ADR 0013 / plan risk R2).
 *
 * [seaOverlapping] is `false` only for sub-rectangles wholly inside land: they are still drawn (their
 * boundary/grid line) but are excluded from tap hit-testing and from zoom-gated code labels, since a
 * wholly-landlocked cell cannot be a real catch location.
 */
data class StatisticalSubRectangleGeometry(
    val subCode: String,
    val parentIcesName: String,
    val rings: List<GeoRing>,
    val bboxCentroid: GeoPoint,
    val boundingBox: GeoBoundingBox,
    val seaOverlapping: Boolean,
)

/** A land mass polygon drawn as map context (no attributes needed beyond its outline). */
data class LandPolygon(
    val rings: List<GeoRing>,
)

/** A UK/EU landing port shown as non-interactive map context (see ADR 0013 — ports are never tappable). */
data class MapPort(
    val name: String,
    val location: GeoPoint,
)

/** An axis-aligned lat/lng bounding box, used for label placement and viewport-culling before each draw. */
data class GeoBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    fun intersects(other: GeoBoundingBox): Boolean =
        minLat <= other.maxLat && maxLat >= other.minLat && minLng <= other.maxLng && maxLng >= other.minLng
}

/** The full set of map layers needed to render the offline statistical-sub-area map — see ADR 0013. */
data class MapGeometryDataset(
    val landPolygons: List<LandPolygon>,
    val subRectangles: List<StatisticalSubRectangleGeometry>,
    val ports: List<MapPort>,
)
