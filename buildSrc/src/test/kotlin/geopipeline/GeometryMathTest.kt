package geopipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryMathTest {
    private fun square(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ) = RawPolygon(
        rings =
            listOf(
                listOf(
                    LatLng(minLat, minLng),
                    LatLng(minLat, maxLng),
                    LatLng(maxLat, maxLng),
                    LatLng(maxLat, minLng),
                    LatLng(minLat, minLng),
                ),
            ),
    )

    @Test
    fun `bounding box centroid is the box midpoint, not a shared parent centre`() {
        val box = BoundingBox(minLat = 50.0, maxLat = 51.0, minLng = 1.0, maxLng = 2.0)
        val centroid = GeometryMath.boundingBoxCentroid(box)
        assertEquals(50.5, centroid.lat, DELTA)
        assertEquals(1.5, centroid.lng, DELTA)
    }

    @Test
    fun `two adjacent sub-rectangles sharing a parent stat_x-stat_y get distinct centroids`() {
        val first = GeometryMath.boundingBoxOf(listOf(square(50.0, 50.5, 1.0, 1.5)))
        val second = GeometryMath.boundingBoxOf(listOf(square(50.0, 50.5, 1.5, 2.0)))
        val firstCentroid = GeometryMath.boundingBoxCentroid(first)
        val secondCentroid = GeometryMath.boundingBoxCentroid(second)
        assertTrue(firstCentroid.lng != secondCentroid.lng)
    }

    @Test
    fun `point strictly inside a polygon is detected`() {
        val polygon = square(0.0, 10.0, 0.0, 10.0)
        assertTrue(GeometryMath.pointInPolygon(LatLng(5.0, 5.0), polygon))
    }

    @Test
    fun `point outside a polygon is not detected`() {
        val polygon = square(0.0, 10.0, 0.0, 10.0)
        assertFalse(GeometryMath.pointInPolygon(LatLng(20.0, 20.0), polygon))
    }

    @Test
    fun `point inside a hole of a polygon is excluded`() {
        val exterior = listOf(LatLng(0.0, 0.0), LatLng(0.0, 10.0), LatLng(10.0, 10.0), LatLng(10.0, 0.0), LatLng(0.0, 0.0))
        val hole = listOf(LatLng(4.0, 4.0), LatLng(4.0, 6.0), LatLng(6.0, 6.0), LatLng(6.0, 4.0), LatLng(4.0, 4.0))
        val polygonWithHole = RawPolygon(rings = listOf(exterior, hole))
        assertFalse(GeometryMath.pointInPolygon(LatLng(5.0, 5.0), polygonWithHole))
        assertTrue(GeometryMath.pointInPolygon(LatLng(1.0, 1.0), polygonWithHole))
    }

    @Test
    fun `a sub-rectangle wholly inside land is not sea-overlapping`() {
        val land = listOf(square(0.0, 10.0, 0.0, 10.0))
        val landLockedRectangle = GeometryMath.boundingBoxOf(listOf(square(2.0, 3.0, 2.0, 3.0)))
        assertFalse(GeometryMath.isSeaOverlapping(landLockedRectangle, land))
    }

    @Test
    fun `a coastal sub-rectangle straddling land and sea is sea-overlapping`() {
        val land = listOf(square(0.0, 10.0, 0.0, 10.0))
        val coastalRectangle = GeometryMath.boundingBoxOf(listOf(square(5.0, 15.0, 5.0, 15.0)))
        assertTrue(GeometryMath.isSeaOverlapping(coastalRectangle, land))
    }

    @Test
    fun `a sub-rectangle wholly at sea (no land) is sea-overlapping`() {
        val noLand = emptyList<RawPolygon>()
        val oceanRectangle = GeometryMath.boundingBoxOf(listOf(square(40.0, 41.0, -10.0, -9.0)))
        assertTrue(GeometryMath.isSeaOverlapping(oceanRectangle, noLand))
    }

    companion object {
        private const val DELTA = 0.0001
    }
}
