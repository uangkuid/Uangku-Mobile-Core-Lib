package com.oratakashi.uangku.core.libs.core_network.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

object KtorClientFactory {

    /**
     * Create a configured HttpClient instance
     *
     * @param config Client configuration
     * @param tokenProvider Lambda to provide authentication token (injected dari feature module)
     * @param customHeaders Additional headers dari feature module
     */
    fun create(
        config: KtorConfig,
        tokenProvider: suspend () -> String? = { null },
        customHeaders: Map<String, String> = emptyMap()
    ): HttpClient {
        return HttpClient(CIO) {

            // Apply base configuration
            applyBaseConfig(config)

            // Apply authentication
            applyAuth(tokenProvider)

            // Apply serialization
            applySerialization()

            // Apply logging (only debug)
            if (config.enableLogging) {
                applyLogging()
            }

            // Apply timeout
            applyTimeout(config)

            // Apply retry logic
            applyRetry(config)

            // Apply default request headers
            applyDefaultHeaders(config.baseUrl, customHeaders)

            // Apply custom plugins from feature modules
            config.customPlugins.forEach { plugin ->
                plugin(this)
            }

            // Engine configuration
            engine {
                dispatcher = Dispatchers.Default

                // Proxy configuration
                // proxy = ProxyBuilder.http("proxy.example.com")
            }
        }
    }

    private fun HttpClientConfig<*>.applyBaseConfig(config: KtorConfig) {
        expectSuccess = config.expectSuccess
    }

    private fun HttpClientConfig<*>.applyAuth(tokenProvider: suspend () -> String?) {
        install(Auth) {
            bearer {
                loadTokens {
                    tokenProvider()?.let {
                        BearerTokens(accessToken = it, refreshToken = "")
                    }
                }

                // Refresh token logic
                refreshTokens {
                    // Implementasi refresh token bisa di-inject dari feature module
                    null
                }
            }
        }
    }

    private fun HttpClientConfig<*>.applySerialization() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // Backward compatibility
                encodeDefaults = true
                explicitNulls = false // Performa optimization
                coerceInputValues = true // Handle null to default values
            })
        }
    }

    private fun HttpClientConfig<*>.applyLogging() {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    println("KtorClient: ${message}")
                }
            }
            level = LogLevel.BODY

            // Security: Sanitize sensitive data
            sanitizeHeader { header -> header == "Authorization" }
        }
    }

    private fun HttpClientConfig<*>.applyTimeout(config: KtorConfig) {
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeout
            connectTimeoutMillis = config.connectTimeout
            socketTimeoutMillis = config.socketTimeout
        }
    }

    private fun HttpClientConfig<*>.applyRetry(config: KtorConfig) {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = config.maxRetries)
            exponentialDelay()

            // Custom retry condition
            retryIf { _, response ->
                // Retry on 5xx errors and 429 (rate limit)
                response.status.value in 500..599 ||
                        response.status == HttpStatusCode.TooManyRequests
            }

            // Don't retry POST/PUT/DELETE (non-idempotent)
            modifyRequest { request ->
                request.method == HttpMethod.Get || request.method == HttpMethod.Head
            }
        }
    }

    private fun HttpClientConfig<*>.applyDefaultHeaders(
        baseUrl: String,
        customHeaders: Map<String, String>
    ) {
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)

            // Custom headers dari feature module
            customHeaders.forEach { (key, value) ->
                header(key, value)
            }
        }
    }
}