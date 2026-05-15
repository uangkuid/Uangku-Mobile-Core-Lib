package com.oratakashi.uangku.core.libs.core_crypto.storage

/**
 * Centralized constants for secure storage keys.
 * Eliminates magic strings and ensures consistency across the application.
 * These identifiers are shared between common and platform-specific implementations.
 *
 * @since 15 May 2026
 */
object SecureStorageKey {
    const val ACCESS_TOKEN = "ACCESS_TOKEN"
    const val REFRESH_TOKEN = "REFRESH_TOKEN"
    const val USER_ID = "USER_ID"
}

