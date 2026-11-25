package uk.gov.defra.mmocatchrecord.core.config

import android.content.Context
import android.content.res.AssetManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.core.network.MissingReferenceDataServiceUrlException
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalNativeAuthConfig
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

private const val CONFIG_ASSET = "mmocr_app_config.json"

class AppConfigurationTests {
    @Test
    fun `loads valid config once and exposes the normalized service root and startup metadata`() {
        val (configuration, assets) =
            configurationFor("""{"API_BASE_URL":"https://example.test","USE_STUB_AUTH":true}""")

        assertEquals("https://example.test/", configuration.requireApiBaseUrl())
        assertEquals(
            "https://example.test/reference-data-service/",
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

    @Test
    fun `sign-in settings build a provisioned MSAL config with the browser fallback`() {
        val configuration =
            configurationFor(
                """{"API_BASE_URL":"https://example.test/","USE_STUB_AUTH":false,"USE_FAKE_SIGN_IN":false,""" +
                    """"MSAL_CLIENT_ID":"client-1",""" +
                    """"MSAL_AUTHORITY_URL":"https://tenant.ciamlogin.com/tenant.onmicrosoft.com/",""" +
                    """"MSAL_REDIRECT_URI":"msauth://pkg/hash"}""",
            ).first

        val msal = Json.parseToJsonElement(configuration.msalNativeAuthConfigJson()).jsonObject

        assertFalse(configuration.useFakeSignIn!!)
        assertEquals("https://example.test/reference-data-service/", configuration.referenceDataServiceRootUrlOrNull())
        assertEquals("client-1", msal["client_id"]!!.jsonPrimitive.content)
        val authority = msal["authorities"]!!.jsonArray.single().jsonObject
        assertEquals("CIAM", authority["type"]!!.jsonPrimitive.content)
        assertEquals(
            "https://tenant.ciamlogin.com/tenant.onmicrosoft.com/",
            authority["authority_url"]!!.jsonPrimitive.content,
        )
        assertEquals("msauth://pkg/hash", msal["redirect_uri"]!!.jsonPrimitive.content)
        assertEquals("BROWSER", msal["authorization_user_agent"]!!.jsonPrimitive.content)
        assertEquals(false, msal["logging"]!!.jsonObject["pii_enabled"]!!.jsonPrimitive.boolean)
        assertTrue(MsalNativeAuthConfig.isProvisioned(configuration.msalNativeAuthConfigJson()))
    }

    @Test
    fun `missing or blank sign-in settings give an unprovisioned MSAL config and no fake sign-in`() {
        val blank =
            configurationFor(
                """{"API_BASE_URL":"","USE_STUB_AUTH":false,"USE_FAKE_SIGN_IN":true,""" +
                    """"MSAL_CLIENT_ID":" ","MSAL_AUTHORITY_URL":""}""",
            ).first
        val absent = configurationFor("""{"API_BASE_URL":"https://example.test/","USE_STUB_AUTH":true}""").first
        val unavailable = configurationFor("[]").first

        assertTrue(blank.useFakeSignIn!!)
        assertNull(blank.referenceDataServiceRootUrlOrNull())
        assertFalse(MsalNativeAuthConfig.isProvisioned(blank.msalNativeAuthConfigJson()))
        val noRedirect = Json.parseToJsonElement(blank.msalNativeAuthConfigJson()).jsonObject
        assertFalse(noRedirect.containsKey("redirect_uri"))
        assertFalse(absent.useFakeSignIn!!)
        assertFalse(MsalNativeAuthConfig.isProvisioned(absent.msalNativeAuthConfigJson()))
        assertNull(unavailable.useFakeSignIn)
        assertFalse(MsalNativeAuthConfig.isProvisioned(unavailable.msalNativeAuthConfigJson()))
    }

    @Test
    fun `reset code lifetime defaults to five minutes when the key is absent or the config is unavailable`() {
        val absent = configurationFor("""{"API_BASE_URL":"https://example.test","USE_STUB_AUTH":true}""").first
        val unavailable = configurationFor("{ invalid json").first

        assertEquals(5, absent.passwordResetCodeLifetimeMinutes())
        assertEquals(5, unavailable.passwordResetCodeLifetimeMinutes())
    }

    @Test
    fun `reset code lifetime reads a valid packaged value`() {
        val configuration =
            configurationFor(
                lifetimeJson("15"),
            ).first

        assertEquals(15, configuration.passwordResetCodeLifetimeMinutes())
    }

    @Test
    fun `reset code lifetime falls back to the default for blank, non-numeric or out-of-range values`() {
        listOf("", "abc", "0", "-3", "61").forEach { value ->
            val configuration =
                configurationFor(
                    lifetimeJson(value),
                ).first

            assertEquals(value, 5, configuration.passwordResetCodeLifetimeMinutes())
        }
    }

    private fun lifetimeJson(value: String) =
        """{"API_BASE_URL":"https://example.test","USE_STUB_AUTH":true,"PASSWORD_RESET_CODE_LIFETIME_MINUTES":"$value"}"""

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
