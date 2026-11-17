package uk.gov.defra.mmocatchrecord.core.network

/** Builds catch-recording URLs as API_BASE_URL + `catch-recording-service/` (ADR 0026, mirrors reference data). */
object CatchRecordingServiceConfig {
    private const val SERVICE_PATH = "catch-recording-service/"

    fun serviceRootUrl(apiBaseUrl: String): String = apiBaseUrl + SERVICE_PATH
}
