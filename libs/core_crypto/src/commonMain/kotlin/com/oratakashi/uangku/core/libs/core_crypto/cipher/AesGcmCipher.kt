package com.oratakashi.uangku.core.libs.core_crypto.cipher

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AES-256-GCM implementation over cryptography-kotlin.
 *
 * The engine's `encrypt`/`decrypt` operate on the combined `iv‖ct‖tag` form, matching the
 * [SealedData] payload directly. CPU-bound, so work runs on [Dispatchers.Default].
 *
 * @since 17 July 2026
 */
class AesGcmCipher(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : SymmetricCipher {

    override suspend fun seal(key: SecretBytes, plaintext: ByteArray): SealedData =
        withContext(Dispatchers.Default) {
            val aesKey = decodeKey(key)
            val output = try {
                aesKey.cipher().encrypt(plaintext)
            } catch (e: Throwable) {
                throw CryptoException.EncryptionFailure(e)
            }
            SealedData.fromIvCiphertextTag(output)
        }

    override suspend fun open(key: SecretBytes, sealed: SealedData): ByteArray =
        withContext(Dispatchers.Default) {
            val aesKey = decodeKey(key)
            try {
                aesKey.cipher().decrypt(sealed.ivCiphertextTag)
            } catch (e: Throwable) {
                // Container is already structurally valid (SealedData.fromRaw), so a failure
                // here is a tag mismatch — wrong key or tampered data. GCM cannot tell them apart.
                throw CryptoException.IntegrityCheckFailure(e)
            }
        }

    private suspend fun decodeKey(key: SecretBytes): AES.GCM.Key =
        try {
            provider.get(AES.GCM).keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, key.copyBytes())
        } catch (e: Throwable) {
            throw CryptoException.DecryptionFailure(e)
        }
}
