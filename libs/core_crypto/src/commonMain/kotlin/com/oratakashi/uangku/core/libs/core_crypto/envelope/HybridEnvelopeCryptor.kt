package com.oratakashi.uangku.core.libs.core_crypto.envelope

import com.oratakashi.uangku.core.libs.core_crypto.asymmetric.AsymmetricCryptor
import com.oratakashi.uangku.core.libs.core_crypto.cipher.SealedData
import com.oratakashi.uangku.core.libs.core_crypto.cipher.SymmetricCipher
import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Hybrid encryption: a random per-message 32-byte data key encrypts the payload with
 * AES-256-GCM; the data key is wrapped to the recipient's RSA public key.
 *
 * Same helper for financial fields (§8) and family private-key wrapping (§9) — the family
 * case is exactly why direct RSA is not an option (a 4096-bit private key PEM is ~3.2 KB,
 * far past the 446-byte RSA-OAEP limit).
 *
 * @since 17 July 2026
 */
class HybridEnvelopeCryptor(
    private val asymmetricCryptor: AsymmetricCryptor,
    private val symmetricCipher: SymmetricCipher,
) {

    private companion object {
        const val DATA_KEY_SIZE = 32
    }

    /** Encrypts [plaintext] to [publicKeyBase64] (`base64(PEM SPKI)`). */
    suspend fun seal(publicKeyBase64: String, plaintext: ByteArray): HybridEnvelope {
        val dataKey = SecretBytes.wrap(CryptographyRandom.nextBytes(DATA_KEY_SIZE))
        try {
            val ct = symmetricCipher.seal(dataKey, plaintext).toBase64()
            val ek = EncodingUtils.encodeBase64(asymmetricCryptor.encrypt(publicKeyBase64, dataKey.copyBytes()))
            return HybridEnvelope(HybridEnvelope.VERSION, ek, ct)
        } finally {
            dataKey.destroy()
        }
    }

    /** Decrypts [envelope] with the PKCS#8 PEM private key bytes in [privateKeyPem]. */
    suspend fun open(privateKeyPem: SecretBytes, envelope: HybridEnvelope): ByteArray {
        val dataKey = SecretBytes.wrap(
            asymmetricCryptor.decrypt(privateKeyPem, EncodingUtils.decodeBase64(envelope.encryptedKey)),
        )
        try {
            return symmetricCipher.open(dataKey, SealedData.fromBase64(envelope.ciphertext))
        } finally {
            dataKey.destroy()
        }
    }
}
