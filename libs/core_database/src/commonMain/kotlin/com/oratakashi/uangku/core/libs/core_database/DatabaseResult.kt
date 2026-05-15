package com.oratakashi.uangku.core.libs.core_database

import com.oratakashi.uangku.core.libs.core_database.exception.DatabaseException

/**
 * Sealed class representing the result of a database operation.
 * Provides a typed, exhaustive vocabulary for all database operation outcomes.
 * This mirrors the existing NetworkResult pattern for consistency.
 *
 * @param T The type of data returned on success.
 * @author oratakashi
 * @since 15 May 2026
 */
sealed class DatabaseResult<out T> {

    /**
     * The operation completed successfully and returned a value.
     *
     * @param T The type of successful data.
     * @param data The returned data from the successful operation.
     */
    data class Success<T>(val data: T) : DatabaseResult<T>()

    /**
     * The operation failed.
     *
     * @param exception The typed DatabaseException describing the failure.
     */
    data class Error(val exception: DatabaseException) : DatabaseResult<Nothing>()

    /**
     * The operation is currently in progress.
     * Useful when exposing database operations as StateFlow in a ViewModel,
     * where the initial emission before the query returns should be a Loading state.
     */
    object Loading : DatabaseResult<Nothing>()
}

/**
 * Executes the provided action if the result is Success.
 *
 * @param action Lambda invoked with the success data.
 * @return The original DatabaseResult<T> for chaining.
 */
inline fun <T> DatabaseResult<T>.onSuccess(action: (T) -> Unit): DatabaseResult<T> {
    if (this is DatabaseResult.Success) {
        action(data)
    }
    return this
}

/**
 * Executes the provided action if the result is Error.
 *
 * @param action Lambda invoked with the DatabaseException.
 * @return The original DatabaseResult<T> for chaining.
 */
inline fun <T> DatabaseResult<T>.onError(action: (DatabaseException) -> Unit): DatabaseResult<T> {
    if (this is DatabaseResult.Error) {
        action(exception)
    }
    return this
}

/**
 * Executes the provided action if the result is Loading.
 *
 * @param action Lambda invoked when Loading state is encountered.
 * @return The original DatabaseResult<T> for chaining.
 */
inline fun <T> DatabaseResult<T>.onLoading(action: () -> Unit): DatabaseResult<T> {
    if (this is DatabaseResult.Loading) {
        action()
    }
    return this
}

/**
 * Maps the success data using the provided transformation function.
 * Returns the same Error or Loading state unchanged.
 *
 * @param U The type of the transformed data.
 * @param transform Lambda that transforms T to U.
 * @return A new DatabaseResult with the transformed data, or the same error/loading state.
 */
inline fun <T, U> DatabaseResult<T>.map(transform: (T) -> U): DatabaseResult<U> {
    return when (this) {
        is DatabaseResult.Success -> DatabaseResult.Success(transform(data))
        is DatabaseResult.Error -> DatabaseResult.Error(exception)
        is DatabaseResult.Loading -> DatabaseResult.Loading
    }
}

/**
 * Executes the provided transformation suspend function if the result is Success.
 * Useful for chaining database operations where one operation depends on the result of another.
 *
 * @param U The type of the transformed data.
 * @param transform Suspend lambda that transforms T to DatabaseResult<U>.
 * @return A new DatabaseResult<U> from the transformation, or the same error/loading state.
 */
suspend inline fun <T, U> DatabaseResult<T>.flatMap(
    transform: suspend (T) -> DatabaseResult<U>
): DatabaseResult<U> {
    return when (this) {
        is DatabaseResult.Success -> transform(data)
        is DatabaseResult.Error -> DatabaseResult.Error(exception)
        is DatabaseResult.Loading -> DatabaseResult.Loading
    }
}

/**
 * Extracts the value if Success, otherwise throws the exception.
 *
 * @return The success data.
 * @throws DatabaseException If the result is Error.
 * @throws IllegalStateException If the result is Loading.
 */
fun <T> DatabaseResult<T>.getOrThrow(): T {
    return when (this) {
        is DatabaseResult.Success -> data
        is DatabaseResult.Error -> throw exception
        is DatabaseResult.Loading -> throw IllegalStateException("Result is still loading")
    }
}

/**
 * Extracts the value if Success, otherwise returns null.
 *
 * @return The success data, or null if Error or Loading.
 */
fun <T> DatabaseResult<T>.getOrNull(): T? {
    return when (this) {
        is DatabaseResult.Success -> data
        is DatabaseResult.Error -> null
        is DatabaseResult.Loading -> null
    }
}

/**
 * Returns true if the result is Success.
 */
fun <T> DatabaseResult<T>.isSuccess(): Boolean = this is DatabaseResult.Success

/**
 * Returns true if the result is Error.
 */
fun <T> DatabaseResult<T>.isError(): Boolean = this is DatabaseResult.Error

/**
 * Returns true if the result is Loading.
 */
fun <T> DatabaseResult<T>.isLoading(): Boolean = this is DatabaseResult.Loading

