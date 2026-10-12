package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

private const val MOBILE_VIEW = "mobile"

/**
 * Reference-data collection endpoints on the `api/v1/reference-data/` base (ADR 0016). Every method
 * returns [Response] (not the bare body) so a bodyless `304 Not Modified` is observable by the caller.
 */
internal interface ReferenceDataApi {
    @GET("manifest")
    suspend fun manifest(
        @Header("If-None-Match") ifNoneMatch: String?,
    ): Response<ManifestDto>

    @GET("vessels")
    suspend fun vessels(
        @Query("view") view: String = MOBILE_VIEW,
        @Header("If-None-Match") ifNoneMatch: String?,
    ): Response<ReferenceDataEnvelopeDto<VesselMobileDto>>

    @GET("ports")
    suspend fun ports(
        @Query("view") view: String = MOBILE_VIEW,
        @Header("If-None-Match") ifNoneMatch: String?,
    ): Response<ReferenceDataEnvelopeDto<PortMobileDto>>

    @GET("species")
    suspend fun species(
        @Query("view") view: String = MOBILE_VIEW,
        @Header("If-None-Match") ifNoneMatch: String?,
    ): Response<ReferenceDataEnvelopeDto<SpeciesMobileDto>>

    @GET("gears")
    suspend fun gears(
        @Query("view") view: String = MOBILE_VIEW,
        @Query("vesselLengthMetres") vesselLengthMetres: Double?,
        @Header("If-None-Match") ifNoneMatch: String?,
    ): Response<GearsEnvelopeDto>
}
