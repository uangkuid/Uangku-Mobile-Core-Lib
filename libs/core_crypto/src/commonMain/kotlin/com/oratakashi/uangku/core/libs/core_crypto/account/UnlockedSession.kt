package com.oratakashi.uangku.core.libs.core_crypto.account

import com.oratakashi.uangku.core.libs.core_crypto.key.SecretBytes

/**
 * An unlocked account: the in-memory secrets held while the user is logged in.
 *
 * Both fields are device-resident only. [unlockKey] re-wraps the private key on credential
 * change; [privateKeyPem] (PKCS#8 PEM bytes) decrypts financial data and family keys.
 *
 * @since 17 July 2026
 */
class UnlockedSession(
    val unlockKey: SecretBytes,
    val privateKeyPem: SecretBytes,
) {
    /** Zeroizes both secrets. Call on logout. */
    fun destroy() {
        unlockKey.destroy()
        privateKeyPem.destroy()
    }
}
