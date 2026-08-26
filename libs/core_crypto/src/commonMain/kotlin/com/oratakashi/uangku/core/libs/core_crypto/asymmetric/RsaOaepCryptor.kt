package com.oratakashi.uangku.core.libs.core_crypto.asymmetric

import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RSA-OAEP implementation over cryptography-kotlin, locked to the backend contract:
 *
 * - **SHA-256** digest, passed explicitly (the library defaults to SHA-512).
 * - 4096-bit, public exponent 65537 (Apple provider hard-checks this).
 * - `public_key` = `base64(PEM SPKI)`; private key = PKCS#8 PEM.
 *
 * 4096-bit key generation is a probabilistic prime search — potentially several seconds,
 * markedly slower on iOS devices — so it runs on [Dispatchers.Default].
 *
 * @since 17 July 2026
 */
class RsaOaepCryptor(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
    private val keySizeBits: Int = 4096,
) : AsymmetricCryptor {

    override val maxPlaintextSizeBytes: Int = keySizeBits / 8 - 2 * 32 - 2 // 446 for 4096-bit

    private val algorithm get() = provider.get(RSA.OAEP)

    override suspend fun generateKeyPair(): RsaKeyPairMaterial = withContext(Dispatchers.Default) {
        try {
            val pair = algorithm.keyPairGenerator(keySize = keySizeBits.bits, digest = SHA256).generateKey()
            val pubPem = pair.publicKey.encodeToByteArray(RSA.PublicKey.Format.PEM)
            val privPem = pair.privateKey.encodeToByteArray(RSA.PrivateKey.Format.PEM)
            RsaKeyPairMaterial(
                publicKeyBase64 = EncodingUtils.encodeBase64(pubPem),
                privateKeyPem = SecretBytes.wrap(privPem),
            )
        } catch (e: Throwable) {
            throw CryptoException.KeyGenerationFailure(e)
        }
    }

    override suspend fun encrypt(publicKeyBase64: String, plaintext: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            try {
                val pemBytes = EncodingUtils.decodeBase64(publicKeyBase64)
                val publicKey = algorithm.publicKeyDecoder(SHA256)
                    .decodeFromByteArray(RSA.PublicKey.Format.PEM, pemBytes)
                publicKey.encryptor().encrypt(plaintext)
            } catch (e: Throwable) {
                throw CryptoException.EncryptionFailure(e)
            }
        }

    override suspend fun decrypt(privateKeyPem: SecretBytes, ciphertext: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            try {
                val privateKey = algorithm.privateKeyDecoder(SHA256)
                    .decodeFromByteArray(RSA.PrivateKey.Format.PEM, privateKeyPem.copyBytes())
                privateKey.decryptor().decrypt(ciphertext)
            } catch (e: Throwable) {
                throw CryptoException.DecryptionFailure(e)
            }
        }
}
