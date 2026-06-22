package com.oratakashi.uangku.core.libs.core_crypto.crypto

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AesCryptorTest {

    private val cryptor = AesCryptor()

    @Test
    fun encryptWithKey_roundTrip() = runBlocking {
        val key = "test_secret_key_for_unit_tests"
        val plaintext = "Hello, World!"
        val encrypted = cryptor.encryptWithKey(plaintext, key)
        val decrypted = cryptor.decryptWithKey(encrypted, key)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encryptWithKey_differentCiphertextEachCall() = runBlocking {
        val key = "same_key"
        val plaintext = "same plaintext"
        val enc1 = cryptor.encryptWithKey(plaintext, key)
        val enc2 = cryptor.encryptWithKey(plaintext, key)
        assertNotEquals(enc1, enc2)
    }

    @Test
    fun encryptWithKey_outputContainsDotSeparator() = runBlocking {
        val encrypted = cryptor.encryptWithKey("data", "key")
        assertTrue(encrypted.contains("."), "Envelope must contain '.' separator")
        assertEquals(2, encrypted.split(".").size)
    }

    @Test
    fun decryptWithKey_wrongKey_throwsDecryptionFailure(): Unit = runBlocking {
        val encrypted = cryptor.encryptWithKey("secret", "correct_key")
        try {
            cryptor.decryptWithKey(encrypted, "wrong_key")
            fail("Expected CryptoException.DecryptionFailure")
        } catch (e: CryptoException.DecryptionFailure) {
            // expected
        }
    }

    @Test
    fun decryptWithKey_tamperedCiphertext_throwsDecryptionFailure(): Unit = runBlocking {
        val key = "tamper_test_key"
        val encrypted = cryptor.encryptWithKey("important data", key)
        val parts = encrypted.split(".")
        val tampered = "${parts[0]}.${parts[1].reversed()}"
        try {
            cryptor.decryptWithKey(tampered, key)
            fail("Expected CryptoException.DecryptionFailure")
        } catch (e: CryptoException.DecryptionFailure) {
            // expected
        }
    }

    @Test
    fun encryptWithKey_emptyString_roundTrip() = runBlocking {
        val key = "empty_test_key"
        val encrypted = cryptor.encryptWithKey("", key)
        val decrypted = cryptor.decryptWithKey(encrypted, key)
        assertEquals("", decrypted)
    }

    @Test
    fun encryptWithKey_unicodeContent_roundTrip() = runBlocking {
        val key = "unicode_key"
        val plaintext = "Uangku — 💰"
        val decrypted = cryptor.decryptWithKey(cryptor.encryptWithKey(plaintext, key), key)
        assertEquals(plaintext, decrypted)
    }
}
