package com.oratakashi.uangku.core.libs.core_crypto.crypto

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HasherTest {

    private val hasher = Hasher()

    // NIST FIPS 180-4: SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
    @Test
    fun sha256_knownVector_empty() = runBlocking {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hasher.sha256("")
        )
    }

    @Test
    fun sha256_outputLength_is64() = runBlocking {
        assertEquals(64, hasher.sha256("any input").length)
    }

    @Test
    fun sha256_deterministicOutput() = runBlocking {
        val input = "consistent"
        assertEquals(hasher.sha256(input), hasher.sha256(input))
    }

    @Test
    fun sha256_differentInputsDifferentOutputs() = runBlocking {
        val h1 = hasher.sha256("input1")
        val h2 = hasher.sha256("input2")
        assertTrue(h1 != h2)
    }

    // NIST FIPS 180-4: SHA-512("abc")
    @Test
    fun sha512_knownVector_abc() = runBlocking {
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            hasher.sha512("abc")
        )
    }

    @Test
    fun sha512_outputLength_is128() = runBlocking {
        assertEquals(128, hasher.sha512("any input").length)
    }

    // HMAC-SHA256(key="key", data="The quick brown fox jumps over the lazy dog")
    @Test
    fun hmacSha256_knownVector_quickBrownFox() = runBlocking {
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            hasher.hmacSha256("The quick brown fox jumps over the lazy dog", "key")
        )
    }

    @Test
    fun hmacSha256_outputLength_is64() = runBlocking {
        assertEquals(64, hasher.hmacSha256("input", "secret").length)
    }

    @Test
    fun hmacSha256_differentSecretsDifferentOutputs() = runBlocking {
        val h1 = hasher.hmacSha256("same message", "secret1")
        val h2 = hasher.hmacSha256("same message", "secret2")
        assertTrue(h1 != h2)
    }
}
