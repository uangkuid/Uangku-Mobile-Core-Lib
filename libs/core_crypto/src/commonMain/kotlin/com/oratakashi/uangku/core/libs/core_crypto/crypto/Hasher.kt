package com.oratakashi.uangku.core.libs.core_crypto.crypto

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException

/**
 * Platform-agnostic hashing utilities for integrity checks and signatures.
 * All outputs are lowercase hex strings.
 *
 * All operations are suspend and safe to call from any coroutine context.
 *
 * @since 13 June 2026
 */
expect class Hasher {

    /**
     * Computes SHA-256 of [input].
     *
     * @return 64-character lowercase hex string
     * @throws CryptoException.EncryptionFailure on failure
     */
    suspend fun sha256(input: String): String

    /**
     * Computes SHA-512 of [input].
     *
     * @return 128-character lowercase hex string
     * @throws CryptoException.EncryptionFailure on failure
     */
    suspend fun sha512(input: String): String

    /**
     * Computes HMAC-SHA-256 of [input] using [secret].
     *
     * @return 64-character lowercase hex string
     * @throws CryptoException.EncryptionFailure on failure
     */
    suspend fun hmacSha256(input: String, secret: String): String
}
