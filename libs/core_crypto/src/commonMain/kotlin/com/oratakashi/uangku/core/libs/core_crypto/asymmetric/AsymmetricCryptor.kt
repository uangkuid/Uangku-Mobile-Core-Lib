package com.oratakashi.uangku.core.libs.core_crypto.asymmetric

import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes

/**
 * A freshly generated RSA key pair in the backend wire encodings.
 *
 * @property publicKeyBase64 `base64(PEM SPKI)` — the double-encoded form the server stores
 *   as `public_key`.
 * @property privateKeyPem the PKCS#8 **PEM** bytes (secret). The caller wraps these with the
 *   unlockKey via AES-GCM to produce `wrapped_private_key`.
 */
class RsaKeyPairMaterial(val publicKeyBase64: String, val privateKeyPem: SecretBytes)

/**
 * RSA-OAEP-SHA256, 4096-bit. Used only to wrap/unwrap small keys (a 32-byte data key or a
 * family private key via hybrid envelope) — never bulk data, which is why the 446-byte limit
 * is fine and is exposed so callers cannot overflow it silently.
 *
 * @since 17 July 2026
 */
interface AsymmetricCryptor {

    /** Max plaintext for one RSA-OAEP-SHA256/4096 operation: `512 − 2*32 − 2` = 446. */
    val maxPlaintextSizeBytes: Int

    /** Generates a 4096-bit key pair in the backend wire encodings. */
    suspend fun generateKeyPair(): RsaKeyPairMaterial

    /**
     * Encrypts [plaintext] to [publicKeyBase64] (`base64(PEM SPKI)`).
     * @throws CryptoException.EncryptionFailure if [plaintext] exceeds [maxPlaintextSizeBytes]
     *   or the key cannot be parsed.
     */
    suspend fun encrypt(publicKeyBase64: String, plaintext: ByteArray): ByteArray

    /** Decrypts [ciphertext] with the PKCS#8 PEM private key bytes in [privateKeyPem]. */
    suspend fun decrypt(privateKeyPem: SecretBytes, ciphertext: ByteArray): ByteArray
}
