package uk.gov.defra.mmocatchrecord.core.network

/**
 * `local`/`dev` binding (ADR 0015): the reference-data service's DEV auth stub accepts any non-empty
 * bearer token, so a fixed, non-secret literal is sufficient until real authentication exists.
 */
class StubTokenProvider : TokenProvider {
    override suspend fun currentToken(): String = STUB_TOKEN

    private companion object {
        const val STUB_TOKEN = "local-stub"
    }
}
