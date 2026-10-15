package uk.gov.defra.mmocatchrecord.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReferenceDataServiceConfigTests {
    @Test
    fun `requireServiceRootUrl throws when the configured URL is blank`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireServiceRootUrl("")
        }
    }

    @Test
    fun `requireServiceRootUrl throws for a whitespace-only URL`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireServiceRootUrl("   ")
        }
    }

    @Test
    fun `requireServiceRootUrl throws for an invalid URL`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireServiceRootUrl("not a url")
        }
    }

    @Test
    fun `requireServiceRootUrl rejects cleartext URLs`() {
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            ReferenceDataServiceConfig.requireServiceRootUrl("http://example.test/")
        }
    }

    @Test
    fun `requireServiceRootUrl rejects credentials queries and fragments`() {
        listOf(
            "https://user:password@example.test/",
            "https://:password@example.test/",
            "https://example.test/?query=value",
            "https://example.test/#fragment",
        ).forEach { configuredUrl ->
            assertThrows(MissingReferenceDataServiceUrlException::class.java) {
                ReferenceDataServiceConfig.requireServiceRootUrl(configuredUrl)
            }
        }
    }

    @Test
    fun `requireServiceRootUrl adds a trailing slash when missing`() {
        val result = ReferenceDataServiceConfig.requireServiceRootUrl("https://example.test")

        assertEquals("https://example.test/", result)
    }

    @Test
    fun `requireServiceRootUrl keeps an existing trailing slash`() {
        val result = ReferenceDataServiceConfig.requireServiceRootUrl("https://example.test/")

        assertEquals("https://example.test/", result)
    }

    @Test
    fun `apiBaseUrl appends the documented API path`() {
        val result = ReferenceDataServiceConfig.apiBaseUrl("https://example.test/")

        assertEquals("https://example.test/api/v1/reference-data/", result)
    }
}
