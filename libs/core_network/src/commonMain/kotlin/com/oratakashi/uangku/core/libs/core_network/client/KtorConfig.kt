package com.oratakashi.uangku.core.libs.core_network.client

import io.ktor.client.HttpClientConfig

/**
 * KtorConfig is a configuration holder for Ktor HttpClient setup in multiplatform projects.
 * It provides options for base URL, logging, timeouts, retries, and custom plugins.
 *
 * Example usage:
 * val config = KtorConfig.Builder("https://api.example.com")
 *     .enableLogging(true)
 *     .connectTimeout(20_000)
 *     .addCustomPlugin { /* plugin config */ }
 *     .build()
 *
 * @property baseUrl The base URL for all network requests.
 * @property enableLogging Enables or disables logging for network requests.
 * @property connectTimeout Connection timeout in milliseconds.
 * @property requestTimeout Request timeout in milliseconds.
 * @property socketTimeout Socket timeout in milliseconds.
 * @property maxRetries Maximum number of retry attempts for failed requests.
 * @property expectSuccess Whether to expect successful HTTP responses (throws on non-2xx).
 * @property customPlugins List of custom plugin configurations for HttpClient.
 * @author oratakashi
 * @since 20 Mar 2026
 */
data class KtorConfig(
    val baseUrl: String,
    val enableLogging: Boolean = DEFAULT_ENABLE_LOGGING,
    val connectTimeout: Long = DEFAULT_CONNECT_TIMEOUT,
    val requestTimeout: Long = DEFAULT_REQUEST_TIMEOUT,
    val socketTimeout: Long = DEFAULT_SOCKET_TIMEOUT,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val expectSuccess: Boolean = DEFAULT_EXPECT_SUCCESS,
    val customPlugins: List<HttpClientConfig<*>.() -> Unit> = emptyList()
) {
    /**
     * Builder is a fluent builder for KtorConfig, allowing step-by-step configuration with validation.
     * @constructor Creates a builder with the required baseUrl.
     * @param baseUrl The base URL for all network requests.
     */
    class Builder(private val baseUrl: String) {
        private var enableLogging: Boolean = DEFAULT_ENABLE_LOGGING
        private var connectTimeout: Long = DEFAULT_CONNECT_TIMEOUT
        private var requestTimeout: Long = DEFAULT_REQUEST_TIMEOUT
        private var socketTimeout: Long = DEFAULT_SOCKET_TIMEOUT
        private var maxRetries: Int = DEFAULT_MAX_RETRIES
        private var expectSuccess: Boolean = DEFAULT_EXPECT_SUCCESS
        private val customPlugins: MutableList<HttpClientConfig<*>.() -> Unit> = mutableListOf()
        /**
         * Enables or disables logging.
         * @param enabled True to enable logging, false otherwise.
         */
        fun enableLogging(enabled: Boolean) = apply { this.enableLogging = enabled }
        /**
         * Sets the connection timeout in milliseconds.
         * @param timeout Timeout in ms, must be positive.
         */
        fun connectTimeout(timeout: Long) = apply { this.connectTimeout = timeout }
        /**
         * Sets the request timeout in milliseconds.
         * @param timeout Timeout in ms, must be positive.
         */
        fun requestTimeout(timeout: Long) = apply { this.requestTimeout = timeout }
        /**
         * Sets the socket timeout in milliseconds.
         * @param timeout Timeout in ms, must be positive.
         */
        fun socketTimeout(timeout: Long) = apply { this.socketTimeout = timeout }
        /**
         * Sets the maximum number of retries.
         * @param retries Number of retries, must be non-negative.
         */
        fun maxRetries(retries: Int) = apply { this.maxRetries = retries }
        /**
         * Sets whether to expect successful HTTP responses.
         * @param expect True to expect success, false otherwise.
         */
        fun expectSuccess(expect: Boolean) = apply { this.expectSuccess = expect }
        /**
         * Adds a custom plugin configuration for the HttpClient.
         * @param pluginConfig Lambda to configure plugin.
         */
        fun addCustomPlugin(pluginConfig: HttpClientConfig<*>.() -> Unit) = apply { this.customPlugins.add(pluginConfig) }

        /**
         * Builds the KtorConfig instance with validation.
         * @throws IllegalArgumentException if any parameter is invalid.
         */
        fun build(): KtorConfig {
            require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
            require(connectTimeout > 0) { "connectTimeout must be positive" }
            require(requestTimeout > 0) { "requestTimeout must be positive" }
            require(socketTimeout > 0) { "socketTimeout must be positive" }
            require(maxRetries >= 0) { "maxRetries must be non-negative" }
            return KtorConfig(
                baseUrl = baseUrl,
                enableLogging = enableLogging,
                connectTimeout = connectTimeout,
                requestTimeout = requestTimeout,
                socketTimeout = socketTimeout,
                maxRetries = maxRetries,
                expectSuccess = expectSuccess,
                customPlugins = customPlugins.toList()
            )
        }
    }

    companion object {
        /** Default value for enableLogging. */
        const val DEFAULT_ENABLE_LOGGING = false
        /** Default value for connectTimeout in ms. */
        const val DEFAULT_CONNECT_TIMEOUT = 15_000L
        /** Default value for requestTimeout in ms. */
        const val DEFAULT_REQUEST_TIMEOUT = 30_000L
        /** Default value for socketTimeout in ms. */
        const val DEFAULT_SOCKET_TIMEOUT = 15_000L
        /** Default value for maxRetries. */
        const val DEFAULT_MAX_RETRIES = 2
        /** Default value for expectSuccess. */
        const val DEFAULT_EXPECT_SUCCESS = true
    }
}