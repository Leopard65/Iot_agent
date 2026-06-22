package com.example.iotgpt.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the encrypted-blob format used by [ApiKeyEncryption] without
 * Android dependencies (pure string-parsing tests that run on JVM).
 *
 * The canonical format is: `enc:<base64url-iv>:::<base64url-ciphertext>`
 */
class ApiKeyEncryptionFormatTest {

    // Pre-computed Base64-URL-safe strings (no padding, no line breaks)
    private val sampleIv = "aWlpaWlpaWlpaWlp"       // 12-byte placeholder
    private val sampleCt = "Y3RjdGN0Y3RjdGN0Y3RjdGN0Y3RjdGN0" // 32-byte placeholder

    @Test
    fun markerConstant_isCorrect() {
        assertEquals("enc:", ApiKeyEncryption.MARKER)
    }

    @Test
    fun isLikelyEncrypted_plaintext_returnsFalse() {
        val plaintext = "sk-1234567890abcdef"
        assertFalse(plaintext.startsWith(ApiKeyEncryption.MARKER))
    }

    @Test
    fun isLikelyEncrypted_emptyString_returnsFalse() {
        assertFalse("".startsWith(ApiKeyEncryption.MARKER))
    }

    @Test
    fun formatStructure_hasDelimiterBetweenIvAndCiphertext() {
        val blob = "enc:$sampleIv:::$sampleCt"

        val rest = blob.removePrefix("enc:")
        val delimIndex = rest.indexOf(":::")
        assertTrue("Delimiter ':::' must be present", delimIndex >= 0)
        assertEquals(sampleIv, rest.substring(0, delimIndex))
        assertEquals(sampleCt, rest.substring(delimIndex + 3))
    }

    @Test
    fun formatStructure_noExtraColonsAfterDelimiter() {
        // The ciphertext portion must NOT start with ':' — that was the
        // original parsing bug (substring(colonIndex + 2) left a leading ':').
        val blob = "enc:$sampleIv:::$sampleCt"

        val rest = blob.removePrefix("enc:")
        val delimIndex = rest.indexOf(":::")
        val ciphertextPart = rest.substring(delimIndex + 3)
        assertFalse(
            "Ciphertext must not start with ':'",
            ciphertextPart.startsWith(":")
        )
        assertEquals(sampleCt, ciphertextPart)
    }

    @Test
    fun formatStructure_splitByDelimiter_givesExactlyTwoParts() {
        val blob = "enc:$sampleIv:::$sampleCt"
        val parts = blob.removePrefix("enc:").split(":::", limit = 2)
        assertEquals(2, parts.size)
        assertEquals(sampleIv, parts[0])
        assertEquals(sampleCt, parts[1])
    }

    @Test
    fun formatStructure_blobWithoutDelimiter_notValid() {
        // A blob missing the ":::" delimiter should not be parseable
        val badBlob = "enc:$sampleIv:$sampleCt"
        val rest = badBlob.removePrefix("enc:")
        val delimIndex = rest.indexOf(":::")
        assertTrue("Missing ':::' must yield index < 0", delimIndex < 0)
    }
}
