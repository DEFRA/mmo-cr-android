package geopipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonParserTest {
    @Test
    fun `parses a Polygon feature with WGS84 degree coordinates`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":
                [[[0.0,50.0],[1.0,50.0],[1.0,51.0],[0.0,51.0],[0.0,50.0]]]}}
            ]}
            """.trimIndent()
        val polygons = GeoJsonParser.parseLandPolygons(geoJson)
        assertEquals(1, polygons.size)
        assertEquals(5, polygons.first().rings.first().size)
        assertEquals(50.0, polygons.first().rings.first().first().lat, DELTA)
    }

    @Test
    fun `parses sub_code and ICESNAME from a MultiPolygon feature in Web Mercator metres`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"sub_code":"27D86","ICESNAME":"27D8"},"geometry":
                {"type":"MultiPolygon","coordinates":[[[[-1264519.1036,6303188.7023],
                  [-1261620.8957,6303188.7023],[-1261620.8957,6281307.2182],
                  [-1264519.1036,6281307.2182],[-1264519.1036,6303188.7023]]]]}}
            ]}
            """.trimIndent()
        val rectangles = GeoJsonParser.parseSubRectangles(geoJson)
        assertEquals(1, rectangles.size)
        assertEquals("27D86", rectangles.first().subCode)
        assertEquals("27D8", rectangles.first().icesName)
        // Reprojected out of raw Web Mercator metres into plausible WGS84 degrees.
        val point = rectangles.first().polygons.first().rings.first().first()
        assertTrue(point.lat in -90.0..90.0)
        assertTrue(point.lng in -180.0..180.0)
    }

    @Test
    fun `tolerates coordinate components encoded as quoted numeric strings`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"sub_code":"38E95","ICESNAME":"38E9"},"geometry":
                {"type":"Polygon","coordinates":[[["1.5","50.5"],["1.6","50.5"],["1.6","50.6"],["1.5","50.5"]]]}}
            ]}
            """.trimIndent()
        val rectangles = GeoJsonParser.parseSubRectangles(geoJson)
        assertEquals(1, rectangles.size)
        val point = rectangles.first().polygons.first().rings.first().first()
        assertEquals(50.5, point.lat, DELTA)
        assertEquals(1.5, point.lng, DELTA)
    }

    @Test
    fun `feature missing sub_code is skipped rather than crashing the build`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"ICESNAME":"27D8"},"geometry":
                {"type":"Polygon","coordinates":[[[0.0,50.0],[1.0,50.0],[1.0,51.0],[0.0,50.0]]]}}
            ]}
            """.trimIndent()
        val rectangles = GeoJsonParser.parseSubRectangles(geoJson)
        assertTrue(rectangles.isEmpty())
    }

    @Test
    fun `parses a MultiPoint port feature`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"port":"Hastings","port_code":123},"geometry":
                {"type":"MultiPoint","coordinates":[[0.5731,50.8551]]}}
            ]}
            """.trimIndent()
        val ports = GeoJsonParser.parsePorts(geoJson)
        assertEquals(1, ports.size)
        assertEquals("Hastings", ports.first().name)
        assertEquals(50.8551, ports.first().location.lat, DELTA)
    }

    @Test
    fun `port feature missing name is skipped`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[0.5,50.8]}}
            ]}
            """.trimIndent()
        assertNull(GeoJsonParser.parsePorts(geoJson).firstOrNull())
    }

    companion object {
        private const val DELTA = 0.0001
    }
}
