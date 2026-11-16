package uk.gov.defra.mmocatchrecord.core.config

import android.content.Context
import android.content.res.AssetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.BuildConfig
import uk.gov.defra.mmocatchrecord.core.network.ReferenceDataServiceConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val APP_CONFIG_ASSET = "mmocr_app_config.json"

private data class PackagedAppConfig(
    val apiBaseUrl: String,
    val useStubAuth: Boolean,
    val useFakeSignIn: Boolean,
    val msalClientId: String,
    val msalAuthorityUrl: String,
    val msalRedirectUri: String,
)

/** Reads the packaged runtime configuration once and exposes validated reference-data settings. */
@Singleton
class AppConfiguration
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val config: PackagedAppConfig? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            readConfig(context.assets)
        }

        val useStubAuth: Boolean?
            get() = config?.useStubAuth

        val useFakeSignIn: Boolean?
            get() = config?.useFakeSignIn

        /** Base URL shared by every backend service; reference data hangs off it (see ReferenceDataServiceConfig). */
        fun requireApiBaseUrl(): String = ReferenceDataServiceConfig.requireApiBaseUrl(config?.apiBaseUrl.orEmpty())

        fun requireReferenceDataServiceRootUrl(): String =
            ReferenceDataServiceConfig.serviceRootUrl(requireApiBaseUrl())

        fun referenceDataServiceRootUrlOrNull(): String? =
            runCatching { requireReferenceDataServiceRootUrl() }.getOrNull()

        /** MSAL native-auth config for this stage; unprovisioned values fail fast in MsalNativeAuthConfig. */
        fun msalNativeAuthConfigJson(): String = msalConfigJson(config, BuildConfig.DEBUG)

        fun apiHostForStartupLog(): String? =
            config
                ?.apiBaseUrl
                ?.toHttpUrlOrNull()
                ?.host

        private fun readConfig(assets: AssetManager): PackagedAppConfig? =
            try {
                assets.open(APP_CONFIG_ASSET).bufferedReader().use { reader ->
                    parseConfig(reader.readText())
                }
            } catch (_: IOException) {
                logUnavailableConfig()
                null
            } catch (_: SerializationException) {
                logUnavailableConfig()
                null
            }

        private fun parseConfig(contents: String): PackagedAppConfig {
            val root =
                Json.parseToJsonElement(contents) as? JsonObject
                    ?: throw SerializationException("App config must be a JSON object.")
            val apiBaseUrl = (root["API_BASE_URL"] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            val useStubAuthPrimitive = root["USE_STUB_AUTH"] as? JsonPrimitive
            val useStubAuth =
                useStubAuthPrimitive
                    ?.takeUnless(JsonPrimitive::isString)
                    ?.content
                    ?.let { value ->
                        when (value) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    }
            if (apiBaseUrl == null || useStubAuth == null) {
                throw SerializationException("App config contains missing or incorrectly typed values.")
            }
            return PackagedAppConfig(
                apiBaseUrl = apiBaseUrl,
                useStubAuth = useStubAuth,
                useFakeSignIn = root.booleanOrFalse("USE_FAKE_SIGN_IN"),
                msalClientId = root.optionalString("MSAL_CLIENT_ID"),
                msalAuthorityUrl = root.optionalString("MSAL_AUTHORITY_URL"),
                msalRedirectUri = root.optionalString("MSAL_REDIRECT_URI"),
            )
        }

        private fun JsonObject.optionalString(key: String): String =
            (this[key] as? JsonPrimitive)
                ?.takeIf(JsonPrimitive::isString)
                ?.content
                ?.trim()
                .orEmpty()

        private fun JsonObject.booleanOrFalse(key: String): Boolean =
            (this[key] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.content == "true"

        private fun logUnavailableConfig() {
            Timber.w("Packaged app configuration is missing or malformed; runtime configuration is unavailable.")
        }
    }

/** Builds MSAL's native-auth JSON; a missing client ID or authority yields the unprovisioned shape. */
private fun msalConfigJson(
    config: PackagedAppConfig?,
    debug: Boolean,
): String =
    buildJsonObject {
        put("client_id", config?.msalClientId.orEmpty())
        putJsonArray("authorities") {
            val authority = config?.msalAuthorityUrl.orEmpty()
            if (authority.isNotEmpty()) {
                addJsonObject {
                    put("type", "CIAM")
                    put("authority_url", authority)
                }
            }
        }
        putJsonArray("challenge_types") {
            add("oob")
            add("password")
        }
        val redirectUri = config?.msalRedirectUri.orEmpty()
        if (redirectUri.isNotEmpty()) {
            put("redirect_uri", redirectUri)
            put("authorization_user_agent", "BROWSER")
        }
        putJsonObject("logging") {
            put("pii_enabled", false)
            put("log_level", if (debug) "VERBOSE" else "WARNING")
            put("logcat_enabled", debug)
        }
    }.toString()
