package com.oratakashi.uangku.core.libs.core_crypto.account

import com.oratakashi.uangku.core.libs.core_crypto.kdf.KdfParameters
import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey

/**
 * High-level account lifecycle facade over 2SKD + RSA + AES-GCM. Returns plain material
 * objects; the app performs the actual HTTP calls. Never touches the network.
 *
 * @since 17 July 2026
 */
interface AccountCrypto {

    /** Prepares registration material. Generates a Secret Key if [secretKey] is null. */
    suspend fun prepareRegistration(
        password: String,
        secretKey: UangkuSecretKey? = null,
        iterations: Int = KdfParameters.DEFAULT_ITERATIONS,
    ): RegistrationMaterial

    /** Login step 1: derives the authKey from the salt challenge. */
    suspend fun prepareLogin(
        password: String,
        secretKey: UangkuSecretKey,
        challenge: SaltChallenge,
    ): LoginPreparation

    /**
     * Login step 2: unwraps the private key with the unlockKey from [preparation].
     * @throws CryptoException.IntegrityCheckFailure if the credentials were wrong.
     */
    suspend fun completeLogin(preparation: LoginPreparation, vault: EncryptedVault): UnlockedSession

    /** Changes credentials while logged in — re-wraps the existing private key, preserving data. */
    suspend fun changeCredentials(
        session: UnlockedSession,
        newPassword: String,
        newSecretKey: UangkuSecretKey,
        iterations: Int = KdfParameters.DEFAULT_ITERATIONS,
    ): CredentialChangeMaterial

    /** Resets credentials without the old password — generates a fresh keypair (old data is lost). */
    suspend fun resetCredentials(
        newPassword: String,
        newSecretKey: UangkuSecretKey,
        iterations: Int = KdfParameters.DEFAULT_ITERATIONS,
    ): CredentialResetMaterial
}
