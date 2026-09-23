package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream

/**
 * Hand-rolls the exact byte layout documented in `shared-geopipeline/.../geopipeline/GeoBinaryWriter.kt`
 * (a plain Kotlin file also compiled into buildSrc — see the cross-reference doc comments on both
 * [MapGeometryBinaryDecoder] and `GeoBinaryWriter`) to prove the runtime decoder reads that format
 * correctly, and fails closed (never throws) on a bad header, an out-of-range/negative declared count, or
 * invalid coordinate/bounding-box data.
 */
class MapGeometryBinaryDecoderTests {
    private fun validAssetBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("MMOGEOV1")
            out.writeInt(1)

            // One land polygon: one ring, a triangle.
            out.writeInt(1)
            out.writeInt(1)
            out.writeInt(3)
            listOf(0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0).forEach { (lat, lng) ->
                out.writeDouble(lat)
                out.writeDouble(lng)
            }

            // One sub-rectangle, one polygon, one ring (a square), sea-overlapping.
            out.writeInt(1)
            out.writeUTF("27D86")
            out.writeUTF("ICES-27")
            out.writeBoolean(true)
            out.writeDouble(50.5)
            out.writeDouble(0.5)
            out.writeDouble(50.0)
            out.writeDouble(51.0)
            out.writeDouble(0.0)
            out.writeDouble(1.0)
            out.writeInt(1)
            out.writeInt(1)
            out.writeInt(4)
            listOf(50.0 to 0.0, 50.0 to 1.0, 51.0 to 1.0, 51.0 to 0.0).forEach { (lat, lng) ->
                out.writeDouble(lat)
                out.writeDouble(lng)
            }

            // One port.
            out.writeInt(1)
            out.writeUTF("Hastings")
            out.writeDouble(50.855)
            out.writeDouble(0.573)
        }
        return bytes.toByteArray()
    }

    @Test
    fun decodesAWellFormedAssetIntoTheExpectedDomainGeometry() {
        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(validAssetBytes()))

        assertTrue(result.isSuccess)
        val dataset = result.getOrThrow()

        assertEquals(1, dataset.landPolygons.size)
        assertEquals(
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0)),
            dataset.landPolygons
                .single()
                .rings
                .single(),
        )

        assertEquals(1, dataset.subRectangles.size)
        val rectangle = dataset.subRectangles.single()
        assertEquals("27D86", rectangle.subCode)
        assertEquals("ICES-27", rectangle.parentIcesName)
        assertTrue(rectangle.seaOverlapping)
        assertEquals(GeoPoint(50.5, 0.5), rectangle.bboxCentroid)
        assertEquals(GeoBoundingBox(50.0, 51.0, 0.0, 1.0), rectangle.boundingBox)
        assertEquals(4, rectangle.rings.single().size)

        assertEquals(1, dataset.ports.size)
        assertEquals("Hastings", dataset.ports.single().name)
        assertEquals(GeoPoint(50.855, 0.573), dataset.ports.single().location)
    }

    @Test
    fun failsClosedOnAnUnrecognisedMagicHeaderRatherThanThrowing() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("NOTAMAGIC")
            out.writeInt(1)
        }

        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(bytes.toByteArray()))

        assertTrue(result.isFailure)
    }

    @Test
    fun failsClosedOnAnUnrecognisedFormatVersionRatherThanThrowing() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("MMOGEOV1")
            out.writeInt(99)
        }

        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(bytes.toByteArray()))

        assertTrue(result.isFailure)
    }

    @Test
    fun failsClosedOnATruncatedStreamRatherThanThrowing() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("MMOGEOV1")
            out.writeInt(1)
            out.writeInt(5) // claims 5 land polygons but the stream ends here
        }

        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(bytes.toByteArray()))

        assertTrue(result.isFailure)
    }

    @Test
    fun failsClosedOnANegativeTopLevelCollectionCountRatherThanCrashingOrLoopingForever() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("MMOGEOV1")
            out.writeInt(1)
            out.writeInt(-1) // negative landPolygonCount
        }

        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(bytes.toByteArray()))

        assertTrue(result.isFailure)
    }

    @Test
    fun failsClosedOnAnAbsurdlyOversizedTopLevelCollectionCountRatherThanAttemptingAHugeAllocation() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("MMOGEOV1")
            out.writeInt(1)
            out.writeInt(Int.MAX_VALUE) // way beyond MAX_TOP_LEVEL_COLLECTION_COUNT
        }

        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(bytes.toByteArray()))

        assertTrue(result.isFailure)
    }

    @Test
    fun failsClosedOnANonFiniteCoordinateRatherThanProducingUnusableGeometry() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("MMOGEOV1")
            out.writeInt(1)
            out.writeInt(1) // one land polygon
            out.writeInt(1) // one ring
            out.writeInt(1) // one point
            out.writeDouble(Double.NaN)
            out.writeDouble(0.0)
            out.writeInt(0) // no sub-rectangles
            out.writeInt(0) // no ports
        }

        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(bytes.toByteArray()))

        assertTrue(result.isFailure)
    }

    @Test
    fun failsClosedOnAnIncorrectlyOrderedBoundingBoxRatherThanProducingUnusableGeometry() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("MMOGEOV1")
            out.writeInt(1)
            out.writeInt(0) // no land polygons
            out.writeInt(1) // one sub-rectangle
            out.writeUTF("27D86")
            out.writeUTF("ICES-27")
            out.writeBoolean(true)
            out.writeDouble(50.5)
            out.writeDouble(0.5)
            // minLat > maxLat: incorrectly ordered.
            out.writeDouble(51.0)
            out.writeDouble(50.0)
            out.writeDouble(0.0)
            out.writeDouble(1.0)
            out.writeInt(0) // no polygons on this sub-rectangle
            out.writeInt(0) // no ports
        }

        val result = MapGeometryBinaryDecoder.decode(ByteArrayInputStream(bytes.toByteArray()))

        assertTrue(result.isFailure)
    }

    /**
     * Finding: "`runCatching` is too broad" — [MapGeometryBinaryDecoder.decode] must never convert a
     * [CancellationException] thrown mid-decode into a [Result.failure] (which [AssetMapGeometryRepository]
     * would otherwise interpret as "corrupt asset, try the GeoJSON fallback" — an expensive, wasted, and
     * semantically wrong reaction to a cancelled coroutine). A malformed [InputStream] that throws
     * [CancellationException] instead of [java.io.IOException] simulates this — the exception must propagate
     * out of `decode` uncaught, never wrapped.
     */
    @Test
    fun propagatesACancellationExceptionRatherThanConvertingItToAFailureResult() {
        val cancellingStream =
            object : InputStream() {
                override fun read(): Int = throw CancellationException("coroutine cancelled mid-decode")
            }

        try {
            MapGeometryBinaryDecoder.decode(cancellingStream)
            fail("expected a CancellationException to propagate, not be swallowed into a Result")
        } catch (expected: CancellationException) {
            assertEquals("coroutine cancelled mid-decode", expected.message)
        }
    }
}
