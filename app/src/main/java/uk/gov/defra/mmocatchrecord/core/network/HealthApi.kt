package uk.gov.defra.mmocatchrecord.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Unauthenticated liveness check at the reference-data service **root** (not under `api/v1/...`) — see
 * ADR 0016 and the service's `docs/api-reference.md` "Authentication and permissions" section.
 */
interface HealthApi {
    @GET("health")
    suspend fun health(): HealthResponse
}

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: String,
)
