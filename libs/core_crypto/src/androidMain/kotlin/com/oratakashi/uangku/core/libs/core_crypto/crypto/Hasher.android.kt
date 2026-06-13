package com.oratakashi.uangku.core.libs.core_crypto.crypto

import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android implementation of [Hasher] using [java.security.MessageDigest] and [javax.crypto.Mac].
 * All operations run on [Dispatchers.IO].
 *
 * @since 13 June 2026
 */
actual class Hasher {

    actual suspend fun sha256(input: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = MessageDigest.getInstance("SHA-256")
                    .digest(input.toByteArray(Charsets.UTF_8))
                EncodingUtils.encodeHex(bytes)
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun sha512(input: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = MessageDigest.getInstance("SHA-512")
                    .digest(input.toByteArray(Charsets.UTF_8))
                EncodingUtils.encodeHex(bytes)
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun hmacSha256(input: String, secret: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val mac = Mac.getInstance("HmacSHA256").apply {
                    init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                }
                EncodingUtils.encodeHex(mac.doFinal(input.toByteArray(Charsets.UTF_8)))
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }
}
