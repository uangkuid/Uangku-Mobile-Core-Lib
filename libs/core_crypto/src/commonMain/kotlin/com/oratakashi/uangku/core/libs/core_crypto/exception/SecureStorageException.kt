package com.oratakashi.uangku.core.libs.core_crypto.exception

/**
 * Sealed hierarchy for secure storage operation failures.
 * All platform-specific errors are mapped into this exhaustive vocabulary to prevent raw
 * exception leakage into consumer projects.
 *
 * @since 15 May 2026
 */
sealed class SecureStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /**
     * Thrown when a write operation fails on either platform.
     * Common causes:
     * - Android: encrypted write operation fails
     * - iOS: Keychain insert/update returns error status
     *
     * @param cause Optional root cause from platform implementation
     * @since 15 May 2026
     */
    data class WriteFailure(val rootCause: Throwable? = null) :
        SecureStorageException("Failed to write value to secure storage", rootCause)

    /**
     * Thrown when a read operation fails on either platform.
     * Common causes:
     * - Android: SharedPreferences read fails
     * - iOS: Keychain lookup returns error status
     *
     * @param cause Optional root cause from platform implementation
     * @since 15 May 2026
     */
    data class ReadFailure(val rootCause: Throwable? = null) :
        SecureStorageException("Failed to read value from secure storage", rootCause)

    /**
     * Thrown when a delete operation fails on either platform.
     * Common causes:
     * - Android: SharedPreferences remove fails
     * - iOS: Keychain deletion returns error status
     *
     * @param cause Optional root cause from platform implementation
     * @since 15 May 2026
     */
    data class DeleteFailure(val rootCause: Throwable? = null) :
        SecureStorageException("Failed to delete value from secure storage", rootCause)

    /**
     * Thrown when secure storage itself becomes unavailable.
     * Common causes:
     * - Android: Keystore unavailable, EncryptedSharedPreferences initialization fails
     * - iOS: Keychain services unavailable
     *
     * @param cause Optional root cause from platform implementation
     * @since 15 May 2026
     */
    data class NotAvailable(val rootCause: Throwable? = null) :
        SecureStorageException("Secure storage is not available", rootCause)
}

