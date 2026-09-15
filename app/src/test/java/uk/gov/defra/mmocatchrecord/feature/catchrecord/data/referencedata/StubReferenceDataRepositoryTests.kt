package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType

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
            val namesConfirmedFieldsPending =
                listOf(
                    "gear-beam-trawls-tbb",
                    "gear-bottom-pair-trawls-ptb",
                    "gear-diving",
                    "gear-dredge",
                    "gear-nets-gillnets-trammels",
                    "gear-trawls-not-specified",
                )
            namesConfirmedFieldsPending.forEach { id ->
                assertTrue(gearTypes.first { it.id == id }.measurementFields.isEmpty())
            }
        }

    @Test
    fun `pots and traps gear types share the same two-field whole-number schema`() =
        runTest {
            val gearTypes = repository.getGearTypes().getOrThrow()
            val pots = gearTypes.first { it.id == "gear-pots" }
            val traps = gearTypes.first { it.id == "gear-traps" }
            assertEquals("Pots", pots.name)
            assertEquals("Traps", traps.name)
            val expectedKeys =
                listOf(
                    GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_HAULED,
                    GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_LEFT_IN_WATER,
                )
            assertEquals(expectedKeys, pots.measurementFields.map { it.key })
            assertEquals(expectedKeys, traps.measurementFields.map { it.key })
            assertTrue(pots.measurementFields.all { it.type == GearMeasurementFieldType.Integer })
            assertTrue(traps.measurementFields.all { it.type == GearMeasurementFieldType.Integer })
            // Pots and Traps are distinct, separately selectable gear types, not merged into one entry.
            assertTrue(pots.id != traps.id)
        }

    @Test
    fun `handlines gear type has a single rods and lines field and a shorter title override`() =
        runTest {
            val gearTypes = repository.getGearTypes().getOrThrow()
            val handlines = gearTypes.first { it.id == "gear-handlines" }
            assertEquals("Handlines and pole lines (hand operated)", handlines.name)
            assertEquals(
                listOf(GearMeasurementFieldKeys.NUMBER_OF_RODS_AND_LINES),
                handlines.measurementFields.map { it.key },
            )
            assertEquals("handlines", handlines.measurementTitleOverride)
        }

    @Test
    fun `drifting longlines gear type has hooks hauled then hooks left in water fields`() =
        runTest {
            val gearTypes = repository.getGearTypes().getOrThrow()
            val driftingLonglines = gearTypes.first { it.id == "gear-drifting-longlines" }
            assertEquals("Drifting longlines", driftingLonglines.name)
            assertEquals(
                listOf(GearMeasurementFieldKeys.TOTAL_HOOKS_HAULED, GearMeasurementFieldKeys.TOTAL_HOOKS_LEFT_IN_WATER),
                driftingLonglines.measurementFields.map { it.key },
            )
            assertTrue(driftingLonglines.measurementFields.all { it.type == GearMeasurementFieldType.Integer })
        }

    @Test
    fun `gillnets circling gear type has mesh size then net length fields, all whole numbers`() =
        runTest {
            val gearTypes = repository.getGearTypes().getOrThrow()
            val gillnets = gearTypes.first { it.id == "gear-gillnets-circling" }
            assertEquals("Gillnets (circling)", gillnets.name)
            assertEquals(
                listOf(
                    GearMeasurementFieldKeys.MESH_SIZE_MM,
                    GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_HAULED_M,
                    GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_LEFT_IN_WATER_M,
                ),
                gillnets.measurementFields.map { it.key },
            )
            assertTrue(gillnets.measurementFields.all { it.type == GearMeasurementFieldType.Integer })
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
