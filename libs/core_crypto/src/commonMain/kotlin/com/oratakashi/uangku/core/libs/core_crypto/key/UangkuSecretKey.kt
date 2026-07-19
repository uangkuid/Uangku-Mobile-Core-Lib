package com.oratakashi.uangku.core.libs.core_crypto.key

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * The account Secret Key, format `UANGKU-XXXXXX-XXXXXX-XXXXX-XXXXX-XXXXX`.
 *
 * Generated client-side with a CSPRNG and shown to the user exactly once. It is the
 * high-entropy second factor in 2SKD and is **never** sent to the server — the KDF feeds
 * [formatted] (the full dashed string, prefix included) as HKDF ikm, byte-for-byte.
 *
 * [parse] validates structure only (prefix, segment lengths, `[A-Z0-9]`), not a restricted
 * alphabet, so keys produced by any conformant client round-trip. [generate] draws from an
 * unambiguous alphabet (Crockford base32, no I/L/O/U) to make manual backup less error-prone.
 *
 * @since 17 July 2026
 */
class UangkuSecretKey private constructor(val formatted: String) {

    override fun toString(): String = "UangkuSecretKey(****)"

    override fun equals(other: Any?): Boolean =
        this === other || (other is UangkuSecretKey && other.formatted == formatted)

    override fun hashCode(): Int = formatted.hashCode()

    companion object {
        const val PREFIX = "UANGKU"
        val SEGMENT_LENGTHS = listOf(6, 6, 5, 5, 5)

        /** Unambiguous generation alphabet (Crockford base32, uppercase). Still a subset of `[A-Z0-9]`. */
        private const val GENERATION_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private val SEGMENT_REGEX = Regex("[A-Z0-9]+")

        /** Generates a new random Secret Key. */
        fun generate(): UangkuSecretKey {
            val segments = SEGMENT_LENGTHS.map { length ->
                buildString(length) {
                    repeat(length) {
                        val index = CryptographyRandom.nextInt(GENERATION_ALPHABET.length)
                        append(GENERATION_ALPHABET[index])
                    }
                }
            }
            return UangkuSecretKey((listOf(PREFIX) + segments).joinToString("-"))
        }

        /**
         * Parses and structurally validates a Secret Key string.
         *
         * @throws CryptoException.InvalidSecretKeyFormat with a non-sensitive reason
         *   (never the offending value) if the structure is wrong.
         */
        fun parse(value: String): UangkuSecretKey {
            val parts = value.split("-")
            if (parts.size != SEGMENT_LENGTHS.size + 1) {
                throw CryptoException.InvalidSecretKeyFormat(
                    "expected ${SEGMENT_LENGTHS.size + 1} dash-separated parts, got ${parts.size}",
                )
            }
            if (parts.first() != PREFIX) {
                throw CryptoException.InvalidSecretKeyFormat("missing '$PREFIX-' prefix")
            }
            parts.drop(1).forEachIndexed { index, segment ->
                val expected = SEGMENT_LENGTHS[index]
                if (segment.length != expected) {
                    throw CryptoException.InvalidSecretKeyFormat(
                        "segment ${index + 1} length ${segment.length}, expected $expected",
                    )
                }
                if (!SEGMENT_REGEX.matches(segment)) {
                    throw CryptoException.InvalidSecretKeyFormat(
                        "segment ${index + 1} contains non-[A-Z0-9] characters",
                    )
                }
            }
            return UangkuSecretKey(value)
        }
    }
}
