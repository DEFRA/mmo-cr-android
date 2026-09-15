package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
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
}
