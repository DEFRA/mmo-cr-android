package uk.gov.defra.mmocatchrecord.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Real AES-GCM with an in-memory key, standing in for [KeystoreAesGcmCipher] off-device: Robolectric has
 * no `AndroidKeyStore` provider, so the Keystore-backed cipher is covered by an instrumented test instead.
 */
class TestAesGcmCipher(
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(KEY_SIZE_BITS) }.generateKey(),
) : AeadCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size <= GCM_IV_LENGTH_BYTES) {
            throw GeneralSecurityException("Ciphertext is too short to contain an IV")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, ciphertext.copyOfRange(0, GCM_IV_LENGTH_BYTES)),
        )
        return cipher.doFinal(ciphertext.copyOfRange(GCM_IV_LENGTH_BYTES, ciphertext.size))
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
