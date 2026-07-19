package com.oratakashi.uangku.core.libs.core_crypto.exception

/**
 * Sealed hierarchy for cryptographic operation failures.
 *
 * All platform-specific errors are mapped into this exhaustive vocabulary so that raw
 * platform exceptions (JCA, CryptoKit, Security.framework) never leak into consumer
 * projects. Mirrors [SecureStorageException].
 *
 * Only failures that are actually thrown by this module are modelled here. Types that
 * carry a `reason`/`version` deliberately expose a non-sensitive descriptor — never the
 * offending secret value, which would end up in crash reports.
 *
 * @since 17 July 2026
 */
sealed class CryptoException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Symmetric or asymmetric encryption failed for a non-integrity reason. */
    data class EncryptionFailure(val rootCause: Throwable? = null) :
        CryptoException("Encryption failed", rootCause)

    /** Decryption failed for a non-integrity reason (e.g. malformed key material). */
    data class DecryptionFailure(val rootCause: Throwable? = null) :
        CryptoException("Decryption failed", rootCause)

    /** RSA key-pair generation failed. */
    data class KeyGenerationFailure(val rootCause: Throwable? = null) :
        CryptoException("Key generation failed", rootCause)

    /** PBKDF2/HKDF derivation failed. */
    data class KeyDerivationFailure(val rootCause: Throwable? = null) :
        CryptoException("Key derivation failed", rootCause)

    /**
     * AES-GCM authentication tag verification failed.
     *
     * Note honestly: a wrong key produces the exact same failure as tampered ciphertext —
     * GCM cannot distinguish the two. Do not read this as "data was definitely tampered".
     */
    data class IntegrityCheckFailure(val rootCause: Throwable? = null) :
        CryptoException("Integrity check failed — wrong key or tampered data", rootCause)

    /** Secret key string did not match the expected `UANGKU-…` format. Carries a reason, never the value. */
    data class InvalidSecretKeyFormat(val reason: String) :
        CryptoException("Invalid secret key format: $reason")

    /** Ciphertext container was structurally malformed (bad length, truncated, not base64). */
    data class InvalidCiphertextFormat(val reason: String) :
        CryptoException("Invalid ciphertext format: $reason")

    /** Container/envelope version byte was not the supported value. */
    data class UnsupportedVersion(val version: Int) :
        CryptoException("Unsupported version: $version")

    /** The requested algorithm is not available from the resolved cryptography provider. */
    data class NotAvailable(val algorithm: String? = null, val rootCause: Throwable? = null) :
        CryptoException(
            "Cryptographic services are not available" + (algorithm?.let { " for $it" } ?: ""),
            rootCause,
        )
}
