package uk.gov.defra.mmocatchrecord.core.network

/**
 * Thrown when a build flavor has no reference-data service root configured (currently only `prod`,
 * deliberately — ADR 0015) and something attempts to use it, rather than silently pointing at DEV.
 */
class MissingReferenceDataServiceUrlException :
    IllegalStateException(
        "REFERENCE_DATA_SERVICE_URL is empty for this build flavor; reference-data networking is unavailable.",
    )

/**
 * Derives the reference-data API base URL from the configured service root (ADR 0015): `/health` lives
 * at the service root, while every other documented route sits under `api/v1/reference-data/`.
 */
object ReferenceDataServiceConfig {
    private const val API_PATH = "api/v1/reference-data/"

    /** @throws MissingReferenceDataServiceUrlException if [configuredUrl] is blank. */
    fun requireServiceRootUrl(configuredUrl: String): String {
        if (configuredUrl.isBlank()) throw MissingReferenceDataServiceUrlException()
        return if (configuredUrl.endsWith("/")) configuredUrl else "$configuredUrl/"
    }

    fun apiBaseUrl(serviceRootUrl: String): String = serviceRootUrl + API_PATH
}
