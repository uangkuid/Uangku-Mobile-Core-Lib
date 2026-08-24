package com.oratakashi.uangku.core.libs.core_crypto.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecAttrSynchronizableAny
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecUseDataProtectionKeychain
import platform.Security.kSecValueData
import kotlin.random.Random
import kotlin.test.Test

/**
 * Diagnostic probe for the `iosSimulatorArm64Test` Keychain failure. **Not a real test** — it asserts
 * nothing and always passes; it exists purely to print the `OSStatus` of every `SecItem*` query shape
 * under investigation in a single CI run, so the root cause stops being guessed one commit at a time.
 *
 * Grep the CI log for `KC-PROBE`. Delete this file once the fix lands (plan Step 4).
 *
 * @since 24 August 2026
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainQueryProbeTest {

    @Test
    fun print_probe_matrix() {
        line("=== begin ===")
        printProcessContext()

        val service = "com.oratakashi.uangku.core_crypto.probe.${Random.nextLong()}"
        val accessibleService = "com.oratakashi.uangku.core_crypto.probe.acc.${Random.nextLong()}"
        seed(service, accessible = false)
        seed(accessibleService, accessible = true)

        // --- reads: run before any delete, so the seeded items are still there ---
        copyMatching("P01", "account,limitOne,returnData,DPK", service, expectArray = false) { query ->
            CFDictionaryAddValue(query, kSecAttrAccount, cfString(ACCOUNT_A))
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        }
        copyMatching("P02", "accountless,limitAll,returnAttrs,DPK", service, expectArray = true) { query ->
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
        }
        copyMatching("P03", "accountless,limitOne,returnAttrs,DPK", service, expectArray = false) { query ->
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        }
        copyMatching("P04", "accountless,limitAll,returnAttrs,NO-DPK", service, expectArray = true) { query ->
            CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
        }
        copyMatching("P05", "accountless,limitAll,returnAttrs,DPK,syncAny", service, expectArray = true) { query ->
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
            CFDictionaryAddValue(query, kSecAttrSynchronizable, kSecAttrSynchronizableAny)
        }
        copyMatching("P06", "accountless,limitAll,returnData,DPK", service, expectArray = true) { query ->
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
        }
        copyMatching("P07", "accountless,limitAll,no-return,DPK", service, expectArray = false) { query ->
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
        }
        copyMatching("P11", "accountless,limitAll,returnAttrs,DPK,seeded-accessible", accessibleService, expectArray = true) { query ->
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
        }

        // --- deletes: destructive, so each is re-seeded first ---
        delete("P08", "account,DPK", service) { query ->
            CFDictionaryAddValue(query, kSecAttrAccount, cfString(ACCOUNT_A))
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
        }
        seed(service, accessible = false)
        delete("P09", "accountless,DPK", service) { query ->
            CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
        }
        seed(service, accessible = false)
        delete("P10", "accountless,NO-DPK", service) { }

        line("=== end ===")
    }

    /** Validates or kills the "unbundled process has no Keychain entitlement" hypothesis. */
    private fun printProcessContext() {
        val bundle = NSBundle.mainBundle
        line("context bundleIdentifier=${bundle.bundleIdentifier}")
        line("context bundlePath=${bundle.bundlePath}")
        line("context executablePath=${bundle.executablePath}")
    }

    /**
     * Writes [ACCOUNT_A] and [ACCOUNT_B] under [service] via the account-scoped `SecItemAdd` path,
     * which prior CI runs proved works. Already-present items report `errSecDuplicateItem`, which is
     * fine — the probe only needs the items to exist.
     */
    private fun seed(service: String, accessible: Boolean) {
        listOf(ACCOUNT_A, ACCOUNT_B).forEach { account ->
            val data = ("value-of-$account" as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            val query = newQuery(service).also { query ->
                CFDictionaryAddValue(query, kSecAttrAccount, cfString(account))
                CFDictionaryAddValue(query, kSecUseDataProtectionKeychain, kCFBooleanTrue)
                CFDictionaryAddValue(query, kSecValueData, CFBridgingRetain(data))
                if (accessible) {
                    CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
                }
            }
            val status = SecItemAdd(query, null)
            line("seed  service=${service.takeLast(SERVICE_TAIL)} account=$account accessible=$accessible -> ${describe(status)}")
        }
    }

    private fun copyMatching(
        id: String,
        shape: String,
        service: String,
        expectArray: Boolean,
        configure: (CFMutableDictionaryRef?) -> Unit,
    ) = memScoped {
        val query = newQuery(service).also(configure)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        val count = if (status == 0) countOf(result.value, expectArray) else "-"
        line("$id copyMatching $shape -> ${describe(status)} count=$count")
    }

    private fun delete(
        id: String,
        shape: String,
        service: String,
        configure: (CFMutableDictionaryRef?) -> Unit,
    ) {
        val status = SecItemDelete(newQuery(service).also(configure))
        line("$id SecItemDelete $shape -> ${describe(status)}")
    }

    /**
     * Item count, only for probes whose caller declared `expectArray` — i.e. `kSecMatchLimitAll`
     * combined with a `kSecReturn*` attribute, the one shape Security guarantees returns a `CFArray`.
     * The check has to be static: `CFGetTypeID` is not exposed by Kotlin/Native's CoreFoundation
     * bindings, and `CFArrayGetCount` on the `CFDictionary` that `kSecMatchLimitOne` returns is
     * undefined behaviour that would take the whole probe run down.
     */
    private fun countOf(result: COpaquePointer?, expectArray: Boolean): String {
        if (!expectArray) return "n/a"
        if (result == null) return "null"
        val array: CFArrayRef? = result.reinterpret()
        return CFArrayGetCount(array).toString()
    }

    /** A mutable query dictionary carrying only class + service; probes add the rest. */
    private fun newQuery(service: String) = CFDictionaryCreateMutable(
        null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
    ).also { query ->
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfString(service))
    }

    private fun cfString(value: String): CValuesRef<*>? = CFBridgingRetain(value as NSString)

    /** `OSStatus` plus its symbolic name, so the CI log needs no lookup table to read. */
    private fun describe(status: Int): String = when (status) {
        0 -> "status=0 errSecSuccess"
        -25291 -> "status=-25291 errSecNotAvailable"
        -25299 -> "status=-25299 errSecDuplicateItem"
        -25300 -> "status=-25300 errSecItemNotFound"
        -25308 -> "status=-25308 errSecInteractionNotAllowed"
        -26276 -> "status=-26276 errSecInvalidKeychain"
        -34018 -> "status=-34018 errSecMissingEntitlement"
        -50 -> "status=-50 errSecParam"
        else -> "status=$status errSecUnknown"
    }

    private fun line(message: String) = println("KC-PROBE $message")

    private companion object {
        const val ACCOUNT_A = "a"
        const val ACCOUNT_B = "b"
        const val SERVICE_TAIL = 8
    }
}
