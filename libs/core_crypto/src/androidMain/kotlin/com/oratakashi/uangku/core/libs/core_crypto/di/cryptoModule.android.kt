package com.oratakashi.uangku.core.libs.core_crypto.di

import com.oratakashi.uangku.core.libs.core_crypto.crypto.AesCryptor
import com.oratakashi.uangku.core.libs.core_crypto.crypto.Hasher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android Koin module for crypto services.
 * Both [AesCryptor] and [Hasher] require no Android-specific dependencies at construction time.
 *
 * @since 13 June 2026
 */
actual val cryptoModule: Module = module {
    single { AesCryptor() }
    single { Hasher() }
}
