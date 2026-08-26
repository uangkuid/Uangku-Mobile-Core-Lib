package com.oratakashi.uangku.core.libs.core_crypto.kdf

import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey
import com.oratakashi.uangku.core.libs.core_crypto.text.normalizeNfc
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PBKDF2 + HKDF + XOR implementation of 2SKD over cryptography-kotlin.
 *
 * This is the highest-risk point in the module: a single byte wrong here means no user can
 * ever log in. Verified byte-for-byte against the backend reference vectors (Set A–B).
 *
 * The 600k-iteration PBKDF2 is CPU-bound and cannot be interrupted mid-derivation — a
 * cancelled login still burns its full cost. Work runs on [Dispatchers.Default].
 *
 * @since 17 July 2026
 */
class Pbkdf2HkdfKeyDerivationEngine(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : KeyDerivationEngine {

    override suspend fun deriveUnlockKey(
        password: String,
        secretKey: UangkuSecretKey,
        params: KdfParameters,
    ): SecretBytes = withContext(Dispatchers.Default) {
        try {
            val kdfPass = provider.get(PBKDF2).secretDerivation(
                digest = SHA256,
                iterations = params.iterations,
                outputSize = 256.bits,
                salt = params.salt,
            ).deriveSecretToByteArray(password.normalizeNfc().encodeToByteArray())

            val kdfSecret = provider.get(HKDF).secretDerivation(
                digest = SHA256,
                outputSize = 256.bits,
                salt = params.salt, // same 16-byte RAW salt as PBKDF2 — the §4.2 contract
                info = KdfConstants.INFO_SECRET_KEY.encodeToByteArray(),
            ).deriveSecretToByteArray(secretKey.formatted.encodeToByteArray())

            val unlock = ByteArray(kdfPass.size) { (kdfPass[it].toInt() xor kdfSecret[it].toInt()).toByte() }
            kdfPass.fill(0)
            kdfSecret.fill(0)
            SecretBytes.wrap(unlock)
        } catch (e: Throwable) {
            throw CryptoException.KeyDerivationFailure(e)
        }
    }

    override suspend fun deriveAuthKey(unlockKey: SecretBytes): String =
        withContext(Dispatchers.Default) {
            try {
                val authBytes = provider.get(HKDF).secretDerivation(
                    digest = SHA256,
                    outputSize = 256.bits,
                    salt = null, // RFC 5869 zeros(HashLen) ≡ PHP hash_hkdf(salt="")
                    info = KdfConstants.INFO_AUTH.encodeToByteArray(),
                ).deriveSecretToByteArray(unlockKey.copyBytes())
                EncodingUtils.encodeBase64(authBytes)
            } catch (e: Throwable) {
                throw CryptoException.KeyDerivationFailure(e)
            }
        }

    override suspend fun deriveCredentials(
        password: String,
        secretKey: UangkuSecretKey,
        params: KdfParameters,
    ): DerivedCredentials {
        val unlockKey = deriveUnlockKey(password, secretKey, params)
        return DerivedCredentials(unlockKey, deriveAuthKey(unlockKey))
    }
}
