package com.oratakashi.uangku.core.libs.core_crypto.envelope

/**
 * The hybrid encryption envelope `{v, ek, ct}` (§4.1):
 *
 * - `ek` = `base64(RSA-OAEP(dataKey))`
 * - `ct` = `base64(ver‖iv‖ciphertext‖tag)` — the same [com.oratakashi.uangku.core.libs.core_crypto.cipher.SealedData]
 *   container as everything else.
 *
 * Used for every financial field and for wrapping family private keys (which are far larger
 * than RSA-OAEP's 446-byte limit, so direct RSA is mathematically impossible).
 *
 * @since 17 July 2026
 */
class HybridEnvelope(
    val version: Int,
    val encryptedKey: String,
    val ciphertext: String,
) {
    companion object {
        const val VERSION = 2
    }
}
