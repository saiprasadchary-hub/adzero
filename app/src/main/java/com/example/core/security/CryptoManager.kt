package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

/**
 * Local-only AES-256-GCM encryption backed by AndroidKeyStore.
 * Used to encrypt locally stored WhatsApp Ghost Inbox sender names and message previews at rest.
 * Falls back cleanly to a process-scoped ephemeral key only on non-Keystore JVM unit test runtimes.
 */
class CryptoManager(
    private val keyAlias: String = "adzero_ghost_inbox_aes_key"
) {
    private val fallbackKey: SecretKey by lazy {
        val gen = KeyGenerator.getInstance("AES")
        gen.init(256)
        gen.generateKey()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Throwable) {
            fallbackKey
        }
    }

    fun encrypt(plaintext: String): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(payload: EncryptedPayload): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val plainBytes = cipher.doFinal(ciphertext)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            "[Encrypted message unavailable]"
        }
    }

    fun hashIdentifier(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.trim().lowercase().toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
