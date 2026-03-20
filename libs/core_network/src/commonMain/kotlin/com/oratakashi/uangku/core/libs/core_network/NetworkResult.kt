package com.oratakashi.uangku.core.libs.core_network

import com.oratakashi.uangku.core.libs.core_network.exception.NetworkException

/**
 * NetworkResult is a sealed class representing the result of a network operation.
 * It encapsulates success, error, and loading states for API calls in a multiplatform environment.
 *
 * @author oratakashi
 * @since 20 Mar 2026
 * @param T The type of data returned on success.
 */
sealed class NetworkResult<out T> {
    /**
     * Success represents a successful network response containing data.
     * @param data The data returned from the network call.
     */
    data class Success<T>(val data: T) : NetworkResult<T>()

    /**
     * Error represents a failed network response containing a NetworkException.
     * @param exception The exception describing the error.
     */
    data class Error(val exception: NetworkException) : NetworkResult<Nothing>()

    /**
     * Loading represents an ongoing network operation.
     */
    object Loading : NetworkResult<Nothing>()
}

/**
 * Executes the given [action] if the result is [NetworkResult.Success].
 *
 * @param action Lambda to execute with the success data.
 * @return The original NetworkResult for fluent chaining.
 */
inline fun <T> NetworkResult<T>.onSuccess(action: (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) action(data)
    return this
}

/**
 * Executes the given [action] if the result is [NetworkResult.Error].
 *
 * @param action Lambda to execute with the exception.
 * @return The original NetworkResult for fluent chaining.
 */
inline fun <T> NetworkResult<T>.onError(action: (NetworkException) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Error) action(exception)
    return this
}

/**
 * Executes the given [action] if the result is [NetworkResult.Loading].
 *
 * @param action Lambda to execute when loading.
 * @return The original NetworkResult for fluent chaining.
 */
inline fun <T> NetworkResult<T>.onLoading(action: () -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Loading) action()
    return this
}