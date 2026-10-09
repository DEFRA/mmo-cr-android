package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType

/** DTO → domain mapping, including the measurement code→key translation table (ADR 0015). */
class ReferenceDataMappersTests {
    @Test
    fun `vessel maps displayName to domain name`() {
        val dto = VesselMobileDto(id = "v1", name = "ACHILLES", displayName = "ACHILLES PH101")

        assertEquals("ACHILLES PH101", dto.toDomain().name)
        assertEquals("v1", dto.toDomain().id)
    }

    @Test
    fun `port maps displayName and a known code to its statistical area`() {
        val dto = PortMobileDto(id = "p1", code = "GBAOT", name = "Abbotsbury", displayName = "Abbotsbury")

        val port = dto.toDomain()

        assertEquals("Abbotsbury", port.name)
        assertEquals(PortStatisticalAreaLookup.UNASSIGNED, port.statisticalAreaId)
    }

    @Test
    fun `species maps displayName and faoCode, keeping domain default quota precision`() {
        val dto = SpeciesMobileDto(id = "s1", faoCode = "STU", displayName = "Sturgeons nei")

        val species = dto.toDomain()

        assertEquals("Sturgeons nei", species.name)
        assertEquals("STU", species.faoCode)
        assertNull(species.monthlyQuotaKg)
        assertNull(species.annualQuotaKg)
    }

    @Test
    fun `ME maps onto the pre-existing mesh-size key as Decimal`() {
        assertEquals(GearMeasurementFieldKeys.MESH_SIZE_MM, gearMeasurementKeyFor("ME"))
        assertEquals(GearMeasurementFieldType.Decimal, gearMeasurementTypeFor("mm"))
    }

    @Test
    fun `every other live code maps to a new deterministic code-derived key`() {
        val otherCodes = listOf("GM", "GN", "MT", "QG", "HE", "NL", "NN", "NI")

        otherCodes.forEach { code ->
            assertEquals("api_gear_measurement_${code.lowercase()}", gearMeasurementKeyFor(code))
        }
    }

    @Test
    fun `an unseen future code still maps deterministically`() {
        assertEquals("api_gear_measurement_zz", gearMeasurementKeyFor("ZZ"))
    }

    @Test
    fun `count unit derives Integer type with no displayable unit`() {
        assertEquals(GearMeasurementFieldType.Integer, gearMeasurementTypeFor("count"))
        assertNull(gearMeasurementUnitFor("count"))
    }

    @Test
    fun `metres and millimetres derive Decimal type and keep their unit`() {
        assertEquals(GearMeasurementFieldType.Decimal, gearMeasurementTypeFor("m"))
        assertEquals("m", gearMeasurementUnitFor("m"))
        assertEquals(GearMeasurementFieldType.Decimal, gearMeasurementTypeFor("mm"))
        assertEquals("mm", gearMeasurementUnitFor("mm"))
    }

    @Test
    fun `unknown or missing unit defaults to Decimal with no displayable unit`() {
        assertEquals(GearMeasurementFieldType.Decimal, gearMeasurementTypeFor(null))
        assertNull(gearMeasurementUnitFor(null))
        assertEquals(GearMeasurementFieldType.Decimal, gearMeasurementTypeFor("fathoms"))
        assertNull(gearMeasurementUnitFor("fathoms"))
    }

    @Test
    fun `gear resolves required-then-variable fields from the same band's measurement catalogue`() {
        val meshMeasurement =
            GearMeasurementDto(id = "mid-1", code = "ME", label = "Mesh size", kind = "number", unit = "mm")
        val gnMeasurement =
            GearMeasurementDto(id = "mid-2", code = "GN", label = "Gear number", kind = "number", unit = "count")
        val gear =
            GearMobileDto(
                id = "g1",
                code = "TBB",
                name = "Beam trawl",
                category = GearCategoryDto(id = "c1", code = "TRAWL_NETS", name = "Trawl nets"),
                requiredMeasurementIds = listOf("mid-1"),
                variableMeasurementIds = listOf("mid-2"),
            )

        val gearType = gear.toDomain(mapOf("mid-1" to meshMeasurement, "mid-2" to gnMeasurement))

        assertEquals(2, gearType.measurementFields.size)
        assertEquals(GearMeasurementFieldKeys.MESH_SIZE_MM, gearType.measurementFields[0].key)
        assertEquals("api_gear_measurement_gn", gearType.measurementFields[1].key)
    }

    @Test
    fun `a measurement id absent from this band's catalogue is skipped`() {
        val gear =
            GearMobileDto(
                id = "g1",
                code = "TBB",
                name = "Beam trawl",
                category = GearCategoryDto(id = "c1", code = "TRAWL_NETS", name = "Trawl nets"),
                variableMeasurementIds = listOf("not-in-this-band"),
            )

        val gearType = gear.toDomain(emptyMap())

        assertEquals(0, gearType.measurementFields.size)
    }
}
