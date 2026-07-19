package com.oratakashi.uangku.core.libs.core_crypto.di

import android.content.Context
import com.oratakashi.uangku.core.libs.core_crypto.storage.AndroidKeystoreSecureStorage
import com.oratakashi.uangku.core.libs.core_crypto.storage.SecureStorage
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Resolves [Context] from the Koin graph — the consumer must register it via
 * `androidContext(this)` before this module is built (see project DI conventions).
 */
actual val secureStorageModule: Module = module {
    single<SecureStorage> { AndroidKeystoreSecureStorage(get<Context>()) }
}
