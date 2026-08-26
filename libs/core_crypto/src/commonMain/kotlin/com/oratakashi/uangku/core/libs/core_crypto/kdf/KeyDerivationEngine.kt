package com.oratakashi.uangku.core.libs.core_crypto.kdf

import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey

/** unlockKey + its derived authKey, produced together by [KeyDerivationEngine.deriveCredentials]. */
class DerivedCredentials(val unlockKey: SecretBytes, val authKey: String)

/**
 * Two-Secret-Key Derivation (2SKD) per the backend contract §4.2.
 *
 * ```
 * kdfPass   = PBKDF2-HMAC-SHA256(NFC(password), rawSalt, iterations, 32)
 * kdfSecret = HKDF-SHA256(ikm=secretKey, salt=rawSalt, info="uangku-secretkey-v1", 32)
 * unlockKey = kdfPass XOR kdfSecret
 * authKey   = base64(HKDF-SHA256(ikm=unlockKey, salt=null, info="uangku-auth-v1", 32))
 * ```
 *
 * @since 17 July 2026
 */
interface KeyDerivationEngine {

    /** Derives the unlockKey from both factors. Never leaves the device. */
    suspend fun deriveUnlockKey(
        password: String,
        secretKey: UangkuSecretKey,
        params: KdfParameters,
    ): SecretBytes

    /** Derives the base64 authKey (44 chars) proving possession of both factors. */
    suspend fun deriveAuthKey(unlockKey: SecretBytes): String

    /** Convenience: derives unlockKey and authKey together. */
    suspend fun deriveCredentials(
        password: String,
        secretKey: UangkuSecretKey,
        params: KdfParameters,
    ): DerivedCredentials
}
