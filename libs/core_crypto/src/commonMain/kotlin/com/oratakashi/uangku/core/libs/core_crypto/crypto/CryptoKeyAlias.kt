package com.oratakashi.uangku.core.libs.core_crypto.crypto

/**
 * Centralized constants for cryptographic key aliases.
 * Each alias maps to a distinct AES-256 key in the platform Keystore/Keychain.
 * Mirrors [com.oratakashi.uangku.core.libs.core_crypto.storage.SecureStorageKey].
 *
 * @since 13 June 2026
 */
object CryptoKeyAlias {
    const val DEFAULT = "uangku_crypto_default"

    /** Key used for encrypting/decrypting API request and response payloads. */
    const val PAYLOAD = "uangku_crypto_payload"

    /** Key used for encrypting data before storing locally (DB/file/preferences). */
    const val LOCAL_DATA = "uangku_crypto_local"
}