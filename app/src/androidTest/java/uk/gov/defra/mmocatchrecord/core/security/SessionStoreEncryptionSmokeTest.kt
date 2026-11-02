package uk.gov.defra.mmocatchrecord.core.security

import androidx.datastore.dataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * `AndroidKeyStore` provider, so the Keystore-backed cipher and session store can only be proven on device.
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreEncryptionSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteSessionFile() {
        context.dataStoreFile(SESSION_METADATA_FILE_NAME).delete()
    }

    @Test
    fun keystoreCipherRoundTripsThroughANonAuthBoundKey() {
        val cipher = KeystoreAesGcmCipher("session_metadata_smoke_test_key")
        val plaintext = "session-metadata-canary".toByteArray()

        assertArrayEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
    }

    @Test
    fun persistedSessionFileIsNotPlaintextReadable() =
        runTest {
            val distinctiveTimestamp = 1_234_567_890_123L
            val store = DataStoreSessionStore(createSessionDataStore(context))

            store.recordVerifiedOnlineNow(distinctiveTimestamp)

            assertEquals(distinctiveTimestamp, store.getLastVerifiedOnlineAtMillis())
            val rawFileText =
                String(
                    context.dataStoreFile(SESSION_METADATA_FILE_NAME).inputStream().use { it.readBytes() },
                    Charsets.ISO_8859_1,
                )
            assertFalse(
                "The raw session metadata file must not contain the plaintext timestamp",
                rawFileText.contains(distinctiveTimestamp.toString()),
            )
            assertFalse(
                "The raw session metadata file must not contain plaintext JSON field names",
                rawFileText.contains("lastVerifiedOnlineAtMillis"),
            )
        }
}
