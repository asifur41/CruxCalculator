package com.tapecalc.core.data.db

import com.tapecalc.core.data.domain.EntryStatus
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.data.domain.VariableItem
import com.tapecalc.core.data.domain.Workspace
import com.tapecalc.core.math.AngleMode

private const val TAG_SEPARATOR = ","

fun encodeTags(tags: List<String>): String =
    tags.map { it.trim().replace(TAG_SEPARATOR, " ").removePrefix("#") }.filter { it.isNotEmpty() }.distinct().joinToString(TAG_SEPARATOR)

fun decodeTags(text: String): List<String> = text.split(TAG_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

fun WorkspaceEntity.toDomain() = Workspace(id, name, sortOrder)

fun HistoryEntity.toDomain() = HistoryEntry(
    id = id,
    workspaceId = workspaceId,
    expression = expression,
    resultText = resultText,
    resultFull = resultFull,
    resultValue = resultValue,
    fraction = fraction,
    unit = unit,
    isError = isError,
    errorMessage = errorMessage,
    label = label,
    note = note,
    tags = decodeTags(tags),
    pinned = pinned,
    status = EntryStatus.fromName(status),
    angleMode = AngleMode.fromName(angleMode),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HistoryEntry.toEntity() = HistoryEntity(
    id = id,
    workspaceId = workspaceId,
    expression = expression,
    resultText = resultText,
    resultFull = resultFull,
    resultValue = resultValue,
    fraction = fraction,
    unit = unit,
    isError = isError,
    errorMessage = errorMessage,
    label = label?.takeIf { it.isNotBlank() },
    note = note?.takeIf { it.isNotBlank() },
    tags = encodeTags(tags),
    pinned = pinned,
    status = status.name,
    angleMode = angleMode.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun VariableEntity.toDomain() = VariableItem(name, valueText, displayText, note)
