package uk.gov.defra.mmocatchrecord.feature.signin.data

import uk.gov.defra.mmocatchrecord.core.network.TokenProvider
import uk.gov.defra.mmocatchrecord.core.security.SessionStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * [TokenProvider] backed solely by MSAL's own cache (ADR 0019 Phase 2.1d); never persists or logs the
 * token, and degrades to `null` on any failure so the interceptor sends the request unauthenticated.
 */
internal class MsalTokenProvider(
    private val nativeAuthClient: NativeAuthClient,
    private val sessionStore: SessionStore,
) : TokenProvider {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun currentToken(): String? {
        // Signed out locally: never touch MSAL (review fix 1.2) — also closes the fix-2 token-leak gate.
        if (sessionStore.getLastAuthenticatedAtMillis() == null) return null
        return try {
            nativeAuthClient.accessToken(listOf(API_SCOPE), forceRefresh = false)?.takeIf { it.isNotBlank() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (thrown: Exception) {
            // Already logged (class name only) inside NativeAuthClient.accessToken; just degrade here.
            null
        }
    }
}
