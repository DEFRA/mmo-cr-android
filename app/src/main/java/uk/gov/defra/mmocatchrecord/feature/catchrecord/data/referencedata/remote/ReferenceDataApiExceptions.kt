package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

internal const val SUPPORTED_REFERENCE_DATA_SCHEMA_VERSION = "1.0"

/** Base type for every reference-data remote failure; [retryable] feeds [SafeErrorMapper]. */
internal sealed class ReferenceDataApiException(
    message: String,
    open val retryable: Boolean,
) : Exception(message)

internal class ReferenceDataHttpException(
    val dataset: String,
    val httpStatusCode: Int,
    val errorCode: String?,
    override val retryable: Boolean,
) : ReferenceDataApiException(
        "Reference-data '$dataset' request failed with HTTP $httpStatusCode" +
            (errorCode?.let { " ($it)" } ?: ""),
        retryable,
    )

/** A missing/invalid/expired bearer token (401) — distinct from other 4xx so a future re-auth flow can
 *  target it specifically; still terminal for the current request (ADR 0015).
 */
internal class ReferenceDataUnauthorizedException(
    val dataset: String,
) : ReferenceDataApiException("Reference-data '$dataset' request was unauthorized", retryable = false)

internal class ReferenceDataMalformedResponseException(
    val dataset: String,
) : ReferenceDataApiException("Reference-data '$dataset' response body was missing or malformed", retryable = false)

/** The server returned a `schemaVersion` this app build does not understand; never cached (ADR 0015). */
internal class ReferenceDataSchemaVersionException(
    val dataset: String,
    val actualSchemaVersion: String,
) : ReferenceDataApiException(
        "Unsupported schemaVersion '$actualSchemaVersion' for dataset '$dataset'; " +
            "expected '$SUPPORTED_REFERENCE_DATA_SCHEMA_VERSION'",
        retryable = false,
    )

internal fun requireSupportedSchemaVersion(
    dataset: String,
    schemaVersion: String,
) {
    if (schemaVersion != SUPPORTED_REFERENCE_DATA_SCHEMA_VERSION) {
        throw ReferenceDataSchemaVersionException(dataset, schemaVersion)
    }
}
