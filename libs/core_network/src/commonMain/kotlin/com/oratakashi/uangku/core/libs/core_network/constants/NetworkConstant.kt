package com.oratakashi.uangku.core.libs.core_network.constants

object NetworkConstant {

	const val ENV_DEV = "env_dev"
	const val ENV_PROD = "env_prod"

    internal const val BASE_URL_DEV = "fzz~}4!!{o`ie{#jkx a|ozoeo}fg mac!o~g"
    internal const val BASE_URL_PROD = "fzz~}4!!{o`ie{#jkx a|ozoeo}fg mac!o~g"

	/**
	 * Encodes the given ByteArray using XOR operation with the provided key.
	 *
	 * @author oratakashi
	 * @since 26 Apr 2026
	 * @param input The ByteArray to encode.
	 * @param key The Int key used for XOR operation.
	 * @return The encoded ByteArray.
	 */
	private fun encodeXor(input: ByteArray, key: Int): ByteArray {
		return input.map { byte -> (byte.toInt() xor key).toByte() }.toByteArray()
	}

	/**
	 * Encodes the given String using XOR operation with the provided key.
	 *
	 * @author oratakashi
	 * @since 26 Apr 2026
	 * @param input The String to encode.
	 * @param key The Int key used for XOR operation.
	 * @return The encoded ByteArray.
	 */
	private fun encodeXor(input: String, key: Int): ByteArray {
		return encodeXor(input.encodeToByteArray(), key)
	}

	/**
	 * Decodes the given ByteArray using XOR operation with the provided key.
	 *
	 * @author oratakashi
	 * @since 26 Apr 2026
	 * @param input The ByteArray to decode.
	 * @param key The Int key used for XOR operation.
	 * @return The decoded ByteArray.
	 */
	private fun decodeXor(input: ByteArray, key: Int): ByteArray {
		// XOR is symmetric: encode and decode are the same
		return encodeXor(input, key)
	}

	/**
	 * Decodes the given ByteArray using XOR operation with the provided key and returns a String.
	 *
	 * @author oratakashi
	 * @since 26 Apr 2026
	 * @param input The ByteArray to decode.
	 * @param key The Int key used for XOR operation.
	 * @return The decoded String.
	 */
	private fun decodeXorToString(input: ByteArray, key: Int): String {
		return decodeXor(input, key).decodeToString()
	}
}