package com.oratakashi.uangku.core.libs.core_crypto.di

import com.oratakashi.uangku.core.libs.core_crypto.storage.SecureStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific Koin module for secure storage.
 * Provides SecureStorage singleton that requires Android Context.
 *
 * @since 15 May 2026
 */
actual val secureStorageModule: Module = module {
    single { SecureStorage(androidContext()) }
}

