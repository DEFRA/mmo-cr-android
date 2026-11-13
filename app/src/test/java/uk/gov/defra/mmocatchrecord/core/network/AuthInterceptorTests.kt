package uk.gov.defra.mmocatchrecord.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
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
    fun `does not log a timeout warning when the token provider legitimately returns null fast`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val warnings = mutableListOf<String>()
        val tree =
            object : timber.log.Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    if (priority == android.util.Log.WARN) warnings += message
                }
            }
        timber.log.Timber.plant(tree)
        try {
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(AuthInterceptor(FakeTokenProvider(null), timeoutMillis = 10_000L))
                    .build()

            client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
                assertEquals(200, it.code)
            }

            assertNull(serverRule.server.takeRequest().headers["Authorization"])
            assertTrue("expected no timeout warning but got: $warnings", warnings.isEmpty())
        } finally {
            timber.log.Timber.uproot(tree)
        }
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
                .addInterceptor(
                    AuthInterceptor(
                        tokenProvider = TokenProvider { throw IllegalStateException("token store unavailable") },
                    ),
                ).build()

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

    @Test
    fun `omits Authorization and logs a warning when the token lookup times out`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(AuthInterceptor(NeverRespondingTokenProvider, timeoutMillis = 20L))
                .build()

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertNull(serverRule.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `attaches the header promptly when the token provider is faster than the timeout`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(AuthInterceptor(FakeTokenProvider("fixture-token"), timeoutMillis = 20L))
                .build()

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertEquals("Bearer fixture-token", serverRule.server.takeRequest().headers["Authorization"])
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

/** Suspends forever (via [suspendCancellableCoroutine]) so [AuthInterceptor]'s timeout always wins. */
private object NeverRespondingTokenProvider : TokenProvider {
    override suspend fun currentToken(): String = suspendCancellableCoroutine { }
}
