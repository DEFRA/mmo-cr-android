package uk.gov.defra.mmocatchrecord.mapdata

import kotlinx.serialization.Serializable

/**
 * The compact, pre-computed dataset generated from the bundled GeoJSON — see [MapDataGenerator] and
 * docs/development/offline-map.md. Plain-data, no Android/Gradle types, `@Serializable` so it round-trips
 * to/from the generated asset JSON and can also be produced by the on-device runtime fallback parser.
 */
@Serializable
data class MapDataset(
    val formatVersion: Int,
    val land: List<SerializableMultiPolygon>,
    val subRectangles: List<SerializableSubRectangle>,
    val ports: List<SerializablePort>,
) {
    companion object {
        /** Bump whenever the on-disk shape changes so a stale/incompatible generated asset is never trusted. */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class SerializablePoint(val lon: Double, val lat: Double)

@Serializable
data class SerializableRing(val points: List<SerializablePoint>)

@Serializable
data class SerializablePolygon(val shell: SerializableRing, val holes: List<SerializableRing> = emptyList())

@Serializable
data class SerializableMultiPolygon(val polygons: List<SerializablePolygon>)

@Serializable
data class SerializableBBox(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double)

@Serializable
data class SerializableSubRectangle(
    val code: String,
    val icesName: String,
    val areaKm2: Double,
    val bbox: SerializableBBox,
    val centroid: SerializablePoint,
    val geometry: SerializableMultiPolygon,
    val isSeaOverlapping: Boolean,
)

@Serializable
data class SerializablePort(val portCode: String, val name: String, val point: SerializablePoint)

fun GeoPoint.toSerializable() = SerializablePoint(lon, lat)

fun SerializablePoint.toGeoPoint() = GeoPoint(lon, lat)

fun Ring.toSerializable() = SerializableRing(points.map { it.toSerializable() })

fun SerializableRing.toRing() = Ring(points.map { it.toGeoPoint() })

fun GeoPolygon.toSerializable() = SerializablePolygon(shell.toSerializable(), holes.map { it.toSerializable() })

fun SerializablePolygon.toGeoPolygon() = GeoPolygon(shell.toRing(), holes.map { it.toRing() })

fun MultiPolygon.toSerializable() = SerializableMultiPolygon(polygons.map { it.toSerializable() })

fun SerializableMultiPolygon.toMultiPolygon() = MultiPolygon(polygons.map { it.toGeoPolygon() })

fun BBox.toSerializable() = SerializableBBox(minLon, minLat, maxLon, maxLat)

fun SerializableBBox.toBBox() = BBox(minLon, minLat, maxLon, maxLat)
