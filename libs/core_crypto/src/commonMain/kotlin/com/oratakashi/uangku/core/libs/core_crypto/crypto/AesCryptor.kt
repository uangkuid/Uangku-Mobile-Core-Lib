package com.oratakashi.uangku.core.libs.core_crypto.crypto

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException

/**
 * Platform-agnostic AES-256-CBC encryption and decryption.
 *
 * Two key modes are supported:
 * - **Managed key** ([encrypt]/[decrypt]/[encryptBytes]/[decryptBytes]): keys are auto-generated
 *   on first use for the given [keyAlias] and stored in the platform Keystore (Android) or
 *   Keychain (iOS). Consumers never handle raw key material.
 * - **External key** ([encryptWithKey]/[decryptWithKey]): accepts a raw string key that is
 *   SHA-256-hashed to 32 bytes before use. Produces a ciphertext envelope compatible with
 *   the backend `EncryptionHelper` format (`base64(iv).base64(ciphertext)`).
 *
 * Ciphertext string format: `"<base64-IV>.<base64-ciphertext>"`
 *
 * All operations are suspend and safe to call from any coroutine context.
 *
 * @since 13 June 2026
 */
expect class AesCryptor {

    /**
     * Encrypts [plainText] using the Keystore/Keychain-managed key for [keyAlias].
     * The key is auto-generated on first use.
     *
     * @return `"<base64-IV>.<base64-ciphertext>"`
     * @throws CryptoException.EncryptionFailure on failure
     * @throws CryptoException.KeyGenerationFailure if the key cannot be created
     */
    suspend fun encrypt(plainText: String, keyAlias: String = CryptoKeyAlias.DEFAULT): String

    /**
     * Decrypts a managed-key ciphertext envelope back to plaintext.
     *
     * @param cipherText `"<base64-IV>.<base64-ciphertext>"`
     * @throws CryptoException.DecryptionFailure on failure or bad data
     * @throws CryptoException.KeyNotFound if no key exists for [keyAlias]
     */
    suspend fun decrypt(cipherText: String, keyAlias: String = CryptoKeyAlias.DEFAULT): String

    /**
     * ByteArray variant of [encrypt]. IV is prepended to the ciphertext in the returned array.
     *
     * @return `IV (16 bytes) || ciphertext`
     * @throws CryptoException.EncryptionFailure on failure
     */
    suspend fun encryptBytes(data: ByteArray, keyAlias: String = CryptoKeyAlias.DEFAULT): ByteArray

    /**
     * ByteArray variant of [decrypt]. Expects `IV (16 bytes) || ciphertext` layout.
     *
     * @throws CryptoException.DecryptionFailure on failure or bad data
     */
    suspend fun decryptBytes(data: ByteArray, keyAlias: String = CryptoKeyAlias.DEFAULT): ByteArray

    /**
     * Encrypts [plainText] with a caller-supplied [secretKey] (backend-compatible).
     * The key is SHA-256-hashed to 32 bytes before use, matching the backend
     * `EncryptionHelper::encrypt()` flow.
     *
     * @return `"<base64-IV>.<base64-ciphertext>"`
     * @throws CryptoException.EncryptionFailure on failure
     */
    suspend fun encryptWithKey(plainText: String, secretKey: String): String

    /**
     * Decrypts a backend-produced ciphertext envelope using [secretKey].
     * Compatible with the backend `EncryptionHelper::decryptFromString()` format.
     *
     * @param cipherText `"<base64-IV>.<base64-ciphertext>"`
     * @throws CryptoException.DecryptionFailure on failure or bad data
     */
    suspend fun decryptWithKey(cipherText: String, secretKey: String): String
}
