package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.di.CatchRecordJson
import java.io.IOException
import javax.inject.Inject

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_THRESHOLD = 500

/** Turns [CatchRecordApi]'s raw [Response]s into typed DTOs/exceptions (ADR 0026). */
internal class CatchRecordRemoteDataSource
    @Inject
    constructor(
        private val api: CatchRecordApi,
        @CatchRecordJson private val json: Json,
    ) {
        suspend fun createDraft(
            payload: CreateDraftCatchRecordPayloadDto,
            idempotencyKey: String? = null,
        ): StandardSaveResponseDto = unwrap("createDraft", api.createDraft(idempotencyKey, payload))

        suspend fun replace(
            id: String,
            ifMatch: String,
            payload: CompleteReplacementPayloadDto,
        ): StandardSaveResponseDto = unwrap("replace", api.replace(id, ifMatch, payload))

        suspend fun submit(
            id: String,
            ifMatch: String,
            idempotencyKey: String? = null,
        ): SubmissionResponseDto = unwrap("submit", api.submit(id, ifMatch, idempotencyKey))

        suspend fun list(
            limit: Int? = null,
            status: String? = null,
        ): CatchRecordListResponseDto = unwrap("list", api.list(limit, status))

        suspend fun retrieve(id: String): RetrieveCatchRecordResponseDto = unwrap("retrieve", api.retrieve(id))

        suspend fun recover(id: String): CatchRecordRecoverySnapshotDto = unwrap("recover", api.recover(id))

        private fun <T> unwrap(
            operation: String,
            response: Response<T>,
        ): T {
            if (!response.isSuccessful) throw toApiException(operation, response)
            return response.body() ?: throw CatchRecordMalformedResponseException(operation)
        }

        private fun toApiException(
            operation: String,
            response: Response<*>,
        ): CatchRecordApiException {
            val httpStatusCode = response.code()
            val errorBody = parseErrorBody(response)
            if (httpStatusCode == HTTP_UNAUTHORIZED) {
                return CatchRecordUnauthorizedException(operation, errorBody?.correlationId)
            }
            val retryable =
                httpStatusCode >= HTTP_SERVER_ERROR_THRESHOLD ||
                    httpStatusCode == HTTP_REQUEST_TIMEOUT ||
                    httpStatusCode == HTTP_TOO_MANY_REQUESTS
            return CatchRecordHttpException(
                operation,
                httpStatusCode,
                errorBody?.code,
                errorBody?.correlationId,
                retryable,
            )
        }

        private fun parseErrorBody(response: Response<*>): ErrorResponseDto? {
            val raw = response.errorBody()?.string() ?: return null
            return try {
                json.decodeFromString<ErrorResponseDto>(raw)
            } catch (error: SerializationException) {
                Timber.w(error, "CatchRecord: malformed error body, falling back to status-based retryable")
                null
            } catch (error: IOException) {
                Timber.w(error, "CatchRecord: error body unreadable, falling back to status-based retryable")
                null
            }
        }
    }
