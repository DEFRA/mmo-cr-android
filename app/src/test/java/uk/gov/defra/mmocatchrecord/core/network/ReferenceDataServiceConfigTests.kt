package uk.gov.defra.mmocatchrecord.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReferenceDataServiceConfigTests {
    @Test
    fun `requireApiBaseUrl throws when the configured URL is blank`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireApiBaseUrl("")
        }
    }

    @Test
    fun `requireApiBaseUrl throws for a whitespace-only URL`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireApiBaseUrl("   ")
        }
    }

    @Test
    fun `requireApiBaseUrl throws for an invalid URL`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireApiBaseUrl("not a url")
        }
    }

    @Test
    fun `requireApiBaseUrl rejects cleartext URLs`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireApiBaseUrl("http://example.test/")
        }
    }

    @Test
    fun `requireApiBaseUrl rejects credentials queries and fragments`() {
        listOf(
            "https://user:password@example.test/",
            "https://:password@example.test/",
            "https://example.test/?query=value",
            "https://example.test/#fragment",
        ).forEach { configuredUrl ->
            assertThrows(MissingReferenceDataServiceUrlException::class.java) {
                ReferenceDataServiceConfig.requireApiBaseUrl(configuredUrl)
            }
        }
    }

    @Test
    fun `requireApiBaseUrl adds a trailing slash when missing`() {
        val result = ReferenceDataServiceConfig.requireApiBaseUrl("https://example.test")

        assertEquals("https://example.test/", result)
    }

    @Test
    fun `requireApiBaseUrl keeps an existing trailing slash`() {
        val result = ReferenceDataServiceConfig.requireApiBaseUrl("https://example.test/")

        assertEquals("https://example.test/", result)
    }

    @Test
    fun `serviceRootUrl appends the reference-data service path to the API base`() {
        val result = ReferenceDataServiceConfig.serviceRootUrl("https://example.test/")

        assertEquals("https://example.test/reference-data-service/", result)
    }

    @Test
    fun `referenceDataApiUrl appends the documented API path to the service root`() {
        val result = ReferenceDataServiceConfig.referenceDataApiUrl("https://example.test/reference-data-service/")

        assertEquals("https://example.test/reference-data-service/api/v1/reference-data/", result)
    }
}
