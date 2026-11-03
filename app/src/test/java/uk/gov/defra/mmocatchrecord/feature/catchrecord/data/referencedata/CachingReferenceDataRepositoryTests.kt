package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDatabase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataGearEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataMetadataEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataPortEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataVesselEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.GearCategoryDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.GearMeasurementDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.GearMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.GearsFetchPayload
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.PortMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.PortStatisticalAreaLookup
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataFetchResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.SpeciesMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.VesselMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.DATASET_PORTS
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.DATASET_SPECIES
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.DATASET_VESSELS
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.VesselLengthBands
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.WorkManagerReferenceDataSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.gearsDatasetKey
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import java.io.IOException
import java.util.concurrent.Executor

private const val FIXED_NOW_MILLIS = 10_000_000L

/** [CachingReferenceDataRepository]'s cache/stub precedence and vessel-length band selection (ADR 0016). */
@RunWith(RobolectricTestRunner::class)
class CachingReferenceDataRepositoryTests {
    private lateinit var database: CatchRecordDatabase
    private lateinit var dao: ReferenceDataDao
    private lateinit var remoteDataSource: ReferenceDataRemoteDataSource
    private lateinit var syncScheduler: WorkManagerReferenceDataSyncScheduler
    private lateinit var connectivityChecker: NetworkConnectivityChecker
    private lateinit var stub: ReferenceDataRepository
    private val languageFlow = MutableStateFlow("en")

    @Before
    fun setUp() {
        val immediateExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CatchRecordDatabase::class.java)
                .setQueryExecutor(immediateExecutor)
                .setTransactionExecutor(immediateExecutor)
                .allowMainThreadQueries()
                .build()
        dao = database.referenceDataDao()
        remoteDataSource = mock()
        syncScheduler = mock()
        connectivityChecker = mock()
        stub = mock()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a cache hit returns the cached rows without calling the network`() =
        runTest {
            dao.replaceVessels(listOf(vesselEntity("v1", "ACHILLES")))
            dao.upsertMetadata(metadata(DATASET_VESSELS, fetchedAtEpochMillis = FIXED_NOW_MILLIS))

            val result = repository().getVessels()

            assertEquals(listOf(Vessel("v1", "ACHILLES")), result.getOrThrow())
            verifyNoInteractions(remoteDataSource)
        }

    @Test
    fun `a cached vessel's pln is carried into the domain model`() =
        runTest {
            dao.replaceVessels(listOf(vesselEntity("v1", "ACHILLES").copy(pln = "PH101")))
            dao.upsertMetadata(metadata(DATASET_VESSELS, fetchedAtEpochMillis = FIXED_NOW_MILLIS))

            val vessel = repository().getVessels().getOrThrow().single()

            assertEquals("PH101", vessel.pln)
        }

    @Test
    fun `a stale cache is returned immediately and triggers a background refresh`() =
        runTest {
            dao.replaceVessels(listOf(vesselEntity("v1", "ACHILLES")))
            dao.upsertMetadata(metadata(DATASET_VESSELS, fetchedAtEpochMillis = 0L))

            val result = repository().getVessels()

            assertEquals(listOf(Vessel("v1", "ACHILLES")), result.getOrThrow())
            verifyNoInteractions(remoteDataSource)
            verify(syncScheduler).scheduleStartupRefresh()
        }

    @Test
    fun `a fresh cache does not trigger a background refresh`() =
        runTest {
            dao.replaceVessels(listOf(vesselEntity("v1", "ACHILLES")))
            dao.upsertMetadata(metadata(DATASET_VESSELS, fetchedAtEpochMillis = FIXED_NOW_MILLIS))

            repository().getVessels()

            verify(syncScheduler, never()).scheduleStartupRefresh()
        }

