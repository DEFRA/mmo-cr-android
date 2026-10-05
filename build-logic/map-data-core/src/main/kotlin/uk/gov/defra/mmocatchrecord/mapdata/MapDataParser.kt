package uk.gov.defra.mmocatchrecord.mapdata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Robust, crash-proof GeoJSON parsing shared by the build-time preprocessing task and the app's on-device
 * runtime fallback (see docs/development/offline-map.md). Deliberately parses coordinates via the generic
 * [kotlinx.serialization.json.JsonElement] tree (not a typed `@Serializable` coordinate model) because a
 * single coordinate component may legitimately be a JSON number *or* a numeric string in these files.
 */
object MapDataParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Diagnostics accumulated while parsing one GeoJSON `FeatureCollection` — never thrown, always returned. */
    data class Diagnostics(
        val totalFeatures: Int,
        val skippedFeatures: List<SkippedFeature>,
        val reprojectedCoordinateCount: Int,
    )

    data class SkippedFeature(val index: Int, val reason: String)

    class ParseResult<T>(val values: List<T>, val diagnostics: Diagnostics)

    /** Parses the land `map.geojson` FeatureCollection into one [MultiPolygon] per feature. */
    fun parseLandFeatures(rawGeoJson: String): ParseResult<MultiPolygon> =
        parseFeatureCollection(rawGeoJson) { _, geometry, _, counter ->
            parseMultiPolygonGeometry(geometry, counter)
        }

    /** Parses `subrectangles.geojson` into [RawSubRectangle]s (still in whatever the source CRS was, resolved). */
    fun parseSubRectangleFeatures(rawGeoJson: String): ParseResult<RawSubRectangle> =
        parseFeatureCollection(rawGeoJson) { _, geometry, properties, counter ->
            val multiPolygon =
                parseMultiPolygonGeometry(geometry, counter) ?: return@parseFeatureCollection null
            val code = stringProperty(properties, "sub_code") ?: return@parseFeatureCollection null
            val icesName = stringProperty(properties, "ICESNAME").orEmpty()
            val areaKm2 = doubleProperty(properties, "AREA_KM2") ?: 0.0
            RawSubRectangle(code = code, icesName = icesName, areaKm2 = areaKm2, geometry = multiPolygon)
        }

    /** Parses `ports.geojson` (Point/MultiPoint) into [RawPort]s. */
    fun parsePortFeatures(rawGeoJson: String): ParseResult<RawPort> =
        parseFeatureCollection(rawGeoJson) { _, geometry, properties, counter ->
            val name = stringProperty(properties, "port") ?: return@parseFeatureCollection null
            val portCode = stringProperty(properties, "port_code").orEmpty()
            val point = parseFirstPointGeometry(geometry, counter) ?: return@parseFeatureCollection null
            RawPort(portCode = portCode, name = name, point = point)
        }

    private fun <T> parseFeatureCollection(
        rawGeoJson: String,
        transform: (index: Int, geometry: JsonObject, properties: JsonObject, reprojectionCounter: IntArray) -> T?,
    ): ParseResult<T> {
        val root = runCatching { json.parseToJsonElement(rawGeoJson) }.getOrNull() as? JsonObject
            ?: return ParseResult(emptyList(), Diagnostics(0, listOf(SkippedFeature(-1, "invalid_root_json")), 0))
        val features = root["features"] as? JsonArray ?: JsonArray(emptyList())
        val values = mutableListOf<T>()
        val skipped = mutableListOf<SkippedFeature>()
        var reprojectedCount = 0
        features.forEachIndexed { index, element ->
            val outcome = parseOneFeature(index, element, transform)
            when (outcome) {
                is FeatureOutcome.Ok -> {
                    values.add(outcome.value)
                    reprojectedCount += outcome.reprojectedCount
                }
                is FeatureOutcome.Skipped -> skipped.add(SkippedFeature(index, outcome.reason))
            }
        }
        return ParseResult(values, Diagnostics(features.size, skipped, reprojectedCount))
    }

    private sealed interface FeatureOutcome<out T> {
        data class Ok<T>(val value: T, val reprojectedCount: Int) : FeatureOutcome<T>

        data class Skipped(val reason: String) : FeatureOutcome<Nothing>
    }

    private fun <T> parseOneFeature(
        index: Int,
        element: JsonElement,
        transform: (Int, JsonObject, JsonObject, IntArray) -> T?,
    ): FeatureOutcome<T> {
        val counter = intArrayOf(0)
        return try {
            val feature = element as? JsonObject ?: return FeatureOutcome.Skipped("malformed_feature")
            val geometry = feature["geometry"]?.takeIf { it != JsonNull } as? JsonObject
                ?: return FeatureOutcome.Skipped("missing_geometry")
            val properties = feature["properties"]?.takeIf { it != JsonNull } as? JsonObject ?: JsonObject(emptyMap())
            val value =
                transform(index, geometry, properties, counter)
                    ?: return FeatureOutcome.Skipped("unsupported_or_invalid_feature")
            FeatureOutcome.Ok(value, counter[0])
        } catch (e: IllegalStateException) {
            FeatureOutcome.Skipped("malformed: ${e.message}")
        } catch (e: NumberFormatException) {
            FeatureOutcome.Skipped("malformed_number: ${e.message}")
        }
    }

    private fun resolvePair(a: Double, b: Double, reprojectionCounter: IntArray): GeoPoint? {
        val resolution = Projection.resolveCoordinatePair(a, b) ?: return null
        if (resolution.reprojected) reprojectionCounter[0]++
        return resolution.point
    }

    private fun parseNumber(element: JsonElement): Double? =
        (element as? JsonPrimitive)?.let { primitive ->
            primitive.doubleOrNull ?: primitive.content.trim().ifEmpty { null }?.toDoubleOrNull()
        }

    private fun parseCoordinatePair(element: JsonElement, reprojectionCounter: IntArray): GeoPoint? {
        val array = (element as? JsonArray) ?: return null
        if (array.size < 2) return null
        val a = parseNumber(array[0]) ?: return null
        val b = parseNumber(array[1]) ?: return null
        return resolvePair(a, b, reprojectionCounter)
    }

    private fun parseRing(element: JsonElement, reprojectionCounter: IntArray): Ring? {
        val array = (element as? JsonArray) ?: return null
        val points = array.map { parseCoordinatePair(it, reprojectionCounter) ?: return null }
        if (points.size < 3) return null
        return Ring(points)
    }

    private fun parsePolygon(element: JsonElement, reprojectionCounter: IntArray): GeoPolygon? {
        val rings = (element as? JsonArray) ?: return null
        if (rings.isEmpty()) return null
        val shell = parseRing(rings[0], reprojectionCounter) ?: return null
        val holes = rings.drop(1).mapNotNull { parseRing(it, reprojectionCounter) }
        return GeoPolygon(shell, holes)
    }

    private fun parseMultiPolygonGeometry(
        geometry: JsonObject,
        reprojectionCounter: IntArray,
    ): MultiPolygon? {
        val type = stringProperty(geometry, "type") ?: return null
        val coordinates = geometry["coordinates"] ?: return null
        return when (type) {
            "Polygon" -> parsePolygon(coordinates, reprojectionCounter)?.let { MultiPolygon(listOf(it)) }
            "MultiPolygon" -> {
                val array = (coordinates as? JsonArray) ?: return null
                val polygons = array.mapNotNull { parsePolygon(it, reprojectionCounter) }
                if (polygons.isEmpty()) null else MultiPolygon(polygons)
            }
            else -> null
        }
    }

    private fun parseFirstPointGeometry(
        geometry: JsonObject,
        reprojectionCounter: IntArray,
    ): GeoPoint? {
        val type = stringProperty(geometry, "type") ?: return null
        val coordinates = geometry["coordinates"] ?: return null
        return when (type) {
            "Point" -> parseCoordinatePair(coordinates, reprojectionCounter)
            "MultiPoint" -> {
                val array = (coordinates as? JsonArray) ?: return null
                array.firstNotNullOfOrNull { parseCoordinatePair(it, reprojectionCounter) }
            }
            else -> null
        }
    }

    private fun stringProperty(container: JsonObject, key: String): String? {
        val value = container[key] ?: return null
        if (value == JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    private fun doubleProperty(container: JsonObject, key: String): Double? {
        val value = container[key] ?: return null
        if (value == JsonNull) return null
        return parseNumber(value)
    }
}

/** A subrectangle feature straight out of GeoJSON, before centroid/bbox/sea-overlap have been computed. */
data class RawSubRectangle(val code: String, val icesName: String, val areaKm2: Double, val geometry: MultiPolygon)

/** A port feature straight out of GeoJSON. */
data class RawPort(val portCode: String, val name: String, val point: GeoPoint)
