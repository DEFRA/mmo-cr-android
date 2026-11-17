package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val SAVE_RESPONSE_BODY =
    """{"id":"cr-1","catchRecordReference":"GBR-B14974-270726-150815","status":"DRAFT",""" +
        """"displayStatus":"Draft","version":1,"savedSection":null,""" +
        """"sectionCompletion":{"trip":false,"pairFishing":false,"gears":false},""" +
        """"progress":{"hasUnsubmittedChanges":true,"numberOfSubmissions":0,""" +
        """"incompleteGearAssociationIds":[],"currentIncompleteGearAssociationId":null,""" +
        """"allGearsComplete":false}}"""

private const val SUBMISSION_RESPONSE_BODY =
    """{"id":"cr-1","catchRecordReference":"GBR-B14974-270726-150815","status":"SUBMITTED",""" +
        """"displayStatus":"Submitted","version":2,"savedSection":null,""" +
        """"sectionCompletion":{"trip":true,"pairFishing":true,"gears":true},""" +
        """"progress":{"hasUnsubmittedChanges":false,"numberOfSubmissions":1,""" +
        """"incompleteGearAssociationIds":[],"currentIncompleteGearAssociationId":null,""" +
        """"allGearsComplete":true},"submittedAt":"2026-07-27T15:08:15Z","submittedBy":"user-1",""" +
        """"artifacts":[{"submissionNumber":1,"type":"JSON_SNAPSHOT"}]}"""

private const val RETRIEVE_RESPONSE_BODY =
    """{"schemaVersion":1,"id":"cr-1","catchRecordReference":"GBR-B14974-270726-150815",""" +
        """"ownerUserId":"user-1","status":"DRAFT","version":1,"numberOfSubmissions":0,""" +
        """"hasUnsubmittedChanges":true,""" +
        """"vessel":{"id":"v1","rssSnapshot":"RSS1","nameSnapshot":"ACHILLES","externalMarkSnapshot":"PH101"},""" +
        """"trip":{"startedAndFinishedToday":true,"dateStarted":"2026-07-27","dateEnded":"2026-07-27",""" +
        """"departurePort":{"id":"p1","codeSnapshot":"GBHTG","nameSnapshot":"HASTINGS"},""" +
        """"returnPort":{"id":"p1","codeSnapshot":"GBHTG","nameSnapshot":"HASTINGS"}},""" +
        """"pairFishing":{"enabled":false,"pairVessel":null,"pairSkipperName":null},"gears":[],""" +
        """"speciesNotLanded":[],"artifacts":[],"createdAt":"2026-07-27T15:00:00Z","createdBy":"user-1",""" +
        """"updatedAt":"2026-07-27T15:00:00Z","updatedBy":"user-1","submittedAt":null,"submittedBy":null,""" +
        """"completedAt":null,"completedBy":null,"displayStatus":"Draft",""" +
        """"sectionCompletion":{"trip":true,"pairFishing":true,"gears":false},""" +
        """"completedSections":["trip","pairFishing"],"incompleteSections":["gears"],""" +
        """"progress":{"hasUnsubmittedChanges":true,"numberOfSubmissions":0,""" +
        """"incompleteGearAssociationIds":[],"currentIncompleteGearAssociationId":null,""" +
        """"allGearsComplete":false},"submissionEligible":false}"""

private const val LIST_RESPONSE_BODY =
    """{"items":[{"id":"cr-1","catchRecordReference":"GBR-B14974-270726-150815","status":"DRAFT",""" +
        """"displayStatus":"Draft","version":1,"vessel":{"nameSnapshot":"ACHILLES","rssSnapshot":"RSS1"},""" +
        """"trip":{"dateStarted":"2026-07-27","dateEnded":"2026-07-27"},"createdAt":"2026-07-27T15:00:00Z",""" +
        """"updatedAt":"2026-07-27T15:00:00Z","submittedAt":null,"completedAt":null,""" +
        """"progress":{"allGearsComplete":false}}],"limit":20,"count":1}"""

