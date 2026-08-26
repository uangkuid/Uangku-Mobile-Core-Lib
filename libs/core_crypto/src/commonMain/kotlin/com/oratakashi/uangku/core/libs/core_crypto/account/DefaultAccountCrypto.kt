package com.oratakashi.uangku.core.libs.core_crypto.account

import com.oratakashi.uangku.core.libs.core_crypto.asymmetric.AsymmetricCryptor
import com.oratakashi.uangku.core.libs.core_crypto.cipher.SealedData
import com.oratakashi.uangku.core.libs.core_crypto.cipher.SymmetricCipher
import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.kdf.KdfParameters
import com.oratakashi.uangku.core.libs.core_crypto.kdf.KeyDerivationEngine
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Default [AccountCrypto], composing the derivation engine, RSA cryptor, and symmetric cipher.
 *
 * @since 17 July 2026
 */
class DefaultAccountCrypto(
    private val keyDerivationEngine: KeyDerivationEngine,
    private val asymmetricCryptor: AsymmetricCryptor,
    private val symmetricCipher: SymmetricCipher,
) : AccountCrypto {

    override suspend fun prepareRegistration(
        password: String,
        secretKey: UangkuSecretKey?,
        iterations: Int,
    ): RegistrationMaterial {
        val sk = secretKey ?: UangkuSecretKey.generate()
        val salt = randomSalt()
        val credentials = keyDerivationEngine.deriveCredentials(password, sk, KdfParameters(salt, iterations))
        val keyPair = asymmetricCryptor.generateKeyPair()
        val wrapped = symmetricCipher.seal(credentials.unlockKey, keyPair.privateKeyPem.copyBytes()).toBase64()
        return RegistrationMaterial(
            secretKey = sk,
            saltBase64 = EncodingUtils.encodeBase64(salt),
            iterations = iterations,
            authKey = credentials.authKey,
            publicKeyBase64 = keyPair.publicKeyBase64,
            wrappedPrivateKeyBase64 = wrapped,
            session = UnlockedSession(credentials.unlockKey, keyPair.privateKeyPem),
        )
    }

    override suspend fun prepareLogin(
        password: String,
        secretKey: UangkuSecretKey,
        challenge: SaltChallenge,
    ): LoginPreparation {
        val salt = EncodingUtils.decodeBase64(challenge.saltBase64)
        val credentials = keyDerivationEngine.deriveCredentials(
            password, secretKey, KdfParameters(salt, challenge.iterations),
        )
        return LoginPreparation(credentials.authKey, credentials.unlockKey)
    }

    override suspend fun completeLogin(preparation: LoginPreparation, vault: EncryptedVault): UnlockedSession {
        val privatePem = symmetricCipher.open(
            preparation.unlockKey,
            SealedData.fromBase64(vault.wrappedPrivateKeyBase64),
        )
        return UnlockedSession(preparation.unlockKey, SecretBytes.wrap(privatePem))
    }

    override suspend fun changeCredentials(
        session: UnlockedSession,
        newPassword: String,
        newSecretKey: UangkuSecretKey,
        iterations: Int,
    ): CredentialChangeMaterial {
        val salt = randomSalt()
        val credentials = keyDerivationEngine.deriveCredentials(newPassword, newSecretKey, KdfParameters(salt, iterations))
        // Re-wrap the SAME private key with the new unlockKey — keypair (and data) preserved.
        val rewrapped = symmetricCipher.seal(credentials.unlockKey, session.privateKeyPem.copyBytes()).toBase64()
        return CredentialChangeMaterial(
            saltBase64 = EncodingUtils.encodeBase64(salt),
            iterations = iterations,
            authKey = credentials.authKey,
            wrappedPrivateKeyBase64 = rewrapped,
            session = UnlockedSession(credentials.unlockKey, SecretBytes.copyOf(session.privateKeyPem.copyBytes())),
        )
    }

    override suspend fun resetCredentials(
        newPassword: String,
        newSecretKey: UangkuSecretKey,
        iterations: Int,
    ): CredentialResetMaterial {
        val salt = randomSalt()
        val credentials = keyDerivationEngine.deriveCredentials(newPassword, newSecretKey, KdfParameters(salt, iterations))
        val keyPair = asymmetricCryptor.generateKeyPair() // fresh keypair — old data becomes unreadable
        val wrapped = symmetricCipher.seal(credentials.unlockKey, keyPair.privateKeyPem.copyBytes()).toBase64()
        return CredentialResetMaterial(
            saltBase64 = EncodingUtils.encodeBase64(salt),
            iterations = iterations,
            authKey = credentials.authKey,
            publicKeyBase64 = keyPair.publicKeyBase64,
            wrappedPrivateKeyBase64 = wrapped,
            session = UnlockedSession(credentials.unlockKey, keyPair.privateKeyPem),
        )
    }

    private fun randomSalt(): ByteArray = CryptographyRandom.nextBytes(KdfParameters.SALT_SIZE_BYTES)
}
