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
import platform.Security.errSecNotAvailable
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
 * This one genuinely calls the Keychain.
 *
 * **This class cannot be exercised from Kotlin/Native's `iosSimulatorArm64Test` task.** The task is
 * configured with `standalone = false`, so the binary is bootstrapped into the simulator's launchd
 * and `securityd` is reachable — that part is solved. What remains is not: `test.kexe` is a bare
 * Mach-O with no app bundle (`NSBundle.mainBundle.bundleIdentifier` is `null`) and therefore no
 * `application-identifier` entitlement, so *every* `SecItem*` call returns `errSecMissingEntitlement`
 * (-34018), including a plain account-scoped `SecItemAdd`. No query-dictionary attribute changes
 * that. The real coverage lives in the `CoreCryptoTests` XCTest target, which runs hosted by the
 * `iosApp` application and therefore holds that entitlement.
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
            val query = accountQuery(key)
            CFDictionaryAddValue(query, kSecValueData, CFBridgingRetain(data))
            SecItemAdd(query, null)
        }
        if (status != errSecSuccess) throw failureFor(status, SecureStorageException.WriteFailure())
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.Default) {
        memScoped {
            val query = accountQuery(key)
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
                else -> throw failureFor(status, SecureStorageException.ReadFailure())
            }
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.Default) {
        verifyDeleted(deleteItem(key))
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        verifyDeleted(memScoped { SecItemDelete(serviceQuery()) })
    }

    override suspend fun contains(key: String): Boolean = get(key) != null

    private fun deleteItem(key: String): Int = memScoped {
        SecItemDelete(accountQuery(key))
    }

    /** A delete that matched nothing already satisfies the caller's intent, so it is not an error. */
    private fun verifyDeleted(status: Int) {
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw failureFor(status, SecureStorageException.DeleteFailure())
        }
    }

    /**
     * Maps a failing `OSStatus` onto the module's exception vocabulary. `errSecNotAvailable` means
     * the Keychain itself could not be reached, which is a different problem from the operation
     * failing — callers can retry the latter, never the former.
     *
     * `errSecMissingEntitlement` deliberately gets no case of its own: a shipped app always holds the
     * entitlement, so it is a test-harness condition, and giving it a type would widen this library's
     * public exception surface for a state no consumer can reach.
     */
    private fun failureFor(status: Int, operationFailure: SecureStorageException) =
        if (status == errSecNotAvailable) SecureStorageException.NotAvailable() else operationFailure

    /** A mutable query dictionary matching every item stored under [service]. */
    private fun serviceQuery() =
        CFDictionaryCreateMutable(
            null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        ).also { query ->
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, cfString(service))
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
        }

    /** [serviceQuery] narrowed to the single generic-password item held under [account]. */
    private fun accountQuery(account: String) = serviceQuery().also { query ->
        CFDictionaryAddValue(query, kSecAttrAccount, cfString(account))
    }

    private fun cfString(value: String): CValuesRef<*>? = CFBridgingRetain(value as NSString)

    private companion object {
        const val DEFAULT_SERVICE = "com.oratakashi.uangku.core_crypto"
    }
}
