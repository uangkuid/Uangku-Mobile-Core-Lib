package com.oratakashi.uangku.core.libs.core_crypto.kdf

/**
 * HKDF domain-separation labels, byte-identical with the backend contract (§4.2).
 *
 * @since 17 July 2026
 */
internal object KdfConstants {
    const val INFO_SECRET_KEY = "uangku-secretkey-v1"
    const val INFO_AUTH = "uangku-auth-v1"
}
