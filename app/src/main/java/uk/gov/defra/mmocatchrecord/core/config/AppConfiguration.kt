package uk.gov.defra.mmocatchrecord.core.config

import android.content.Context
import android.content.res.AssetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.network.ReferenceDataServiceConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val APP_CONFIG_ASSET = "mmocr_app_config.json"

private data class PackagedAppConfig(
    val apiBaseUrl: String,
    val useStubAuth: Boolean,
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

        fun requireReferenceDataServiceRootUrl(): String =
            ReferenceDataServiceConfig.requireServiceRootUrl(config?.apiBaseUrl.orEmpty())

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
            return PackagedAppConfig(apiBaseUrl, useStubAuth)
        }

        private fun logUnavailableConfig() {
            Timber.w("Packaged app configuration is missing or malformed; runtime configuration is unavailable.")
        }
    }
