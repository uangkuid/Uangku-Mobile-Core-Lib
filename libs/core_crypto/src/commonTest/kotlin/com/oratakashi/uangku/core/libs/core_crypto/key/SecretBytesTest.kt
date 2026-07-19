package com.oratakashi.uangku.core.libs.core_crypto.key

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretBytesTest {

    @Test
    fun toString_never_leaks_the_bytes() {
        val secret = SecretBytes.wrap(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        val text = secret.toString().lowercase()
        assertFalse(text.contains("deadbeef"), "toString must not contain hex of the bytes")
        assertFalse(text.contains("beef"), "toString must not contain hex of the bytes")
        assertTrue(secret.toString().contains("size=4"))
    }

    @Test
    fun copyBytes_returns_an_independent_copy() {
        val secret = SecretBytes.copyOf(byteArrayOf(1, 2, 3))
        val a = secret.copyBytes()
        a[0] = 99
        assertContentEquals(byteArrayOf(1, 2, 3), secret.copyBytes(), "mutating a copy must not affect the holder")
    }

    @Test
    fun copyOf_does_not_alias_the_source() {
        val source = byteArrayOf(1, 2, 3)
        val secret = SecretBytes.copyOf(source)
        source[0] = 99
        assertContentEquals(byteArrayOf(1, 2, 3), secret.copyBytes(), "copyOf must not alias the source array")
    }

    @Test
    fun destroy_zeroes_and_is_idempotent() {
        val backing = byteArrayOf(7, 7, 7)
        val secret = SecretBytes.wrap(backing)
        secret.destroy()
        assertTrue(secret.isDestroyed)
        assertEquals(0, secret.size)
        assertContentEquals(byteArrayOf(0, 0, 0), backing, "destroy must zero the backing array")
        secret.destroy() // idempotent, no throw
    }
}
