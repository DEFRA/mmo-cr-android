package uk.gov.defra.mmocatchrecord.core.security

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented cover for the real `AndroidKeyStore` path (ADR 0006/0019): Robolectric has no
 * `AndroidKeyStore` provider, so the Keystore-backed cipher can only be proven on a device.
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreEncryptionSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.dataStoreFile(TEST_FILE_NAME)

    @Before
    @After
    fun deleteTestFile() {
        file.delete()
    }

    @Test
    fun keystoreCipherRoundTripsThroughANonAuthBoundKey() {
        val cipher = KeystoreAesGcmCipher(KEY_ALIAS)
        val plaintext = "session-metadata-canary".toByteArray()

        assertArrayEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
    }

    @Test
    fun persistedSessionFileIsNotPlaintextReadableAndSurvivesReopening() =
        runTest {
            withStore { it.recordVerifiedOnlineNow(DISTINCTIVE_TIMESTAMP) }

            val rawFileText = String(file.inputStream().use { it.readBytes() }, Charsets.ISO_8859_1)
            assertFalse(
                "The raw session metadata file must not contain the plaintext timestamp",
                rawFileText.contains(DISTINCTIVE_TIMESTAMP.toString()),
            )
            assertFalse(
                "The raw session metadata file must not contain plaintext JSON field names",
                rawFileText.contains("lastVerifiedOnlineAtMillis"),
            )
            withStore { assertEquals(DISTINCTIVE_TIMESTAMP, it.getLastVerifiedOnlineAtMillis()) }
        }

    /** DataStore forbids two live instances over one file, so each block owns a scope it then tears down. */
    private suspend fun withStore(block: suspend (DataStoreSessionStore) -> Unit) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            block(
                DataStoreSessionStore(
                    DataStoreFactory.create(
                        serializer = SessionMetadataSerializer(KeystoreAesGcmCipher(KEY_ALIAS)),
                        scope = scope,
                        produceFile = { file },
                    ),
                ),
            )
        } finally {
            scope.cancel()
            scope.coroutineContext.job.join()
        }
    }

    private companion object {
        const val TEST_FILE_NAME = "session_metadata_smoke_test.pb"
        const val KEY_ALIAS = "session_metadata_smoke_test_key"
        const val DISTINCTIVE_TIMESTAMP = 1_234_567_890_123L
    }
}
