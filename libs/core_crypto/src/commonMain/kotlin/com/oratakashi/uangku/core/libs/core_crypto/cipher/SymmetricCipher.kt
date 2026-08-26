package com.oratakashi.uangku.core.libs.core_crypto.cipher

import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes

/**
 * AES-256-GCM authenticated encryption producing/consuming the [SealedData] container.
 *
 * No AAD: the backend contract carries none, so the API omits it.
 *
 * @since 17 July 2026
 */
interface SymmetricCipher {

    /** Encrypts [plaintext] under a 32-byte [key], returning the versioned container. */
    suspend fun seal(key: SecretBytes, plaintext: ByteArray): SealedData

    /** Decrypts [sealed] under [key]. @throws CryptoException.IntegrityCheckFailure on wrong key or tamper. */
    suspend fun open(key: SecretBytes, sealed: SealedData): ByteArray
}
