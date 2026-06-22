package com.oratakashi.uangku.core.libs.core_database.helper

import com.oratakashi.uangku.core.libs.core_database.DatabaseResult
import com.oratakashi.uangku.core.libs.core_database.exception.DatabaseException
import kotlinx.coroutines.CancellationException

/**
 * Centralized execution wrapper for all DAO calls that maps every possible exception
 * to a typed DatabaseException and returns a DatabaseResult.
 *
 * No consumer repository should ever contain a try-catch block for database operations.
 * All database operations should go through one of the functions in this object.
 *
 * Exception mapping strategy:
 * - NullPointerException → DatabaseException.NotFound
 * - SQLiteException with "UNIQUE constraint" → DatabaseException.InsertFailed
 * - SQLiteException with "no such table" or query syntax errors → DatabaseException.QueryFailed
 * - SQLiteException (other) → DatabaseException.Unknown
 * - Any other Exception → DatabaseException.Unknown
 *
 * CancellationException is never caught and always propagates to preserve
 * coroutine structured concurrency semantics.
 *
 * @author oratakashi
 * @since 15 May 2026
 */
object SafeDatabaseCall {

    /**
     * Executes a read (SELECT) database operation with automatic exception mapping.
     * Use this for queries that retrieve data (getById, getAll, custom queries, etc.).
     *
     * @param T The type of data returned by the operation.
     * @param block The suspend lambda that performs the read operation.
     * @return A DatabaseResult containing the success data or the mapped exception.
     */
    suspend fun <T> executeRead(block: suspend () -> T): DatabaseResult<T> {
        return execute(block)
    }

    /**
     * Executes a write (INSERT, UPDATE, DELETE) database operation with automatic exception mapping.
     * Use this for operations that modify data (insert, insertAll, update, delete, etc.).
     *
     * The distinction between executeRead and executeWrite is semantic and expressed
     * in the call site for clarity. Both use the same exception mapping internally.
     *
     * @param T The type of data returned by the operation (often Unit for write operations).
     * @param block The suspend lambda that performs the write operation.
     * @return A DatabaseResult containing the success data or the mapped exception.
     */
    suspend fun <T> executeWrite(block: suspend () -> T): DatabaseResult<T> {
        return execute(block)
    }

    /**
     * Internal shared implementation of exception mapping and wrapping.
     * Maps SQLite and Room-specific exceptions to typed DatabaseException subtypes.
     *
     * @param T The type of data returned by the operation.
     * @param block The suspend lambda to execute and wrap with exception handling.
     * @return A DatabaseResult containing the success data or the mapped exception.
     */
    private suspend fun <T> execute(block: suspend () -> T): DatabaseResult<T> {
        return try {
            val result = block()
            DatabaseResult.Success(result)
        } catch (e: CancellationException) {
            // Never catch CancellationException - it must propagate to preserve
            // coroutine structured concurrency semantics
            throw e
        } catch (e: NullPointerException) {
            // Room throws NPE when a non-null return type DAO function receives null
            DatabaseResult.Error(DatabaseException.NotFound())
        } catch (e: Exception) {
            val mappedException = mapException(e)
            DatabaseResult.Error(mappedException)
        }
    }

    /**
     * Maps a generic exception to a specific DatabaseException subtype based on
     * the exception message and type.
     *
     * @param e The exception to map.
     * @return The appropriate DatabaseException subtype.
     */
    private fun mapException(e: Exception): DatabaseException {
        val message = e.message?.lowercase() ?: ""

        return when {
            message.contains("unique constraint") -> {
                DatabaseException.InsertFailed(e)
            }

            message.contains("no such table") || message.contains("syntax error") -> {
                DatabaseException.QueryFailed(e)
            }

            e::class.simpleName?.contains("SQLiteException", ignoreCase = true) == true -> {
                // Generic SQLite errors that don't match known patterns
                when {
                    message.contains("insert") -> DatabaseException.InsertFailed(e)
                    message.contains("update") -> DatabaseException.UpdateFailed(e)
                    message.contains("delete") -> DatabaseException.DeleteFailed(e)
                    message.contains("select") || message.contains("query") -> {
                        DatabaseException.QueryFailed(e)
                    }

                    else -> DatabaseException.Unknown(e)
                }
            }

            else -> DatabaseException.Unknown(e)
        }
    }
}

