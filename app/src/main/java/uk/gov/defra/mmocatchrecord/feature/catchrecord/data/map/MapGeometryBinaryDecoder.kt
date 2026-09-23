package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map

import kotlinx.coroutines.CancellationException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.LandPolygon
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Decodes the compact derived binary geometry asset produced by the build-time `GeoPrecomputeTask`
 * (`shared-geopipeline/.../geopipeline/GeoBinaryWriter.kt` is the authoritative format reference — this
 * decoder must stay in lock-step with it) into runtime domain geometry — see ADR 0013.
 *
 * The magic/version header lets this decoder **fail closed** (a [Result.failure], never a crash) on a
 * missing/corrupt/unrecognised-version asset; [AssetMapGeometryRepository] treats a decode failure as the
 * trigger for its on-device GeoJSON re-parse fallback (see its doc comment), only degrading further (an
 * explicit error result) if that also fails.
 *
 * **Defensive bounds** (finding: "unbounded/unvalidated binary counts"): every length-prefixed collection
 * count read from the stream is validated to be non-negative and below a generous, documented ceiling
 * *before* it is used to size an allocation/loop — a corrupt or maliciously-crafted asset (a huge or
 * negative declared count) must fail closed immediately, not attempt a huge allocation or loop indefinitely.
 * The real bundled dataset (~7 land polygons, ~3,465 sub-rectangles, ~938 ports, ~44,000 total coordinate
 * points across every ring — derived from the ~716KB real asset size / 16 bytes-per-point) is comfortably
 * inside every bound below with more than 10x headroom. Every decoded [GeoPoint] must be finite (never
 * `NaN`/`Infinite`) and within valid WGS84 degree ranges, and every [GeoBoundingBox] must have its
 * min/max ordered correctly — a violation of either fails the decode rather than producing geometry that
 * would silently misrender or misbehave in hit-testing/projection maths.
 */
object MapGeometryBinaryDecoder {
    /** Must match `GeoPrecomputeTask.DERIVED_ASSET_FILE_NAME` (buildSrc) exactly. */
    const val DERIVED_ASSET_FILE_NAME = "map_geometry.bin"
    private const val EXPECTED_MAGIC = "MMOGEOV1"
    private const val EXPECTED_VERSION = 1

    // Generous, documented ceilings — see the class doc comment for the real dataset's actual scale.
    private const val MAX_TOP_LEVEL_COLLECTION_COUNT = 200_000
    private const val MAX_RING_COUNT_PER_POLYGON = 10_000
    private const val MAX_POINT_COUNT_PER_RING = 200_000
    private const val MAX_TOTAL_POINTS = 500_000

    private const val MAX_LAT_DEGREES = 90.0
    private const val MAX_LNG_DEGREES = 180.0

    fun decode(input: InputStream): Result<MapGeometryDataset> =
        try {
            Result.success(decodeInternal(input))
        } catch (cancellation: CancellationException) {
            // Never swallowed: standard Kotlin coroutines convention — a cancelled coroutine must keep
            // propagating cancellation, not be converted into a "safe" Result.failure that would trigger an
            // unnecessary/expensive GeoJSON fallback parse (see finding: "runCatching is too broad").
            throw cancellation
        } catch (expected: IOException) {
            // The only failure mode this decode genuinely expects: a missing/truncated/corrupt asset stream
            // (EOFException/UTFDataFormatException are IOException subtypes), or one of this decoder's own
            // explicit `throw IOException(...)` validation-failure calls below. Deliberately does **not**
            // catch a bare `Throwable`/`Exception` — a JVM `Error` (e.g. `OutOfMemoryError`) must propagate,
            // never be caught and converted into a fallback trigger.
            Result.failure(expected)
        }

