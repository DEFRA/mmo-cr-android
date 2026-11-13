package uk.gov.defra.mmocatchrecord.core.network

/**
 * Thrown when a build flavor has no catch-recording service root configured (currently only `prod`,
 * deliberately — ADR 0022) and something attempts to use it, rather than silently pointing at DEV.
 */
class MissingCatchRecordingServiceUrlException :
    IllegalStateException(
        "CATCH_RECORDING_SERVICE_URL is empty for this build flavor; catch-record networking is unavailable.",
    )

/**
 * Validates the configured catch-recording service root (ADR 0022). Unlike the reference-data service,
 * every documented route sits directly under the root — no extra `api/v1/...` path segment.
 */
object CatchRecordingServiceConfig {
    /** @throws MissingCatchRecordingServiceUrlException if [configuredUrl] is blank. */
    fun requireServiceRootUrl(configuredUrl: String): String {
        if (configuredUrl.isBlank()) throw MissingCatchRecordingServiceUrlException()
        return if (configuredUrl.endsWith("/")) configuredUrl else "$configuredUrl/"
    }
}
