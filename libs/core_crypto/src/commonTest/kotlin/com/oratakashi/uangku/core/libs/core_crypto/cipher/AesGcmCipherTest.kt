package com.oratakashi.uangku.core.libs.core_crypto.cipher

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AesGcmCipherTest {

    private val cipher = AesGcmCipher()
    private fun key(seed: Byte = 1) = SecretBytes.wrap(ByteArray(32) { (it + seed).toByte() })
    private val plaintext = "uangku secret payload".encodeToByteArray()

    @Test
    fun round_trip() = runTest {
        val sealed = cipher.seal(key(), plaintext)
        assertContentEquals(plaintext, cipher.open(key(), sealed))
    }

    @Test
    fun container_layout_is_ver_iv_ct_tag() = runTest {
        val pt = "hello".encodeToByteArray()
        val sealed = cipher.seal(key(), pt)
        assertEquals(pt.size + SealedData.OVERHEAD_BYTES, sealed.bytes.size, "size must be pt + 29")
        assertEquals(SealedData.VERSION, sealed.bytes[0], "first byte must be version 0x02")
        assertEquals(SealedData.IV_SIZE, sealed.iv.size)
    }

    @Test
    fun same_plaintext_twice_yields_different_container() = runTest {
        val a = cipher.seal(key(), plaintext)
        val b = cipher.seal(key(), plaintext)
        // Random IV per encryption — a static/predictable IV (the old iOS bug) would fail this.
        assertFalse(a.bytes.contentEquals(b.bytes), "IV must be random per seal")
    }

    @Test
    fun tampered_ciphertext_byte_fails_integrity() = runTest {
        val raw = cipher.seal(key(), plaintext).bytes.copyOf()
        raw[13] = (raw[13].toInt() xor 0xFF).toByte() // first ciphertext byte
        assertFailsWith<CryptoException.IntegrityCheckFailure> {
            cipher.open(key(), SealedData.fromRaw(raw))
        }
    }

    @Test
    fun tampered_iv_fails_integrity() = runTest {
        val raw = cipher.seal(key(), plaintext).bytes.copyOf()
        raw[1] = (raw[1].toInt() xor 0xFF).toByte() // first IV byte
        assertFailsWith<CryptoException.IntegrityCheckFailure> {
            cipher.open(key(), SealedData.fromRaw(raw))
        }
    }

    @Test
    fun tampered_tag_fails_integrity() = runTest {
        val raw = cipher.seal(key(), plaintext).bytes.copyOf()
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0xFF).toByte() // last tag byte
        assertFailsWith<CryptoException.IntegrityCheckFailure> {
            cipher.open(key(), SealedData.fromRaw(raw))
        }
    }

    @Test
    fun wrong_key_fails_integrity() = runTest {
        val sealed = cipher.seal(key(seed = 1), plaintext)
        assertFailsWith<CryptoException.IntegrityCheckFailure> {
            cipher.open(key(seed = 2), sealed)
        }
    }

    @Test
    fun container_shorter_than_overhead_is_rejected() {
        val tooShort = ByteArray(20).also { it[0] = SealedData.VERSION }
        assertFailsWith<CryptoException.InvalidCiphertextFormat> { SealedData.fromRaw(tooShort) }
    }

    @Test
    fun unsupported_version_is_rejected() {
        val buf = ByteArray(SealedData.OVERHEAD_BYTES).also { it[0] = 0x01 }
        assertFailsWith<CryptoException.UnsupportedVersion> { SealedData.fromRaw(buf) }
    }

    @Test
    fun invalid_base64_is_rejected() {
        assertFailsWith<CryptoException.InvalidCiphertextFormat> { SealedData.fromBase64("!!!not base64!!!") }
    }

    @Test
    fun base64_round_trip() = runTest {
        val sealed = cipher.seal(key(), plaintext)
        val reparsed = SealedData.fromBase64(sealed.toBase64())
        assertContentEquals(plaintext, cipher.open(key(), reparsed))
    }
}
