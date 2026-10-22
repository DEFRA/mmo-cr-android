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
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePort
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

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
    fun `nearbyRectanglesFromDataset returns the closest sea-overlapping codes ranked by distance`() {
        val port = Port("port-hastings", "Hastings", "AREA-HASTINGS")
        val dataset =
            datasetFixture(
                ports = listOf(datasetPort("Hastings", lon = 0.5, lat = 50.86)),
                rectangles =
                    listOf(
                        datasetRectangle("NEAR", lon = 0.6, lat = 50.9),
                        datasetRectangle("FAR", lon = 10.0, lat = 60.0),
                        datasetRectangle("INLAND", lon = 0.55, lat = 50.88, isSeaOverlapping = false),
                    ),
            )
        val nearby = MapSupport.nearbyRectanglesFromDataset(port, dataset)
        assertEquals(listOf("NEAR", "FAR"), nearby.map { it.code })
    }

    @Test
    fun `nearbyRectanglesFromDataset is empty when the departure port can't be matched by name`() {
        val port = Port("port-unknown", "Unknown Port", "AREA-X")
        val dataset = datasetFixture(ports = listOf(datasetPort("Hastings", lon = 0.5, lat = 50.86)))
        assertTrue(MapSupport.nearbyRectanglesFromDataset(port, dataset).isEmpty())
    }

    @Test
    fun `nearbyRectanglesFromDataset is empty for a null departure port`() {
        val dataset = datasetFixture(ports = listOf(datasetPort("Hastings", lon = 0.5, lat = 50.86)))
        assertTrue(MapSupport.nearbyRectanglesFromDataset(null, dataset).isEmpty())
    }

    @Test
    fun `allRectanglesFromDataset returns only sea-overlapping codes`() {
        val dataset =
            datasetFixture(
                rectangles =
                    listOf(
                        datasetRectangle("38E95", lon = 0.6, lat = 50.9),
                        datasetRectangle("INLAND", lon = 0.55, lat = 50.88, isSeaOverlapping = false),
                    ),
            )
        assertEquals(listOf("38E95"), MapSupport.allRectanglesFromDataset(dataset).map { it.code })
    }

    @Test
    fun `allRectanglesFromDataset populates centroid coordinates and ICES rectangle name`() {
        val dataset = datasetFixture(rectangles = listOf(datasetRectangle("38E95", lon = 0.6, lat = 50.9)))
        val rectangle = MapSupport.allRectanglesFromDataset(dataset).single()
        assertEquals(50.9, rectangle.centroidLatitude)
        assertEquals(0.6, rectangle.centroidLongitude)
        assertEquals("ICES-38E95", rectangle.icesRectangleName)
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

    @Test
    fun `withStatRectangleCode also sets the centre coordinates when provided`() {
        val gearUse = GearUse(id = "gear-use-1", gearTypeId = seineNets.id, statisticalSubRectangleCode = null)
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
            )
        val updated = MapSupport.withStatRectangleCode(draft, gearUse, "38E95", latitude = 50.9, longitude = 0.6)
        val updatedGearUse = updated.gearUses.single()
        assertEquals(50.9, updatedGearUse.statisticalSubRectangleLatitude)
        assertEquals(0.6, updatedGearUse.statisticalSubRectangleLongitude)
    }

    @Test
    fun `withStatRectangleCode defaults coordinates to null when not provided`() {
        val gearUse = GearUse(id = "gear-use-1", gearTypeId = seineNets.id, statisticalSubRectangleCode = null)
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
            )
        val updated = MapSupport.withStatRectangleCode(draft, gearUse, "38E95")
        val updatedGearUse = updated.gearUses.single()
        assertEquals(null, updatedGearUse.statisticalSubRectangleLatitude)
        assertEquals(null, updatedGearUse.statisticalSubRectangleLongitude)
    }

    @Test
    fun `rectangleForCode finds a case-insensitive match in all before nearby`() {
        val options =
            StatRectangleOptions(
                nearby = listOf(StatisticalSubRectangle("rect-nearby", "38E95", "AREA-HASTINGS")),
                all =
                    listOf(
                        StatisticalSubRectangle(
                            id = "rect-all",
                            code = "38E95",
                            statisticalAreaId = "",
                            centroidLatitude = 50.9,
                            centroidLongitude = 0.6,
                        ),
                    ),
            )
        val resolved = MapSupport.rectangleForCode("38e95", options)
        assertEquals("rect-all", resolved?.id)
        assertEquals(50.9, resolved?.centroidLatitude)
    }

    @Test
    fun `rectangleForCode falls back to nearby when not found in all`() {
        val options =
            StatRectangleOptions(
                nearby = listOf(StatisticalSubRectangle("rect-nearby", "30F10", "AREA-DOVER")),
                all = emptyList(),
            )
        assertEquals("rect-nearby", MapSupport.rectangleForCode("30F10", options)?.id)
    }

    @Test
    fun `rectangleForCode returns null when the code matches nothing`() {
        val options = StatRectangleOptions(nearby = emptyList(), all = emptyList())
        assertEquals(null, MapSupport.rectangleForCode("99Z99", options))
    }

    // --- Fixtures for the dataset-backed nearby/all rectangle functions ---------------------------------

    private fun datasetFixture(
        ports: List<SerializablePort> = emptyList(),
        rectangles: List<SerializableSubRectangle> = emptyList(),
    ) = MapDataset(
        formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
        land = emptyList(),
        subRectangles = rectangles,
        ports = ports,
    )

    private fun datasetPort(
        name: String,
        lon: Double,
        lat: Double,
    ) = SerializablePort(portCode = name, name = name, point = SerializablePoint(lon, lat))

    private fun datasetRectangle(
        code: String,
        lon: Double,
        lat: Double,
        isSeaOverlapping: Boolean = true,
    ) = SerializableSubRectangle(
        code = code,
        icesName = "ICES-$code",
        areaKm2 = 1.0,
        bbox = SerializableBBox(minLon = lon, minLat = lat, maxLon = lon, maxLat = lat),
        centroid = SerializablePoint(lon, lat),
        geometry = SerializableMultiPolygon(emptyList()),
        isSeaOverlapping = isSeaOverlapping,
    )
}
