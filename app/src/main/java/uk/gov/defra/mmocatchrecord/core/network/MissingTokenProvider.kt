package uk.gov.defra.mmocatchrecord.core.network

/**
 * `prod` binding (ADR 0016): there is no real token source yet, so every request is sent unauthenticated
 * (the empty `prod` service URL already fails fast before any request would be attempted).
 */
class MissingTokenProvider : TokenProvider {
    override suspend fun currentToken(): String? = null
}
