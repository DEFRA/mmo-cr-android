package uk.gov.defra.mmocatchrecord.di

import kotlinx.serialization.json.Json
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.network.ReferenceDataServiceConfig

/**
 * [ReferenceDataRetrofitModule] is a plain Hilt `@Module object` — its `@Provides` functions are
 * exercised directly here, the same pattern as [NetworkModuleTests].
 */
class ReferenceDataRetrofitModuleTests {
    @JvmField
    @Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `provideReferenceDataApiBaseUrl appends the documented api path`() {
        val serviceRoot = serverRule.server.url("/").toString()

        val apiBase = ReferenceDataRetrofitModule.provideReferenceDataApiBaseUrl(serviceRoot)

        assertEquals(ReferenceDataServiceConfig.apiBaseUrl(serviceRoot), apiBase)
    }

    @Test
    fun `provideHealthApi builds a usable retrofit proxy`() {
        val serviceRoot = serverRule.server.url("/").toString()
        val client = OkHttpClient.Builder().build()
        val json = Json { ignoreUnknownKeys = true }

        val retrofit =
            ReferenceDataRetrofitModule.provideReferenceDataServiceRootRetrofit(client, json, serviceRoot)
        val healthApi = ReferenceDataRetrofitModule.provideHealthApi(retrofit)

        assertNotNull(healthApi)
    }
}
