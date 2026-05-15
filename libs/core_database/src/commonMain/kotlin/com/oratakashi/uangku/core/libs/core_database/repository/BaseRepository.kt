package com.oratakashi.uangku.core.libs.core_database.repository

import com.oratakashi.uangku.core.libs.core_database.DatabaseResult
import com.oratakashi.uangku.core.libs.core_database.dao.BaseDao
import com.oratakashi.uangku.core.libs.core_database.helper.SafeDatabaseCall

/**
 * Abstract base repository providing standard CRUD operations for all entity types.
 * Eliminates the need to re-declare the four identical CRUD functions that would otherwise
 * be duplicated in every repository in every consumer project.
 *
 * Consumer repositories should extend this class and add only query-specific functions.
 * The base class handles all standard CRUD operations with proper exception mapping
 * through SafeDatabaseCall.
 *
 * Example usage in a consumer project:
 * ```
 * class UserRepository(
 *     private val userDao: UserDao
 * ) : BaseRepository<UserEntity, UserDao>() {
 *
 *     override val dao: UserDao = userDao
 *
 *     // insert(), insertAll(), update(), delete() are inherited — do not re-write them
 *
 *     // Only write functions that are specific to UserEntity
 *     suspend fun getById(id: Long): DatabaseResult<UserEntity?> =
 *         SafeDatabaseCall.executeRead { dao.getById(id) }
 *
 *     suspend fun getAll(): DatabaseResult<List<UserEntity>> =
 *         SafeDatabaseCall.executeRead { dao.getAll() }
 * }
 * ```
 *
 * @param Entity The entity type managed by the DAO.
 * @param Dao The DAO interface managing the entity (must extend BaseDao<Entity>).
 *
 * @author oratakashi
 * @since 15 May 2026
 */
abstract class BaseRepository<Entity, Dao : BaseDao<Entity>> {

    /**
     * The DAO instance used by this repository.
     * Protected visibility ensures the DAO is not accidentally exposed as part of
     * the repository's public API, while allowing the base class to call DAO methods directly.
     */
    protected abstract val dao: Dao

    /**
     * Inserts a single entity into the database.
     * If an entity with the same primary key exists, it is replaced.
     *
     * @param entity The entity to insert.
     * @return A DatabaseResult containing the row ID of the inserted entity, or an error.
     */
    suspend fun insert(entity: Entity): DatabaseResult<Long> =
        SafeDatabaseCall.executeWrite { dao.insert(entity) }

    /**
     * Inserts multiple entities into the database.
     * If entities with the same primary key exist, they are replaced.
     *
     * @param entities The list of entities to insert.
     * @return A DatabaseResult<Unit> indicating success or an error.
     */
    suspend fun insertAll(entities: List<Entity>): DatabaseResult<Unit> =
        SafeDatabaseCall.executeWrite { dao.insertAll(entities) }

    /**
     * Updates an existing entity in the database.
     *
     * @param entity The entity with updated values.
     * @return A DatabaseResult<Unit> indicating success or an error.
     */
    suspend fun update(entity: Entity): DatabaseResult<Unit> =
        SafeDatabaseCall.executeWrite { dao.update(entity) }

    /**
     * Deletes an entity from the database.
     *
     * @param entity The entity to delete.
     * @return A DatabaseResult<Unit> indicating success or an error.
     */
    suspend fun delete(entity: Entity): DatabaseResult<Unit> =
        SafeDatabaseCall.executeWrite { dao.delete(entity) }
}

