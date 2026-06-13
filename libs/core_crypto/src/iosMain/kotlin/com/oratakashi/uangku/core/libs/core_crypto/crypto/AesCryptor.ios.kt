package com.oratakashi.uangku.core.libs.core_crypto.crypto

import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * iOS implementation of [AesCryptor] using pure-Kotlin AES-256-CBC.
 *
 * Managed keys are kept in memory (per-alias map); they are regenerated on each process start,
 * which is suitable for transient session data. For persistent key storage, wire in a
 * Keychain-backed store and replace [getOrCreateKey].
 *
 * External-key operations derive a 32-byte key from the caller-supplied string via SHA-256,
 * matching the backend `EncryptionHelper::encrypt()` contract.
 *
 * NOTE: For production, replace the pure-Kotlin AES with CommonCrypto (via cinterop) or
 * CryptoKit (iOS 13+) for hardware-accelerated performance.
 *
 * @since 13 June 2026
 */
actual class AesCryptor {

    private val keyStore = mutableMapOf<String, ByteArray>()

    private fun getOrCreateKey(keyAlias: String): ByteArray =
        keyStore.getOrPut(keyAlias) { Random.nextBytes(32) }

    actual suspend fun encrypt(plainText: String, keyAlias: String): String {
        return withContext(Dispatchers.Default) {
            try {
                val key = getOrCreateKey(keyAlias)
                val iv = Random.nextBytes(IV_SIZE)
                val ciphertext = PureKotlinAes.encryptCBC(plainText.encodeToByteArray(), key, iv)
                "${EncodingUtils.encodeBase64(iv)}.${EncodingUtils.encodeBase64(ciphertext)}"
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun decrypt(cipherText: String, keyAlias: String): String {
        return withContext(Dispatchers.Default) {
            try {
                val key = getOrCreateKey(keyAlias)
                val (iv, data) = splitEnvelope(cipherText)
                PureKotlinAes.decryptCBC(data, key, iv).decodeToString()
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.DecryptionFailure(e)
            }
        }
    }

    actual suspend fun encryptBytes(data: ByteArray, keyAlias: String): ByteArray {
        return withContext(Dispatchers.Default) {
            try {
                val key = getOrCreateKey(keyAlias)
                val iv = Random.nextBytes(IV_SIZE)
                iv + PureKotlinAes.encryptCBC(data, key, iv)
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun decryptBytes(data: ByteArray, keyAlias: String): ByteArray {
        return withContext(Dispatchers.Default) {
            try {
                val key = getOrCreateKey(keyAlias)
                val iv = data.copyOfRange(0, IV_SIZE)
                val ciphertext = data.copyOfRange(IV_SIZE, data.size)
                PureKotlinAes.decryptCBC(ciphertext, key, iv)
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.DecryptionFailure(e)
            }
        }
    }

    actual suspend fun encryptWithKey(plainText: String, secretKey: String): String {
        return withContext(Dispatchers.Default) {
            try {
                val key = PureKotlinSha256.digest(secretKey.encodeToByteArray())
                val iv = Random.nextBytes(IV_SIZE)
                val ciphertext = PureKotlinAes.encryptCBC(plainText.encodeToByteArray(), key, iv)
                "${EncodingUtils.encodeBase64(iv)}.${EncodingUtils.encodeBase64(ciphertext)}"
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.EncryptionFailure(e)
            }
        }
    }

    actual suspend fun decryptWithKey(cipherText: String, secretKey: String): String {
        return withContext(Dispatchers.Default) {
            try {
                val key = PureKotlinSha256.digest(secretKey.encodeToByteArray())
                val (iv, data) = splitEnvelope(cipherText)
                PureKotlinAes.decryptCBC(data, key, iv).decodeToString()
            } catch (e: CryptoException) {
                throw e
            } catch (e: Exception) {
                throw CryptoException.DecryptionFailure(e)
            }
        }
    }

    private fun splitEnvelope(envelope: String): Pair<ByteArray, ByteArray> {
        val parts = envelope.split(".")
        require(parts.size == 2) { "Ciphertext envelope must be '<base64-iv>.<base64-ciphertext>'" }
        return EncodingUtils.decodeBase64(parts[0]) to EncodingUtils.decodeBase64(parts[1])
    }

    private companion object {
        const val IV_SIZE = 16
    }
}

// ---------------------------------------------------------------------------
// Pure-Kotlin AES-256 (FIPS 197) — CBC mode with PKCS7 padding
// ---------------------------------------------------------------------------
internal object PureKotlinAes {

    // AES substitution box (FIPS 197 Figure 7)
    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
    )

    // AES inverse substitution box (FIPS 197 Figure 14)
    private val INV_SBOX = intArrayOf(
        0x52, 0x09, 0x6a, 0xd5, 0x30, 0x36, 0xa5, 0x38, 0xbf, 0x40, 0xa3, 0x9e, 0x81, 0xf3, 0xd7, 0xfb,
        0x7c, 0xe3, 0x39, 0x82, 0x9b, 0x2f, 0xff, 0x87, 0x34, 0x8e, 0x43, 0x44, 0xc4, 0xde, 0xe9, 0xcb,
        0x54, 0x7b, 0x94, 0x32, 0xa6, 0xc2, 0x23, 0x3d, 0xee, 0x4c, 0x95, 0x0b, 0x42, 0xfa, 0xc3, 0x4e,
        0x08, 0x2e, 0xa1, 0x66, 0x28, 0xd9, 0x24, 0xb2, 0x76, 0x5b, 0xa2, 0x49, 0x6d, 0x8b, 0xd1, 0x25,
        0x72, 0xf8, 0xf6, 0x64, 0x86, 0x68, 0x98, 0x16, 0xd4, 0xa4, 0x5c, 0xcc, 0x5d, 0x65, 0xb6, 0x92,
        0x6c, 0x70, 0x48, 0x50, 0xfd, 0xed, 0xb9, 0xda, 0x5e, 0x15, 0x46, 0x57, 0xa7, 0x8d, 0x9d, 0x84,
        0x90, 0xd8, 0xab, 0x00, 0x8c, 0xbc, 0xd3, 0x0a, 0xf7, 0xe4, 0x58, 0x05, 0xb8, 0xb3, 0x45, 0x06,
        0xd0, 0x2c, 0x1e, 0x8f, 0xca, 0x3f, 0x0f, 0x02, 0xc1, 0xaf, 0xbd, 0x03, 0x01, 0x13, 0x8a, 0x6b,
        0x3a, 0x91, 0x11, 0x41, 0x4f, 0x67, 0xdc, 0xea, 0x97, 0xf2, 0xcf, 0xce, 0xf0, 0xb4, 0xe6, 0x73,
        0x96, 0xac, 0x74, 0x22, 0xe7, 0xad, 0x35, 0x85, 0xe2, 0xf9, 0x37, 0xe8, 0x1c, 0x75, 0xdf, 0x6e,
        0x47, 0xf1, 0x1a, 0x71, 0x1d, 0x29, 0xc5, 0x89, 0x6f, 0xb7, 0x62, 0x0e, 0xaa, 0x18, 0xbe, 0x1b,
        0xfc, 0x56, 0x3e, 0x4b, 0xc6, 0xd2, 0x79, 0x20, 0x9a, 0xdb, 0xc0, 0xfe, 0x78, 0xcd, 0x5a, 0xf4,
        0x1f, 0xdd, 0xa8, 0x33, 0x88, 0x07, 0xc7, 0x31, 0xb1, 0x12, 0x10, 0x59, 0x27, 0x80, 0xec, 0x5f,
        0x60, 0x51, 0x7f, 0xa9, 0x19, 0xb5, 0x4a, 0x0d, 0x2d, 0xe5, 0x7a, 0x9f, 0x93, 0xc9, 0x9c, 0xef,
        0xa0, 0xe0, 0x3b, 0x4d, 0xae, 0x2a, 0xf5, 0xb0, 0xc8, 0xeb, 0xbb, 0x3c, 0x83, 0x53, 0x99, 0x61,
        0x17, 0x2b, 0x04, 0x7e, 0xba, 0x77, 0xd6, 0x26, 0xe1, 0x69, 0x14, 0x63, 0x55, 0x21, 0x0c, 0x7d
    )

    // Round constants for AES-256 key schedule (10 needed; GF(2^8) powers of x)
    private val RCON = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36)

    // GF(2^8) multiply by 2 (xtime)
    private fun xtime(b: Int): Int = if (b and 0x80 != 0) ((b shl 1) xor 0x1b) and 0xFF else (b shl 1) and 0xFF

    // GF(2^8) multiply by an arbitrary constant (repeated xtime + accumulate)
    private fun gmul(b: Int, n: Int): Int {
        var result = 0; var bb = b; var nn = n
        while (nn > 0) {
            if (nn and 1 != 0) result = result xor bb
            bb = xtime(bb); nn = nn ushr 1
        }
        return result
    }

    /** Expand a 32-byte (AES-256) key into 60 32-bit words (15 × 4-word round keys). */
    private fun expandKey256(key: ByteArray): IntArray {
        require(key.size == 32)
        val w = IntArray(60)
        for (i in 0..7) {
            w[i] = ((key[i * 4].toInt() and 0xFF) shl 24) or
                ((key[i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((key[i * 4 + 2].toInt() and 0xFF) shl 8) or
                (key[i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 8 until 60) {
            var temp = w[i - 1]
            when {
                i % 8 == 0 -> temp = subWord(rotWord(temp)) xor (RCON[i / 8 - 1] shl 24)
                i % 8 == 4 -> temp = subWord(temp)
            }
            w[i] = w[i - 8] xor temp
        }
        return w
    }

    private fun subWord(w: Int): Int =
        (SBOX[(w ushr 24) and 0xFF] shl 24) or
            (SBOX[(w ushr 16) and 0xFF] shl 16) or
            (SBOX[(w ushr 8) and 0xFF] shl 8) or
            SBOX[w and 0xFF]

    private fun rotWord(w: Int): Int = (w shl 8) or (w ushr 24)

    /** Encrypt one 16-byte block with AES-256 (14 rounds). */
    private fun encryptBlock(block: ByteArray, ek: IntArray): ByteArray {
        val s = IntArray(16) { block[it].toInt() and 0xFF }
        addRoundKey(s, ek, 0)
        for (r in 1..13) { subBytes(s); shiftRows(s); mixColumns(s); addRoundKey(s, ek, r) }
        subBytes(s); shiftRows(s); addRoundKey(s, ek, 14)
        return ByteArray(16) { s[it].toByte() }
    }

    /** Decrypt one 16-byte block with AES-256 (14 rounds). */
    private fun decryptBlock(block: ByteArray, ek: IntArray): ByteArray {
        val s = IntArray(16) { block[it].toInt() and 0xFF }
        addRoundKey(s, ek, 14)
        for (r in 13 downTo 1) { invShiftRows(s); invSubBytes(s); addRoundKey(s, ek, r); invMixColumns(s) }
        invShiftRows(s); invSubBytes(s); addRoundKey(s, ek, 0)
        return ByteArray(16) { s[it].toByte() }
    }

    private fun addRoundKey(s: IntArray, w: IntArray, round: Int) {
        val base = round * 4
        for (col in 0..3) {
            val word = w[base + col]
            s[col * 4]     = s[col * 4]     xor ((word ushr 24) and 0xFF)
            s[col * 4 + 1] = s[col * 4 + 1] xor ((word ushr 16) and 0xFF)
            s[col * 4 + 2] = s[col * 4 + 2] xor ((word ushr 8) and 0xFF)
            s[col * 4 + 3] = s[col * 4 + 3] xor (word and 0xFF)
        }
    }

    private fun subBytes(s: IntArray) { for (i in s.indices) s[i] = SBOX[s[i]] }
    private fun invSubBytes(s: IntArray) { for (i in s.indices) s[i] = INV_SBOX[s[i]] }

    private fun shiftRows(s: IntArray) {
        var t = s[1]; s[1] = s[5]; s[5] = s[9]; s[9] = s[13]; s[13] = t
        t = s[2]; s[2] = s[10]; s[10] = t; t = s[6]; s[6] = s[14]; s[14] = t
        t = s[3]; s[3] = s[15]; s[15] = s[11]; s[11] = s[7]; s[7] = t
    }

    private fun invShiftRows(s: IntArray) {
        var t = s[13]; s[13] = s[9]; s[9] = s[5]; s[5] = s[1]; s[1] = t
        t = s[2]; s[2] = s[10]; s[10] = t; t = s[6]; s[6] = s[14]; s[14] = t
        t = s[7]; s[7] = s[11]; s[11] = s[15]; s[15] = s[3]; s[3] = t
    }

    private fun mixColumns(s: IntArray) {
        for (col in 0..3) {
            val b = col * 4
            val s0 = s[b]; val s1 = s[b+1]; val s2 = s[b+2]; val s3 = s[b+3]
            s[b]   = gmul(s0, 2) xor gmul(s1, 3) xor s2 xor s3
            s[b+1] = s0 xor gmul(s1, 2) xor gmul(s2, 3) xor s3
            s[b+2] = s0 xor s1 xor gmul(s2, 2) xor gmul(s3, 3)
            s[b+3] = gmul(s0, 3) xor s1 xor s2 xor gmul(s3, 2)
        }
    }

    private fun invMixColumns(s: IntArray) {
        for (col in 0..3) {
            val b = col * 4
            val s0 = s[b]; val s1 = s[b+1]; val s2 = s[b+2]; val s3 = s[b+3]
            s[b]   = gmul(s0,14) xor gmul(s1,11) xor gmul(s2,13) xor gmul(s3, 9)
            s[b+1] = gmul(s0, 9) xor gmul(s1,14) xor gmul(s2,11) xor gmul(s3,13)
            s[b+2] = gmul(s0,13) xor gmul(s1, 9) xor gmul(s2,14) xor gmul(s3,11)
            s[b+3] = gmul(s0,11) xor gmul(s1,13) xor gmul(s2, 9) xor gmul(s3,14)
        }
    }

    /** AES-256-CBC encrypt with PKCS7 padding. Returns ciphertext only (IV provided separately). */
    fun encryptCBC(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        require(iv.size == 16)  { "AES-CBC requires a 16-byte IV" }
        val ek = expandKey256(key)
        val padded = pkcs7Pad(plaintext)
        val out = ByteArray(padded.size)
        var prev = iv
        for (i in padded.indices step 16) {
            val xored = ByteArray(16) { j -> (padded[i + j].toInt() xor prev[j].toInt()).toByte() }
            val enc = encryptBlock(xored, ek)
            enc.copyInto(out, i)
            prev = enc
        }
        return out
    }

    /** AES-256-CBC decrypt, strips PKCS7 padding. Throws [CryptoException.DecryptionFailure] on bad padding. */
    fun decryptCBC(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        require(iv.size == 16)  { "AES-CBC requires a 16-byte IV" }
        if (ciphertext.isEmpty() || ciphertext.size % 16 != 0)
            throw CryptoException.DecryptionFailure()
        val ek = expandKey256(key)
        val out = ByteArray(ciphertext.size)
        var prev = iv
        for (i in ciphertext.indices step 16) {
            val block = ciphertext.copyOfRange(i, i + 16)
            val dec = decryptBlock(block, ek)
            for (j in 0..15) out[i + j] = (dec[j].toInt() xor prev[j].toInt()).toByte()
            prev = block
        }
        return try {
            pkcs7Unpad(out)
        } catch (e: IllegalArgumentException) {
            throw CryptoException.DecryptionFailure(e)
        }
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        return data + ByteArray(pad) { pad.toByte() }
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "Cannot unpad empty data" }
        val pad = data.last().toInt() and 0xFF
        require(pad in 1..16) { "Invalid PKCS7 padding value: $pad" }
        val unpadLen = data.size - pad
        require(unpadLen >= 0) { "Invalid PKCS7 padding: exceeds data length" }
        return data.copyOfRange(0, unpadLen)
    }
}
