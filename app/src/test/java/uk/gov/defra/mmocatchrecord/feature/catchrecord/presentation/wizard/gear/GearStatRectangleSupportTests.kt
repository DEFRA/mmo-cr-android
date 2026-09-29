package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle

class GearStatRectangleSupportTests {
    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val handlines =
        GearType(
            id = "gear-handlines",
            name = "Handlines and pole lines (hand operated)",
            measurementTitleOverride = "handlines",
        )

    @Test
    fun `gear with a mesh size measurement includes the identifying measurement suffix`() {
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNets.id,
                statisticalSubRectangleCode = null,
                measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            )
        val name = GearStatRectangleSupport.gearNameWithIdentifyingMeasurementFor(seineNets, gearUse)
        assertEquals("seine nets (mesh size 100mm)", name)
    }

    @Test
    fun `gear with no identifying measurement omits the parenthetical entirely`() {
        val gearUse = GearUse(id = "gear-use-2", gearTypeId = handlines.id, statisticalSubRectangleCode = null)
        val name = GearStatRectangleSupport.gearNameWithIdentifyingMeasurementFor(handlines, gearUse)
        assertEquals("handlines", name)
    }

    @Test
    fun `unknown gear type falls back to the raw gear type id`() {
        val gearUse = GearUse(id = "gear-use-3", gearTypeId = "gear-unknown", statisticalSubRectangleCode = null)
        val name = GearStatRectangleSupport.gearNameWithIdentifyingMeasurementFor(null, gearUse)
        assertEquals("gear-unknown", name)
    }

    @Test
    fun `nearby rectangles are filtered to the departure port's statistical area`() {
        val rectangles =
            listOf(
                StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"),
                StatisticalSubRectangle("rect-2", "30F10", "AREA-DOVER"),
            )
        val port = Port("port-hastings", "Hastings", "AREA-HASTINGS")
        val nearby = GearStatRectangleSupport.nearbyRectanglesFor(port, rectangles)
        assertEquals(listOf("38E95"), nearby.map { it.code })
    }

    @Test
    fun `null departure port yields no nearby rectangles`() {
        val rectangles = listOf(StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"))
        assertTrue(GearStatRectangleSupport.nearbyRectanglesFor(null, rectangles).isEmpty())
    }

    private fun geometry(
        code: String,
        seaOverlapping: Boolean = true,
    ) = StatisticalSubRectangleGeometry(
        subCode = code,
        parentIcesName = "ICES-$code",
        rings = listOf(listOf(GeoPoint(50.0, 0.0), GeoPoint(50.0, 1.0), GeoPoint(51.0, 1.0), GeoPoint(51.0, 0.0))),
        bboxCentroid = GeoPoint(50.5, 0.5),
        boundingBox = GeoBoundingBox(50.0, 51.0, 0.0, 1.0),
        seaOverlapping = seaOverlapping,
    )

    @Test
    fun `nearbyGeometryFor joins nearby codes onto sea-overlapping geometry only`() {
        val dataset =
            MapGeometryDataset(
                landPolygons = emptyList(),
                subRectangles = listOf(geometry("38E95"), geometry("38E98")),
                ports = emptyList(),
            )
        val result = GearStatRectangleSupport.nearbyGeometryFor(listOf("38E95", "38E98"), dataset)
        assertEquals(listOf("38E95", "38E98"), result.map { it.subCode })
    }

    @Test
    fun `nearbyGeometryFor omits a nearby code with no matching geometry`() {
        // OD-2: a nearby reference-data code (e.g. "38F02") with no geometry in the loaded dataset is
        // simply excluded from the nearby map's selectable set — it remains reachable via "Other".
        val dataset =
            MapGeometryDataset(
                landPolygons = emptyList(),
                subRectangles = listOf(geometry("38E95")),
                ports = emptyList(),
            )
        val result = GearStatRectangleSupport.nearbyGeometryFor(listOf("38E95", "38F02"), dataset)
        assertEquals(listOf("38E95"), result.map { it.subCode })
    }

    @Test
    fun `nearbyGeometryFor excludes a landlocked nearby code even when present in the dataset`() {
        val dataset =
            MapGeometryDataset(
                landPolygons = emptyList(),
                subRectangles = listOf(geometry("38E95"), geometry("00LND", seaOverlapping = false)),
                ports = emptyList(),
            )
        val result = GearStatRectangleSupport.nearbyGeometryFor(listOf("38E95", "00LND"), dataset)
        assertEquals(listOf("38E95"), result.map { it.subCode })
    }
}
