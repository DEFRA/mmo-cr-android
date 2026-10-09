package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDatabase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataMetadataEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataVesselEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataApi
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataSchemaVersionException

private fun datasetEntry(
    dataset: String,
    version: String,
    etag: String,
): String =
    """{"dataset":"$dataset","collectionId":"col-$dataset","schemaVersion":"1.0","version":"$version",""" +
        """"etag":"$etag","url":"/$dataset","format":"json","itemCount":1}"""

private fun manifestBody(vararg datasetEntries: String): String =
    """{"manifestId":"m-1","version":"2026-01-01T00:00:00Z","datasets":[${datasetEntries.joinToString(",")}]}"""

private fun vesselsBody(schemaVersion: String = "1.0"): String =
    """{"dataset":"vessels","collectionId":"col-vessels","schemaVersion":"$schemaVersion","version":"v2",""" +
        """"view":"mobile","total":1,"items":[{"id":"v1","name":"ACHILLES","displayName":"ACHILLES PH101"}]}"""

private fun portsBody(): String =
    """{"dataset":"ports","collectionId":"col-ports","schemaVersion":"1.0","version":"p2",""" +
        """"view":"mobile","total":1,"items":[{"id":"port-1","code":"GBHTG","name":"Hastings",""" +
        """"displayName":"Hastings"}]}"""

private fun speciesBody(): String =
    """{"dataset":"species","collectionId":"col-species","schemaVersion":"1.0","version":"s2",""" +
        """"view":"mobile","total":1,"items":[{"id":"sp1","faoCode":"COD","displayName":"Cod"}]}"""

private fun gearsBody(): String =
    """{"dataset":"gears","collectionId":"col-gears","schemaVersion":"1.0","version":"g2","view":"mobile",""" +
        """"total":1,"measurements":[{"id":"mid-1","code":"ME","label":"Mesh size","kind":"number",""" +
        """"unit":"mm"}],"items":[{"id":"g1","code":"TBB","name":"Beam trawl","category":{"id":"c1",""" +
        """"code":"TRAWL_NETS","name":"Trawl nets"},"pairFishing":false,"requiredMeasurementIds":[],""" +
        """"variableMeasurementIds":["mid-1"]}]}"""

private fun errorBody(code: String): String = """{"error":{"code":"$code","message":"failed"}}"""

