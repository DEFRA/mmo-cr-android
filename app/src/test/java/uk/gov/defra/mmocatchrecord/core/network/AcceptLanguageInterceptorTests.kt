package uk.gov.defra.mmocatchrecord.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository

/**
 * [AcceptLanguageInterceptor] sends the current app language, and degrades to no header (never
 * crashes, never hangs) when the underlying preference read fails (ADR 0012).
 */
class AcceptLanguageInterceptorTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `sends Accept-Language with the current app language`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val client = clientWithLanguage(FakeAppLanguageRepository(flowOf("cy")))

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertEquals("cy", serverRule.server.takeRequest().headers["Accept-Language"])
    }

    @Test
    fun `omits the header when the language read fails`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val failingFlow: Flow<String> = flow { throw IllegalStateException("DataStore unavailable") }
        val client = clientWithLanguage(FakeAppLanguageRepository(failingFlow))

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertNull(serverRule.server.takeRequest().headers["Accept-Language"])
    }

    private fun clientWithLanguage(repository: AppLanguageRepository): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(AcceptLanguageInterceptor(repository))
            .build()
}

private class FakeAppLanguageRepository(
    override val language: Flow<String>,
) : AppLanguageRepository {
    override suspend fun setLanguage(language: String) = Unit
}
