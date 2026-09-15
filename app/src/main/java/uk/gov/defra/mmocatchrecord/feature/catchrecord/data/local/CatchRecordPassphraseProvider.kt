package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates (or retrieves) the SQLCipher passphrase used to encrypt the catch-record draft database (see
 * ADR 0006). The passphrase itself is a random value, wrapped (encrypted) by a **non-auth-bound** AES-GCM
 * Android Keystore key, and the wrapped bytes are stored in **internal app storage only**
 * (`context.filesDir`), never external storage and never a backup-eligible path.
 *
 * The Keystore key is not biometric/auth-bound: the database must be openable in the background (e.g. by
 * a future WorkManager sync worker) without requiring the user to re-authenticate on every access. App
 * re-entry biometric gating is a separate, UI-level concern (see `core/security`).
 */
class CatchRecordPassphraseProvider(
    private val context: Context,
) {
    /**
     * Returns the raw passphrase bytes, generating and persisting a new one (wrapped by the Keystore key)
     * on first call. Safe to call repeatedly — subsequent calls unwrap the same persisted passphrase.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val secretKey = getOrCreateWrappingKey()
        val file = passphraseFile()

        return if (file.exists()) {
            unwrap(file.readBytes(), secretKey)
        } else {
            val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            file.writeBytes(wrap(passphrase, secretKey))
            passphrase
        }
    }

    private fun passphraseFile(): File = File(context.filesDir, PASSPHRASE_FILE_NAME)

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // Not auth-bound: readable without user re-authentication, see class doc comment.
                .setUserAuthenticationRequired(false)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun wrap(
        plaintext: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    private fun unwrap(
        wrapped: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val iv = wrapped.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = wrapped.copyOfRange(GCM_IV_LENGTH_BYTES, wrapped.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "catch_record_draft_db_key"
        const val PASSPHRASE_FILE_NAME = "catch_record_draft.passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val PASSPHRASE_LENGTH_BYTES = 32
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
