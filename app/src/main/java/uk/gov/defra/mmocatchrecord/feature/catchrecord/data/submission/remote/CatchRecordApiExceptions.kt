package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote

/** Base type for every catch-record remote failure; [retryable] feeds the Phase 5 status/retry logic. */
internal sealed class CatchRecordApiException(
    message: String,
    open val retryable: Boolean,
) : Exception(message)

/** A non-2xx response; `retryable` is derived from its HTTP status — see ADR 0026. */
internal class CatchRecordHttpException(
    val operation: String,
    val httpStatusCode: Int,
    val serverCode: String?,
    val correlationId: String?,
    override val retryable: Boolean,
) : CatchRecordApiException(
        "Catch-record '$operation' request failed with HTTP $httpStatusCode" +
            (serverCode?.let { " ($it)" } ?: ""),
        retryable,
    )

/** A missing/invalid/expired bearer token (401) — terminal for the current request (ADR 0026). */
internal class CatchRecordUnauthorizedException(
    val operation: String,
    val correlationId: String?,
) : CatchRecordApiException("Catch-record '$operation' request was unauthorized", retryable = false)

internal class CatchRecordMalformedResponseException(
    val operation: String,
) : CatchRecordApiException("Catch-record '$operation' response body was missing or malformed", retryable = false)