    private fun decodeInternal(input: InputStream): MapGeometryDataset =
        DataInputStream(input).use { data ->
            val magic = data.readUTF()
            val version = data.readInt()
            if (magic != EXPECTED_MAGIC || version != EXPECTED_VERSION) {
                throw IOException("Unrecognised map-geometry asset header: magic=$magic version=$version")
            }

            val totalPoints = TotalPointsBudget()

            val landPolygons =
                (0 until readBoundedCount(data, "landPolygonCount")).map {
                    LandPolygon(rings = readPolygonRings(data, totalPoints))
                }

            val subRectangles =
                (0 until readBoundedCount(data, "subRectangleCount")).map {
                    val subCode = data.readUTF()
                    val icesName = data.readUTF()
                    val seaOverlapping = data.readBoolean()
                    val centroid = readValidGeoPoint(data)
                    val boundingBox = readValidBoundingBox(data)
                    val polygonCount = readBoundedCount(data, "subRectangle.polygonCount")
                    val rings = (0 until polygonCount).flatMap { readPolygonRings(data, totalPoints) }
                    StatisticalSubRectangleGeometry(
                        subCode = subCode,
                        parentIcesName = icesName,
                        rings = rings,
                        bboxCentroid = centroid,
                        boundingBox = boundingBox,
                        seaOverlapping = seaOverlapping,
                    )
                }

            val ports =
                (0 until readBoundedCount(data, "portCount")).map {
                    MapPort(name = data.readUTF(), location = readValidGeoPoint(data))
                }

            MapGeometryDataset(landPolygons = landPolygons, subRectangles = subRectangles, ports = ports)
        }

    /** A running total of every coordinate point decoded so far, capped at [MAX_TOTAL_POINTS] overall. */
    private class TotalPointsBudget {
        private var total = 0

        fun consume(pointCount: Int) {
            total += pointCount
            if (total > MAX_TOTAL_POINTS) {
                throw IOException(
                    "Map geometry asset declares more than $MAX_TOTAL_POINTS total coordinate points " +
                        "across every ring — rejecting as corrupt/malicious rather than risking an " +
                        "out-of-memory allocation.",
                )
            }
        }
    }

    private fun readBoundedCount(
        data: DataInputStream,
        fieldName: String,
    ): Int {
        val count = data.readInt()
        if (count < 0 || count > MAX_TOP_LEVEL_COLLECTION_COUNT) {
            throw IOException(
                "Map geometry asset declares an out-of-range $fieldName=$count " +
                    "(expected 0..$MAX_TOP_LEVEL_COLLECTION_COUNT)",
            )
        }
        return count
    }

    private fun readPolygonRings(
        data: DataInputStream,
        totalPoints: TotalPointsBudget,
    ): List<List<GeoPoint>> {
        val ringCount = data.readInt()
        if (ringCount < 0 || ringCount > MAX_RING_COUNT_PER_POLYGON) {
            throw IOException(
                "Map geometry asset declares an out-of-range ringCount=$ringCount " +
                    "(expected 0..$MAX_RING_COUNT_PER_POLYGON)",
            )
        }
        return (0 until ringCount).map {
            val pointCount = data.readInt()
            if (pointCount < 0 || pointCount > MAX_POINT_COUNT_PER_RING) {
                throw IOException(
                    "Map geometry asset declares an out-of-range pointCount=$pointCount " +
                        "(expected 0..$MAX_POINT_COUNT_PER_RING)",
                )
            }
            totalPoints.consume(pointCount)
            (0 until pointCount).map { readValidGeoPoint(data) }
        }
    }

    private fun readValidGeoPoint(data: DataInputStream): GeoPoint {
        val lat = data.readDouble()
        val lng = data.readDouble()
        if (!isValidLatLng(lat, lng)) {
            throw IOException("Map geometry asset contains an invalid coordinate: lat=$lat lng=$lng")
        }
        return GeoPoint(lat = lat, lng = lng)
    }

    /** Split out of [readValidGeoPoint] purely to keep that condition under detekt's complexity threshold. */
    private fun isValidLatLng(
        lat: Double,
        lng: Double,
    ): Boolean {
        val latInRange = lat.isFinite() && lat in -MAX_LAT_DEGREES..MAX_LAT_DEGREES
        val lngInRange = lng.isFinite() && lng in -MAX_LNG_DEGREES..MAX_LNG_DEGREES
        return latInRange && lngInRange
    }

    private fun readValidBoundingBox(data: DataInputStream): GeoBoundingBox {
        val minLat = data.readDouble()
        val maxLat = data.readDouble()
        val minLng = data.readDouble()
        val maxLng = data.readDouble()
        val values = listOf(minLat, maxLat, minLng, maxLng)
        if (values.any { !it.isFinite() }) {
            throw IOException("Map geometry asset contains a non-finite bounding box: $values")
        }
        if (minLat > maxLat || minLng > maxLng) {
            throw IOException(
                "Map geometry asset contains an incorrectly-ordered bounding box: " +
                    "minLat=$minLat maxLat=$maxLat minLng=$minLng maxLng=$maxLng",
            )
        }
        return GeoBoundingBox(minLat = minLat, maxLat = maxLat, minLng = minLng, maxLng = maxLng)
    }
}
