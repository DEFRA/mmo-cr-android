package uk.gov.defra.mmocatchrecord.core.network

import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The debug-only logging interceptor (ADR 0015/0016, wired in [uk.gov.defra.mmocatchrecord.di.NetworkModule])
 * must never leak the raw bearer token into its log output, even at `HEADERS` level.
 */
class LoggingRedactionTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `Authorization header value never appears in logged output`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val secretToken = "super-secret-token-value"
        val logs = mutableListOf<String>()
        val loggingInterceptor =
            HttpLoggingInterceptor { message -> logs += message }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
                redactHeader("Authorization")
            }
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(
                    AuthInterceptor(
                        object : TokenProvider {
                            override suspend fun currentToken() = secretToken
                        },
                    ),
                ).addInterceptor(loggingInterceptor)
                .build()

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {}

        assertFalse(logs.any { it.contains(secretToken) })
        assertTrue(logs.any { it.contains("Authorization", ignoreCase = true) })
    }
}
