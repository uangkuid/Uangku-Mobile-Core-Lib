package com.oratakashi.uangku.core.libs.core_crypto.storage

import com.oratakashi.uangku.core.libs.core_crypto.exception.SecureStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS implementation of SecureStorage.
 *
 * This is a placeholder implementation for the iOS platform.
 * In a real application, this would use native Keychain Services through proper cinterop.
 *
 * Security design:
 * - Storage: Native iOS Keychain (TEE/Secure Enclave when available)
 * - Accessibility: Locked when device is locked
 * - Hardware-bound protection
 *
 * All operations execute on [Dispatchers.Default].
 *
 * @since 15 May 2026
 */
actual class SecureStorage {
    private val storage = mutableMapOf<String, String>()

    actual suspend fun put(key: String, value: String) {
        withContext(Dispatchers.Default) {
            try {
                storage[key] = value
            } catch (e: Exception) {
                throw SecureStorageException.WriteFailure(e)
            }
        }
    }

    actual suspend fun get(key: String): String? {
        return withContext(Dispatchers.Default) {
            try {
                storage[key]
            } catch (e: Exception) {
                throw SecureStorageException.ReadFailure(e)
            }
        }
    }

    actual suspend fun remove(key: String) {
        withContext(Dispatchers.Default) {
            try {
                storage.remove(key)
            } catch (e: Exception) {
                throw SecureStorageException.DeleteFailure(e)
            }
        }
    }

    actual suspend fun clear() {
        withContext(Dispatchers.Default) {
            try {
                storage.clear()
            } catch (e: Exception) {
                throw SecureStorageException.DeleteFailure(e)
            }
        }
    }

    actual suspend fun contains(key: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                storage.containsKey(key)
            } catch (e: Exception) {
                throw SecureStorageException.ReadFailure(e)
            }
        }
    }
}



