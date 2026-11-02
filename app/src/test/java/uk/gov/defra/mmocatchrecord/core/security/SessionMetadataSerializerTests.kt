package uk.gov.defra.mmocatchrecord.core.security

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SessionMetadataSerializerTests {
    private val cipher = TestAesGcmCipher()
    private val serializer = SessionMetadataSerializer(cipher)

    @Test
    fun `a round trip preserves both timestamps`() =
        runTest {
            val metadata = SessionMetadata(APP_LOCK_AT, ONLINE_AT)

            assertEquals(metadata, serializer.readFrom(ByteArrayInputStream(write(metadata))))
        }

    @Test
    fun `the default value is empty so a missing file reads as never authenticated`() {
        assertEquals(SessionMetadata(), serializer.defaultValue)
    }

    @Test
    fun `an empty stream deserialises to the default rather than failing`() =
        runTest {
            assertEquals(SessionMetadata(), serializer.readFrom(ByteArrayInputStream(ByteArray(0))))
        }

    @Test
    fun `the written bytes are not plaintext-readable`() =
        runTest {
            val raw = String(write(SessionMetadata(APP_LOCK_AT, ONLINE_AT)), Charsets.ISO_8859_1)

            assertFalse(raw.contains(APP_LOCK_AT.toString()))
            assertFalse(raw.contains(ONLINE_AT.toString()))
            assertFalse(raw.contains("lastVerifiedOnlineAtMillis"))
        }

    @Test(expected = CorruptionException::class)
    fun `undecryptable bytes are reported as corruption so DataStore can reset the file`() =
        runTest {
            serializer.readFrom(ByteArrayInputStream(ByteArray(GARBAGE_SIZE) { it.toByte() }))
        }

    @Test(expected = CorruptionException::class)
    fun `a truncated file is reported as corruption`() =
        runTest {
            serializer.readFrom(ByteArrayInputStream(ByteArray(TRUNCATED_SIZE)))
        }

    @Test(expected = CorruptionException::class)
    fun `decryptable but malformed content is reported as corruption`() =
        runTest {
            serializer.readFrom(ByteArrayInputStream(cipher.encrypt("not json".toByteArray())))
        }

    @Test
    fun `unknown fields written by a future version are ignored`() =
        runTest {
            val payload = """{"lastVerifiedOnlineAtMillis":$ONLINE_AT,"somethingNew":true}"""

            val decoded = serializer.readFrom(ByteArrayInputStream(cipher.encrypt(payload.toByteArray())))

            assertEquals(SessionMetadata(lastVerifiedOnlineAtMillis = ONLINE_AT), decoded)
        }

    private suspend fun write(metadata: SessionMetadata): ByteArray =
        ByteArrayOutputStream().also { serializer.writeTo(metadata, it) }.toByteArray()

    private companion object {
        const val APP_LOCK_AT = 1_700_000_000_000L
        const val ONLINE_AT = 1_600_000_000_000L
        const val GARBAGE_SIZE = 64
        const val TRUNCATED_SIZE = 4
    }
}
