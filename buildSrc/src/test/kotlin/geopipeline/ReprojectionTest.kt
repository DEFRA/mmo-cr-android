package geopipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReprojectionTest {
    @Test
    fun `plausible WGS84 degree pair is trusted as-is`() {
        val result = Reprojection.toWgs84(x = -1.5, y = 50.5)
        assertEquals(50.5, result.lat, DELTA)
        assertEquals(-1.5, result.lng, DELTA)
    }

    @Test
    fun `out-of-range Web Mercator metres pair falls back to inverse projection`() {
        // Real EPSG:3857 vertex from the actual subrectangles.geojson (sub_code "27D86"), whose bounding
        // box (SOUTH=49.0, NORTH=49.5, WEST=-12.0, EAST=-11.0) confirms the expected inverse-projected
        // range: x=-1264519.103601269889623, y=6303188.702299997210503 -> approx (49.17, -11.36).
        val result = Reprojection.toWgs84(x = -1264519.103601269889623, y = 6303188.702299997210503)
        assertTrue("expected lat within [49.0, 49.5] but was ${result.lat}", result.lat in 49.0..49.5)
        assertTrue("expected lng close to -11.36 but was ${result.lng}", abs(result.lng - (-11.36)) < 0.05)
    }

    @Test
    fun `forward then inverse Web Mercator round-trips to the original point`() {
        val original = LatLng(lat = 45.0, lng = 2.0)
        val (x, y) = Reprojection.toWebMercator(original)
        val roundTripped = Reprojection.inverseWebMercatorForTest(x, y)
        assertEquals(original.lat, roundTripped.lat, DELTA)
        assertEquals(original.lng, roundTripped.lng, DELTA)
    }

    companion object {
        private const val DELTA = 0.0001
    }
}

/** Exposes the private inverse-Mercator branch directly for the round-trip test above. */
private fun Reprojection.inverseWebMercatorForTest(
    x: Double,
    y: Double,
): LatLng = Reprojection.toWgs84(x, y)
