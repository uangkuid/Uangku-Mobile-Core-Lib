package com.oratakashi.uangku.core.libs.core_crypto.key

/**
 * A holder for raw secret key material (unlock key, data key, derived bytes).
 *
 * Provides best-effort zeroization via [destroy] and a [toString] that **never** leaks
 * the bytes — secret material in an exception message or log ends up in crash reporting.
 *
 * Best-effort, not a guarantee: the JVM/Native runtime may have copied the array before
 * [destroy] runs, and immutable [ByteArray] copies handed out by [copyBytes] are the
 * caller's responsibility to clear.
 *
 * @since 17 July 2026
 */
class SecretBytes private constructor(private var value: ByteArray?) {

    val isDestroyed: Boolean get() = value == null

    val size: Int get() = value?.size ?: 0

    /** Returns a defensive copy of the bytes. Throws if already destroyed. */
    fun copyBytes(): ByteArray =
        (value ?: error("SecretBytes already destroyed")).copyOf()

    /** Overwrites the backing array with zeros and releases it. Idempotent. */
    fun destroy() {
        value?.fill(0)
        value = null
    }

    override fun toString(): String = "SecretBytes(size=$size, destroyed=$isDestroyed)"

    companion object {
        /** Wraps [bytes] directly — the caller must not reuse or mutate the array afterwards. */
        fun wrap(bytes: ByteArray): SecretBytes = SecretBytes(bytes)

        /** Copies [bytes] into a new holder, leaving the source untouched. */
        fun copyOf(bytes: ByteArray): SecretBytes = SecretBytes(bytes.copyOf())
    }
}
