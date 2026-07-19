package com.oratakashi.uangku.core.libs.core_crypto.di

import com.oratakashi.uangku.core.libs.core_crypto.storage.KeychainSecureStorage
import com.oratakashi.uangku.core.libs.core_crypto.storage.SecureStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual val secureStorageModule: Module = module {
    single<SecureStorage> { KeychainSecureStorage() }
}
