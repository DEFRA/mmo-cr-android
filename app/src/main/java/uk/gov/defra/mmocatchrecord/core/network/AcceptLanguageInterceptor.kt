package uk.gov.defra.mmocatchrecord.core.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository

/**
 * Sends the current app language preference (ADR 0012) as `Accept-Language`, degrading to no header
 * (never throws, never blocks indefinitely) if the DataStore-backed read fails — see ADR 0015.
 */
class AcceptLanguageInterceptor(
    private val appLanguageRepository: AppLanguageRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val language = currentLanguageOrNull()
        val request = chain.request()
        return if (language.isNullOrBlank()) {
            chain.proceed(request)
        } else {
            chain.proceed(request.newBuilder().header("Accept-Language", language).build())
        }
    }

    // Deliberately broad: a DataStore read failure must never crash a request; always degrade.
    @Suppress("TooGenericExceptionCaught")
    private fun currentLanguageOrNull(): String? =
        try {
            runBlocking {
                withTimeoutOrNull(LANGUAGE_LOOKUP_TIMEOUT_MS) { appLanguageRepository.language.first() }
            }
        } catch (error: Exception) {
            Timber.w(error, "AcceptLanguageInterceptor: language lookup failed, sending no header")
            null
        }

    private companion object {
        const val LANGUAGE_LOOKUP_TIMEOUT_MS = 2_000L
    }
}
