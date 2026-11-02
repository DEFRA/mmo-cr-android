package uk.gov.defra.mmocatchrecord.core.security

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/** Persisted session timing metadata. Timestamps only — never a token, token fragment or account id. */
@kotlinx.serialization.Serializable
data class SessionMetadata(
    val lastAuthenticatedAtMillis: Long? = null,
    val lastVerifiedOnlineAtMillis: Long? = null,
)

/**
 * Encrypts [SessionMetadata] at rest with a non-auth-bound Keystore AES-GCM key (ADR 0006), so a
 * background token refresh never needs a biometric prompt.
 */
class SessionMetadataSerializer(
    private val cipher: AeadCipher,
) : Serializer<SessionMetadata> {
    override val defaultValue: SessionMetadata = SessionMetadata()

    override suspend fun readFrom(input: InputStream): SessionMetadata {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(String(cipher.decrypt(bytes), Charsets.UTF_8))
        } catch (error: GeneralSecurityException) {
            throw CorruptionException(UNREADABLE, error)
        } catch (error: SerializationException) {
            throw CorruptionException(UNREADABLE, error)
        }
    }

    override suspend fun writeTo(
        t: SessionMetadata,
        output: OutputStream,
    ) {
        output.write(cipher.encrypt(json.encodeToString(t).toByteArray(Charsets.UTF_8)))
    }

    private companion object {
        const val UNREADABLE = "Session metadata could not be read"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Process-death-surviving [SessionStore] backed by an encrypted [DataStore] (ADR 0019). */
class DataStoreSessionStore(
    private val dataStore: DataStore<SessionMetadata>,
) : SessionStore {
    override suspend fun getLastAuthenticatedAtMillis(): Long? = dataStore.data.first().lastAuthenticatedAtMillis

    override suspend fun recordAuthenticatedNow(nowMillis: Long) {
        dataStore.updateData { it.copy(lastAuthenticatedAtMillis = nowMillis) }
    }

    override suspend fun getLastVerifiedOnlineAtMillis(): Long? = dataStore.data.first().lastVerifiedOnlineAtMillis

    override suspend fun recordVerifiedOnlineNow(nowMillis: Long) {
        dataStore.updateData { it.copy(lastVerifiedOnlineAtMillis = nowMillis) }
    }

    override suspend fun clear() {
        dataStore.updateData { SessionMetadata() }
    }
}

/** DataStore file name, under `filesDir/datastore/` — already excluded from backup by the allowlist. */
internal const val SESSION_METADATA_FILE_NAME = "session_metadata.pb"

private const val SESSION_METADATA_KEY_ALIAS = "session_metadata_key"

/**
 * Builds the encrypted session [DataStore]. A file that cannot be decrypted or parsed resets to "never
 * authenticated", which fails safe to the sign-in screen rather than crashing on launch.
 */
fun createSessionDataStore(context: Context): DataStore<SessionMetadata> =
    DataStoreFactory.create(
        serializer = SessionMetadataSerializer(KeystoreAesGcmCipher(SESSION_METADATA_KEY_ALIAS)),
        corruptionHandler = ReplaceFileCorruptionHandler { SessionMetadata() },
        produceFile = { context.dataStoreFile(SESSION_METADATA_FILE_NAME) },
    )
