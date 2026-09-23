package geopipeline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Numeric-string-tolerant, WGS84-first/Web-Mercator-fallback GeoJSON parser (build-time only) — see
 * [Reprojection]. Parses only the shapes this pipeline needs (`Polygon`/`MultiPolygon` rings for land and
 * sub-rectangles, `Point`/`MultiPoint` for ports); other geometry types are skipped defensively rather than
 * throwing, since a single malformed/unexpected feature must never fail the whole build — see
 * `GeoPrecomputeTask`'s minimum-feature-count safety check.
 */
object GeoJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseLandPolygons(geoJsonText: String): List<RawPolygon> = parseFeatures(geoJsonText).flatMap { it.polygons }

    fun parseSubRectangles(geoJsonText: String): List<RawSubRectangle> =
        parseFeatures(geoJsonText).mapNotNull { feature ->
            val subCode = feature.properties["sub_code"].asStringOrNull() ?: return@mapNotNull null
            val icesName = feature.properties["ICESNAME"].asStringOrNull().orEmpty()
            RawSubRectangle(subCode = subCode, icesName = icesName, polygons = feature.polygons)
        }

    fun parsePorts(geoJsonText: String): List<RawPort> =
        featureArrayOf(geoJsonText).mapNotNull { element ->
            val feature = element.jsonObject
            val properties = feature["properties"]?.jsonObject.orEmpty()
            val name = properties["port"].asStringOrNull() ?: return@mapNotNull null
            val geometry = feature["geometry"]?.jsonObject ?: return@mapNotNull null
            val point = firstPointOf(geometry) ?: return@mapNotNull null
            RawPort(name = name, location = point)
        }

    private data class ParsedFeature(
        val properties: JsonObject,
        val polygons: List<RawPolygon>,
    )

    private fun featureArrayOf(geoJsonText: String): JsonArray =
        json.parseToJsonElement(geoJsonText).jsonObject["features"]?.jsonArray ?: JsonArray(emptyList())

    private fun parseFeatures(geoJsonText: String): List<ParsedFeature> =
        featureArrayOf(geoJsonText).mapNotNull { element ->
            val feature = element.jsonObject
            val properties = feature["properties"]?.jsonObject ?: JsonObject(emptyMap())
            val geometry = feature["geometry"]?.jsonObject ?: return@mapNotNull null
            val polygons = polygonsOf(geometry)
            if (polygons.isEmpty()) null else ParsedFeature(properties, polygons)
        }

    private fun polygonsOf(geometry: JsonObject): List<RawPolygon> {
        val type = geometry["type"].asStringOrNull()
        val coordinates = geometry["coordinates"] ?: return emptyList()
        return when (type) {
            "Polygon" -> listOf(polygonOf(coordinates.jsonArray))
            "MultiPolygon" -> coordinates.jsonArray.map { polygonOf(it.jsonArray) }
            else -> emptyList()
        }
    }

    private fun polygonOf(polygonCoordinates: JsonArray): RawPolygon =
        RawPolygon(rings = polygonCoordinates.map { ring -> ringOf(ring.jsonArray) })

    private fun ringOf(ringCoordinates: JsonArray): List<LatLng> = ringCoordinates.mapNotNull { pointOf(it.jsonArray) }

    private fun firstPointOf(geometry: JsonObject): LatLng? {
        val type = geometry["type"].asStringOrNull()
        val coordinates = geometry["coordinates"] ?: return null
        return when (type) {
            "Point" -> pointOf(coordinates.jsonArray)
            "MultiPoint" -> coordinates.jsonArray.firstOrNull()?.let { pointOf(it.jsonArray) }
            else -> null
        }
    }

    /** A single `[x, y, ...]` coordinate tuple (ignoring any optional altitude component). */
    private fun pointOf(tuple: JsonArray): LatLng? {
        if (tuple.size < 2) return null
        val x = tuple[0].asNumberOrNull() ?: return null
        val y = tuple[1].asNumberOrNull() ?: return null
        return Reprojection.toWgs84(x, y)
    }

    /** Numeric-string tolerant: accepts a raw JSON number OR a quoted numeric string. */
    private fun JsonElement.asNumberOrNull(): Double? = (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.content
}
