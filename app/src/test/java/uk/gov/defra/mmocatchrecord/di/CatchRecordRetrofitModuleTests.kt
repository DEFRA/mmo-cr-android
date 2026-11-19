package uk.gov.defra.mmocatchrecord.di

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.RealCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft

/** [CatchRecordRetrofitModule]'s `@Provides` functions and [submissionRepositoryFor], exercised directly. */
class CatchRecordRetrofitModuleTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `provideCatchRecordingServiceRootUrl reads the packaged configuration`() {
        val appConfiguration = mock<AppConfiguration>()
        whenever(appConfiguration.requireCatchRecordingServiceRootUrl()).thenReturn("https://example.test/")

        assertEquals(
            "https://example.test/",
            CatchRecordRetrofitModule.provideCatchRecordingServiceRootUrl(appConfiguration),
        )
    }

    @Test
    fun `the provided json ignores unknown keys`() {
        val json = CatchRecordRetrofitModule.provideCatchRecordJson()

        assertTrue(json.configuration.ignoreUnknownKeys)
    }

    @Test
    fun `the provided api targets the service root and omits unset list query parameters`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = """{"items":[],"limit":20,"count":0}"""))
            val serviceRoot = serverRule.server.url("/").toString()
            val retrofit =
                CatchRecordRetrofitModule.provideCatchRecordingRetrofit(
                    OkHttpClient.Builder().build(),
                    CatchRecordRetrofitModule.provideCatchRecordJson(),
                    serviceRoot,
                )

            val response = CatchRecordRetrofitModule.provideCatchRecordApi(retrofit).list()

            assertEquals(serviceRoot, retrofit.baseUrl().toString())
            assertEquals(0, response.body()?.count)
            val request = serverRule.server.takeRequest()
            assertEquals("/v1/catch-records", request.url.encodedPath)
            assertNull(request.url.queryParameter("limit"))
        }

    @Test
    fun `submissionRepositoryFor selects the stub only when stubbing is enabled`() {
        val real = mock<RealCatchRecordSubmissionRepository>()
        val stub = StubCatchRecordSubmissionRepository()

        assertSame(stub, submissionRepositoryFor(useStubSubmission = true, real = real, stub = stub))
        assertSame(real, submissionRepositoryFor(useStubSubmission = false, real = real, stub = stub))
    }

    @Test
    fun `the stub submission repository always succeeds with the same draft`() =
        runBlocking {
            val draft = CatchRecordDraft(id = "draft-1", vesselId = "vessel-1", modifiedAtEpochMillis = 0L)

            assertSame(draft, StubCatchRecordSubmissionRepository().submit(draft).getOrThrow())
        }
}
