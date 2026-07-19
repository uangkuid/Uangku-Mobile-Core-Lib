package com.oratakashi.uangku.core.libs.core_crypto.storage

import com.oratakashi.uangku.core.libs.core_crypto.exception.SecureStorageException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * [SecureStorage] backed by the iOS Keychain (`SecItem*`, generic-password class, keyed by a
 * shared service name + the entry key as the account).
 *
 * Replaces the old `mutableMapOf` implementation that falsely claimed to be Keychain-backed.
 * This one genuinely calls the Keychain. Its on-device behaviour is pending simulator/device
 * verification (the KDF/cipher/RSA/envelope contract is what the cross-platform test gate
 * covers; this store is a device-local cache).
 *
 * @since 17 July 2026
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecureStorage(
    private val service: String = DEFAULT_SERVICE,
) : SecureStorage {

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.Default) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw SecureStorageException.WriteFailure()
        deleteItem(key) // upsert: remove any existing, then add
        val status = memScoped {
            val query = baseQuery(key)
            CFDictionaryAddValue(query, kSecValueData, CFBridgingRetain(data))
            SecItemAdd(query, null)
        }
        if (status != errSecSuccess) throw SecureStorageException.WriteFailure()
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.Default) {
        memScoped {
            val query = baseQuery(key)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> {
                    val data = CFBridgingRelease(result.value) as? NSData
                        ?: return@memScoped null
                    NSString.create(data, NSUTF8StringEncoding) as String?
                }
                errSecItemNotFound -> null
                else -> throw SecureStorageException.ReadFailure()
            }
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.Default) {
        val status = deleteItem(key)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw SecureStorageException.DeleteFailure()
        }
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        val status = memScoped {
            val query = CFDictionaryCreateMutable(
                null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
            )
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, cfString(service))
            SecItemDelete(query)
        }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw SecureStorageException.DeleteFailure()
        }
    }

    override suspend fun contains(key: String): Boolean = get(key) != null

    private fun deleteItem(key: String): Int = memScoped {
        SecItemDelete(baseQuery(key))
    }

    /** A mutable query dictionary for one generic-password item (class + service + account). */
    private fun baseQuery(account: String) =
        CFDictionaryCreateMutable(
            null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        ).also { query ->
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, cfString(service))
            CFDictionaryAddValue(query, kSecAttrAccount, cfString(account))
        }

    private fun cfString(value: String): CValuesRef<*>? = CFBridgingRetain(value as NSString)

    private companion object {
        const val DEFAULT_SERVICE = "com.oratakashi.uangku.core_crypto"
    }
}
