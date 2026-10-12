package uk.gov.defra.mmocatchrecord.core.network

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [AuthInterceptor] adds/omits `Authorization` based on [TokenProvider.currentToken] — verified against
 * a real [mockwebserver3.MockWebServer] request/response round trip, not a mocked [Interceptor.Chain].
 */
class AuthInterceptorTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `adds Bearer header when the token provider returns a token`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val client = clientWithToken("a-token")

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertEquals("Bearer a-token", serverRule.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `omits Authorization header when the token provider returns null`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val client = clientWithToken(null)

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertNull(serverRule.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `omits Authorization header when the token provider returns a blank string`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val client = clientWithToken("   ")

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertNull(serverRule.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `omits Authorization header and still proceeds when the token provider throws`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(AuthInterceptor { throw IllegalStateException("token store unavailable") })
                .build()

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertNull(serverRule.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `stub token provider supplies a non-blank token and the missing provider supplies none`() =
        runBlocking {
            assertTrue(StubTokenProvider().currentToken().isNotBlank())
            assertNull(MissingTokenProvider().currentToken())
        }

    private fun clientWithToken(token: String?): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(AuthInterceptor(FakeTokenProvider(token)))
            .build()
}

private class FakeTokenProvider(
    private val token: String?,
) : TokenProvider {
    override suspend fun currentToken(): String? = token
}
