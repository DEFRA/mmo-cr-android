package uk.gov.defra.mmocatchrecord.core.security

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [DataStoreSessionStore] is tested against an in-memory [DataStore]; the real file and `AndroidKeyStore`
 * wiring is covered by `SessionStoreEncryptionSmokeTest` on device.
 */
class DataStoreSessionStoreTests {
    private val dataStore = FakeDataStore(SessionMetadata())
    private val store = DataStoreSessionStore(dataStore)

    @Test
    fun `an untouched store reports neither timestamp`() =
        runTest {
            assertNull(store.getLastAuthenticatedAtMillis())
            assertNull(store.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `both timestamps round-trip through the persisted metadata`() =
        runTest {
            store.recordAuthenticatedNow(APP_LOCK_AT)
            store.recordVerifiedOnlineNow(ONLINE_AT)

            assertEquals(APP_LOCK_AT, store.getLastAuthenticatedAtMillis())
            assertEquals(ONLINE_AT, store.getLastVerifiedOnlineAtMillis())
            assertEquals(SessionMetadata(APP_LOCK_AT, ONLINE_AT), dataStore.current())
        }

    @Test
    fun `recording app-lock re-entry never advances the online verification timestamp`() =
        runTest {
            store.recordVerifiedOnlineNow(ONLINE_AT)

            store.recordAuthenticatedNow(APP_LOCK_AT)

            assertEquals(APP_LOCK_AT, store.getLastAuthenticatedAtMillis())
            assertEquals(ONLINE_AT, store.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `recording an online verification never advances the app-lock timestamp`() =
        runTest {
            store.recordAuthenticatedNow(APP_LOCK_AT)

            store.recordVerifiedOnlineNow(ONLINE_AT)

            assertEquals(APP_LOCK_AT, store.getLastAuthenticatedAtMillis())
            assertEquals(ONLINE_AT, store.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `clearing on sign-out removes both timestamps`() =
        runTest {
            store.recordAuthenticatedNow(APP_LOCK_AT)
            store.recordVerifiedOnlineNow(ONLINE_AT)

            store.clear()

            assertNull(store.getLastAuthenticatedAtMillis())
            assertNull(store.getLastVerifiedOnlineAtMillis())
            assertEquals(SessionMetadata(), dataStore.current())
        }

    private class FakeDataStore(
        initial: SessionMetadata,
    ) : DataStore<SessionMetadata> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<SessionMetadata> = state

        override suspend fun updateData(transform: suspend (t: SessionMetadata) -> SessionMetadata): SessionMetadata {
            val updated = transform(state.value)
            state.update { updated }
            return updated
        }

        fun current(): SessionMetadata = state.value
    }

    private companion object {
        const val APP_LOCK_AT = 1_700_000_000_000L
        const val ONLINE_AT = 1_600_000_000_000L
    }
}
