package com.oratakashi.uangku.core.libs.core_crypto.storage

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [KeychainSecureStorage] against the real iOS Keychain (`SecItem*`). Mirrors
 * [SecureStorageContractTest], scoped to a unique `service` per instance so entries don't collide
 * across test runs on the same simulator/device.
 */
class KeychainSecureStorageTest {

    private val service = "com.oratakashi.uangku.core_crypto.test.${Random.nextLong()}"
    private val storage = KeychainSecureStorage(service)

    @AfterTest
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
}
