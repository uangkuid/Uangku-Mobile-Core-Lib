package com.oratakashi.uangku.core.libs.core_crypto.exception

/**
 * Sealed hierarchy for cryptographic operation failures.
 * All platform-specific errors are mapped into this exhaustive vocabulary to prevent raw
 * exception leakage into consumer projects. Mirrors [SecureStorageException].
 *
 * @since 13 June 2026
 */
sealed class CryptoException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    data class EncryptionFailure(val rootCause: Throwable? = null) :
        CryptoException("Encryption failed", rootCause)

    data class DecryptionFailure(val rootCause: Throwable? = null) :
        CryptoException("Decryption failed", rootCause)

    data class KeyGenerationFailure(val rootCause: Throwable? = null) :
        CryptoException("Key generation failed", rootCause)

    data class KeyNotFound(val alias: String, val rootCause: Throwable? = null) :
        CryptoException("Key not found for alias: $alias", rootCause)

    /** Thrown when GCM authentication tag or any integrity check fails (data tampered). */
    data class IntegrityCheckFailure(val rootCause: Throwable? = null) :
        CryptoException("Integrity check failed — data may have been tampered with", rootCause)

    data class NotAvailable(val rootCause: Throwable? = null) :
        CryptoException("Cryptographic services are not available", rootCause)
}