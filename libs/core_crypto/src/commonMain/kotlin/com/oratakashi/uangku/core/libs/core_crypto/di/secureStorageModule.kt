package com.oratakashi.uangku.core.libs.core_crypto.di

import org.koin.core.module.Module

/**
 * Koin module providing SecureStorage instance.
 * Actual implementations differ by platform because constructor requirements differ:
 * - Android requires Context
 * - iOS requires no additional dependencies
 *
 * Consumer projects include this in their Koin configuration:
 * ```kotlin
 * modules(secureStorageModule)
 * ```
 *
 * @since 15 May 2026
 */
expect val secureStorageModule: Module


