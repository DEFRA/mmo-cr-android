package uk.gov.defra.mmocatchrecord.core.network

/** No service root configured for this flavor (`prod`, deliberately). See ADR 0022. */
class MissingCatchRecordingServiceUrlException :
    IllegalStateException(
        "CATCH_RECORDING_SERVICE_URL is empty for this build flavor; catch-record networking is unavailable.",
    )

/** Validates the configured catch-recording service root; routes sit directly under it. See ADR 0022. */
object CatchRecordingServiceConfig {
    /** @throws MissingCatchRecordingServiceUrlException if [configuredUrl] is blank. */
    fun requireServiceRootUrl(configuredUrl: String): String {
        if (configuredUrl.isBlank()) throw MissingCatchRecordingServiceUrlException()
        return if (configuredUrl.endsWith("/")) configuredUrl else "$configuredUrl/"
    }
}
