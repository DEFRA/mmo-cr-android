package uk.gov.defra.mmocatchrecord.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Missing or invalid packaged service root; no backend fallback is permitted. */
class MissingReferenceDataServiceUrlException :
    IllegalStateException(
        "API_BASE_URL is missing or invalid; reference-data networking is unavailable.",
    )

/** Builds reference-data API URLs from the packaged service root. */
object ReferenceDataServiceConfig {
    private const val API_PATH = "api/v1/reference-data/"

    /** @throws MissingReferenceDataServiceUrlException if [configuredUrl] is blank or invalid. */
    fun requireServiceRootUrl(configuredUrl: String): String {
        val trimmedUrl = configuredUrl.trim()
        val parsedUrl = trimmedUrl.toHttpUrlOrNull()
        if (!isValidServiceRootUrl(trimmedUrl, parsedUrl)) {
            throw MissingReferenceDataServiceUrlException()
        }
        return if (trimmedUrl.endsWith("/")) trimmedUrl else "$trimmedUrl/"
    }

    fun apiBaseUrl(serviceRootUrl: String): String = serviceRootUrl + API_PATH

    private fun isValidServiceRootUrl(
        configuredUrl: String,
        parsedUrl: HttpUrl?,
    ): Boolean {
        if (configuredUrl.isBlank() || parsedUrl == null) return false
        return parsedUrl.isHttps && !parsedUrl.hasCredentialsOrParameters()
    }

    private fun HttpUrl.hasCredentialsOrParameters(): Boolean =
        username.isNotEmpty() || password.isNotEmpty() || query != null || fragment != null
}
