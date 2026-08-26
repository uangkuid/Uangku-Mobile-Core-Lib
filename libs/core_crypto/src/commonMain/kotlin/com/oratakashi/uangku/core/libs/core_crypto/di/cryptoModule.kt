package com.oratakashi.uangku.core.libs.core_crypto.di

import com.oratakashi.uangku.core.libs.core_crypto.account.AccountCrypto
import com.oratakashi.uangku.core.libs.core_crypto.account.DefaultAccountCrypto
import com.oratakashi.uangku.core.libs.core_crypto.asymmetric.AsymmetricCryptor
import com.oratakashi.uangku.core.libs.core_crypto.asymmetric.RsaOaepCryptor
import com.oratakashi.uangku.core.libs.core_crypto.cipher.AesGcmCipher
import com.oratakashi.uangku.core.libs.core_crypto.cipher.SymmetricCipher
import com.oratakashi.uangku.core.libs.core_crypto.envelope.HybridEnvelopeCryptor
import com.oratakashi.uangku.core.libs.core_crypto.kdf.KeyDerivationEngine
import com.oratakashi.uangku.core.libs.core_crypto.kdf.Pbkdf2HkdfKeyDerivationEngine
import org.koin.dsl.module

/**
 * Koin module for the ZK crypto toolkit. Plain `val` — no longer expect/actual, since none of
 * these types need platform-specific construction (the cryptography provider resolves per
 * algorithm at runtime). Register alongside [secureStorageModule]:
 *
 * ```kotlin
 * startKoin { androidContext(app); modules(secureStorageModule, cryptoModule) }
 * ```
 *
 * @since 17 July 2026
 */
val cryptoModule = module {
    single<KeyDerivationEngine> { Pbkdf2HkdfKeyDerivationEngine() }
    single<SymmetricCipher> { AesGcmCipher() }
    single<AsymmetricCryptor> { RsaOaepCryptor() }
    single { HybridEnvelopeCryptor(get(), get()) }
    single<AccountCrypto> { DefaultAccountCrypto(get(), get(), get()) }
}
