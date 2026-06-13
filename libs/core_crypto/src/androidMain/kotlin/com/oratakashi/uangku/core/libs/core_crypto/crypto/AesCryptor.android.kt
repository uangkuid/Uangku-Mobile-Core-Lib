package com.oratakashi.uangku.core.libs.core_crypto.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android implementation of [AesCryptor] using AES-256-CBC.
 *
 * Managed keys are backed by AndroidKeyStore (hardware-backed when available).
 * External-key operations derive a 32-byte key from the caller-supplied string via SHA-256,
 * matching the backend `EncryptionHelper::encrypt()` contract.
 *
 * All operations run on [Dispatchers.IO].
 *
 * @since 13 June 2026
 */
actual class AesCryptor {

    private fun getOrCreateKey(keyAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
        }
        return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    actual suspend fun encrypt(plainText: String, keyAlias: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateKey(keyAlias)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                "${EncodingUtils.encodeBase64(iv)}.${EncodingUtils.encodeBase64(ciphertext)}"
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun decrypt(cipherText: String, keyAlias: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateKey(keyAlias)
                val (iv, data) = splitEnvelope(cipherText)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                String(cipher.doFinal(data), Charsets.UTF_8)
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.DecryptionFailure(e)
            }
        }
    }

    actual suspend fun encryptBytes(data: ByteArray, keyAlias: String): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateKey(keyAlias)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                iv + cipher.doFinal(data)
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun decryptBytes(data: ByteArray, keyAlias: String): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateKey(keyAlias)
                val iv = data.copyOfRange(0, IV_SIZE)
                val ciphertext = data.copyOfRange(IV_SIZE, data.size)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                cipher.doFinal(ciphertext)
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.DecryptionFailure(e)
            }
        }
    }

    actual suspend fun encryptWithKey(plainText: String, secretKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val keySpec = deriveKeySpec(secretKey)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                "${EncodingUtils.encodeBase64(iv)}.${EncodingUtils.encodeBase64(ciphertext)}"
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun decryptWithKey(cipherText: String, secretKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val keySpec = deriveKeySpec(secretKey)
                val (iv, data) = splitEnvelope(cipherText)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                String(cipher.doFinal(data), Charsets.UTF_8)
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.DecryptionFailure(e)
            }
        }
    }

    /** SHA-256 hash of [secretKey] → 32-byte [SecretKeySpec] for AES-256. */
    private fun deriveKeySpec(secretKey: String): SecretKeySpec {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(secretKey.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun splitEnvelope(envelope: String): Pair<ByteArray, ByteArray> {
        val parts = envelope.split(".")
        require(parts.size == 2) { "Ciphertext envelope must be '<base64-iv>.<base64-ciphertext>'" }
        return EncodingUtils.decodeBase64(parts[0]) to EncodingUtils.decodeBase64(parts[1])
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val IV_SIZE = 16
    }
}
