package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
