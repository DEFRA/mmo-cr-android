package uk.gov.defra.mmocatchrecord.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Missing or invalid packaged API base URL; no backend fallback is permitted. */
class MissingReferenceDataServiceUrlException :
    IllegalStateException(
        "API_BASE_URL is missing or invalid; reference-data networking is unavailable.",
    )

/** Builds reference-data URLs as API_BASE_URL + `reference-data-service/` + endpoint (same base as iOS). */
object ReferenceDataServiceConfig {
    private const val SERVICE_PATH = "reference-data-service/"
    private const val API_PATH = "api/v1/reference-data/"

    /** The validated API base URL with a trailing slash; @throws MissingReferenceDataServiceUrlException. */
    fun requireApiBaseUrl(configuredUrl: String): String {
        val trimmedUrl = configuredUrl.trim()
        val parsedUrl = trimmedUrl.toHttpUrlOrNull()
        if (!isValidServiceRootUrl(trimmedUrl, parsedUrl)) {
            throw MissingReferenceDataServiceUrlException()
        }
        return if (trimmedUrl.endsWith("/")) trimmedUrl else "$trimmedUrl/"
    }

    fun serviceRootUrl(apiBaseUrl: String): String = apiBaseUrl + SERVICE_PATH

    fun referenceDataApiUrl(serviceRootUrl: String): String = serviceRootUrl + API_PATH

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
