package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import android.content.Context
import uk.gov.defra.mmocatchrecord.core.security.KeyUnavailableException
import uk.gov.defra.mmocatchrecord.core.security.KeystoreAesGcmCipher
import java.io.File
import java.security.SecureRandom

/**
 * Generates (or retrieves) the SQLCipher passphrase for the catch-record draft database (ADR 0006). The
 * passphrase is random, wrapped by a non-auth-bound Keystore key, and stored in internal storage only.
 */
class CatchRecordPassphraseProvider(
    private val context: Context,
) {
    private val cipher = KeystoreAesGcmCipher(KEY_ALIAS)

    /**
     * Returns the raw passphrase bytes, generating one on first call. Throws [KeyUnavailableException] if
     * the Keystore key is lost: the draft database is then unreadable, so recovery is a product decision.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val file = passphraseFile()

        return if (file.exists()) {
            cipher.decrypt(file.readBytes())
        } else {
            val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            file.writeBytes(cipher.encrypt(passphrase))
            passphrase
        }
    }

    private fun passphraseFile(): File = File(context.filesDir, PASSPHRASE_FILE_NAME)

    private companion object {
        const val KEY_ALIAS = "catch_record_draft_db_key"
        const val PASSPHRASE_FILE_NAME = "catch_record_draft.passphrase"
        const val PASSPHRASE_LENGTH_BYTES = 32
    }
}
