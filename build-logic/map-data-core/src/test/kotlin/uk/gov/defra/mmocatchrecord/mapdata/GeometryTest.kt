package uk.gov.defra.mmocatchrecord.mapdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private fun square(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
        Ring(
            listOf(
                GeoPoint(minLon, minLat),
                GeoPoint(maxLon, minLat),
                GeoPoint(maxLon, maxLat),
                GeoPoint(minLon, maxLat),
            ),
        )

    @Test
    fun `point in simple polygon`() {
        val polygon = GeoPolygon(square(0.0, 0.0, 10.0, 10.0))
        assertTrue(polygon.containsPoint(GeoPoint(5.0, 5.0)))
        assertFalse(polygon.containsPoint(GeoPoint(15.0, 5.0)))
    }

    @Test
    fun `polygon hole excludes contained point`() {
        val outer = square(0.0, 0.0, 10.0, 10.0)
        val hole = square(2.0, 2.0, 8.0, 8.0)
        val polygon = GeoPolygon(outer, listOf(hole))
        assertFalse(polygon.containsPoint(GeoPoint(5.0, 5.0)))
        assertTrue(polygon.containsPoint(GeoPoint(1.0, 1.0)))
    }

    @Test
    fun `multipolygon contains point in either member`() {
        val a = GeoPolygon(square(0.0, 0.0, 5.0, 5.0))
        val b = GeoPolygon(square(10.0, 10.0, 15.0, 15.0))
        val multi = MultiPolygon(listOf(a, b))
        assertTrue(multi.containsPoint(GeoPoint(12.0, 12.0)))
        assertFalse(multi.containsPoint(GeoPoint(7.0, 7.0)))
    }

    @Test
    fun `bbox centroid is midpoint`() {
        val bbox = BBox(0.0, 0.0, 10.0, 20.0)
        assertEquals(5.0, bbox.centroid.lon, 0.0001)
        assertEquals(10.0, bbox.centroid.lat, 0.0001)
    }

    @Test
    fun `bbox overlap detection`() {
        val a = BBox(0.0, 0.0, 10.0, 10.0)
        val b = BBox(5.0, 5.0, 15.0, 15.0)
        val c = BBox(20.0, 20.0, 30.0, 30.0)
        assertTrue(a.overlaps(b))
        assertFalse(a.overlaps(c))
    }
}
