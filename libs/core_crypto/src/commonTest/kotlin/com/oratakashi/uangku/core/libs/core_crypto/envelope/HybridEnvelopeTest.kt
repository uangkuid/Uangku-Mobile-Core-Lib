package com.oratakashi.uangku.core.libs.core_crypto.envelope

import com.oratakashi.uangku.core.libs.core_crypto.asymmetric.RsaOaepCryptor
import com.oratakashi.uangku.core.libs.core_crypto.cipher.AesGcmCipher
import com.oratakashi.uangku.core.libs.core_crypto.cipher.SealedData
import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import com.oratakashi.uangku.core.libs.core_crypto.support.KdfVectors
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HybridEnvelopeTest {

    private val cryptor = HybridEnvelopeCryptor(RsaOaepCryptor(), AesGcmCipher())

    @Test
    fun seal_open_round_trip() = runTest {
        val pair = RsaOaepCryptor().generateKeyPair()
        val plaintext = "150000".encodeToByteArray()
        val envelope = cryptor.seal(pair.publicKeyBase64, plaintext)
        assertEquals(HybridEnvelope.VERSION, envelope.version)
        assertContentEquals(plaintext, cryptor.open(pair.privateKeyPem, envelope))
    }

    @Test
    fun round_trips_a_payload_far_larger_than_the_rsa_limit() = runTest {
        // A family private key PEM (~3.2 KB) — the whole reason hybrid exists. Direct RSA
        // caps at 446 bytes, so this proves the envelope handles the real family case.
        val pair = RsaOaepCryptor().generateKeyPair()
        val bigPayload = pair.privateKeyPem.copyBytes()
        val envelope = cryptor.seal(pair.publicKeyBase64, bigPayload)
        assertContentEquals(bigPayload, cryptor.open(pair.privateKeyPem, envelope))
    }

    @Test
    fun set_e_ct_opens_with_data_key() = runTest {
        // The envelope ct is the same ver|iv|ct|tag container — decode with the vector data key.
        val dataKey = SecretBytes.wrap(EncodingUtils.decodeHex(KdfVectors.setEDataKeyHex))
        val plain = AesGcmCipher().open(dataKey, SealedData.fromBase64(KdfVectors.setEEnvelopeCtB64))
        assertEquals(KdfVectors.setEPlaintext, plain.decodeToString())
    }

    @Test
    fun codec_round_trip() {
        val envelope = HybridEnvelope(2, "ekBase64==", "ctBase64==")
        val decoded = HybridEnvelopeCodec.decode(HybridEnvelopeCodec.encode(envelope))
        assertEquals(envelope.version, decoded.version)
        assertEquals(envelope.encryptedKey, decoded.encryptedKey)
        assertEquals(envelope.ciphertext, decoded.ciphertext)
    }

    @Test
    fun codec_tolerates_whitespace_field_order_and_unknown_fields() {
        val messy = """
            {
              "ct": "ctValue",
              "extra": "ignored",
              "v": 2,
              "ek": "ekValue"
            }
        """.trimIndent()
        val decoded = HybridEnvelopeCodec.decode(messy)
        assertEquals(2, decoded.version)
        assertEquals("ekValue", decoded.encryptedKey)
        assertEquals("ctValue", decoded.ciphertext)
    }

    @Test
    fun codec_rejects_unsupported_version() {
        assertFailsWith<CryptoException.UnsupportedVersion> {
            HybridEnvelopeCodec.decode("""{"v":1,"ek":"e","ct":"c"}""")
        }
    }

    @Test
    fun codec_rejects_missing_fields_and_bad_json() {
        assertFailsWith<CryptoException.InvalidCiphertextFormat> {
            HybridEnvelopeCodec.decode("""{"v":2,"ct":"c"}""") // missing ek
        }
        assertFailsWith<CryptoException.InvalidCiphertextFormat> {
            HybridEnvelopeCodec.decode("not json at all")
        }
    }
}
