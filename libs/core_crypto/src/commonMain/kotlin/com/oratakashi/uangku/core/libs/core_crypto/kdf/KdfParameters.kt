package com.oratakashi.uangku.core.libs.core_crypto.kdf

/**
 * Per-user KDF parameters obtained from `POST /auth/salt`.
 *
 * `iterations` lives here — never a constant on the derivation path — because the backend
 * treats it as a per-user runtime value that can be raised for new accounts without locking
 * out old ones. Keeping it here also lets tests derive at low iteration counts for speed.
 *
 * @property salt the 16-byte RAW salt (already base64-decoded from `/auth/salt`), used for
 *   BOTH PBKDF2 and the HKDF(kdfSecret) step.
 *
 * @since 17 July 2026
 */
class KdfParameters(val salt: ByteArray, val iterations: Int = DEFAULT_ITERATIONS) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is KdfParameters && iterations == other.iterations && salt.contentEquals(other.salt))

    override fun hashCode(): Int = 31 * salt.contentHashCode() + iterations

    companion object {
        const val DEFAULT_ITERATIONS = 600_000
        const val SALT_SIZE_BYTES = 16
    }
}
