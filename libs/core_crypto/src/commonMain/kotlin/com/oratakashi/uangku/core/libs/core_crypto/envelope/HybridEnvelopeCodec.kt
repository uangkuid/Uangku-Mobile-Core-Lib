package com.oratakashi.uangku.core.libs.core_crypto.envelope

import com.oratakashi.uangku.core.libs.core_crypto.exception.CryptoException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for [HybridEnvelope] using kotlinx.serialization (never a hand-rolled parser
 * in a security module). Tolerant of whitespace, field order, and unknown fields; strict
 * about the version and required fields.
 *
 * @since 17 July 2026
 */
object HybridEnvelopeCodec {

    @Serializable
    private class EnvelopeDto(
        @SerialName("v") val v: Int,
        @SerialName("ek") val ek: String? = null,
        @SerialName("ct") val ct: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(envelope: HybridEnvelope): String =
        json.encodeToString(EnvelopeDto(envelope.version, envelope.encryptedKey, envelope.ciphertext))

    /**
     * @throws CryptoException.InvalidCiphertextFormat on malformed JSON or missing `ek`/`ct`.
     * @throws CryptoException.UnsupportedVersion if `v` is not [HybridEnvelope.VERSION].
     */
    fun decode(text: String): HybridEnvelope {
        val dto = try {
            json.decodeFromString<EnvelopeDto>(text)
        } catch (e: Throwable) {
            throw CryptoException.InvalidCiphertextFormat("malformed envelope JSON")
        }
        if (dto.v != HybridEnvelope.VERSION) throw CryptoException.UnsupportedVersion(dto.v)
        val ek = dto.ek ?: throw CryptoException.InvalidCiphertextFormat("missing 'ek'")
        val ct = dto.ct ?: throw CryptoException.InvalidCiphertextFormat("missing 'ct'")
        return HybridEnvelope(dto.v, ek, ct)
    }
}
