package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.StubReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map.MapSupport

/** DTO → domain mapping, including the measurement code→key translation table (ADR 0016). */
class ReferenceDataMappersTests {
    @Test
    fun `vessel maps displayName to domain name`() {
        val dto = VesselMobileDto(id = "v1", name = "ACHILLES", displayName = "ACHILLES PH101")

        assertEquals("ACHILLES PH101", dto.toDomain().name)
        assertEquals("v1", dto.toDomain().id)
    }

    @Test
    fun `port with an unmapped code falls back to the unassigned sentinel`() {
        val dto = PortMobileDto(id = "p1", code = "GBAOT", name = "Abbotsbury", displayName = "Abbotsbury")

        val port = dto.toDomain()

        assertEquals("Abbotsbury", port.name)
        assertEquals(PortStatisticalAreaLookup.UNASSIGNED, port.statisticalAreaId)
    }

    @Test
    fun `a live Hastings port code resolves to the stub Hastings statistical area`() {
        val dto =
            PortMobileDto(
                id = "a4135a7b-069b-422e-be2e-932393ef6239",
                code = "GBHTG",
                name = "Hastings",
                displayName = "Hastings",
            )

        assertEquals("AREA-HASTINGS", dto.toDomain().statisticalAreaId)
    }

    @Test
    fun `a live Dover port code resolves to the stub Dover statistical area`() {
        val dto =
            PortMobileDto(
                id = "5baffc07-b927-4090-81f4-5c47811518d7",
                code = "GBDVR",
                name = "Dover",
                displayName = "Dover",
            )

        assertEquals("AREA-DOVER", dto.toDomain().statisticalAreaId)
    }

    @Test
    fun `a live Hastings port resolves to the same rectangles MapSupport shows for the stub Hastings port`() =
        runTest {
            val livePort =
                PortMobileDto(
                    id = "a4135a7b-069b-422e-be2e-932393ef6239",
                    code = "GBHTG",
                    name = "Hastings",
                    displayName = "Hastings",
                ).toDomain()
            val stubRectangles = StubReferenceDataRepository().getStatisticalSubRectangles().getOrThrow()

            val nearby = MapSupport.nearbyRectanglesFor(livePort, stubRectangles)

            assertTrue(nearby.isNotEmpty())
            assertTrue(nearby.all { it.statisticalAreaId == "AREA-HASTINGS" })
        }

    @Test
    fun `an unassigned port yields no nearby rectangles, not a crash`() =
        runTest {
            val unmappedPort =
                PortMobileDto(
                    id = "p2",
                    code = "GBAOT",
                    name = "Abbotsbury",
                    displayName = "Abbotsbury",
                ).toDomain()
            val stubRectangles = StubReferenceDataRepository().getStatisticalSubRectangles().getOrThrow()

            assertTrue(MapSupport.nearbyRectanglesFor(unmappedPort, stubRectangles).isEmpty())
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
