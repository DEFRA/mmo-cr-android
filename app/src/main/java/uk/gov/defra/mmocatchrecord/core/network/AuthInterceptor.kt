package uk.gov.defra.mmocatchrecord.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * Adds an `Authorization` bearer header when [tokenProvider] supplies a non-blank token (ADR 0016);
 * omits it otherwise and never logs the token — see ADR 0016 for why `runBlocking` here is safe.
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentTokenOrNull()
        val request = chain.request()
        return if (token.isNullOrBlank()) {
            chain.proceed(request)
        } else {
            chain.proceed(
                request
                    .newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build(),
            )
        }
    }

    // Deliberately broad: a future real TokenProvider must never crash a request; always degrade.
    @Suppress("TooGenericExceptionCaught")
    private fun currentTokenOrNull(): String? =
        try {
            runBlocking { withTimeoutOrNull(TOKEN_LOOKUP_TIMEOUT_MS) { tokenProvider.currentToken() } }
        } catch (error: Exception) {
            Timber.w(error, "AuthInterceptor: token lookup failed, sending request unauthenticated")
            null
        }

    private companion object {
        const val TOKEN_LOOKUP_TIMEOUT_MS = 2_000L
    }
}
