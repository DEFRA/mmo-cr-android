package uk.gov.defra.mmocatchrecord.di

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.core.network.AcceptLanguageInterceptor
import uk.gov.defra.mmocatchrecord.core.network.AuthInterceptor
import uk.gov.defra.mmocatchrecord.core.network.HealthResponse
import uk.gov.defra.mmocatchrecord.core.network.MissingTokenProvider
import uk.gov.defra.mmocatchrecord.core.network.StubTokenProvider

/**
 * [NetworkModule] is a plain Hilt `@Module object` — its `@Provides` functions are ordinary functions
 * exercised directly here, the same pattern as [RepositoryModuleTests].
 */
class NetworkModuleTests {
    @Test
    fun `tokenProviderFor binds the stub provider for local and dev`() {
        assertTrue(NetworkModule.tokenProviderFor(useStubAuth = true) is StubTokenProvider)
    }

    @Test
    fun `tokenProviderFor binds the missing provider for prod`() {
        assertTrue(NetworkModule.tokenProviderFor(useStubAuth = false) is MissingTokenProvider)
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
    fun `provideOkHttpClient wires both interceptors`() {
        val authInterceptor = AuthInterceptor(StubTokenProvider())
        val acceptLanguageInterceptor = AcceptLanguageInterceptor(FakeAppLanguageRepository)

        val client = NetworkModule.provideOkHttpClient(authInterceptor, acceptLanguageInterceptor)

        assertTrue(client.interceptors.contains(authInterceptor))
        assertTrue(client.interceptors.contains(acceptLanguageInterceptor))
    }
}

private object FakeAppLanguageRepository : AppLanguageRepository {
    override val language: Flow<String> = flowOf("en")

    override suspend fun setLanguage(language: String) = Unit
}
