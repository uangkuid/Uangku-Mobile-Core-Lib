package com.oratakashi.uangku.core.libs.core_crypto.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oratakashi.uangku.core.libs.core_crypto.exception.SecureStorageException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

/**
 * Exercises [AndroidKeystoreSecureStorage] against the real AndroidKeyStore + SharedPreferences
 * on a device/emulator. Mirrors [SecureStorageContractTest], plus edge cases that only a real
 * encrypted backend can prove.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecureStorageTest {

    private val runId = UUID.randomUUID().toString()
    private val prefsName = "core_crypto_test_prefs_$runId"
    private val keyAlias = "core_crypto_test_key_$runId"
    private lateinit var storage: AndroidKeystoreSecureStorage

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = AndroidKeystoreSecureStorage(context, prefsName, keyAlias)
    }

    @After
    fun tearDown() = runTest {
        storage.clear()
    }

    @Test
    fun put_get_round_trip() = runTest {
        storage.put("token", "abc123")
        assertEquals("abc123", storage.get("token"))
    }

    @Test
    fun get_absent_key_is_null() = runTest {
        assertNull(storage.get("missing"))
    }

    @Test
    fun put_overwrites() = runTest {
        storage.put("k", "v1")
        storage.put("k", "v2")
        assertEquals("v2", storage.get("k"))
    }

    @Test
    fun contains_reflects_presence() = runTest {
        assertFalse(storage.contains("k"))
        storage.put("k", "v")
        assertTrue(storage.contains("k"))
    }

    @Test
    fun remove_deletes_entry() = runTest {
        storage.put("k", "v")
        storage.remove("k")
        assertFalse(storage.contains("k"))
        assertNull(storage.get("k"))
    }

    @Test
    fun clear_removes_everything() = runTest {
        storage.put("a", "1")
        storage.put("b", "2")
        storage.clear()
        assertFalse(storage.contains("a"))
        assertFalse(storage.contains("b"))
    }

    @Test
    fun stored_value_is_not_plaintext() = runTest {
        storage.put("secret", "this-is-plaintext")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString("secret", null)

        assertTrue(!raw.isNullOrEmpty())
        assertFalse(raw!!.contains("this-is-plaintext"))
    }

    @Test
    fun corrupted_ciphertext_throws_read_failure() = runTest {
        storage.put("secret", "value")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val garbage = Base64.getEncoder().encodeToString(Random.nextBytes(32))
        prefs.edit().putString("secret", garbage).commit()

        try {
            storage.get("secret")
            fail("Expected SecureStorageException.ReadFailure")
        } catch (e: SecureStorageException.ReadFailure) {
            // expected
        }
    }
}
