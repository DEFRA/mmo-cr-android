package geopipeline

import java.io.DataOutputStream
import java.io.OutputStream

/**
 * Writes the compact derived binary geometry asset consumed at runtime by
 * `uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map.MapGeometryBinaryDecoder` (app main source
 * set — see ADR 0013). A plain, hand-rolled, versioned `DataOutputStream` layout was chosen over a
 * schema'd format (protobuf/FlatBuffers) to avoid adding a new runtime dependency for a small, fully
 * build-internal format; forward-compatibility is provided by [FORMAT_VERSION] (a decoder encountering an
 * unknown version fails closed, never silently misreads).
 *
 * Layout (`Double`-based throughout, matching the plan's "Double-based binary format" decision):
 * ```
 * magic: UTF ("MMOGEOV1")
 * version: Int
 * landPolygonCount: Int
 *   [ringCount: Int, [pointCount: Int, [lat: Double, lng: Double] * pointCount] * ringCount] * landPolygonCount
 * subRectangleCount: Int
 *   [subCode: UTF, icesName: UTF, seaOverlapping: Boolean,
 *    bboxCentroidLat: Double, bboxCentroidLng: Double,
 *    bboxMinLat/bboxMaxLat/bboxMinLng/bboxMaxLng: Double,
 *    polygonCount: Int, [ringCount: Int, [pointCount: Int, [lat, lng] * pointCount] * ringCount] * polygonCount
 *   ] * subRectangleCount
 * portCount: Int
 *   [name: UTF, lat: Double, lng: Double] * portCount
 * ```
 */
object GeoBinaryWriter {
    const val MAGIC = "MMOGEOV1"
    const val FORMAT_VERSION = 1

    fun write(
        output: OutputStream,
        landPolygons: List<RawPolygon>,
        subRectangles: List<PrecomputedSubRectangle>,
        ports: List<RawPort>,
    ) {
        DataOutputStream(output).use { out ->
            out.writeUTF(MAGIC)
            out.writeInt(FORMAT_VERSION)

            out.writeInt(landPolygons.size)
            landPolygons.forEach { writePolygon(out, it) }

            out.writeInt(subRectangles.size)
            subRectangles.forEach { rect ->
                out.writeUTF(rect.subCode)
                out.writeUTF(rect.icesName)
                out.writeBoolean(rect.seaOverlapping)
                out.writeDouble(rect.bboxCentroid.lat)
                out.writeDouble(rect.bboxCentroid.lng)
                out.writeDouble(rect.boundingBox.minLat)
                out.writeDouble(rect.boundingBox.maxLat)
                out.writeDouble(rect.boundingBox.minLng)
                out.writeDouble(rect.boundingBox.maxLng)
                out.writeInt(rect.polygons.size)
                rect.polygons.forEach { writePolygon(out, it) }
            }

            out.writeInt(ports.size)
            ports.forEach { port ->
                out.writeUTF(port.name)
                out.writeDouble(port.location.lat)
                out.writeDouble(port.location.lng)
            }
        }
    }

    private fun writePolygon(
        out: DataOutputStream,
        polygon: RawPolygon,
    ) {
        out.writeInt(polygon.rings.size)
        polygon.rings.forEach { ring ->
            out.writeInt(ring.size)
            ring.forEach { point ->
                out.writeDouble(point.lat)
                out.writeDouble(point.lng)
            }
        }
    }
}

/** A [RawSubRectangle] enriched with its own bbox/centroid/sea-overlap flag — see [GeometryMath]. */
data class PrecomputedSubRectangle(
    val subCode: String,
    val icesName: String,
    val polygons: List<RawPolygon>,
    val seaOverlapping: Boolean,
    val bboxCentroid: LatLng,
    val boundingBox: BoundingBox,
)
