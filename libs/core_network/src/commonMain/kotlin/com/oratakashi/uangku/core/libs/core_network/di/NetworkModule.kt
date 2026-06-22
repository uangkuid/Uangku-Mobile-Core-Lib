package com.oratakashi.uangku.core.libs.core_network.di

import com.oratakashi.uangku.core.libs.core_network.client.KtorClientFactory
import com.oratakashi.uangku.core.libs.core_network.client.KtorConfig
import com.oratakashi.uangku.core.libs.core_network.constants.NetworkConstant
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * NetworkModule is an interface that provides a contract for supplying Koin modules
 * responsible for dependency injection of network-related classes, such as Ktor clients.
 * This interface ensures that network configuration and client instantiation are managed
 * in a centralized and maintainable way, supporting multiple environments (e.g., development and production).
 *
 * @author oratakashi
 * @since 26 Apr 2026
 */
interface NetworkModule {
    /**
     * Provides a Koin [Module] that registers network dependencies for different environments.
     * This includes Ktor client instances configured for development and production, each
     * accepting a logging flag for runtime configuration.
     *
     * @return [Module] containing network-related dependency definitions.
     */
    fun provideNetworkModule(): Module {
        return module {
            single(named(NetworkConstant.ENV_DEV)) { (enableLog: Boolean) ->
                KtorClientFactory.create(
                    KtorConfig(
                        baseUrl = NetworkConstant.ENV_DEV,
                        enableLogging = enableLog
                    )
                )
            }

            single(named(NetworkConstant.ENV_PROD)) { (enableLog: Boolean) ->
                KtorClientFactory.create(
                    KtorConfig(
                        baseUrl = NetworkConstant.ENV_PROD,
                        enableLogging = enableLog
                    )
                )

            }
        }
    }
}