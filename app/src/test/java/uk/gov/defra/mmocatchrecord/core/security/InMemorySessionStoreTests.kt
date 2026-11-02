package uk.gov.defra.mmocatchrecord.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemorySessionStoreTests {
    @Test
    fun `a new store has neither timestamp`() =
        runTest {
            val store = InMemorySessionStore()

            assertNull(store.getLastAuthenticatedAtMillis())
            assertNull(store.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `initial values are surfaced independently`() =
        runTest {
            val store =
                InMemorySessionStore(
                    initialLastAuthenticatedAtMillis = 10L,
                    initialLastVerifiedOnlineAtMillis = 20L,
                )

            assertEquals(10L, store.getLastAuthenticatedAtMillis())
            assertEquals(20L, store.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `recording app-lock re-entry never advances the online verification timestamp`() =
        runTest {
            val store = InMemorySessionStore(initialLastVerifiedOnlineAtMillis = 20L)

            store.recordAuthenticatedNow(999L)

            assertEquals(999L, store.getLastAuthenticatedAtMillis())
            assertEquals(20L, store.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `recording an online verification never advances the app-lock timestamp`() =
        runTest {
            val store = InMemorySessionStore(initialLastAuthenticatedAtMillis = 10L)

            store.recordVerifiedOnlineNow(999L)

            assertEquals(10L, store.getLastAuthenticatedAtMillis())
            assertEquals(999L, store.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `clearing resets both timestamps`() =
        runTest {
            val store = InMemorySessionStore(10L, 20L)

            store.clear()

            assertNull(store.getLastAuthenticatedAtMillis())
            assertNull(store.getLastVerifiedOnlineAtMillis())
        }
}
