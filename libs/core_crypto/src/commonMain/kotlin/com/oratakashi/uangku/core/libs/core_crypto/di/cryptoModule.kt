package com.oratakashi.uangku.core.libs.core_crypto.di

import org.koin.core.module.Module

/**
 * Koin module providing [AesCryptor] and [Hasher] instances.
 * Actual implementations differ by platform:
 * - Android uses AndroidKeyStore + javax.crypto
 * - iOS uses Keychain + CommonCrypto
 *
 * Consumer projects include this in their Koin configuration:
 * ```kotlin
 * modules(secureStorageModule, cryptoModule)
 * ```
 *
 * @since 13 June 2026
 */
expect val cryptoModule: Module
