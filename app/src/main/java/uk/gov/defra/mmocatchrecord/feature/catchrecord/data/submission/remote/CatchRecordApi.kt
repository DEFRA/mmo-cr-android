package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Catch-record submission endpoints on the catch-recording service root (ADR 0022). Mirrors
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataApi]: every
 * method returns [Response] so error bodies and headers (`ETag`, `If-Match`) are inspectable by the
 * caller. The two `GET`s exist only for drafted/submitted-record recovery (OQ-1) — not general listing.
 */
internal interface CatchRecordApi {
    @POST("v1/catch-records")
    suspend fun createDraft(
        @Header("Idempotency-Key") idempotencyKey: String?,
        @Body payload: CreateDraftCatchRecordPayloadDto,
    ): Response<StandardSaveResponseDto>

    @PUT("v1/catch-records/{id}")
    suspend fun replace(
        @Path("id") id: String,
        @Header("If-Match") ifMatch: String,
        @Body payload: CompleteReplacementPayloadDto,
    ): Response<StandardSaveResponseDto>

    @POST("v1/catch-records/{id}/submission")
    suspend fun submit(
        @Path("id") id: String,
        @Header("If-Match") ifMatch: String,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): Response<SubmissionResponseDto>

    @GET("v1/catch-records")
    suspend fun list(
        @Query("limit") limit: Int? = null,
        @Query("status") status: String? = null,
    ): Response<CatchRecordListResponseDto>

    @GET("v1/catch-records/{id}")
    suspend fun retrieve(
        @Path("id") id: String,
    ): Response<RetrieveCatchRecordResponseDto>
}
