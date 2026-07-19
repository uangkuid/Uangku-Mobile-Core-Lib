package com.oratakashi.uangku.core.libs.core_crypto.text

/**
 * Normalizes a string to Unicode NFC (precomposed) form.
 *
 * Mandatory before PBKDF2: PHP `hash_pbkdf2()` does not normalize, so a password typed as
 * NFD on one client (e.g. macOS `café` = `e` + combining acute) and NFC on another
 * (Android `é` = U+00E9) would derive different authKeys and fail login permanently for
 * non-ASCII passwords. Kotlin/Native has no built-in Unicode normalizer, hence expect/actual.
 *
 * @since 17 July 2026
 */
internal expect fun String.normalizeNfc(): String
