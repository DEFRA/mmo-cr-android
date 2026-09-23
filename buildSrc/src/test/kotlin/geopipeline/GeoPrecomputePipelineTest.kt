package geopipeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream

class GeoPrecomputePipelineTest {
    @Test
    fun `writes a decodable binary asset with the expected header and section counts`() {
        val land =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":
                [[[0.0,0.0],[0.0,20.0],[20.0,20.0],[20.0,0.0],[0.0,0.0]]]}}
            ]}
            """.trimIndent()
        val subRectangles =
            buildString {
                append("""{"type":"FeatureCollection","features":[""")
                val features =
                    (0 until GeoPrecomputePipeline.MIN_EXPECTED_SUB_RECTANGLES).map { index ->
                        val lat = 5.0 + index * 0.01
                        """{"type":"Feature","properties":{"sub_code":"CODE$index","ICESNAME":"AREA"},
                            "geometry":{"type":"Polygon","coordinates":
                            [[[5.0,$lat],[5.1,$lat],[5.1,${lat + 0.01}],[5.0,${lat + 0.01}],[5.0,$lat]]]}}"""
                    }
                append(features.joinToString(","))
                append("]}")
            }
        val ports =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"port":"Testport"},"geometry":{"type":"Point","coordinates":[5.05,5.005]}}
            ]}
            """.trimIndent()

        val output = ByteArrayOutputStream()
        GeoPrecomputePipeline.run(land, subRectangles, ports, output)

        val input = DataInputStream(ByteArrayInputStream(output.toByteArray()))
        assertEquals(GeoBinaryWriter.MAGIC, input.readUTF())
        assertEquals(GeoBinaryWriter.FORMAT_VERSION, input.readInt())
        assertEquals(1, input.readInt()) // land polygon count
        skipPolygon(input)
        val subRectangleCount = input.readInt()
        assertEquals(GeoPrecomputePipeline.MIN_EXPECTED_SUB_RECTANGLES, subRectangleCount)
    }

    @Test(expected = IllegalStateException::class)
    fun `fails fast when far fewer sub-rectangles parse than expected`() {
        val land = """{"type":"FeatureCollection","features":[]}"""
        val fewSubRectangles =
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"sub_code":"ONLY1"},"geometry":{"type":"Polygon","coordinates":
                [[[0.0,0.0],[0.0,1.0],[1.0,1.0],[0.0,0.0]]]}}
            ]}
            """.trimIndent()
        val ports = """{"type":"FeatureCollection","features":[]}"""
        GeoPrecomputePipeline.run(land, fewSubRectangles, ports, ByteArrayOutputStream())
    }

    private fun skipPolygon(input: DataInputStream) {
        val ringCount = input.readInt()
        repeat(ringCount) {
            val pointCount = input.readInt()
            repeat(pointCount) {
                input.readDouble()
                input.readDouble()
            }
        }
    }
}
