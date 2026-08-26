package com.oratakashi.uangku.core.libs.core_crypto.text

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

internal actual fun String.normalizeNfc(): String =
    (this as NSString).precomposedStringWithCanonicalMapping
