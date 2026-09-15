package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

class GearMeasurementSupportTests {
    @Test
    fun `display name strips the parenthetical reference code suffix`() {
        val gearType = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
        assertEquals("Seine nets", GearMeasurementSupport.displayNameFor(gearType))
    }

    @Test
    fun `display name for bottom otter trawls strips the TB code`() {
        val gearType = GearType(id = "gear-bottom-otter-trawls-tb", name = "Bottom otter trawls (TB)")
        assertEquals("Bottom otter trawls", GearMeasurementSupport.displayNameFor(gearType))
    }

    @Test
    fun `display name with no parenthetical suffix is returned unchanged`() {
        val gearType = GearType(id = "gear-x", name = "Handlines")
        assertEquals("Handlines", GearMeasurementSupport.displayNameFor(gearType))
    }

    @Test
    fun `title gear name is lowercased`() {
        val gearType = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
        assertEquals("seine nets", GearMeasurementSupport.titleGearNameFor(gearType))
    }

    @Test
    fun `title gear name uses the measurement title override when present`() {
        val gearType =
            GearType(
                id = "gear-handlines",
                name = "Handlines and pole lines (hand operated)",
                measurementTitleOverride = "handlines",
            )
        assertEquals("handlines", GearMeasurementSupport.titleGearNameFor(gearType))
    }

    @Test
    fun `title gear name falls back to the derived display name when no override is set`() {
        val gearType = GearType(id = "gear-drifting-longlines", name = "Drifting longlines")
        assertEquals("drifting longlines", GearMeasurementSupport.titleGearNameFor(gearType))
    }

    @Test
    fun `measurement summary derives mesh size mm text`() {
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statRectangleId = null,
                measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            )
        assertEquals("100mm mesh", GearMeasurementSupport.measurementSummaryFor(gearUse))
    }

    @Test
    fun `measurement summary formats a non-whole mesh size without trailing zero`() {
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statRectangleId = null,
                measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(12.5, "mm")),
            )
        assertEquals("12.5mm mesh", GearMeasurementSupport.measurementSummaryFor(gearUse))
    }

    @Test
    fun `measurement summary is null when there is no mesh size measurement`() {
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-bottom-pair-trawls-ptb",
                statRectangleId = null,
            )
        assertNull(GearMeasurementSupport.measurementSummaryFor(gearUse))
    }

    @Test
    fun `format number strips a trailing decimal zero`() {
        assertEquals("100", GearMeasurementSupport.formatNumber(100.0))
    }

    @Test
    fun `format number preserves a genuine decimal value`() {
        assertEquals("12.5", GearMeasurementSupport.formatNumber(12.5))
    }
}
