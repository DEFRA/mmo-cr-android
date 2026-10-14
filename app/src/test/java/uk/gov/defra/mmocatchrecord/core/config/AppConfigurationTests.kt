package uk.gov.defra.mmocatchrecord.core.config

import android.content.Context
import android.content.res.AssetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.core.network.MissingReferenceDataServiceUrlException
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

private const val CONFIG_ASSET = "mmocr_app_config.json"

class AppConfigurationTests {
    @Test
    fun `loads valid config once and exposes the normalized service root and startup metadata`() {
        val (configuration, assets) =
            configurationFor("""{"API_BASE_URL":"https://example.test/reference-data","USE_STUB_AUTH":true}""")

        assertEquals(
            "https://example.test/reference-data/",
            configuration.requireReferenceDataServiceRootUrl(),
        )
        assertEquals("example.test", configuration.apiHostForStartupLog())
        assertTrue(configuration.useStubAuth == true)
        configuration.useStubAuth
        verify(assets, times(1)).open(CONFIG_ASSET)
    }

    @Test
    fun `blank service root is retained but fails when reference data networking requests it`() {
        val configuration = configurationFor("""{"API_BASE_URL":"  ","USE_STUB_AUTH":false}""").first

        assertNull(configuration.apiHostForStartupLog())
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            configuration.requireReferenceDataServiceRootUrl()
        }
    }

    @Test
    fun `missing asset does not fail during startup metadata reads`() {
        val assets = mock<AssetManager>()
        whenever(assets.open(CONFIG_ASSET)).thenThrow(FileNotFoundException(CONFIG_ASSET))
        val configuration = configurationFor(assets)

        assertNull(configuration.apiHostForStartupLog())
        assertNull(configuration.useStubAuth)
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            configuration.requireReferenceDataServiceRootUrl()
        }
        verify(assets, times(1)).open(CONFIG_ASSET)
    }

    @Test
    fun `malformed JSON does not fail during startup metadata reads`() {
        val configuration = configurationFor("{ invalid json").first

        assertNull(configuration.apiHostForStartupLog())
        assertNull(configuration.useStubAuth)
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            configuration.requireReferenceDataServiceRootUrl()
        }
    }

    @Test
    fun `wrong property types make packaged configuration unavailable`() {
        val urlHasWrongType = configurationFor("""{"API_BASE_URL":42,"USE_STUB_AUTH":true}""").first
        val authHasWrongType =
            configurationFor(
                """{"API_BASE_URL":"https://example.test","USE_STUB_AUTH":"true"}""",
            ).first
        val authHasNonBooleanPrimitive =
            configurationFor("""{"API_BASE_URL":"https://example.test","USE_STUB_AUTH":1}""").first
        val rootHasWrongType = configurationFor("[]").first

        assertNull(urlHasWrongType.apiHostForStartupLog())
        assertNull(authHasWrongType.useStubAuth)
        assertNull(authHasNonBooleanPrimitive.useStubAuth)
        assertNull(rootHasWrongType.useStubAuth)
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            urlHasWrongType.requireReferenceDataServiceRootUrl()
        }
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            authHasWrongType.requireReferenceDataServiceRootUrl()
        }
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            authHasNonBooleanPrimitive.requireReferenceDataServiceRootUrl()
        }
        assertThrows(MissingReferenceDataServiceUrlException::class.java) {
            rootHasWrongType.requireReferenceDataServiceRootUrl()
        }
    }

    private fun configurationFor(contents: String): Pair<AppConfiguration, AssetManager> {
        val assets = mock<AssetManager>()
        whenever(assets.open(CONFIG_ASSET)).thenReturn(ByteArrayInputStream(contents.toByteArray()))
        return configurationFor(assets) to assets
    }

    private fun configurationFor(assets: AssetManager): AppConfiguration {
        val context = mock<Context>()
        whenever(context.assets).thenReturn(assets)
        return AppConfiguration(context)
    }
}
