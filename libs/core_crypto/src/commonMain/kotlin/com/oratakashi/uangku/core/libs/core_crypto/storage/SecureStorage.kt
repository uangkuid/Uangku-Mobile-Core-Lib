package com.oratakashi.uangku.core.libs.core_crypto.storage

import com.oratakashi.uangku.core.libs.core_crypto.exception.SecureStorageException

/**
 * SecureStorage provides a unified, platform-agnostic interface for storing and retrieving
 * sensitive values. On Android, it delegates to EncryptedSharedPreferences (AES-256-CBC).
 * On iOS, it delegates to native Keychain Services (hardware-backed when available).
 *
 * All operations are safe to call from UI coroutines:
 * - Android implementation executes on Dispatchers.IO
 * - iOS implementation executes on Dispatchers.Default
 *
 * All platform-specific exceptions are mapped into the [SecureStorageException] hierarchy.
 * Consumers never interact with raw platform storage APIs or exceptions.
 *
 * @since 15 May 2026
 */
expect class SecureStorage {

    /**
     * Stores a sensitive value under the given key.
     * Fails fast if secure storage is unavailable (see [SecureStorageException.NotAvailable]).
     *
     * @param key Unique identifier for the value (consider using constants from [SecureStorageKey])
     * @param value The sensitive string to store (e.g., authentication token)
     * @throws SecureStorageException.WriteFailure if the operation fails
     * @throws SecureStorageException.NotAvailable if secure storage is unavailable
     * @since 15 May 2026
     */
    suspend fun put(key: String, value: String)

    /**
     * Retrieves a previously stored value.
     *
     * @param key Unique identifier for the value
     * @return The stored string, or null if no value exists under this key
     * @throws SecureStorageException.ReadFailure if the operation fails
     * @throws SecureStorageException.NotAvailable if secure storage is unavailable
     * @since 15 May 2026
     */
    suspend fun get(key: String): String?

    /**
     * Deletes a value from secure storage.
     *
     * @param key Unique identifier for the value to delete
     * @throws SecureStorageException.DeleteFailure if the operation fails
     * @throws SecureStorageException.NotAvailable if secure storage is unavailable
     * @since 15 May 2026
     */
    suspend fun remove(key: String)

    /**
     * Deletes all stored values.
     * Typically called during user logout to clear all authentication tokens and sensitive data.
     *
     * @throws SecureStorageException.DeleteFailure if the operation fails
     * @throws SecureStorageException.NotAvailable if secure storage is unavailable
     * @since 15 May 2026
     */
    suspend fun clear()

    /**
     * Checks whether a value exists under the given key without retrieving it.
     *
     * @param key Unique identifier to check
     * @return true if a value exists, false otherwise
     * @throws SecureStorageException.ReadFailure if the operation fails
     * @throws SecureStorageException.NotAvailable if secure storage is unavailable
     * @since 15 May 2026
     */
    suspend fun contains(key: String): Boolean
}


