package com.oratakashi.uangku.core.libs.core_crypto.kdf

import com.oratakashi.uangku.core.libs.core_crypto.cipher.AesGcmCipher
import com.oratakashi.uangku.core.libs.core_crypto.cipher.SealedData
import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes
import com.oratakashi.uangku.core.libs.core_crypto.key.UangkuSecretKey
import com.oratakashi.uangku.core.libs.core_crypto.support.KdfVectors
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * THE gate for the whole project (plan Phase 3): byte-identity against the backend's
 * reference vectors at the real 600k iterations. If this is green on Android host AND iOS
 * simulator, the Blocker #1 class of bug is closed. If it fails, the first mismatch points
 * straight at the broken step.
 */
class KeyDerivationVectorTest {

    private val engine = Pbkdf2HkdfKeyDerivationEngine()
    private val cipher = AesGcmCipher()
    private val provider = CryptographyProvider.Default

    private fun passwordOf(case: KdfVectors.SetACase): String =
        EncodingUtils.decodeHex(case.passwordHex).decodeToString()

    private suspend fun deriveHex(case: KdfVectors.SetACase): Pair<String, String> {
        val params = KdfParameters(EncodingUtils.decodeBase64(case.saltB64), case.iterations)
        val creds = engine.deriveCredentials(passwordOf(case), UangkuSecretKey.parse(case.secretKey), params)
        return EncodingUtils.encodeHex(creds.unlockKey.copyBytes()) to creds.authKey
    }

    /** Set A byte-identity for the normalization-stable cases (all but nfd_cafe). */
    @Test
    fun set_a_unlock_and_auth_key_are_byte_identical() = runTest {
        val cases = KdfVectors.setA.filter { it.label != "nfd_cafe" }
        for (case in cases) {
            val (unlockHex, authKey) = deriveHex(case)
            assertEquals(case.unlockKeyHex, unlockHex, "unlockKey mismatch for ${case.label}")
            assertEquals(case.authKey, authKey, "authKey mismatch for ${case.label}")
        }
    }

    /**
     * NFC/NFD convergence — the single most valuable property. Our client normalizes to NFC,
     * so an NFD-typed password must derive the SAME authKey as its NFC form (the nfc_cafe
     * vector). The nfd_cafe vector proves PHP does NOT normalize (its authKey genuinely
     * differs), which is exactly why the client must.
     */
    @Test
    fun nfd_password_converges_to_the_nfc_vector() = runTest {
        val nfc = KdfVectors.setACase("nfc_cafe")
        val nfd = KdfVectors.setACase("nfd_cafe")
        val sk = UangkuSecretKey.parse(nfc.secretKey)
        val params = KdfParameters(EncodingUtils.decodeBase64(nfc.saltB64), nfc.iterations)

        val fromNfc = engine.deriveCredentials(passwordOf(nfc), sk, params).authKey
        val fromNfd = engine.deriveCredentials(passwordOf(nfd), sk, params).authKey

        assertEquals(nfc.authKey, fromNfc, "NFC input must reproduce the nfc_cafe vector")
        assertEquals(nfc.authKey, fromNfd, "NFD input must normalize and converge to the nfc_cafe vector")
        assertNotEquals(nfc.authKey, nfd.authKey, "vectors must genuinely differ — PHP does not normalize")
    }

    /** Set B — our HKDF salt=null must equal PHP's salt="" (RFC 5869 zeros). */
    @Test
    fun set_b_null_salt_equals_empty_salt() = runTest {
        val ikm = EncodingUtils.decodeHex(KdfVectors.setBIkmHex)
        val okm = provider.get(HKDF).secretDerivation(
            digest = SHA256,
            outputSize = 256.bits,
            salt = null,
            info = KdfVectors.setBInfo.encodeToByteArray(),
        ).deriveSecretToByteArray(ikm)
        assertEquals(KdfVectors.setBSaltOmittedOkmHex, EncodingUtils.encodeHex(okm))
        assertEquals(KdfVectors.setBSaltOmittedOkmHex, KdfVectors.setBSaltEmptyOkmHex)
    }

    /** Set C — open a fixed-IV container made by PHP. Cross-impl AES-GCM decrypt. */
    @Test
    fun set_c_open_php_container() = runTest {
        val key = SecretBytes.wrap(EncodingUtils.decodeHex(KdfVectors.setCKeyHex))
        val plain = cipher.open(key, SealedData.fromBase64(KdfVectors.setCContainerB64))
        assertEquals(KdfVectors.setCPlaintextHex, EncodingUtils.encodeHex(plain))
    }

    /** Set D — open wrapped_private_key with the unlockKey; result must be a PKCS#8 PEM. */
    @Test
    fun set_d_unwrap_private_key_yields_pem() = runTest {
        val unlockKey = SecretBytes.wrap(EncodingUtils.decodeHex(KdfVectors.setDUnlockKeyHex))
        val pem = cipher.open(unlockKey, SealedData.fromBase64(KdfVectors.setDWrappedPrivateKey)).decodeToString()
        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"), "unwrapped material must be a PKCS#8 PEM")
    }

    /** Set E — open the envelope ct sub-container with the data key. */
    @Test
    fun set_e_open_envelope_ct() = runTest {
        val dataKey = SecretBytes.wrap(EncodingUtils.decodeHex(KdfVectors.setEDataKeyHex))
        val plain = cipher.open(dataKey, SealedData.fromBase64(KdfVectors.setEEnvelopeCtB64)).decodeToString()
        assertEquals(KdfVectors.setEPlaintext, plain)
    }
}
