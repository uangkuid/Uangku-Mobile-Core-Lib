package com.oratakashi.uangku.core.libs.core_crypto.text

import java.text.Normalizer

internal actual fun String.normalizeNfc(): String =
    Normalizer.normalize(this, Normalizer.Form.NFC)
