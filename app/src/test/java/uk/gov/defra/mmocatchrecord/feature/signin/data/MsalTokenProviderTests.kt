package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore
import kotlin.coroutines.cancellation.CancellationException

/** [MsalTokenProvider] against [FakeNativeAuthClient] (ADR 0019 2.1d) — never persists/logs the token. */
class MsalTokenProviderTests {
    private suspend fun signedInSessionStore() = InMemorySessionStore().apply { recordSignedInNow(1_000L) }

    @Test
    fun `returns the token from the native-auth client's own cache`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenResult = "fixture-token")

            val token = MsalTokenProvider(client, signedInSessionStore()) { FIXTURE_SCOPE }.currentToken()

            assertEquals("fixture-token", token)
            assertEquals(listOf(FIXTURE_SCOPE), client.lastAccessTokenScopes)
            assertEquals(false, client.lastForceRefresh)
        }

    @Test
    fun `returns null when there is no cached account`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenResult = null)

            assertNull(MsalTokenProvider(client, signedInSessionStore()) { FIXTURE_SCOPE }.currentToken())
        }

    @Test
    fun `returns null for a blank token`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenResult = "   ")

            assertNull(MsalTokenProvider(client, signedInSessionStore()) { FIXTURE_SCOPE }.currentToken())
        }

    @Test
    fun `degrades to null when the client throws`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenThrown = IllegalStateException("boom"))

            assertNull(MsalTokenProvider(client, signedInSessionStore()) { FIXTURE_SCOPE }.currentToken())
        }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates out uncaught`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenThrown = CancellationException("unwind"))

            MsalTokenProvider(client, signedInSessionStore()) { FIXTURE_SCOPE }.currentToken()
        }

    @Test
    fun `never forces a refresh, so a cache hit is never mistaken for an online verification`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenResult = "fixture-token")

            MsalTokenProvider(client, signedInSessionStore()) { FIXTURE_SCOPE }.currentToken()

            assertTrue(client.lastForceRefresh == false)
        }

    @Test
    fun `returns null without touching MSAL when the app session is signed out`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenResult = "fixture-token")

            val token = MsalTokenProvider(client, InMemorySessionStore()) { FIXTURE_SCOPE }.currentToken()

            assertNull(token)
            assertNull(client.lastAccessTokenScopes)
        }

    @Test
    fun `a cleared app session gates a still-cached MSAL account (review fix 2 — no token leak)`() =
        runTest {
            // MSAL's own cache still has an account, but the app session was cleared on sign-out.
            val client = FakeNativeAuthClient(accessTokenResult = "fixture-token")
            val sessionStore = signedInSessionStore()
            sessionStore.clear()

            val token = MsalTokenProvider(client, sessionStore) { FIXTURE_SCOPE }.currentToken()

            assertNull(token)
            assertNull(client.lastAccessTokenScopes)
        }

    @Test
    fun `a blank API scope returns null without touching MSAL`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenResult = "fixture-token")

            val token = MsalTokenProvider(client, signedInSessionStore()) { "  " }.currentToken()

            assertNull(token)
            assertNull(client.lastAccessTokenScopes)
        }

    @Test
    fun `the configured API scope is trimmed before it is requested`() =
        runTest {
            val client = FakeNativeAuthClient(accessTokenResult = "fixture-token")

            MsalTokenProvider(client, signedInSessionStore()) { " $FIXTURE_SCOPE " }.currentToken()

            assertEquals(listOf(FIXTURE_SCOPE), client.lastAccessTokenScopes)
        }

    private companion object {
        const val FIXTURE_SCOPE = "api://fixture-app/access_as_user"
    }
}
