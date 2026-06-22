package com.oratakashi.uangku.core.libs.core_database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/**
 * Generic DAO interface providing standard CRUD operations for all entity types.
 * Consumer DAOs should extend this interface and add only query-specific functions.
 * This eliminates the need to re-declare the four standard CRUD methods in every DAO.
 *
 * @param T The entity type managed by this DAO.
 * @author oratakashi
 * @since 15 May 2026
 */
@Dao
interface BaseDao<T> {

    /**
     * Inserts a single entity into the database.
     * If an entity with the same primary key exists, it is replaced.
     *
     * @param entity The entity to insert.
     * @return The row ID of the inserted or replaced entity.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: T): Long

    /**
     * Inserts multiple entities into the database.
     * If entities with the same primary key exist, they are replaced.
     *
     * @param entities The list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<T>)

    /**
     * Updates an existing entity in the database.
     *
     * @param entity The entity with updated values.
     */
    @Update
    suspend fun update(entity: T)

    /**
     * Deletes an entity from the database.
     *
     * @param entity The entity to delete.
     */
    @Delete
    suspend fun delete(entity: T)
}

