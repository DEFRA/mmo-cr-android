package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

private const val VESSELS_BODY =
    """{"dataset":"vessels","collectionId":"col-1","schemaVersion":"1.0","version":"v1","view":"mobile",""" +
        """"total":1,"items":[{"id":"v1","name":"ACHILLES","pln":"PH101","cfr":"GBR0000001",""" +
        """"displayName":"ACHILLES PH101","lengthOverallMetres":8.74}]}"""

private const val GEARS_BODY =
    """{"dataset":"gears","collectionId":"col-4","schemaVersion":"1.0","version":"v1","view":"mobile",""" +
        """"total":1,"context":{"vesselLengthBand":"under-10m"},"measurements":[{"id":"mid-1","code":"ME",""" +
        """"label":"Mesh size","kind":"number","unit":"mm"}],"items":[{"id":"g1","code":"TBB",""" +
        """"name":"Beam trawl","category":{"id":"c1","code":"TRAWL_NETS","name":"Trawl nets"},""" +
        """"pairFishing":false,"requiredMeasurementIds":[],"variableMeasurementIds":["mid-1"]}]}"""

private const val MANIFEST_BODY =
    """{"manifestId":"m-1","version":"2026-01-01T00:00:00Z","datasets":[{"dataset":"vessels",""" +
        """"collectionId":"col-1","schemaVersion":"1.0","version":"v1",""" +
        """"etag":"61dd8fed9ed7cc2a4a96a97da8a1174c","url":"/vessels","format":"json","itemCount":30}]}"""

/** [ReferenceDataRemoteDataSource] against a real [mockwebserver3.MockWebServer] round trip (ADR 0015). */
class ReferenceDataRemoteDataSourceTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `fetchVessels parses the raw DTO envelope and captures the response ETag`() =
        runBlocking {
            serverRule.server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(VESSELS_BODY)
                    .addHeader("ETag", "\"sha256-abc=\"")
                    .build(),
            )

            val result = remoteDataSource().fetchVessels() as ReferenceDataFetchResult.Modified

            assertEquals(1, result.items.size)
            assertEquals("ACHILLES PH101", result.items.single().displayName)
            assertEquals("PH101", result.items.single().pln)
            assertEquals("col-1", result.collectionId)
            assertEquals("\"sha256-abc=\"", result.responseETag)
        }

    @Test
    fun `fetchVessels returns NotModified on a bodyless 304`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 304))

            val result = remoteDataSource().fetchVessels(ifNoneMatch = "\"sha256-abc=\"")

            assertTrue(result is ReferenceDataFetchResult.NotModified)
        }

    @Test
    fun `fetchGears resolves band context and the measurement catalogue`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = GEARS_BODY))

            val result = remoteDataSource().fetchGears(vesselLengthMetres = 8.0) as ReferenceDataFetchResult.Modified

            val payload = result.items
            assertEquals("TBB", payload.items.single().code)
            assertEquals("ME", payload.measurementsById.getValue("mid-1").code)
        }

    @Test
    fun `fetchManifest maps successfully and ignores unrecognised dataset names downstream`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = MANIFEST_BODY))

            val result = remoteDataSource().fetchManifest() as ManifestFetchResult.Modified

            assertEquals("m-1", result.manifest.manifestId)
        }

    @Test
    fun `fetchManifest returns NotModified on a bodyless 304`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 304))

            val result = remoteDataSource().fetchManifest()

            assertTrue(result is ManifestFetchResult.NotModified)
        }

    @Test
    fun `a schemaVersion mismatch throws and is never treated as cacheable`() {
        val mismatchedBody = VESSELS_BODY.replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"2.0\"")
        serverRule.server.enqueue(MockResponse(code = 200, body = mismatchedBody))

        val error =
            assertThrows(ReferenceDataSchemaVersionException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertEquals("2.0", error.actualSchemaVersion)
        assertTrue(!error.retryable)
    }

    @Test
    fun `a 400 with an explicit retryable false is classified terminal`() {
        enqueueError(400, "invalid_request", retryable = false)

        val error =
            assertThrows(ReferenceDataHttpException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertEquals(400, error.httpStatusCode)
        assertTrue(!error.retryable)
    }

    @Test
    fun `a 401 is classified as unauthorized, not a generic http exception`() {
        serverRule.server.enqueue(MockResponse(code = 401, body = "unauthorized"))

        val error =
            assertThrows(ReferenceDataUnauthorizedException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertTrue(!error.retryable)
    }

    @Test
    fun `a 403 defaults to terminal`() {
        enqueueError(403, "forbidden", retryable = null)

        val error =
            assertThrows(ReferenceDataHttpException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertTrue(!error.retryable)
    }

    @Test
    fun `a 404 defaults to terminal`() {
        enqueueError(404, "reference_item_not_found", retryable = null)

        val error =
            assertThrows(ReferenceDataHttpException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertTrue(!error.retryable)
    }

    @Test
    fun `a 500 with no retryable field defaults to transient`() {
        enqueueError(500, "internal_error", retryable = null)

        val error =
            assertThrows(ReferenceDataHttpException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertTrue(error.retryable)
    }

    @Test
    fun `a 503 with an explicit retryable false override is honoured over the status default`() {
        enqueueError(503, "reference_data_unavailable", retryable = false)

        val error =
            assertThrows(ReferenceDataHttpException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertTrue(!error.retryable)
    }

    @Test
    fun `a malformed 503 error body falls back to a transient status default`() {
        serverRule.server.enqueue(MockResponse(code = 503, body = "not json"))

        val error =
            assertThrows(ReferenceDataHttpException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertTrue(error.retryable)
        assertNull(error.errorCode)
    }

    @Test
    fun `a malformed 400 error body falls back to a terminal status default`() {
        serverRule.server.enqueue(MockResponse(code = 400, body = "not json"))

        val error =
            assertThrows(ReferenceDataHttpException::class.java) {
                runBlocking { remoteDataSource().fetchVessels() }
            }

        assertTrue(!error.retryable)
    }

    @Test
    fun `a 204 with no body is treated as malformed, not a missing-304 success`() {
        serverRule.server.enqueue(MockResponse.Builder().code(204).build())

        assertThrows(ReferenceDataMalformedResponseException::class.java) {
            runBlocking { remoteDataSource().fetchVessels() }
        }
    }

    @Test
    fun `a client-side read timeout propagates as a raw SocketTimeoutException`() {
        serverRule.server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .headersDelay(SLOW_RESPONSE_DELAY_MS, TimeUnit.MILLISECONDS)
                .build(),
        )

        assertThrows(SocketTimeoutException::class.java) {
            runBlocking { remoteDataSource(readTimeoutMs = FAST_READ_TIMEOUT_MS).fetchVessels() }
        }
    }

    private fun enqueueError(
        httpStatusCode: Int,
        code: String,
        retryable: Boolean?,
    ) {
        val retryableField = retryable?.let { ""","retryable":$it""" } ?: ""
        serverRule.server.enqueue(
            MockResponse(
                code = httpStatusCode,
                body = """{"error":{"code":"$code","message":"failed"$retryableField}}""",
            ),
        )
    }

    private fun remoteDataSource(readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS): ReferenceDataRemoteDataSource {
        val client = OkHttpClient.Builder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(serverRule.server.url("/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        return ReferenceDataRemoteDataSource(retrofit.create(ReferenceDataApi::class.java), json)
    }

    private companion object {
        const val DEFAULT_READ_TIMEOUT_MS = 10_000L
        const val FAST_READ_TIMEOUT_MS = 200L
        const val SLOW_RESPONSE_DELAY_MS = 5_000L
    }
}
