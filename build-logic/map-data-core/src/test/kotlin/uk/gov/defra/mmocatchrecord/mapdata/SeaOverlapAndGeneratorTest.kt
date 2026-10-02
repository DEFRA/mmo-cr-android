package uk.gov.defra.mmocatchrecord.mapdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaOverlapAndGeneratorTest {
    private fun square(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
        MultiPolygon(
            listOf(
                GeoPolygon(
                    Ring(
                        listOf(
                            GeoPoint(minLon, minLat),
                            GeoPoint(maxLon, minLat),
                            GeoPoint(maxLon, maxLat),
                            GeoPoint(minLon, maxLat),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun `fully inland bbox is not sea-overlapping`() {
        val land = listOf(square(-10.0, -10.0, 10.0, 10.0))
        val bounds = land.map { BBox.of(it.polygons[0].shell.points) }
        val bbox = BBox(-1.0, -1.0, 1.0, 1.0)
        assertFalse(SeaOverlap.isSeaOverlapping(bbox, land, bounds))
    }

    @Test
    fun `fully offshore bbox is sea-overlapping`() {
        val land = listOf(square(50.0, 50.0, 60.0, 60.0))
        val bounds = land.map { BBox.of(it.polygons[0].shell.points) }
        val bbox = BBox(-10.0, -10.0, -5.0, -5.0)
        assertTrue(SeaOverlap.isSeaOverlapping(bbox, land, bounds))
    }

    @Test
    fun `coastal bbox straddling land edge is sea-overlapping`() {
        val land = listOf(square(0.0, -10.0, 10.0, 10.0))
        val bounds = land.map { BBox.of(it.polygons[0].shell.points) }
        val bbox = BBox(-5.0, -5.0, 5.0, 5.0)
        assertTrue(SeaOverlap.isSeaOverlapping(bbox, land, bounds))
    }

    @Test
    fun `sibling subrectangles sharing stat_x stat_y get distinct bbox centroid labels`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"sub_code":"A1","ICESNAME":"A","AREA_KM2":1,
                "stat_x":0.0,"stat_y":0.0},
               "geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}},
              {"type":"Feature","properties":{"sub_code":"A2","ICESNAME":"A","AREA_KM2":1,
                "stat_x":0.0,"stat_y":0.0},
               "geometry":{"type":"Polygon","coordinates":[[[2,2],[3,2],[3,3],[2,3],[2,2]]]}}
            ]}
            """.trimIndent()
        val land = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        val ports = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        val result = MapDataGenerator.generate(land, geoJson, ports)
        val a1 = result.dataset.subRectangles.first { it.code == "A1" }
        val a2 = result.dataset.subRectangles.first { it.code == "A2" }
        assertFalse(a1.centroid == a2.centroid)
        assertEquals(0.5, a1.centroid.lon, 0.0001)
        assertEquals(2.5, a2.centroid.lon, 0.0001)
    }

    @Test
    fun `generator marks a subrectangle fully inside land as not sea-overlapping and not selectable`() {
        val land = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{}," +
            "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[-10,-10],[10,-10],[10,10],[-10,10],[-10,-10]]]}}]}"
        val subRects = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"properties\":{\"sub_code\":\"INLAND1\",\"ICESNAME\":\"I\",\"AREA_KM2\":1}," +
            "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[-1,-1],[1,-1],[1,1],[-1,1],[-1,-1]]]}}]}"
        val ports = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        val result = MapDataGenerator.generate(land, subRects, ports)
        assertEquals(1, result.stats.inlandSubRectangleCount)
        assertFalse(result.dataset.subRectangles.single().isSeaOverlapping)
    }

    @Test
    fun `dataset serialization round trips`() {
        val land = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        val subRects = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"properties\":{\"sub_code\":\"S1\",\"ICESNAME\":\"S\",\"AREA_KM2\":42}," +
            "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}}]}"
        val ports = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"properties\":{\"port\":\"Test Port\",\"port_code\":\"7\"}," +
            "\"geometry\":{\"type\":\"Point\",\"coordinates\":[1.0,2.0]}}]}"
        val generated = MapDataGenerator.generate(land, subRects, ports).dataset
        val encoded = MapDataSerializer.encode(generated)
        val decoded = MapDataSerializer.decodeOrNull(encoded)
        assertEquals(generated, decoded)
    }

    @Test
    fun `decodeOrNull rejects a version-mismatched or corrupt payload`() {
        assertEquals(null, MapDataSerializer.decodeOrNull("not json"))
        assertEquals(null, MapDataSerializer.decodeOrNull("""{"formatVersion":999,"land":[],"subRectangles":[],"ports":[]}"""))
    }
}
