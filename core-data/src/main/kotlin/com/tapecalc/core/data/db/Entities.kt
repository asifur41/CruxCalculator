package com.tapecalc.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workspaceId")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: Long,
    val expression: String,
    val resultText: String,
    val resultFull: String,
    val resultValue: String,
    val fraction: String?,
    val unit: String?,
    val isError: Boolean,
    val errorMessage: String?,
    val label: String?,
    val note: String?,
    val tags: String,
    val pinned: Boolean,
    val status: String,
    val angleMode: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "variables")
data class VariableEntity(
    @PrimaryKey val name: String,
    val valueText: String,
    val displayText: String,
    val note: String?,
    val updatedAt: Long
)
