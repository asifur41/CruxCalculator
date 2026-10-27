package com.tapecalc.core.data

import com.tapecalc.core.data.db.HistoryDao
import com.tapecalc.core.data.db.HistoryEntity
import com.tapecalc.core.data.db.VariableDao
import com.tapecalc.core.data.db.VariableEntity
import com.tapecalc.core.data.db.WorkspaceDao
import com.tapecalc.core.data.db.WorkspaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-ins for the Room DAOs so repository logic can be tested on the JVM. */
class FakeWorkspaceDao : WorkspaceDao {
    private val state = MutableStateFlow<List<WorkspaceEntity>>(emptyList())
    private var nextId = 1L

    private fun sorted(list: List<WorkspaceEntity>) = list.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override fun observeAll(): Flow<List<WorkspaceEntity>> = state.map { sorted(it) }
    override suspend fun getAll(): List<WorkspaceEntity> = sorted(state.value)
    override suspend fun count(): Int = state.value.size
    override suspend fun insert(entity: WorkspaceEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        state.value = state.value + entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: WorkspaceEntity) {
        state.value = state.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeHistoryDao : HistoryDao {
    private val state = MutableStateFlow<List<HistoryEntity>>(emptyList())
    private var nextId = 1L

    override fun observe(workspaceId: Long): Flow<List<HistoryEntity>> =
        state.map { list -> list.filter { it.workspaceId == workspaceId }.sortedBy { it.id } }

    override suspend fun getAll(workspaceId: Long): List<HistoryEntity> =
        state.value.filter { it.workspaceId == workspaceId }.sortedBy { it.id }

    override suspend fun get(id: Long): HistoryEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun insert(entity: HistoryEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        state.value = state.value + entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: HistoryEntity) {
        state.value = state.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun updateAll(entities: List<HistoryEntity>) {
        val byId = entities.associateBy { it.id }
        state.value = state.value.map { byId[it.id] ?: it }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun clear(workspaceId: Long) {
        state.value = state.value.filterNot { it.workspaceId == workspaceId }
    }
}

class FakeVariableDao : VariableDao {
    private val state = MutableStateFlow<List<VariableEntity>>(emptyList())

    override fun observeAll(): Flow<List<VariableEntity>> = state.map { list -> list.sortedBy { it.name.lowercase() } }
    override suspend fun getAll(): List<VariableEntity> = state.value
    override suspend fun upsert(entity: VariableEntity) {
        state.value = state.value.filterNot { it.name == entity.name } + entity
    }

    override suspend fun delete(name: String) {
        state.value = state.value.filterNot { it.name == name }
    }
}
