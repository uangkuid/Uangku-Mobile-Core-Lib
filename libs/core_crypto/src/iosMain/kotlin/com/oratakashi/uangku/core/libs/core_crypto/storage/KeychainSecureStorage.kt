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
import platform.Security.kSecUseDataProtectionKeychain
import platform.Security.kSecValueData

/**
 * [SecureStorage] backed by the iOS Keychain (`SecItem*`, generic-password class, keyed by a
 * shared service name + the entry key as the account).
 *
 * Replaces the old `mutableMapOf` implementation that falsely claimed to be Keychain-backed.
 * This one genuinely calls the Keychain. Every query opts into `kSecUseDataProtectionKeychain` —
 * without it, `SecItem*` calls fail (observed as `errSecMissingEntitlement`-class statuses) when
 * run from an unsigned/unbundled process, which is how Kotlin/Native's `iosSimulatorArm64Test`
 * binary executes; this store is a device-local cache exercised via [KeychainSecureStorageTest].
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
            logOsStatusDiagnostic("remove", status)
            throw SecureStorageException.DeleteFailure()
        }
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        // A single SecItemDelete with kSecMatchLimitAll against the data-protection keychain
        // (class+service query, no account — matches every item under the service) reliably
        // fails on the iOS Simulator with errSecNotAvailable(-25291), even though the identical
        // single-item shape below (with kSecAttrAccount) works fine — a known simulator quirk
        // with bulk/"delete all" keychain operations. Deleting one match at a time instead
        // reuses the query shape that's already proven to work.
        var status: Int
        do {
            status = memScoped {
                val query = CFDictionaryCreateMutable(
                    null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
                )
                CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionaryAddValue(query, kSecAttrService, cfString(service))
                CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
                CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
                SecItemDelete(query)
            }
        } while (status == errSecSuccess)
        if (status != errSecItemNotFound) {
            logOsStatusDiagnostic("clear", status)
            throw SecureStorageException.DeleteFailure()
        }
    }

    override suspend fun contains(key: String): Boolean = get(key) != null

    private fun deleteItem(key: String): Int = memScoped {
        SecItemDelete(baseQuery(key))
    }

    /**
     * Prints a failing `SecItem*` `OSStatus` straight to stdout.
     *
     * TODO: remove once the root cause of the `clear()` / `iosSimulatorArm64Test` delete failure is
     * confirmed — this is diagnostic-only, not a fix. Deliberately `println`, not
     * `DeleteFailure(rootCause = ...)`: the Kotlin/Native test-failure summary renders
     * `rootCause.toString()` truncated to the exception's qualified name only, dropping `.message`
     * entirely (confirmed from a real CI run — `DeleteFailure(rootCause=kotlin.IllegalStateException`
     * with no message and no closing paren), so that channel can't carry this value out.
     */
    private fun logOsStatusDiagnostic(op: String, status: Int) {
        println("KeychainSecureStorage.$op(): SecItemDelete failed with OSStatus=$status")
    }

    /** A mutable query dictionary for one generic-password item (class + service + account). */
    private fun baseQuery(account: String) =
        CFDictionaryCreateMutable(
            null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        ).also { query ->
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, cfString(service))
            CFDictionaryAddValue(query, kSecAttrAccount, cfString(account))
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
        }

    private fun cfString(value: String): CValuesRef<*>? = CFBridgingRetain(value as NSString)

    private companion object {
        const val DEFAULT_SERVICE = "com.oratakashi.uangku.core_crypto"
    }
}
