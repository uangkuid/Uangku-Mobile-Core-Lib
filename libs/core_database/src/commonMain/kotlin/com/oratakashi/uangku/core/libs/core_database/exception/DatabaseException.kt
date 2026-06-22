package com.oratakashi.uangku.core.libs.core_database.exception

/**
 * Sealed class representing all possible database operation failures.
 * Each subtype represents a distinct failure scenario at the database layer.
 * Consumers should map all database exceptions to these types for consistent error handling.
 *
 * @author oratakashi
 * @since 15 May 2026
 */
sealed class DatabaseException(
    message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Thrown when a query executes successfully but returns no result.
     * This is not thrown by SQLite itself, but by SafeDatabaseCall when
     * a nullable query result is null or a non-nullable query result is null.
     */
    class NotFound : DatabaseException(MESSAGE_NOT_FOUND)

    /**
     * Thrown when an INSERT operation fails due to constraint violation,
     * disk full, or other SQLite-level rejection.
     *
     * @param cause The underlying exception that caused the insert failure.
     */
    data class InsertFailed(val causedException: Throwable) :
        DatabaseException(MESSAGE_INSERT_FAILED, causedException)

    /**
     * Thrown when an UPDATE operation fails.
     * Rare but possible when constraints are violated on the updated value.
     *
     * @param cause The underlying exception that caused the update failure.
     */
    data class UpdateFailed(val causedException: Throwable) :
        DatabaseException(MESSAGE_UPDATE_FAILED, causedException)

    /**
     * Thrown when a DELETE operation fails.
     * Possible when foreign key constraints are enabled and a referenced row cannot be deleted.
     *
     * @param cause The underlying exception that caused the delete failure.
     */
    data class DeleteFailed(val causedException: Throwable) :
        DatabaseException(MESSAGE_DELETE_FAILED, causedException)

    /**
     * Thrown when a SELECT or any read query fails at the SQLite level.
     * Examples: malformed SQL in a @RawQuery, schema mismatch after migration.
     *
     * @param cause The underlying exception that caused the query failure.
     */
    data class QueryFailed(val causedException: Throwable) :
        DatabaseException(MESSAGE_QUERY_FAILED, causedException)

    /**
     * Thrown when a @Transaction-annotated function rolls back or throws.
     * A failure inside a transaction may involve multiple operations
     * and should be distinguishable from a single-operation failure.
     *
     * @param cause The underlying exception that caused the transaction failure.
     */
    data class TransactionFailed(val causedException: Throwable) :
        DatabaseException(MESSAGE_TRANSACTION_FAILED, causedException)

    /**
     * Thrown when any exception is not covered by the above subtypes.
     * Examples: unexpected NullPointerException inside a DAO body,
     * or a coroutine cancellation that propagates as an exception.
     *
     * @param cause The underlying unexpected exception.
     */
    data class Unknown(val causedException: Throwable) :
        DatabaseException(MESSAGE_UNKNOWN, causedException)

    companion object {
        private const val MESSAGE_NOT_FOUND = "No data found in the database"
        private const val MESSAGE_INSERT_FAILED = "Failed to insert data"
        private const val MESSAGE_UPDATE_FAILED = "Failed to update data"
        private const val MESSAGE_DELETE_FAILED = "Failed to delete data"
        private const val MESSAGE_QUERY_FAILED = "Failed to query data"
        private const val MESSAGE_TRANSACTION_FAILED = "Database transaction failed"
        private const val MESSAGE_UNKNOWN = "Unknown database error"
    }
}


