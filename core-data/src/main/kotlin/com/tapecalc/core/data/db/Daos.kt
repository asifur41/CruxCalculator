package com.tapecalc.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<WorkspaceEntity>

    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: WorkspaceEntity): Long

    @Update
    suspend fun update(entity: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE workspaceId = :workspaceId ORDER BY id ASC")
    fun observe(workspaceId: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE workspaceId = :workspaceId ORDER BY id ASC")
    suspend fun getAll(workspaceId: Long): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: Long): HistoryEntity?

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Update
    suspend fun update(entity: HistoryEntity)

    @Update
    suspend fun updateAll(entities: List<HistoryEntity>)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE workspaceId = :workspaceId")
    suspend fun clear(workspaceId: Long)
}

@Dao
interface VariableDao {
    @Query("SELECT * FROM variables ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<VariableEntity>>

    @Query("SELECT * FROM variables")
    suspend fun getAll(): List<VariableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VariableEntity)

    @Query("DELETE FROM variables WHERE name = :name")
    suspend fun delete(name: String)
}
