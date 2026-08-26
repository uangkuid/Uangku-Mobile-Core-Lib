package com.oratakashi.uangku.core.libs.core_crypto.cipher

import com.oratakashi.uangku.core.libs.core_crypto.encoding.EncodingUtils
import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException

/**
 * The AES-256-GCM ciphertext container, byte-exact with the backend:
 *
 * ```
 * ver(1B, 0x02) ‖ iv(12B) ‖ ciphertext(N) ‖ tag(16B)
 * ```
 *
 * base64-encoded as a whole. The `iv‖ct‖tag` slice (everything after the version byte) is
 * exactly what the cryptography-kotlin AES-GCM engine emits and consumes, so no manual
 * IV/tag splicing is needed.
 *
 * @since 17 July 2026
 */
class SealedData private constructor(val bytes: ByteArray) {

    /** iv‖ciphertext‖tag without the version prefix — the form the AES-GCM engine consumes. */
    val ivCiphertextTag: ByteArray get() = bytes.copyOfRange(1, bytes.size)

    val iv: ByteArray get() = bytes.copyOfRange(1, 1 + IV_SIZE)

    val ciphertextWithTag: ByteArray get() = bytes.copyOfRange(1 + IV_SIZE, bytes.size)

    fun toBase64(): String = EncodingUtils.encodeBase64(bytes)

    companion object {
        const val VERSION: Byte = 0x02
        const val IV_SIZE = 12
        const val TAG_SIZE = 16

        /** Minimum length: version + iv + tag (empty ciphertext). */
        const val OVERHEAD_BYTES = 1 + IV_SIZE + TAG_SIZE // 29

        /**
         * Validates the container structurally and wraps it.
         *
         * @throws CryptoException.InvalidCiphertextFormat if too short to hold version+iv+tag.
         * @throws CryptoException.UnsupportedVersion if the version byte is not [VERSION].
         */
        fun fromRaw(bytes: ByteArray): SealedData {
            if (bytes.size < OVERHEAD_BYTES) {
                throw CryptoException.InvalidCiphertextFormat(
                    "length ${bytes.size} < minimum $OVERHEAD_BYTES",
                )
            }
            val version = bytes[0].toInt() and 0xFF
            if (version != VERSION.toInt()) {
                throw CryptoException.UnsupportedVersion(version)
            }
            return SealedData(bytes)
        }

        /** Decodes a base64 container. @throws CryptoException.InvalidCiphertextFormat on bad base64. */
        fun fromBase64(value: String): SealedData {
            val raw = try {
                EncodingUtils.decodeBase64(value)
            } catch (e: Throwable) {
                throw CryptoException.InvalidCiphertextFormat("not valid base64")
            }
            return fromRaw(raw)
        }

        /** Prefixes the version byte onto an engine output (`iv‖ct‖tag`). */
        fun fromIvCiphertextTag(ivCiphertextTag: ByteArray): SealedData =
            fromRaw(byteArrayOf(VERSION) + ivCiphertextTag)
    }
}
