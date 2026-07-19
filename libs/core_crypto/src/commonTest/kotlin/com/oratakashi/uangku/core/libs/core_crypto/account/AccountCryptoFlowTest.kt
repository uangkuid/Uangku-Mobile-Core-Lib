package com.oratakashi.uangku.core.libs.core_crypto.account

import com.oratakashi.uangku.core.libs.core_crypto.asymmetric.RsaOaepCryptor
import com.oratakashi.uangku.core.libs.core_crypto.cipher.AesGcmCipher
import com.oratakashi.uangku.core.libs.core_crypto.envelope.HybridEnvelopeCryptor
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import com.oratakashi.uangku.core.libs.core_crypto.kdf.Pbkdf2HkdfKeyDerivationEngine
import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * End-to-end account lifecycle against an in-memory stand-in for the server. Runs at 1000
 * iterations for speed — byte-identity with the real 600k contract is the separate vector gate.
 */
class AccountCryptoFlowTest {

    private val iterations = 1000
    private val rsa = RsaOaepCryptor()
    private val cipher = AesGcmCipher()
    private val account = DefaultAccountCrypto(Pbkdf2HkdfKeyDerivationEngine(), rsa, cipher)
    private val hybrid = HybridEnvelopeCryptor(rsa, cipher)

    /** Minimal stand-in for what the server persists per account. */
    private class ServerRecord(
        var saltBase64: String,
        var iterations: Int,
        var authKey: String,
        var publicKeyBase64: String,
        var wrappedPrivateKeyBase64: String,
    )

    private fun store(m: RegistrationMaterial) = ServerRecord(
        m.saltBase64, m.iterations, m.authKey, m.publicKeyBase64, m.wrappedPrivateKeyBase64,
    )

    @Test
    fun register_then_login_recovers_the_same_private_key() = runTest {
        val pw = "correcthorse"
        val sk = UangkuSecretKey.generate()
        val reg = account.prepareRegistration(pw, sk, iterations)
        val server = store(reg)

        val prep = account.prepareLogin(pw, sk, SaltChallenge(server.saltBase64, server.iterations))
        assertEquals(server.authKey, prep.authKey, "authKey proof must match what the server stored")

        val session = account.completeLogin(prep, EncryptedVault(server.wrappedPrivateKeyBase64))
        assertContentEquals(
            reg.session.privateKeyPem.copyBytes(),
            session.privateKeyPem.copyBytes(),
            "login must recover the exact private key from registration",
        )
    }

    @Test
    fun wrong_password_fails_to_unlock() = runTest {
        val sk = UangkuSecretKey.generate()
        val reg = account.prepareRegistration("rightpass", sk, iterations)
        val server = store(reg)

        val badPrep = account.prepareLogin("wrongpass", sk, SaltChallenge(server.saltBase64, server.iterations))
        assertNotEquals(server.authKey, badPrep.authKey, "wrong password must not produce the stored authKey")
        assertFailsWith<CryptoException.IntegrityCheckFailure> {
            account.completeLogin(badPrep, EncryptedVault(server.wrappedPrivateKeyBase64))
        }
    }

    @Test
    fun change_credentials_preserves_the_keypair_and_data() = runTest {
        val sk = UangkuSecretKey.generate()
        val reg = account.prepareRegistration("oldpass", sk, iterations)
        val server = store(reg)

        // Encrypt some financial data to the account's public key.
        val envelope = hybrid.seal(reg.publicKeyBase64, "150000".encodeToByteArray())

        val newSk = UangkuSecretKey.generate()
        val change = account.changeCredentials(reg.session, "newpass", newSk, iterations)
        server.saltBase64 = change.saltBase64
        server.authKey = change.authKey
        server.wrappedPrivateKeyBase64 = change.wrappedPrivateKeyBase64
        // publicKey unchanged by design.

        // Log in with the NEW credentials and confirm old data still decrypts.
        val prep = account.prepareLogin("newpass", newSk, SaltChallenge(server.saltBase64, server.iterations))
        assertEquals(server.authKey, prep.authKey)
        val session = account.completeLogin(prep, EncryptedVault(server.wrappedPrivateKeyBase64))
        assertEquals("150000", hybrid.open(session.privateKeyPem, envelope).decodeToString())
    }

    @Test
    fun reset_credentials_makes_old_data_unreadable() = runTest {
        val sk = UangkuSecretKey.generate()
        val reg = account.prepareRegistration("oldpass", sk, iterations)

        val oldEnvelope = hybrid.seal(reg.publicKeyBase64, "150000".encodeToByteArray())

        val reset = account.resetCredentials("brandnew", UangkuSecretKey.generate(), iterations)
        assertNotEquals(reg.publicKeyBase64, reset.publicKeyBase64, "reset must generate a fresh keypair")

        // The new private key cannot decrypt data sealed to the old public key.
        assertFailsWith<CryptoException> {
            hybrid.open(reset.session.privateKeyPem, oldEnvelope)
        }
    }
}
