package uk.gov.defra.mmocatchrecord.core.network

/**
 * Supplies the bearer token for reference-data API calls (ADR 0016), chosen from the packaged app
 * configuration in [uk.gov.defra.mmocatchrecord.di.NetworkModule]; real Entra sign-in uses MSAL (ADR 0019).
 */
fun interface TokenProvider {
    /** `null` (or blank) means "send no `Authorization` header". */
    suspend fun currentToken(): String?
}
