package uk.gov.defra.mmocatchrecord.core.network

/** Bearer-token provider selected by the packaged app configuration. */
fun interface TokenProvider {
    /** `null` (or blank) means "send no `Authorization` header". */
    suspend fun currentToken(): String?
}
