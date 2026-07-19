package com.oratakashi.uangku.core.libs.core_crypto.key

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UangkuSecretKeyTest {

    @Test
    fun generate_produces_unique_well_formed_keys() {
        val keys = HashSet<String>()
        repeat(1000) {
            val key = UangkuSecretKey.generate()
            assertTrue(keys.add(key.formatted), "generated a duplicate secret key")
            // round-trips through the structural parser
            UangkuSecretKey.parse(key.formatted)
        }
        assertEquals(1000, keys.size)
    }

    @Test
    fun parse_accepts_the_vector_reference_key() {
        // The exact secret_key used in kdf-vectors.json Set A — contains an 'O', so the
        // parser validates structure only, not the generation alphabet.
        val key = UangkuSecretKey.parse("UANGKU-ABC123-DEF456-GHI78-JKL90-MNO12")
        assertEquals("UANGKU-ABC123-DEF456-GHI78-JKL90-MNO12", key.formatted)
    }

    @Test
    fun parse_rejects_wrong_prefix() {
        assertFailsWith<CryptoException.InvalidSecretKeyFormat> {
            UangkuSecretKey.parse("WRONG-ABC123-DEF456-GHI78-JKL90-MNO12")
        }
    }

    @Test
    fun parse_rejects_wrong_segment_count() {
        assertFailsWith<CryptoException.InvalidSecretKeyFormat> {
            UangkuSecretKey.parse("UANGKU-ABC123-DEF456-GHI78-JKL90")
        }
    }

    @Test
    fun parse_rejects_wrong_segment_length() {
        assertFailsWith<CryptoException.InvalidSecretKeyFormat> {
            UangkuSecretKey.parse("UANGKU-ABC12-DEF456-GHI78-JKL90-MNO12")
        }
    }

    @Test
    fun parse_rejects_lowercase_or_symbols() {
        assertFailsWith<CryptoException.InvalidSecretKeyFormat> {
            UangkuSecretKey.parse("UANGKU-abc123-DEF456-GHI78-JKL90-MNO12")
        }
    }

    @Test
    fun error_reason_never_contains_the_value() {
        val bad = "UANGKU-secretpart-DEF456-GHI78-JKL90-MNO12"
        val ex = assertFailsWith<CryptoException.InvalidSecretKeyFormat> { UangkuSecretKey.parse(bad) }
        assertFalse(ex.reason.contains("secretpart"), "reason must not echo the offending value")
    }

    @Test
    fun toString_is_masked() {
        assertEquals("UangkuSecretKey(****)", UangkuSecretKey.generate().toString())
    }
}
