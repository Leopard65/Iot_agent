package com.example.iotgpt.core.preferences

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption for API keys, backed by Android Keystore.
 *
 * Encrypted values are stored as `enc:<base64(iv)>:::<base64(ciphertext)>` so
 * that plaintext and encrypted strings can be distinguished without extra
 * metadata.
 */
class ApiKeyEncryption(context: Context) {

    private val appContext = context.applicationContext

    /** Encrypts [plainText] with a Keystore-backed AES key. Returns null on failure. */
    fun encryptApiKey(plainText: String): String? {
        if (plainText.isBlank()) return plainText
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            "$MARKER${iv.toBase64()}$DELIMITER${encrypted.toBase64()}"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encrypt API key", e)
            null
        }
    }

    /** Decrypts a value previously produced by [encryptApiKey]. Returns null on failure. */
    fun decryptApiKey(encrypted: String): String? {
        if (encrypted.isBlank() || !isLikelyEncrypted(encrypted)) return encrypted
        return try {
            val parts = encrypted.removePrefix(MARKER).split(DELIMITER, limit = 2)
            if (parts.size != 2) return null
            val iv = parts[0].fromBase64()
            val ciphertext = parts[1].fromBase64()
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt API key", e)
            null
        }
    }

    /** Returns true if [value] looks like an encrypted blob (has marker and valid Base64 parts). */
    fun isLikelyEncrypted(value: String): Boolean {
        if (!value.startsWith(MARKER)) return false
        val rest = value.removePrefix(MARKER)
        val delimIndex = rest.indexOf(DELIMITER)
        if (delimIndex < 0) return false
        return try {
            rest.substring(0, delimIndex).fromBase64()
            rest.substring(delimIndex + DELIMITER.length).fromBase64()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setIsStrongBoxBacked(false)
        }
        generator.init(specBuilder.build())
        return generator.generateKey()
    }

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE)

    companion object {
        const val MARKER = "enc:"
        private const val DELIMITER = ":::"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "lot_api_key_encryption"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val TAG = "ApiKeyEncryption"
    }
}