/** [CatchRecordRemoteDataSource] against a real [mockwebserver3.MockWebServer] round trip (ADR 0026). */
class CatchRecordRemoteDataSourceTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `createDraft posts the payload and parses the standard save response`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 201, body = SAVE_RESPONSE_BODY))

            val result = remoteDataSource().createDraft(CreateDraftCatchRecordPayloadDto("vessel-1"), "key-1")

            assertEquals("cr-1", result.id)
            assertEquals("GBR-B14974-270726-150815", result.catchRecordReference)
            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/catch-records", request.url.encodedPath)
            assertEquals("key-1", request.headers["Idempotency-Key"])
        }

    @Test
    fun `replace sends If-Match and parses the standard save response`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = SAVE_RESPONSE_BODY))
            val payload =
                CompleteReplacementPayloadDto(
                    vessel = VesselRefDto("vessel-1"),
                    trip =
                        TripDto(
                            startedAndFinishedToday = true,
                            departurePort = PortRefDto("port-1", "GBHTG", "Hastings"),
                            returnPort = PortRefDto("port-1", "GBHTG", "Hastings"),
                        ),
                    pairFishing = PairFishingDto(enabled = false, pairVessel = null, pairSkipperName = null),
                    gears = emptyList(),
                    speciesNotLanded = emptyList(),
                )

            val result = remoteDataSource().replace("cr-1", "1", payload)

            assertEquals(1, result.version)
            val request = serverRule.server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/v1/catch-records/cr-1", request.url.encodedPath)
            assertEquals("1", request.headers["If-Match"])
        }

    @Test
    fun `submit sends If-Match and an optional Idempotency-Key and parses the submission response`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = SUBMISSION_RESPONSE_BODY))

            val result = remoteDataSource().submit("cr-1", "1", "key-2")

            assertEquals("SUBMITTED", result.status)
            assertEquals("user-1", result.submittedBy)
            assertEquals(1, result.artifacts.single().submissionNumber)
            val request = serverRule.server.takeRequest()
            assertEquals("/v1/catch-records/cr-1/submission", request.url.encodedPath)
            assertEquals("1", request.headers["If-Match"])
            assertEquals("key-2", request.headers["Idempotency-Key"])
        }

    @Test
    fun `submit omits Idempotency-Key when none is supplied`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = SUBMISSION_RESPONSE_BODY))

            remoteDataSource().submit("cr-1", "1")

            val request = serverRule.server.takeRequest()
            assertNull(request.headers["Idempotency-Key"])
        }

    @Test
    fun `retrieve parses the full canonical catch record snapshot`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = RETRIEVE_RESPONSE_BODY))

            val result = remoteDataSource().retrieve("cr-1")

            assertEquals("ACHILLES", result.vessel.nameSnapshot)
            assertFalse(result.submissionEligible)
            assertEquals(listOf("gears"), result.incompleteSections)
        }

    @Test
    fun `list parses the bounded owner listing`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = LIST_RESPONSE_BODY))

            val result = remoteDataSource().list(limit = 20)

            assertEquals(1, result.count)
            val item = result.items.single()
            assertEquals("ACHILLES", item.vessel.nameSnapshot)
        }

    @Test
    fun `a 401 is classified as unauthorized, not a generic http exception`() {
        enqueueError(401, "UNAUTHORIZED", correlationId = null)

        val error =
            assertThrows(CatchRecordUnauthorizedException::class.java) {
                runBlocking { remoteDataSource().retrieve("cr-1") }
            }

        assertFalse(error.retryable)
    }

    @Test
    fun `a 404 with a correlationId is classified terminal and carries the id through`() {
        enqueueError(404, "CATCH_RECORD_NOT_FOUND", correlationId = "4c2c4e3a-0000-4000-8000-000000000000")

        val error =
            assertThrows(CatchRecordHttpException::class.java) {
                runBlocking { remoteDataSource().retrieve("cr-1") }
            }

        assertEquals(404, error.httpStatusCode)
        assertEquals("CATCH_RECORD_NOT_FOUND", error.serverCode)
        assertEquals("4c2c4e3a-0000-4000-8000-000000000000", error.correlationId)
        assertFalse(error.retryable)
    }

    @Test
    fun `a 409 idempotency conflict is classified terminal`() {
        enqueueError(409, "IDEMPOTENCY_CONFLICT", correlationId = null)

        val error =
            assertThrows(CatchRecordHttpException::class.java) {
                runBlocking { remoteDataSource().createDraft(CreateDraftCatchRecordPayloadDto("vessel-1")) }
            }

        assertFalse(error.retryable)
    }

    @Test
    fun `a 503 with no server code defaults to transient`() {
        enqueueError(503, "SERVICE_UNAVAILABLE", correlationId = null)

        val error =
            assertThrows(CatchRecordHttpException::class.java) {
                runBlocking { remoteDataSource().retrieve("cr-1") }
            }

        assertTrue(error.retryable)
    }

    @Test
    fun `a malformed error body falls back to a status-based retryable classification`() {
        serverRule.server.enqueue(MockResponse(code = 503, body = "not json"))

        val error =
            assertThrows(CatchRecordHttpException::class.java) {
                runBlocking { remoteDataSource().retrieve("cr-1") }
            }

        assertTrue(error.retryable)
        assertNull(error.serverCode)
    }

    @Test
    fun `a 204 with no body is treated as malformed`() {
        serverRule.server.enqueue(MockResponse.Builder().code(204).build())

        assertThrows(CatchRecordMalformedResponseException::class.java) {
            runBlocking { remoteDataSource().retrieve("cr-1") }
        }
    }

    private fun enqueueError(
        httpStatusCode: Int,
        code: String,
        correlationId: String?,
    ) {
        val correlationField = correlationId?.let { ""","correlationId":"$it"""" } ?: ""
        serverRule.server.enqueue(
            MockResponse(
                code = httpStatusCode,
                body = """{"statusCode":$httpStatusCode,"code":"$code","message":"failed"$correlationField}""",
            ),
        )
    }

    private fun remoteDataSource(): CatchRecordRemoteDataSource {
        val client = OkHttpClient.Builder().build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(serverRule.server.url("/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        return CatchRecordRemoteDataSource(retrofit.create(CatchRecordApi::class.java), json)
    }
}