/** [ReferenceDataRefresher]'s manifest/etag-driven per-dataset refresh (ADR 0015). */
@RunWith(RobolectricTestRunner::class)
class ReferenceDataRefresherTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    private lateinit var database: CatchRecordDatabase
    private lateinit var dao: ReferenceDataDao
    private val languageFlow = MutableStateFlow("en")

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CatchRecordDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.referenceDataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an unchanged manifest entry makes no dataset request at all`() =
        runTest {
            dao.upsertMetadata(metadata("vessels", manifestVersion = "v1", manifestEtag = "e1"))
            serverRule.server.enqueue(
                MockResponse(code = 200, body = manifestBody(datasetEntry("vessels", "v1", "e1"))),
            )

            val result = refresher().refresh()

            assertTrue(result.datasetOutcomes.getValue("vessels").isSuccess)
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `a changed manifest version fetches, sending the stored ETag byte-for-byte`() =
        runTest {
            dao.upsertMetadata(
                metadata("vessels", manifestVersion = "v1", manifestEtag = "e1", responseETag = "\"sha256-old=\""),
            )
            serverRule.server.enqueue(
                MockResponse(code = 200, body = manifestBody(datasetEntry("vessels", "v2", "e2"))),
            )
            serverRule.server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(vesselsBody())
                    .addHeader("ETag", "\"sha256-new=\"")
                    .build(),
            )

            refresher().refresh()
            serverRule.server.takeRequest()
            val vesselsRequest = serverRule.server.takeRequest()

            assertEquals("\"sha256-old=\"", vesselsRequest.headers["If-None-Match"])
            assertEquals("ACHILLES PH101", dao.getVessels().single().displayName)
        }

    @Test
    fun `a bodyless 304 keeps the cached rows and only touches the metadata`() =
        runTest {
            dao.upsertMetadata(
                metadata("vessels", manifestVersion = "v1", manifestEtag = "e1", responseETag = "\"sha256-old=\""),
            )
            dao.replaceVessels(
                listOf(
                    ReferenceDataVesselEntity(
                        id = "v-cached",
                        name = "HERCULES",
                        displayName = "HERCULES",
                        pln = null,
                        cfr = null,
                        lengthOverallMetres = null,
                    ),
                ),
            )
            serverRule.server.enqueue(
                MockResponse(code = 200, body = manifestBody(datasetEntry("vessels", "v2", "e2"))),
            )
            serverRule.server.enqueue(MockResponse(code = 304))

            refresher().refresh()

            assertEquals("v-cached", dao.getVessels().single().id)
            assertEquals("v2", dao.getMetadata("vessels")?.manifestVersion)
        }

    @Test
    fun `a ports refresh maps its live port code to the stub's statistical area`() =
        runTest {
            serverRule.server.enqueue(MockResponse(code = 200, body = manifestBody(datasetEntry("ports", "p2", "pe2"))))
            serverRule.server.enqueue(MockResponse(code = 200, body = portsBody()))

            refresher().refresh()

            assertEquals("AREA-HASTINGS", dao.getPorts().single().statisticalAreaId)
        }

    @Test
    fun `a species language change refetches without If-None-Match even when the manifest is unchanged`() =
        runTest {
            dao.upsertMetadata(
                metadata(
                    "species",
                    manifestVersion = "v1",
                    manifestEtag = "e1",
                    responseETag = "\"sha256-old=\"",
                    languageTag = "en",
                ),
            )
            languageFlow.value = "cy"
            serverRule.server.enqueue(
                MockResponse(code = 200, body = manifestBody(datasetEntry("species", "v1", "e1"))),
            )
            serverRule.server.enqueue(MockResponse(code = 200, body = speciesBody()))

            refresher().refresh()
            serverRule.server.takeRequest()
            val speciesRequest = serverRule.server.takeRequest()

            assertNull(speciesRequest.headers["If-None-Match"])
            assertEquals("cy", dao.getMetadata("species")?.languageTag)
        }

    @Test
    fun `one dataset failing does not discard another dataset's successful update`() =
        runTest {
            serverRule.server.enqueue(
                MockResponse(
                    code = 200,
                    body = manifestBody(datasetEntry("vessels", "v2", "e2"), datasetEntry("ports", "p2", "pe2")),
                ),
            )
            serverRule.server.enqueue(MockResponse(code = 200, body = vesselsBody()))
            serverRule.server.enqueue(MockResponse(code = 500, body = errorBody("internal_error")))

            val result = refresher().refresh()

            assertTrue(result.datasetOutcomes.getValue("vessels").isSuccess)
            assertTrue(result.datasetOutcomes.getValue("ports").isFailure)
            assertEquals(1, dao.getVessels().size)
            assertEquals(0, dao.getPorts().size)
        }

    @Test
    fun `a schemaVersion mismatch is never cached`() =
        runTest {
            serverRule.server.enqueue(
                MockResponse(code = 200, body = manifestBody(datasetEntry("vessels", "v2", "e2"))),
            )
            serverRule.server.enqueue(MockResponse(code = 200, body = vesselsBody(schemaVersion = "2.0")))

            val result = refresher().refresh()

            val error = result.datasetOutcomes.getValue("vessels").exceptionOrNull()
            assertTrue(error is ReferenceDataSchemaVersionException)
            assertEquals(0, dao.getVessels().size)
        }

    @Test
    fun `gears are fetched for all four vessel-length variants with the correct lengths`() =
        runTest {
            serverRule.server.enqueue(MockResponse(code = 200, body = manifestBody(datasetEntry("gears", "g2", "ge2"))))
            repeat(VesselLengthBands.cacheableVariants.size) {
                serverRule.server.enqueue(MockResponse(code = 200, body = gearsBody()))
            }

            refresher().refresh()

            serverRule.server.takeRequest()
            val expectedLengths = VesselLengthBands.cacheableVariants.map { it.second }
            expectedLengths.forEach { expectedLength ->
                val request = serverRule.server.takeRequest()
                val actualLength = request.url.queryParameter("vesselLengthMetres")?.toDouble()
                assertEquals(expectedLength, actualLength)
            }
            VesselLengthBands.cacheableVariants.forEach { (band, _) ->
                assertEquals(1, dao.getGearsForBand(band).size)
            }
        }

    private fun refresher(): ReferenceDataRefresher {
        val client = OkHttpClient.Builder().build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(serverRule.server.url("/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        val remoteDataSource = ReferenceDataRemoteDataSource(retrofit.create(ReferenceDataApi::class.java), json)
        return ReferenceDataRefresher(
            remoteDataSource,
            dao,
            FakeAppLanguageRepository(languageFlow),
            clock = { 1_000L },
        )
    }

    private fun metadata(
        datasetKey: String,
        manifestVersion: String,
        manifestEtag: String,
        responseETag: String? = null,
        languageTag: String? = null,
    ) = ReferenceDataMetadataEntity(
        datasetKey = datasetKey,
        collectionId = "col-1",
        schemaVersion = "1.0",
        manifestVersion = manifestVersion,
        manifestEtag = manifestEtag,
        responseETag = responseETag,
        fetchedAtEpochMillis = 500L,
        languageTag = languageTag,
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
