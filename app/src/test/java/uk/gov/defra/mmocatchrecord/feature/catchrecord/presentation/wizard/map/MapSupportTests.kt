package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle

class MapSupportTests {
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
        val name = MapSupport.gearNameWithIdentifyingMeasurementFor(seineNets, gearUse)
        assertEquals("seine nets (mesh size 100mm)", name)
    }

    @Test
    fun `gear with no identifying measurement omits the parenthetical entirely`() {
        val gearUse = GearUse(id = "gear-use-2", gearTypeId = handlines.id, statisticalSubRectangleCode = null)
        val name = MapSupport.gearNameWithIdentifyingMeasurementFor(handlines, gearUse)
        assertEquals("handlines", name)
    }

    @Test
    fun `unknown gear type falls back to the raw gear type id`() {
        val gearUse = GearUse(id = "gear-use-3", gearTypeId = "gear-unknown", statisticalSubRectangleCode = null)
        val name = MapSupport.gearNameWithIdentifyingMeasurementFor(null, gearUse)
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
        val nearby = MapSupport.nearbyRectanglesFor(port, rectangles)
        assertEquals(listOf("38E95"), nearby.map { it.code })
    }

    @Test
    fun `null departure port yields no nearby rectangles`() {
        val rectangles = listOf(StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"))
        assertTrue(MapSupport.nearbyRectanglesFor(null, rectangles).isEmpty())
    }

    @Test
    fun `currentGearUseFor resolves the matching gear use on the edit path`() {
        val gearUse = GearUse(id = "gear-use-1", gearTypeId = seineNets.id, statisticalSubRectangleCode = null)
        val otherGearUse = GearUse(id = "gear-use-2", gearTypeId = handlines.id, statisticalSubRectangleCode = null)
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse, otherGearUse),
                modifiedAtEpochMillis = 0L,
            )
        val resolved = MapSupport.currentGearUseFor(draft, editGearUseId = "gear-use-2")
        assertEquals("gear-use-2", resolved?.id)
    }

    @Test
    fun `currentGearUseFor returns null when editGearUseId does not match any gear use`() {
        val gearUse = GearUse(id = "gear-use-1", gearTypeId = seineNets.id, statisticalSubRectangleCode = null)
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
            )
        assertEquals(null, MapSupport.currentGearUseFor(draft, editGearUseId = "no-such-gear-use"))
    }

    @Test
    fun `currentGearUseFor falls back to the next confirmed gear use pending a code on the add path`() {
        val confirmedPending =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNets.id,
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(confirmedPending),
                modifiedAtEpochMillis = 0L,
            )
        val resolved = MapSupport.currentGearUseFor(draft, editGearUseId = null)
        assertEquals("gear-use-1", resolved?.id)
    }

    @Test
    fun `currentGearUseFor returns null for a null draft`() {
        assertEquals(null, MapSupport.currentGearUseFor(null, editGearUseId = null))
    }

    @Test
    fun `withStatRectangleCode sets the code on only the matching gear use`() {
        val gearUse = GearUse(id = "gear-use-1", gearTypeId = seineNets.id, statisticalSubRectangleCode = null)
        val otherGearUse = GearUse(id = "gear-use-2", gearTypeId = handlines.id, statisticalSubRectangleCode = "30F10")
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse, otherGearUse),
                modifiedAtEpochMillis = 0L,
            )
        val updated = MapSupport.withStatRectangleCode(draft, gearUse, "38E95")
        assertEquals("38E95", updated.gearUses.first { it.id == "gear-use-1" }.statisticalSubRectangleCode)
        assertEquals("30F10", updated.gearUses.first { it.id == "gear-use-2" }.statisticalSubRectangleCode)
    }
}
