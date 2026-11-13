package uk.gov.defra.mmocatchrecord.di

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.decodeFromString
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import uk.gov.defra.mmocatchrecord.BuildConfig
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.core.network.AcceptLanguageInterceptor
import uk.gov.defra.mmocatchrecord.core.network.AuthInterceptor
import uk.gov.defra.mmocatchrecord.core.network.HealthResponse
import uk.gov.defra.mmocatchrecord.core.network.MissingTokenProvider
import uk.gov.defra.mmocatchrecord.core.network.StubTokenProvider
import uk.gov.defra.mmocatchrecord.core.network.TokenProvider
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalTokenProvider
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthClient

/**
 * [NetworkModule] is a plain Hilt `@Module object` — its `@Provides` functions are ordinary functions
 * exercised directly here, the same pattern as [RepositoryModuleTests].
 */
class NetworkModuleTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `tokenProviderFor binds the stub provider when useStubAuth is set (local)`() {
        val provider =
            NetworkModule.tokenProviderFor(
                useStubAuth = true,
                useFakeSignIn = true,
                referenceDataServiceUrl = "https://example.com/",
                nativeAuthClient = mock<NativeAuthClient>(),
                sessionStore = InMemorySessionStore(),
            )

        assertTrue(provider is StubTokenProvider)
    }

    @Test
    fun `tokenProviderFor binds MSAL when the stub is off, sign-in is real and the url is configured (dev)`() {
        val provider =
            NetworkModule.tokenProviderFor(
                useStubAuth = false,
                useFakeSignIn = false,
                referenceDataServiceUrl = "https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/",
                nativeAuthClient = mock<NativeAuthClient>(),
                sessionStore = InMemorySessionStore(),
            )

        assertTrue(provider is MsalTokenProvider)
    }

    @Test
    fun `tokenProviderFor binds the missing provider when the service url is the prod fail-fast placeholder`() {
        val provider =
            NetworkModule.tokenProviderFor(
                useStubAuth = false,
                useFakeSignIn = false,
                referenceDataServiceUrl = "",
                nativeAuthClient = mock<NativeAuthClient>(),
                sessionStore = InMemorySessionStore(),
            )

        assertTrue(provider is MissingTokenProvider)
    }

    @Test
    fun `tokenProviderFor binds the missing provider when sign-in is still the fake`() {
        val provider =
            NetworkModule.tokenProviderFor(
                useStubAuth = false,
                useFakeSignIn = true,
                referenceDataServiceUrl = "https://example.com/",
                nativeAuthClient = mock<NativeAuthClient>(),
                sessionStore = InMemorySessionStore(),
            )

        assertTrue(provider is MissingTokenProvider)
    }

    @Test
    fun `provideJson ignores unknown keys instead of throwing`() {
        val json = NetworkModule.provideJson()

        val response =
            json.decodeFromString<HealthResponse>(
                """{"status":"ok","service":"svc","timestamp":"now","extraField":"ignored"}""",
            )

        assertTrue(response.status == "ok")
    }

    @Test
    fun `provideTokenProvider wires the token source this build flavor's USE_STUB_AUTH flag selects`() {
        val provider = NetworkModule.provideTokenProvider(mock<NativeAuthClient>(), InMemorySessionStore())

        if (BuildConfig.USE_STUB_AUTH) {
            assertTrue(provider is StubTokenProvider)
        } else {
            assertTrue(provider is MsalTokenProvider)
        }
    }

    @Test
    fun `provideAuthInterceptor wraps the given token provider`() {
        val tokenProvider = StubTokenProvider()

        val interceptor = NetworkModule.provideAuthInterceptor(tokenProvider)

        assertNotNull(interceptor)
    }

    @Test
    fun `provideAcceptLanguageInterceptor wraps the given app language repository`() {
        val interceptor = NetworkModule.provideAcceptLanguageInterceptor(FakeAppLanguageRepository)

        assertNotNull(interceptor)
    }

    @Test
    fun `provideOkHttpClient wires both interceptors`() {
        val authInterceptor = AuthInterceptor(StubTokenProvider())
        val acceptLanguageInterceptor = AcceptLanguageInterceptor(FakeAppLanguageRepository)

        val client = NetworkModule.provideOkHttpClient(authInterceptor, acceptLanguageInterceptor)

        assertTrue(client.interceptors.contains(authInterceptor))
        assertTrue(client.interceptors.contains(acceptLanguageInterceptor))
    }

    @Test
    fun `provideOkHttpClient attaches a real bearer token end-to-end through the built client`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val authInterceptor = AuthInterceptor(TokenProvider { "fixture-token" })
        val client =
            NetworkModule.provideOkHttpClient(authInterceptor, AcceptLanguageInterceptor(FakeAppLanguageRepository))

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertEquals("Bearer fixture-token", serverRule.server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `provideOkHttpClient omits Authorization end-to-end when the token provider has no token`() {
        serverRule.server.enqueue(MockResponse(code = 200))
        val authInterceptor = AuthInterceptor(TokenProvider { null })
        val client =
            NetworkModule.provideOkHttpClient(authInterceptor, AcceptLanguageInterceptor(FakeAppLanguageRepository))

        client.newCall(Request.Builder().url(serverRule.server.url("/health")).build()).execute().use {
            assertEquals(200, it.code)
        }

        assertNull(serverRule.server.takeRequest().headers["Authorization"])
    }
}

private object FakeAppLanguageRepository : AppLanguageRepository {
    override val language: Flow<String> = flowOf("en")

    override suspend fun setLanguage(language: String) = Unit
}
