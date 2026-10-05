package uk.gov.defra.mmocatchrecord.mapdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDataParserTest {
    @Test
    fun `parses polygon and multipolygon land features`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":
                [[[0,0],[10,0],[10,10],[0,10],[0,0]]]}},
              {"type":"Feature","properties":{},"geometry":{"type":"MultiPolygon","coordinates":
                [[[[0,0],[5,0],[5,5],[0,5],[0,0]]],[[[10,10],[15,10],[15,15],[10,15],[10,10]]]]}}
            ]}
            """.trimIndent()
        val result = MapDataParser.parseLandFeatures(geoJson)
        assertEquals(2, result.values.size)
        assertEquals(1, result.values[0].polygons.size)
        assertEquals(2, result.values[1].polygons.size)
        assertTrue(result.diagnostics.skippedFeatures.isEmpty())
    }

    @Test
    fun `parses polygon holes`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":
                [[[0,0],[10,0],[10,10],[0,10],[0,0]],[[2,2],[8,2],[8,8],[2,8],[2,2]]]}}
            ]}
            """.trimIndent()
        val result = MapDataParser.parseLandFeatures(geoJson)
        assertEquals(1, result.values[0].polygons[0].holes.size)
    }

    @Test
    fun `malformed feature is skipped not thrown`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":"not-an-array"}},
              {"type":"Feature","properties":{},"geometry":null},
              {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":
                [[[0,0],[10,0],[10,10],[0,10],[0,0]]]}}
            ]}
            """.trimIndent()
        val result = MapDataParser.parseLandFeatures(geoJson)
        assertEquals(1, result.values.size)
        assertEquals(2, result.diagnostics.skippedFeatures.size)
    }

    @Test
    fun `still-invalid coordinate pair rejects the feature`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"port":"Bad","port_code":"1"},
               "geometry":{"type":"Point","coordinates":[999999999999.0, 999999999999.0]}}
            ]}
            """.trimIndent()
        val result = MapDataParser.parsePortFeatures(geoJson)
        assertTrue(result.values.isEmpty())
        assertEquals(1, result.diagnostics.skippedFeatures.size)
    }

    @Test
    fun `parses multipoint ports and takes the first point`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"port":"Kyle","port_code":"12"},
               "geometry":{"type":"MultiPoint","coordinates":[[-5.7,57.28]]}}
            ]}
            """.trimIndent()
        val result = MapDataParser.parsePortFeatures(geoJson)
        assertEquals(1, result.values.size)
        assertEquals("Kyle", result.values[0].name)
        assertFalse(result.diagnostics.skippedFeatures.isNotEmpty())
    }

    @Test
    fun `subrectangle coordinates in epsg3857 are reprojected`() {
        val geoJson =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"sub_code":"42E41","ICESNAME":"42E4","AREA_KM2":100,
                "stat_x":-6.0,"stat_y":57.0},
               "geometry":{"type":"MultiPolygon","coordinates":
                 [[[[-668258.0,7379925.0],[-556597.0,7379925.0],[-556597.0,7514065.0],
                    [-668258.0,7514065.0],[-668258.0,7379925.0]]]]}}
            ]}
            """.trimIndent()
        val result = MapDataParser.parseSubRectangleFeatures(geoJson)
        assertEquals(1, result.values.size)
        assertEquals("42E41", result.values[0].code)
        assertTrue(result.diagnostics.reprojectedCoordinateCount > 0)
    }
}
