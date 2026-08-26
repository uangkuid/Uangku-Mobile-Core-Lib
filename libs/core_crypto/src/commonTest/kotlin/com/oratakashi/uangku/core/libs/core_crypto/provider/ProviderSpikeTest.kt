package com.oratakashi.uangku.core.libs.core_crypto.provider

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase-1 de-risking spike. This is the gate to every other phase: it proves the
 * cryptography-kotlin composite provider resolves the four algorithms we depend on
 * on BOTH Android host (JCA) and iOS simulator (CryptoKit + Apple/CommonCrypto).
 *
 * CryptoKit has neither RSA nor PBKDF2 — the whole approach hinges on the composite
 * falling back to the Apple provider for those two. If any assertion here fails on
 * iOS, the library choice is wrong and the rest of the plan is void.
 */
class ProviderSpikeTest {

    private val provider = CryptographyProvider.Default

    @Test
    fun default_provider_is_registered() {
        // Also exercises whether Kotlin/Native DCE kept the @EagerInitialization hook.
        assertTrue(provider.name.isNotBlank(), "provider name should be non-blank")
    }

    @Test
    fun composite_resolves_all_four_algorithms() {
        assertNotNull(provider.getOrNull(PBKDF2), "PBKDF2 must resolve (Apple fallback on iOS)")
        assertNotNull(provider.getOrNull(HKDF), "HKDF must resolve")
        assertNotNull(provider.getOrNull(AES.GCM), "AES.GCM must resolve (CryptoKit on iOS)")
        assertNotNull(provider.getOrNull(RSA.OAEP), "RSA.OAEP must resolve (Apple fallback on iOS)")
    }

    @OptIn(DelicateCryptographyApi::class)
    @Test
    fun aes_gcm_encrypt_layout_is_iv_ct_tag() = runTest {
        val key = provider.get(AES.GCM).keyGenerator().generateKey()
        val plaintext = "hello".encodeToByteArray()
        val output = key.cipher().encrypt(plaintext)
        // Expect IV(12) ‖ ciphertext(N) ‖ tag(16) with no version prefix from the library.
        assertEquals(12 + plaintext.size + 16, output.size, "GCM output must be iv|ct|tag")

        // Cross-check the split is iv|ct|tag and NOT iv|tag|ct.
        val iv = output.copyOfRange(0, 12)
        val ctWithTag = output.copyOfRange(12, output.size)
        val roundTrip = key.cipher().decryptWithIv(iv, ctWithTag)
        assertEquals("hello", roundTrip.decodeToString())
    }

    @Test
    fun rsa_oaep_sha256_max_plaintext_is_446_bytes() = runTest {
        val keyPair = provider.get(RSA.OAEP)
            .keyPairGenerator(keySize = 4096.bits, digest = SHA256)
            .generateKey()
        val encryptor = keyPair.publicKey.encryptor()

        // 446 = 512 − 2*32 − 2 must succeed; 447 must fail.
        val ok = encryptor.encrypt(ByteArray(446))
        assertTrue(ok.isNotEmpty(), "446-byte plaintext must encrypt")

        var threw = false
        try {
            encryptor.encrypt(ByteArray(447))
        } catch (_: Throwable) {
            threw = true
        }
        assertTrue(threw, "447-byte plaintext must exceed OAEP limit and throw")
    }
}
