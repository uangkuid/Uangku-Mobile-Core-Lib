package com.oratakashi.uangku.core.libs.core_crypto.storage

/**
 * Device-local secure key/value store, backed by the Android Keystore or the iOS Keychain.
 *
 * An **interface**, not an `expect class` — so it can be faked in `commonTest` and swapped by
 * consumers. Used to cache the unlockKey (behind local PIN/biometric, never the server) and
 * session tokens. Values never leave the device.
 *
 * @since 17 July 2026
 */
interface SecureStorage {

    /** Encrypts and stores [value] under [key], overwriting any existing entry. */
    suspend fun put(key: String, value: String)

    /** Returns the decrypted value for [key], or null if absent. */
    suspend fun get(key: String): String?

    /** Removes the entry for [key] if present. */
    suspend fun remove(key: String)

    /** Removes all entries owned by this store. */
    suspend fun clear()

    /** Returns true if an entry exists for [key]. */
    suspend fun contains(key: String): Boolean
}
