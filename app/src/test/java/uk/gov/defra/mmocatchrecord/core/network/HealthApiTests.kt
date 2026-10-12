package uk.gov.defra.mmocatchrecord.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * [HealthApi] against a real [mockwebserver3.MockWebServer] round trip — happy path, a documented
 * `503`, a malformed body, and a client-side timeout (ADR 0016's verified DEV behaviour).
 */
class HealthApiTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `health parses a successful response`() =
        runBlocking {
            serverRule.server.enqueue(MockResponse(code = 200, body = HEALTHY_BODY))

            val response = healthApi().health()

            assertEquals("ok", response.status)
            assertEquals("mmo-cr-reference-data-service", response.service)
            assertEquals("2026-01-01T00:00:00Z", response.timestamp)
        }

    @Test
    fun `health throws HttpException on a 503`() {
        serverRule.server.enqueue(MockResponse(code = 503, body = "service unavailable"))

        val error =
            assertThrows(HttpException::class.java) {
                runBlocking { healthApi().health() }
            }

        assertEquals(503, error.code())
    }

    @Test
    fun `health throws on a malformed body`() {
        serverRule.server.enqueue(MockResponse(code = 200, body = "not json"))

        assertThrows(Exception::class.java) {
            runBlocking { healthApi().health() }
        }
    }

    @Test
    fun `health throws SocketTimeoutException when the server is too slow`() {
        serverRule.server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .headersDelay(SLOW_RESPONSE_DELAY_MS, TimeUnit.MILLISECONDS)
                .build(),
        )

        assertThrows(SocketTimeoutException::class.java) {
            runBlocking { healthApi(readTimeoutMs = FAST_READ_TIMEOUT_MS).health() }
        }
    }

    private fun healthApi(readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS): HealthApi {
        val client =
            OkHttpClient
                .Builder()
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(serverRule.server.url("/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        return retrofit.create(HealthApi::class.java)
    }

    private companion object {
        const val DEFAULT_READ_TIMEOUT_MS = 10_000L
        const val FAST_READ_TIMEOUT_MS = 200L
        const val SLOW_RESPONSE_DELAY_MS = 5_000L
        const val HEALTHY_BODY =
            """{"status":"ok","service":"mmo-cr-reference-data-service",""" +
                """"timestamp":"2026-01-01T00:00:00Z"}"""
    }
}
