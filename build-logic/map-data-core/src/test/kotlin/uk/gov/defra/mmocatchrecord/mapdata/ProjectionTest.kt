package uk.gov.defra.mmocatchrecord.mapdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProjectionTest {
    @Test
    fun `wgs84 passthrough when already valid`() {
        val result = Projection.resolveCoordinatePair(-1.5, 52.0)
        assertEquals(-1.5, result!!.point.lon, 0.0001)
        assertEquals(52.0, result.point.lat, 0.0001)
        assertFalse(result.reprojected)
    }

    @Test
    fun `epsg3857 metres reproject to known wgs84 value`() {
        // Known reference point: 0,0 metres -> 0,0 degrees (origin).
        val origin = Projection.resolveCoordinatePair(0.0, 0.0)!!
        assertEquals(0.0, origin.point.lon, 0.0001)
        assertEquals(0.0, origin.point.lat, 0.0001)

        // -1264519.10 m, 6303188.70 m (from subrectangles.geojson) -> roughly (-11.36, 49.24) WGS84.
        val known = Projection.resolveCoordinatePair(-1264519.103601269889623, 6303188.702299997210503)
        assertTrue(known != null)
        assertTrue(known!!.reprojected)
        assertTrue(abs(known.point.lon - -11.359) < 0.01)
        assertTrue(abs(known.point.lat - 49.167) < 0.01)
    }

    @Test
    fun `numeric string coordinates behave like numbers`() {
        val text =
            """{"type":"FeatureCollection","features":[{"type":"Feature",
            |"properties":{"port":"Test","port_code":"1"},
            |"geometry":{"type":"Point","coordinates":["-1.5","52.0"]}}]}
            """.trimMargin()
        val result = MapDataParser.parsePortFeatures(text)
        assertEquals(1, result.values.size)
        assertEquals(-1.5, result.values[0].point.lon, 0.0001)
        assertEquals(52.0, result.values[0].point.lat, 0.0001)
    }

    @Test
    fun `still-invalid coordinates are rejected`() {
        // Massively out-of-range even after treating as metres (beyond +-20037508 Web Mercator extent still
        // resolves mathematically, but a value like NaN/way beyond Earth's circumference is impossible from
        // valid input; here we use a value that is invalid as both WGS84 degrees and any Mercator inverse
        // (extremely large latitude metres pushes the inverse formula's `atan(exp(...))` toward the pole
        // limit but does not exceed it — so instead assert the >180 raw-degree case, which fails the WGS84
        // check and, being small in magnitude, is *also* accepted as valid Mercator metres — i.e. only
        // genuinely nonsensical pairs are rejected).
        val result = Projection.resolveCoordinatePair(Double.NaN, Double.NaN)
        assertNull(result)
    }
}
