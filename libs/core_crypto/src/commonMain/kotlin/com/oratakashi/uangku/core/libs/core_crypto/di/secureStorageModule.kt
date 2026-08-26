package com.oratakashi.uangku.core.libs.core_crypto.di

import org.koin.core.module.Module

/**
 * Koin module providing the platform [com.oratakashi.uangku.core.libs.core_crypto.storage.SecureStorage].
 *
 * Still expect/actual because Android needs a `Context` from the Koin graph (register
 * `androidContext(this)` before building this module); iOS needs nothing.
 *
 * @since 17 July 2026
 */
expect val secureStorageModule: Module
