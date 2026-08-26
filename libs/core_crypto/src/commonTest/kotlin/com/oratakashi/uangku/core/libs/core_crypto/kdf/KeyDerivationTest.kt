package com.oratakashi.uangku.core.libs.core_crypto.kdf

import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Fast 2SKD logic tests at 1000 iterations — all structural properties live here, so the
 * suite stays in milliseconds. Byte-identity against the real 600k contract is the separate
 * [KeyDerivationVectorTest] gate.
 */
class KeyDerivationTest {

    private val engine = Pbkdf2HkdfKeyDerivationEngine()
    private val salt = ByteArray(16) { it.toByte() }
    private val params = KdfParameters(salt, iterations = 1000)
    private val secretKey = UangkuSecretKey.parse("UANGKU-ABC123-DEF456-GHI78-JKL90-MNO12")
    private val otherSecretKey = UangkuSecretKey.parse("UANGKU-ZZZ999-YYY888-WWW77-VVV66-UUU55")

    private suspend fun unlockHex(password: String, sk: UangkuSecretKey, p: KdfParameters) =
        engine.deriveUnlockKey(password, sk, p).copyBytes().toHex()

    @Test
    fun derivation_is_deterministic() = runTest {
        val a = engine.deriveCredentials("correcthorse", secretKey, params)
        val b = engine.deriveCredentials("correcthorse", secretKey, params)
        assertEquals(a.unlockKey.copyBytes().toHex(), b.unlockKey.copyBytes().toHex())
        assertEquals(a.authKey, b.authKey)
    }

    @Test
    fun password_is_load_bearing() = runTest {
        // If kdfPass (the password factor) didn't contribute, these would collide.
        assertNotEquals(unlockHex("passwordA", secretKey, params), unlockHex("passwordB", secretKey, params))
    }

    @Test
    fun secret_key_is_load_bearing() = runTest {
        // If kdfSecret (the secret-key factor) didn't contribute, these would collide.
        assertNotEquals(unlockHex("samepass", secretKey, params), unlockHex("samepass", otherSecretKey, params))
    }

    @Test
    fun salt_is_load_bearing() = runTest {
        val other = KdfParameters(ByteArray(16) { (it + 1).toByte() }, iterations = 1000)
        assertNotEquals(unlockHex("samepass", secretKey, params), unlockHex("samepass", secretKey, other))
    }

    @Test
    fun auth_key_is_44_char_base64() = runTest {
        val authKey = engine.deriveCredentials("correcthorse", secretKey, params).authKey
        assertEquals(44, authKey.length)
    }

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
