package com.oratakashi.uangku.core.libs.core_network.client

import com.oratakashi.uangku.core.libs.core_network.NetworkResult
import com.oratakashi.uangku.core.libs.core_network.exception.NetworkException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object KtorNetworkClient {
    /**
     * Executes an API call safely with automatic error handling.
     * Handles HTTP errors, network issues, timeouts, and serialization failures.
     *
     * @param T The expected return type of the API call.
     * @param apiCall A suspending lambda representing the actual API call.
     * @return A [NetworkResult] wrapping either a success value or a [NetworkException].
     */
    suspend fun <T> safeApiCall(
        apiCall: suspend () -> T
    ): NetworkResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                NetworkResult.Success(apiCall())

            } catch (e: ResponseException) {
                // HTTP errors (4xx, 5xx)
                NetworkResult.Error(handleResponseException(e))

            } catch (e: UnresolvedAddressException) {
                // No internet
                NetworkResult.Error(NetworkException.NoInternet())

            } catch (e: HttpRequestTimeoutException) {
                // Timeout
                NetworkResult.Error(NetworkException.Timeout())

            } catch (e: SerializationException) {
                // JSON parse error
                NetworkResult.Error(NetworkException.ParseError(e))

            } catch (e: Exception) {
                // Unknown error
                NetworkResult.Error(NetworkException.Unknown(e))
            }
        }
    }

    /**
     * Maps a [ResponseException] to the appropriate [NetworkException] subtype.
     *
     * @param e The [ResponseException] received from Ktor.
     * @return A [NetworkException] representing the specific HTTP error.
     */
    private suspend fun handleResponseException(e: ResponseException): NetworkException {
        return when (e.response.status) {
            HttpStatusCode.Unauthorized -> NetworkException.Unauthorized()
            HttpStatusCode.Forbidden -> NetworkException.Forbidden()
            HttpStatusCode.NotFound -> NetworkException.NotFound()
            HttpStatusCode.BadRequest -> {
                val errors = try {
                    Json.decodeFromString<Map<String, String>>(e.response.bodyAsText())
                } catch (ex: Exception) {
                    null
                }
                NetworkException.BadRequest(errors)
            }
            else -> {
                if (e.response.status.value in 500..599) {
                    NetworkException.ServerError(e.response.status.value)
                } else {
                    NetworkException.Unknown(e)
                }
            }
        }
    }
}