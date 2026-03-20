package com.oratakashi.uangku.core.libs.core_network.exception

/**
 * NetworkException is a sealed class representing various network-related errors that can occur during API communication.
 * This class provides a structured way to handle client, server, network, and parsing errors in a multiplatform environment.
 *
 * Note: All exception types should be instantiated as classes (not objects) to ensure proper stack trace and error handling.
 *
 * @author oratakashi
 * @since 20 Mar 2026
 * @constructor Creates a NetworkException with an optional message.
 * @param message The detail message for the exception.
 */
sealed class NetworkException(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        private const val UNAUTHORIZED_MESSAGE = "Unauthorized access"
        private const val FORBIDDEN_MESSAGE = "Access forbidden"
        private const val NOT_FOUND_MESSAGE = "Resource not found"
        private const val BAD_REQUEST_MESSAGE = "Invalid request"
        private const val NO_INTERNET_MESSAGE = "No internet connection"
        private const val TIMEOUT_MESSAGE = "Request timeout"
        private const val SERVER_ERROR_MESSAGE = "Server error: "
        private const val PARSE_ERROR_MESSAGE = "Failed to parse response"
    }

    /**
     * Represents HTTP 401 Unauthorized error.
     */
    class Unauthorized : NetworkException(UNAUTHORIZED_MESSAGE)

    /**
     * Represents HTTP 403 Forbidden error.
     */
    class Forbidden : NetworkException(FORBIDDEN_MESSAGE)

    /**
     * Represents HTTP 404 Not Found error.
     */
    class NotFound : NetworkException(NOT_FOUND_MESSAGE)

    /**
     * Represents HTTP 400 Bad Request error with optional field errors.
     * @param errors A map of field names to error messages, or null if not available.
     */
    data class BadRequest(val errors: Map<String, String>?) : NetworkException(BAD_REQUEST_MESSAGE)

    /**
     * Represents HTTP 5xx Server Error with a specific status code.
     * @param code The HTTP status code returned by the server.
     */
    data class ServerError(val code: Int) : NetworkException("$SERVER_ERROR_MESSAGE$code")

    /**
     * Represents a network error when there is no internet connection.
     */
    class NoInternet : NetworkException(NO_INTERNET_MESSAGE)

    /**
     * Represents a network error when a request times out.
     */
    class Timeout : NetworkException(TIMEOUT_MESSAGE)

    /**
     * Represents an error when parsing the response fails.
     * @param cause The underlying cause of the parsing failure.
     */
    data class ParseError(val error: Throwable) : NetworkException(PARSE_ERROR_MESSAGE, error)

    /**
     * Represents an unknown error with an optional cause.
     * @param cause The underlying cause of the unknown error.
     */
    data class Unknown(val error: Throwable) : NetworkException(error.message, error)
}
