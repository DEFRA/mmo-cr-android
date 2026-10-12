package uk.gov.defra.mmocatchrecord.core.network

/**
 * Supplies the bearer token for reference-data API calls (ADR 0016). Implementations are bound per
 * flavor in [uk.gov.defra.mmocatchrecord.di.NetworkModule] — real auth is not yet designed.
 */
interface TokenProvider {
    /** `null` (or blank) means "send no `Authorization` header". */
    suspend fun currentToken(): String?
}
