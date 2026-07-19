package com.oratakashi.uangku.core.libs.core_crypto.asymmetric

import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import com.oratakashi.uangku.core.libs.core_crypto.support.KdfVectors
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RsaOaepCryptorTest {

    private val cryptor = RsaOaepCryptor()

    @Test
    fun max_plaintext_size_is_446() {
        assertEquals(446, cryptor.maxPlaintextSizeBytes)
    }

    @Test
    fun generate_encrypt_decrypt_round_trip() = runTest {
        val pair = cryptor.generateKeyPair()
        val plaintext = "a 32-byte data key placeholder..".encodeToByteArray()
        val ct = cryptor.encrypt(pair.publicKeyBase64, plaintext)
        assertContentEquals(plaintext, cryptor.decrypt(pair.privateKeyPem, ct))
    }

    @Test
    fun generated_public_key_is_base64_of_spki_pem() = runTest {
        val pair = cryptor.generateKeyPair()
        val pem = EncodingUtils.decodeBase64(pair.publicKeyBase64).decodeToString()
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"), "public key must be base64 of an SPKI PEM")
        val privPem = pair.privateKeyPem.copyBytes().decodeToString()
        assertTrue(privPem.startsWith("-----BEGIN PRIVATE KEY-----"), "private key must be a PKCS#8 PEM")
    }

    @Test
    fun plaintext_at_limit_encrypts_but_over_limit_fails() = runTest {
        val pair = cryptor.generateKeyPair()
        val ok = cryptor.encrypt(pair.publicKeyBase64, ByteArray(446))
        assertTrue(ok.isNotEmpty(), "446-byte plaintext must encrypt")
        assertFailsWith<CryptoException.EncryptionFailure> {
            cryptor.encrypt(pair.publicKeyBase64, ByteArray(447))
        }
    }

    /**
     * Parses a keypair generated OUTSIDE this library (the Set E fixture, PHP-produced SPKI +
     * PKCS#8 PEM, base64-wrapped) and round-trips through it. This is the real iOS risk: SecRsa
     * ASN.1 handling of foreign PEMs. `ek` in the vector is null (PHP OAEP is SHA-1), so this is
     * a self-consistent SHA-256 round-trip, not a cross-impl digest check.
     */
    @Test
    fun parses_foreign_pem_keypair_and_round_trips() = runTest {
        val privatePem = SecretBytes.wrap(EncodingUtils.decodeBase64(KdfVectors.setEPrivateKeyPemB64))
        val plaintext = "hybrid data key".encodeToByteArray()
        val ct = cryptor.encrypt(KdfVectors.setEPublicKeyB64, plaintext)
        assertContentEquals(plaintext, cryptor.decrypt(privatePem, ct))
    }
}
