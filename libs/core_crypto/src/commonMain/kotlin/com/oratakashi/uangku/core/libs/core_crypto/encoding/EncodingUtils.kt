package com.oratakashi.uangku.core.libs.core_crypto.encoding

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cross-platform Base64 and Hex encoding utilities.
 * Uses [kotlin.io.encoding.Base64] from the Kotlin stdlib (available since 1.8.20).
 *
 * @since 13 June 2026
 */
@OptIn(ExperimentalEncodingApi::class)
object EncodingUtils {

    fun encodeBase64(data: ByteArray): String = Base64.encode(data)

    fun decodeBase64(data: String): ByteArray = Base64.decode(data)

    fun encodeHex(data: ByteArray): String = buildString(data.size * 2) {
        data.forEach { byte ->
            append((byte.toInt() and 0xFF).toString(16).padStart(2, '0'))
        }
    }

    fun decodeHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
