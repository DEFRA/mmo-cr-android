package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [ReferenceDataDao]'s per-dataset replace-all, band scoping, and metadata upsert (ADR 0015). */
@RunWith(RobolectricTestRunner::class)
class ReferenceDataDaoTests {
    private lateinit var database: CatchRecordDatabase
    private lateinit var dao: ReferenceDataDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CatchRecordDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.referenceDataDao()
    }

    @Test
    fun `replaceVessels deletes the previous set and inserts the new one`() =
        runTest {
            dao.replaceVessels(listOf(vessel("v1"), vessel("v2")))
            dao.replaceVessels(listOf(vessel("v3")))

            val vessels = dao.getVessels()

            assertEquals(1, vessels.size)
            assertEquals("v3", vessels.single().id)
        }

    @Test
    fun `replacePorts preserves a null coordinate`() =
        runTest {
            dao.replacePorts(listOf(port("p1", latitude = null, longitude = null)))

            val port = dao.getPorts().single()

            assertNull(port.latitude)
            assertNull(port.longitude)
        }

    @Test
    fun `replaceSpecies deletes the previous set and inserts the new one`() =
        runTest {
            dao.replaceSpecies(listOf(species("s1")))
            dao.replaceSpecies(listOf(species("s2")))

            assertEquals(listOf("s2"), dao.getSpecies().map { it.id })
        }

    @Test
    fun `replaceGearsForBand only replaces its own band, not other bands`() =
        runTest {
            dao.replaceGearsForBand("under-10m", listOf(gear("g1", "under-10m")), emptyList())
            dao.replaceGearsForBand("over-12m", listOf(gear("g1", "over-12m")), emptyList())
            dao.replaceGearsForBand("under-10m", listOf(gear("g2", "under-10m")), emptyList())

            assertEquals(listOf("g2"), dao.getGearsForBand("under-10m").map { it.id })
            assertEquals(listOf("g1"), dao.getGearsForBand("over-12m").map { it.id })
        }

    @Test
    fun `replacing a band's gears cascades delete to that band's measurements`() =
        runTest {
            dao.replaceGearsForBand(
                "under-10m",
                listOf(gear("g1", "under-10m")),
                listOf(measurement("g1", "under-10m")),
            )

            dao.replaceGearsForBand("under-10m", listOf(gear("g2", "under-10m")), emptyList())

            assertEquals(0, dao.getGearMeasurementsForBand("under-10m").size)
        }

    @Test
    fun `metadata upserts by datasetKey and is readable back`() =
        runTest {
            dao.upsertMetadata(metadata("vessels", responseETag = "\"etag-1\""))
            dao.upsertMetadata(metadata("vessels", responseETag = "\"etag-2\""))

            val stored = dao.getMetadata("vessels")

            assertEquals("\"etag-2\"", stored?.responseETag)
        }

    @Test
    fun `metadata for an unknown dataset key reads back null`() =
        runTest {
            assertNull(dao.getMetadata("gears:under-10m"))
        }

    private fun vessel(id: String) =
        ReferenceDataVesselEntity(
            id = id,
            name = id,
            displayName = id,
            pln = null,
            cfr = null,
            lengthOverallMetres = null,
        )

    private fun port(
        id: String,
        latitude: Double?,
        longitude: Double?,
    ) = ReferenceDataPortEntity(
        id = id,
        code = id,
        name = id,
        displayName = id,
        latitude = latitude,
        longitude = longitude,
        statisticalAreaId = "AREA-UNASSIGNED",
    )

    private fun species(id: String) =
        ReferenceDataSpeciesEntity(id = id, faoCode = id, scientificName = null, displayName = id)

    private fun gear(
        id: String,
        band: String,
    ) = ReferenceDataGearEntity(
        id = id,
        vesselLengthBand = band,
        code = id,
        name = id,
        categoryId = "c1",
        categoryCode = "TRAWL_NETS",
        categoryName = "Trawl nets",
        pairFishing = false,
        orderIndex = 0,
    )

    private fun measurement(
        gearId: String,
        band: String,
    ) = ReferenceDataGearMeasurementEntity(
        gearId = gearId,
        vesselLengthBand = band,
        key = "mesh_size_mm",
        label = "Mesh size",
        type = "Decimal",
        unit = "mm",
        orderIndex = 0,
    )

    private fun metadata(
        datasetKey: String,
        responseETag: String?,
    ) = ReferenceDataMetadataEntity(
        datasetKey = datasetKey,
        collectionId = "col-1",
        schemaVersion = "1.0",
        manifestVersion = "v1",
        manifestEtag = "manifest-etag",
        responseETag = responseETag,
        fetchedAtEpochMillis = 1_000L,
    )
}
