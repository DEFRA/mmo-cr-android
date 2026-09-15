package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys

class StubReferenceDataRepositoryTests {
    private val repository = StubReferenceDataRepository()

    @Test
    fun `vessels include the confirmed Stage-1 screenshot vessels`() =
        runTest {
            val vessels = repository.getVessels().getOrThrow()
            assertTrue(vessels.any { it.name == "ACHILLES" })
            assertTrue(vessels.any { it.name == "HERCULES" })
        }

    @Test
    fun `ports include the confirmed Stage-1 screenshot ports`() =
        runTest {
            val ports = repository.getPorts().getOrThrow()
            assertTrue(ports.any { it.name == "Hastings" })
            assertTrue(ports.any { it.name == "Dover" })
        }

    @Test
    fun `achilles has previously used ports and hercules exercises first time search`() =
        runTest {
            val achillesPorts = repository.getPreviouslyUsedPorts("vessel-achilles").getOrThrow()
            val herculesPorts = repository.getPreviouslyUsedPorts("vessel-hercules").getOrThrow()
            assertEquals(listOf("Hastings", "Dover"), achillesPorts.map { it.name })
            assertTrue(herculesPorts.isEmpty())
        }

    @Test
    fun `gear types are non-empty`() =
        runTest {
            assertTrue(repository.getGearTypes().getOrThrow().isNotEmpty())
        }

    @Test
    fun `seine nets gear type has a single mesh size measurement field`() =
        runTest {
            val gearTypes = repository.getGearTypes().getOrThrow()
            val seineNets = gearTypes.first { it.id == "gear-seine-nets" }
            assertEquals("Seine nets (not specified)", seineNets.name)
            assertEquals(
                listOf(GearMeasurementFieldKeys.MESH_SIZE_MM),
                seineNets.measurementFields.map { it.key },
            )
        }

    @Test
    fun `bottom otter trawls gear type has trawl net count then mesh size measurement fields`() =
        runTest {
            val gearTypes = repository.getGearTypes().getOrThrow()
            val bottomOtterTrawls = gearTypes.first { it.id == "gear-bottom-otter-trawls-tb" }
            assertEquals("Bottom otter trawls (TB)", bottomOtterTrawls.name)
            assertEquals(
                listOf(GearMeasurementFieldKeys.NUMBER_OF_TRAWL_NETS, GearMeasurementFieldKeys.MESH_SIZE_MM),
                bottomOtterTrawls.measurementFields.map { it.key },
            )
        }

    @Test
    fun `gear types confirmed by name only have no measurement fields yet`() =
        runTest {
            val gearTypes = repository.getGearTypes().getOrThrow()
            val namesConfirmedFieldsPending = listOf("gear-beam-trawls-tbb", "gear-bottom-pair-trawls-ptb")
            namesConfirmedFieldsPending.forEach { id ->
                assertTrue(gearTypes.first { it.id == id }.measurementFields.isEmpty())
            }
        }

    @Test
    fun `species are non-empty`() =
        runTest {
            assertTrue(repository.getSpecies().getOrThrow().isNotEmpty())
        }

    @Test
    fun `statistical sub-rectangles are non-empty`() =
        runTest {
            assertTrue(repository.getStatisticalSubRectangles().getOrThrow().isNotEmpty())
        }

    @Test
    fun `statistical sub-rectangles for a known port are scoped to its statistical area`() =
        runTest {
            val hastingsPort = repository.getPorts().getOrThrow().first { it.name == "Hastings" }
            val rectangles = repository.getStatisticalSubRectanglesForPort(hastingsPort.id).getOrThrow()
            assertTrue(rectangles.isNotEmpty())
            assertTrue(rectangles.all { it.statisticalAreaId == hastingsPort.statisticalAreaId })
        }

    @Test
    fun `statistical sub-rectangles for an unknown port fails`() =
        runTest {
            val result = repository.getStatisticalSubRectanglesForPort("unknown-port")
            assertTrue(result.isFailure)
        }

    @Test
    fun `port to statistical area mapping is one area per port`() =
        runTest {
            val ports = repository.getPorts().getOrThrow()
            ports.forEach { port -> assertEquals(1, setOf(port.statisticalAreaId).size) }
        }
}
