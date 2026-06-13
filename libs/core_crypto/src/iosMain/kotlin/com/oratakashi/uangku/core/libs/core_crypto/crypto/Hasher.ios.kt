package com.oratakashi.uangku.core.libs.core_crypto.crypto

import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS implementation of [Hasher] using pure-Kotlin SHA-256 / SHA-512 / HMAC.
 * Produces identical output to the Android (javax.crypto) implementation.
 *
 * NOTE: For production, replace with native CommonCrypto (via cinterop) or CryptoKit
 * (iOS 13+) for hardware-accelerated performance.
 *
 * @since 13 June 2026
 */
actual class Hasher {

    actual suspend fun sha256(input: String): String {
        return withContext(Dispatchers.Default) {
            try {
                EncodingUtils.encodeHex(PureKotlinSha256.digest(input.encodeToByteArray()))
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun sha512(input: String): String {
        return withContext(Dispatchers.Default) {
            try {
                EncodingUtils.encodeHex(PureKotlinSha512.digest(input.encodeToByteArray()))
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun hmacSha256(input: String, secret: String): String {
        return withContext(Dispatchers.Default) {
            try {
                val key = secret.encodeToByteArray()
                val msg = input.encodeToByteArray()
                EncodingUtils.encodeHex(PureKotlinHmac.hmacSha256(key, msg))
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pure-Kotlin SHA-256 (FIPS 180-4)
// ---------------------------------------------------------------------------
internal object PureKotlinSha256 {
    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    fun digest(message: ByteArray): ByteArray {
        val padded = pad(message)
        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        for (i in padded.indices step 64) {
            val w = IntArray(64)
            for (j in 0..15) {
                w[j] = ((padded[i + j * 4].toInt() and 0xFF) shl 24) or
                    ((padded[i + j * 4 + 1].toInt() and 0xFF) shl 16) or
                    ((padded[i + j * 4 + 2].toInt() and 0xFF) shl 8) or
                    (padded[i + j * 4 + 3].toInt() and 0xFF)
            }
            for (j in 16..63) {
                val s0 = rotr(w[j - 15], 7) xor rotr(w[j - 15], 18) xor (w[j - 15] ushr 3)
                val s1 = rotr(w[j - 2], 17) xor rotr(w[j - 2], 19) xor (w[j - 2] ushr 10)
                w[j] = w[j - 16] + s0 + w[j - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (j in 0..63) {
                val S1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + S1 + ch + K[j] + w[j]
                val S0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = S0 + maj
                h = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }
            h0 += a; h1 += b; h2 += c; h3 += d
            h4 += e; h5 += f; h6 += g; h7 += h
        }
        return intToBytes(h0) + intToBytes(h1) + intToBytes(h2) + intToBytes(h3) +
            intToBytes(h4) + intToBytes(h5) + intToBytes(h6) + intToBytes(h7)
    }

    private fun pad(msg: ByteArray): ByteArray {
        val bitLen = msg.size.toLong() * 8
        val padLen = if ((msg.size + 1) % 64 <= 56) 56 - (msg.size + 1) % 64
        else 120 - (msg.size + 1) % 64
        val result = ByteArray(msg.size + 1 + padLen + 8)
        msg.copyInto(result)
        result[msg.size] = 0x80.toByte()
        for (i in 0..7) result[result.size - 8 + i] = ((bitLen shr (56 - i * 8)) and 0xFFL).toByte()
        return result
    }

    private fun rotr(x: Int, n: Int) = (x ushr n) or (x shl (32 - n))
    private fun intToBytes(v: Int) = byteArrayOf(
        (v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()
    )
}

// ---------------------------------------------------------------------------
// Pure-Kotlin SHA-512 (FIPS 180-4)
// ---------------------------------------------------------------------------
internal object PureKotlinSha512 {
    // All 80 K constants declared as ULong literals to avoid signed-Long range errors,
    // then converted to Long bit-for-bit at array construction time.
    private val K: LongArray = ulongArrayOf(
        0x428a2f98d728ae22uL, 0x7137449123ef65cduL, 0xb5c0fbcfec4d3b2fuL, 0xe9b5dba58189dbbcuL,
        0x3956c25bf348b538uL, 0x59f111f1b605d019uL, 0x923f82a4af194f9buL, 0xab1c5ed5da6d8118uL,
        0xd807aa98a3030242uL, 0x12835b0145706fbeuL, 0x243185be4ee4b28cuL, 0x550c7dc3d5ffb4e2uL,
        0x72be5d74f27b896fuL, 0x80deb1fe3b1696b1uL, 0x9bdc06a725c71235uL, 0xc19bf174cf692694uL,
        0xe49b69c19ef14ad2uL, 0xefbe4786384f25e3uL, 0x0fc19dc68b8cd5b5uL, 0x240ca1cc77ac9c65uL,
        0x2de92c6f592b0275uL, 0x4a7484aa6ea6e483uL, 0x5cb0a9dcbd41fbd4uL, 0x76f988da831153b5uL,
        0x983e5152ee66dfabuL, 0xa831c66d2db43210uL, 0xb00327c898fb213fuL, 0xbf597fc7beef0ee4uL,
        0xc6e00bf33da88fc2uL, 0xd5a79147930aa725uL, 0x06ca6351e003826fuL, 0x142929670a0e6e70uL,
        0x27b70a8546d22ffcuL, 0x2e1b21385c26c926uL, 0x4d2c6dfc5ac42aeduL, 0x53380d139d95b3dfuL,
        0x650a73548baf63deuL, 0x766a0abb3c77b2a8uL, 0x81c2c92e47edaee6uL, 0x92722c851482353buL,
        0xa2bfe8a14cf10364uL, 0xa81a664bbc423001uL, 0xc24b8b70d0f89791uL, 0xc76c51a30654be30uL,
        0xd192e819d6ef5218uL, 0xd69906245565a910uL, 0xf40e35855771202auL, 0x106aa07032bbd1b8uL,
        0x19a4c116b8d2d0c8uL, 0x1e376c085141ab53uL, 0x2748774cdf8eeb99uL, 0x34b0bcb5e19b48a8uL,
        0x391c0cb3c5c95a63uL, 0x4ed8aa4ae3418acbuL, 0x5b9cca4f7763e373uL, 0x682e6ff3d6b2b8a3uL,
        0x748f82ee5defb2fcuL, 0x78a5636f43172f60uL, 0x84c87814a1f0ab72uL, 0x8cc702081a6439ecuL,
        0x90befffa23631e28uL, 0xa4506cebde82bde9uL, 0xbef9a3f7b2c67915uL, 0xc67178f2e372532buL,
        0xca273eceea26619cuL, 0xd186b8c721c0c207uL, 0xeada7dd6cde0eb1euL, 0xf57d4f7fee6ed178uL,
        0x06f067aa72176fbauL, 0x0a637dc5a2c898a6uL, 0x113f9804bef90daeuL, 0x1b710b35131c471buL,
        0x28db77f523047d84uL, 0x32caab7b40c72493uL, 0x3c9ebe0a15c9bebcuL, 0x431d67c49c100d4cuL,
        0x4cc5d4becb3e42b6uL, 0x597f299cfc657e2auL, 0x5fcb6fab3ad6faecuL, 0x6c44198c4a475817uL
    ).let { ua -> LongArray(ua.size) { ua[it].toLong() } }

    fun digest(message: ByteArray): ByteArray {
        val padded = pad(message)
        var h0 = 0x6a09e667f3bcc908uL.toLong()
        var h1 = 0xbb67ae8584caa73buL.toLong()
        var h2 = 0x3c6ef372fe94f82buL.toLong()
        var h3 = 0xa54ff53a5f1d36f1uL.toLong()
        var h4 = 0x510e527fade682d1uL.toLong()
        var h5 = 0x9b05688c2b3e6c1fuL.toLong()
        var h6 = 0x1f83d9abfb41bd6buL.toLong()
        var h7 = 0x5be0cd19137e2179uL.toLong()

        for (i in padded.indices step 128) {
            val w = LongArray(80)
            for (j in 0..15) {
                var v = 0L
                for (b in 0..7) v = (v shl 8) or (padded[i + j * 8 + b].toLong() and 0xFF)
                w[j] = v
            }
            for (j in 16..79) {
                val s0 = rotr64(w[j-15], 1) xor rotr64(w[j-15], 8) xor (w[j-15] ushr 7)
                val s1 = rotr64(w[j-2], 19) xor rotr64(w[j-2], 61) xor (w[j-2] ushr 6)
                w[j] = w[j-16] + s0 + w[j-7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (j in 0..79) {
                val S1 = rotr64(e,14) xor rotr64(e,18) xor rotr64(e,41)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + S1 + ch + K[j] + w[j]
                val S0 = rotr64(a,28) xor rotr64(a,34) xor rotr64(a,39)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = S0 + maj
                h = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            h0 += a; h1 += b; h2 += c; h3 += d
            h4 += e; h5 += f; h6 += g; h7 += h
        }
        return longToBytes(h0) + longToBytes(h1) + longToBytes(h2) + longToBytes(h3) +
            longToBytes(h4) + longToBytes(h5) + longToBytes(h6) + longToBytes(h7)
    }

    private fun pad(msg: ByteArray): ByteArray {
        val bitLen = msg.size.toLong() * 8
        val padLen = if ((msg.size + 1) % 128 <= 112) 112 - (msg.size + 1) % 128
        else 240 - (msg.size + 1) % 128
        val result = ByteArray(msg.size + 1 + padLen + 16)
        msg.copyInto(result)
        result[msg.size] = 0x80.toByte()
        for (i in 0..7) result[result.size - 8 + i] = ((bitLen shr (56 - i * 8)) and 0xFFL).toByte()
        return result
    }

    private fun rotr64(x: Long, n: Int) = (x ushr n) or (x shl (64 - n))
    private fun longToBytes(v: Long) = ByteArray(8) { i -> ((v shr (56 - i * 8)) and 0xFFL).toByte() }
}

// ---------------------------------------------------------------------------
// Pure-Kotlin HMAC-SHA256 (RFC 2104)
// ---------------------------------------------------------------------------
internal object PureKotlinHmac {
    private const val BLOCK_SIZE = 64

    fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val normalizedKey = if (key.size > BLOCK_SIZE) PureKotlinSha256.digest(key) else key
        val paddedKey = normalizedKey.copyOf(BLOCK_SIZE)
        val ipad = ByteArray(BLOCK_SIZE) { i -> (paddedKey[i].toInt() xor 0x36).toByte() }
        val opad = ByteArray(BLOCK_SIZE) { i -> (paddedKey[i].toInt() xor 0x5c).toByte() }
        val inner = PureKotlinSha256.digest(ipad + message)
        return PureKotlinSha256.digest(opad + inner)
    }
}