    @Test
    fun `an empty cache online fetches once inline, caches it, and returns it`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(true)
            whenever(remoteDataSource.fetchVessels()).thenReturn(
                ReferenceDataFetchResult.Modified(
                    items = listOf(VesselMobileDto(id = "v1", name = "ACHILLES", displayName = "ACHILLES PH101")),
                    collectionId = "col-vessels",
                    schemaVersion = "1.0",
                    datasetVersion = "v1",
                    responseETag = "\"sha256-a=\"",
                ),
            )

            val result = repository().getVessels()

            assertEquals(listOf(Vessel("v1", "ACHILLES PH101")), result.getOrThrow())
            assertEquals(1, dao.getVessels().size)
            assertEquals("\"sha256-a=\"", dao.getMetadata(DATASET_VESSELS)?.responseETag)
        }

    @Test
    fun `an empty cache offline falls back to the stub with no hang`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(false)
            whenever(stub.getVessels()).thenReturn(Result.success(listOf(Vessel("stub-v1", "STUB VESSEL"))))

            val result = repository().getVessels()

            assertEquals(listOf(Vessel("stub-v1", "STUB VESSEL")), result.getOrThrow())
            verifyNoInteractions(remoteDataSource)
        }

    @Test
    fun `an empty cache online whose fetch fails falls back to the stub`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(true)
            whenever(remoteDataSource.fetchVessels()).thenAnswer { throw IOException("boom") }
            whenever(stub.getVessels()).thenReturn(Result.success(listOf(Vessel("stub-v1", "STUB VESSEL"))))

            val result = repository().getVessels()

            assertEquals(listOf(Vessel("stub-v1", "STUB VESSEL")), result.getOrThrow())
            assertEquals(0, dao.getVessels().size)
        }

    @Test
    fun `a stale cache that would 401 on refresh still serves the cache unharmed`() =
        runTest {
            dao.replaceVessels(listOf(vesselEntity("v1", "ACHILLES")))
            dao.upsertMetadata(metadata(DATASET_VESSELS, fetchedAtEpochMillis = 0L))
            whenever(connectivityChecker.isConnected()).thenReturn(true)

            val result = repository().getVessels()

            assertTrue(result.isSuccess)
            assertEquals(1, dao.getVessels().size)
            verifyNoInteractions(remoteDataSource)
        }

    @Test
    fun `a vessel of 8_74 metres selects the under-10m gear band`() =
        runTest {
            assertBandSelected(8.74, VesselLengthBands.UNDER_10M)
        }

    @Test
    fun `a vessel of 11 metres selects the 10-to-12m gear band`() =
        runTest {
            assertBandSelected(11.0, VesselLengthBands.TEN_TO_12M)
        }

    @Test
    fun `a vessel of 15 metres selects the over-12m gear band`() =
        runTest {
            assertBandSelected(15.0, VesselLengthBands.OVER_12M)
        }

    @Test
    fun `an unknown vessel length selects the all gear band`() =
        runTest {
            assertBandSelected(null, VesselLengthBands.ALL)
        }

    private suspend fun assertBandSelected(
        vesselLengthMetres: Double?,
        expectedBand: String,
    ) {
        VesselLengthBands.cacheableVariants.forEach { (band, _) ->
            dao.replaceGearsForBand(band, listOf(gearEntity(band)), emptyList())
            dao.upsertMetadata(metadata(gearsDatasetKey(band), fetchedAtEpochMillis = FIXED_NOW_MILLIS))
        }

        val result = repository().getGearTypes(vesselLengthMetres)

        assertEquals(listOf(GearType(id = "gear-$expectedBand", name = "Gear $expectedBand")), result.getOrThrow())
    }

    @Test
    fun `a port resolved from the cache scopes sub-rectangles to its statistical area`() =
        runTest {
            dao.replacePorts(listOf(portEntity("live-port-hastings", "GBHTG", "AREA-HASTINGS")))
            whenever(stub.getStatisticalSubRectangles()).thenReturn(
                Result.success(
                    listOf(
                        statisticalSubRectangle("rect-1", "AREA-HASTINGS"),
                        statisticalSubRectangle("rect-2", "AREA-DOVER"),
                    ),
                ),
            )

            val result = repository().getStatisticalSubRectanglesForPort("live-port-hastings")

            assertEquals(listOf("rect-1"), result.getOrThrow().map { it.id })
        }

    @Test
    fun `an unresolvable port id for sub-rectangles succeeds with an empty list`() =
        runTest {
            dao.replacePorts(listOf(portEntity("live-port-hastings", "GBHTG", "AREA-HASTINGS")))
            whenever(stub.getStatisticalSubRectangles()).thenReturn(
                Result.success(listOf(statisticalSubRectangle("rect-1", "AREA-HASTINGS"))),
            )

            val result = repository().getStatisticalSubRectanglesForPort("unknown-port")

            assertEquals(emptyList<String>(), result.getOrThrow().map { it.id })
        }

    @Test
    fun `an empty port cache falls back to the stub ports to resolve sub-rectangles`() =
        runTest {
            val stubPort = Port(id = "stub-port-hastings", name = "Hastings", statisticalAreaId = "AREA-HASTINGS")
            whenever(stub.getPorts()).thenReturn(Result.success(listOf(stubPort)))
            whenever(stub.getStatisticalSubRectangles()).thenReturn(
                Result.success(listOf(statisticalSubRectangle("rect-1", "AREA-HASTINGS"))),
            )

            val result = repository().getStatisticalSubRectanglesForPort("stub-port-hastings")

            assertEquals(listOf("rect-1"), result.getOrThrow().map { it.id })
        }

    @Test
    fun `previously used ports for a live vessel id succeed with an empty list`() =
        runTest {
            whenever(stub.getPreviouslyUsedPorts("live-vessel-guid")).thenReturn(Result.success(emptyList()))

            val result = repository().getPreviouslyUsedPorts("live-vessel-guid")

            assertEquals(emptyList<Port>(), result.getOrThrow())
        }

    @Test
    fun `a live Hastings port GUID resolves to the real stub's Hastings rectangles`() =
        runTest {
            val hastingsId = "a4135a7b-069b-422e-be2e-932393ef6239"
            val hastingsAreaId = PortStatisticalAreaLookup.statisticalAreaId("GBHTG")
            dao.replacePorts(listOf(portEntity(hastingsId, "GBHTG", hastingsAreaId)))
            val repositoryWithRealStub =
                CachingReferenceDataRepository(
                    dao = dao,
                    remoteDataSource = remoteDataSource,
                    syncScheduler = syncScheduler,
                    connectivityChecker = connectivityChecker,
                    appLanguageRepository = FakeAppLanguageRepository(languageFlow),
                    stub = StubReferenceDataRepository(),
                    clock = { FIXED_NOW_MILLIS },
                )

            val rectangles = repositoryWithRealStub.getStatisticalSubRectanglesForPort(hastingsId).getOrThrow()

            val expectedCodes =
                setOf("38E95", "38E98", "38F02", "38E96", "38E99", "38F03", "37E97", "37F01", "37F02")
            assertEquals(expectedCodes, rectangles.map { it.code }.toSet())
        }

    @Test
    fun `a cache hit with no stored metadata is treated as stale and triggers a background refresh`() =
        runTest {
            dao.replaceVessels(listOf(vesselEntity("v1", "ACHILLES")))

            repository().getVessels()

            verify(syncScheduler).scheduleStartupRefresh()
        }

    @Test
    fun `an empty port cache online fetches inline, caches it, and serves later reads from the cache`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(true)
            whenever(remoteDataSource.fetchPorts()).thenReturn(
                modified(listOf(PortMobileDto(id = "p1", code = "GBHTG", name = "HASTINGS", displayName = "Hastings"))),
            )
            val expected = listOf(Port("p1", "Hastings", PortStatisticalAreaLookup.statisticalAreaId("GBHTG")))

            val fetched = repository().getPorts()
            val cached = repository().getPorts()

            assertEquals(expected, fetched.getOrThrow())
            assertEquals(expected, cached.getOrThrow())
            assertEquals("col-1", dao.getMetadata(DATASET_PORTS)?.collectionId)
            verify(remoteDataSource, times(1)).fetchPorts()
        }

    @Test
    fun `an empty port cache offline falls back to the stub with no hang`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(false)
            val stubPort = Port(id = "stub-port-hastings", name = "Hastings", statisticalAreaId = "AREA-HASTINGS")
            whenever(stub.getPorts()).thenReturn(Result.success(listOf(stubPort)))

            val result = repository().getPorts()

            assertEquals(listOf(stubPort), result.getOrThrow())
            verifyNoInteractions(remoteDataSource)
        }

    @Test
    fun `an empty species cache online fetches inline and records the current language`() =
        runTest {
            languageFlow.value = "cy"
            whenever(connectivityChecker.isConnected()).thenReturn(true)
            whenever(remoteDataSource.fetchSpecies()).thenReturn(
                modified(listOf(SpeciesMobileDto(id = "s1", faoCode = "COD", displayName = "Cod"))),
            )
            val expected = listOf(Species(id = "s1", name = "Cod", faoCode = "COD"))

            val fetched = repository().getSpecies()
            val cached = repository().getSpecies()

            assertEquals(expected, fetched.getOrThrow())
            assertEquals(expected, cached.getOrThrow())
            assertEquals("cy", dao.getMetadata(DATASET_SPECIES)?.languageTag)
            verify(remoteDataSource, times(1)).fetchSpecies()
        }

    @Test
    fun `an empty species cache offline falls back to the stub with no hang`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(false)
            val stubSpecies = Species(id = "stub-s1", name = "Stub Cod", faoCode = "COD")
            whenever(stub.getSpecies()).thenReturn(Result.success(listOf(stubSpecies)))

            val result = repository().getSpecies()

            assertEquals(listOf(stubSpecies), result.getOrThrow())
            verifyNoInteractions(remoteDataSource)
        }

    @Test
    fun `an empty gear cache online fetches the band inline and caches its measurement fields`() =
        runTest {
            val band = VesselLengthBands.bandFor(8.0)
            val representativeLength = VesselLengthBands.cacheableVariants.first { it.first == band }.second
            val measurement =
                GearMeasurementDto(id = "m1", code = "ME", label = "Mesh size", kind = "number", unit = "mm")
            val gear =
                GearMobileDto(
                    id = "g1",
                    code = "TBB",
                    name = "Beam trawl",
                    category = GearCategoryDto(id = "c1", code = "TRAWL_NETS", name = "Trawl nets"),
                    requiredMeasurementIds = listOf("m1"),
                )
            whenever(connectivityChecker.isConnected()).thenReturn(true)
            whenever(remoteDataSource.fetchGears(vesselLengthMetres = representativeLength)).thenReturn(
                modified(GearsFetchPayload(items = listOf(gear), measurementsById = mapOf("m1" to measurement))),
            )

            val fetched = repository().getGearTypes(8.0).getOrThrow()
            val cached = repository().getGearTypes(8.0).getOrThrow()

            assertEquals(listOf("g1"), fetched.map { it.id })
            assertEquals(1, fetched.single().measurementFields.size)
            assertEquals(fetched, cached)
            assertEquals("col-1", dao.getMetadata(gearsDatasetKey(band))?.collectionId)
            verify(remoteDataSource, times(1)).fetchGears(vesselLengthMetres = representativeLength)
        }

    @Test
    fun `an empty gear cache offline falls back to the stub with no hang`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(false)
            val stubGearType = GearType(id = "stub-gear-1", name = "Stub Gear")
            whenever(stub.getGearTypes(8.0)).thenReturn(Result.success(listOf(stubGearType)))

            val result = repository().getGearTypes(8.0)

            assertEquals(listOf(stubGearType), result.getOrThrow())
            verifyNoInteractions(remoteDataSource)
        }

    @Test
    fun `an inline fetch answered with an unexpected 304 falls back to the stub`() =
        runTest {
            whenever(connectivityChecker.isConnected()).thenReturn(true)
            whenever(remoteDataSource.fetchVessels()).thenReturn(ReferenceDataFetchResult.NotModified)
            whenever(stub.getVessels()).thenReturn(Result.success(listOf(Vessel("stub-v1", "STUB VESSEL"))))

            val result = repository().getVessels()

            assertEquals(listOf(Vessel("stub-v1", "STUB VESSEL")), result.getOrThrow())
            assertEquals(0, dao.getVessels().size)
        }

    @Test
    fun `statistical sub-rectangles are served from the stub`() =
        runTest {
            val rectangles = listOf(statisticalSubRectangle("rect-1", "AREA-HASTINGS"))
            whenever(stub.getStatisticalSubRectangles()).thenReturn(Result.success(rectangles))

            val result = repository().getStatisticalSubRectangles()

            assertEquals(rectangles, result.getOrThrow())
        }

    @Test
    fun `failing stub ports and rectangles resolve sub-rectangles for a port to an empty list`() =
        runTest {
            whenever(stub.getPorts()).thenReturn(Result.failure(IOException("boom")))
            whenever(stub.getStatisticalSubRectangles()).thenReturn(Result.failure(IOException("boom")))

            val result = repository().getStatisticalSubRectanglesForPort("any-port")

            assertEquals(emptyList<StatisticalSubRectangle>(), result.getOrThrow())
        }

    private fun <T> modified(items: T) =
        ReferenceDataFetchResult.Modified(
            items = items,
            collectionId = "col-1",
            schemaVersion = "1.0",
            datasetVersion = "v1",
            responseETag = null,
        )

    private fun repository(): CachingReferenceDataRepository =
        CachingReferenceDataRepository(
            dao = dao,
            remoteDataSource = remoteDataSource,
            syncScheduler = syncScheduler,
            connectivityChecker = connectivityChecker,
            appLanguageRepository = FakeAppLanguageRepository(languageFlow),
            stub = stub,
            clock = { FIXED_NOW_MILLIS },
        )

    private fun vesselEntity(
        id: String,
        displayName: String,
    ) = ReferenceDataVesselEntity(
        id = id,
        name = displayName,
        displayName = displayName,
        pln = null,
        cfr = null,
        lengthOverallMetres = null,
    )

    private fun portEntity(
        id: String,
        code: String,
        statisticalAreaId: String,
    ) = ReferenceDataPortEntity(
        id = id,
        code = code,
        name = code,
        displayName = code,
        latitude = null,
        longitude = null,
        statisticalAreaId = statisticalAreaId,
    )

    private fun statisticalSubRectangle(
        id: String,
        statisticalAreaId: String,
    ) = StatisticalSubRectangle(id = id, code = id, statisticalAreaId = statisticalAreaId)

    private fun gearEntity(band: String) =
        ReferenceDataGearEntity(
            id = "gear-$band",
            vesselLengthBand = band,
            code = "CODE-$band",
            name = "Gear $band",
            categoryId = "cat-1",
            categoryCode = "CAT",
            categoryName = "Category",
            pairFishing = false,
            orderIndex = 0,
        )

    private fun metadata(
        datasetKey: String,
        fetchedAtEpochMillis: Long,
    ) = ReferenceDataMetadataEntity(
        datasetKey = datasetKey,
        collectionId = "col-1",
        schemaVersion = "1.0",
        manifestVersion = null,
        manifestEtag = null,
        responseETag = null,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        languageTag = null,
    )
}

private class FakeAppLanguageRepository(
    private val flow: MutableStateFlow<String>,
) : AppLanguageRepository {
    override val language = flow

    override suspend fun setLanguage(language: String) {
        flow.value = language
    }
}
